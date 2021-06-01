package shop.cashregister;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import shop.models.CashRegister;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//TODO odebrac interakcje z id uzytkownika itd
//TODO rozpoczac obsluge klienta i usunac go ze sklepu
public class CashRegisterFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private CashRegisterAmbassador cashRegisterAmbassador;
  private List<CashRegister> cashRegisterList = new ArrayList<>();

  public void runFederate() throws RTIexception {

    rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

    try {
      File fom = new File("shop-simulation.fed");
      rtiamb.createFederationExecution("Shop-Federation", fom.toURI().toURL());
      log("Created Federation");
    } catch (FederationExecutionAlreadyExists exists) {
      log("Didn't create federation, it already existed");
    } catch (MalformedURLException urle) {
      log("Exception processing fom: " + urle.getMessage());
      urle.printStackTrace();
      return;
    }

    cashRegisterAmbassador = new CashRegisterAmbassador();
    rtiamb.joinFederationExecution("CashRegisterFederate", "Shop-Federation", cashRegisterAmbassador);
    log("Joined Federation as CashRegisterFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (cashRegisterAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (cashRegisterAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    while (cashRegisterAmbassador.running) {
      double timeToAdvance = cashRegisterAmbassador.federateTime + cashRegisterAmbassador.federateLookahead;
      advanceTime(cashRegisterAmbassador.federateLookahead);

      for (int i = 0; i < cashRegisterList.size(); i++) {
        if (cashRegisterList.get(i).isFree()) {
          try {
            freeCashRegister(cashRegisterList.get(i).getId(), timeToAdvance + 1.0);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      }

      if (!cashRegisterAmbassador.externalEvents.isEmpty()) {
        cashRegisterAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : cashRegisterAmbassador.externalEvents) {
          cashRegisterAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.ADD) {
            createNewCashRegister();
          }
        }
        cashRegisterAmbassador.externalEvents.clear();
      }
      if (cashRegisterAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += cashRegisterAmbassador.federateLookahead;
        cashRegisterAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void createNewCashRegister() {
    cashRegisterList.add(new CashRegister(cashRegisterList.size() + 1));
    log("Otworzono nowa kase nr: " + cashRegisterList.size());
  }

  private void freeCashRegister(int id, double timeStep) throws RTIexception {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    byte[] idVar = EncodingHelpers.encodeInt(id);
    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FreeCashRegister");
    int idHandle = rtiamb.getParameterHandle("id", interactionHandle);

    parameters.add(idHandle, idVar);

    LogicalTime time = convertTime(timeStep);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
    log("Kasa, id: " + id + " jest wolna, czas: " + cashRegisterAmbassador.federateTime);
  }

  private void waitForUser() {
    log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    try {
      reader.readLine();
    } catch (Exception e) {
      log("Error while waiting for user input: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void enableTimePolicy() throws RTIexception {
    LogicalTime currentTime = convertTime(cashRegisterAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(cashRegisterAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (cashRegisterAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (cashRegisterAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int simObjectClassHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingQueue");
    int numberOfQueuesHandle = rtiamb.getAttributeHandle("numberOfQueues", simObjectClassHandle);

    AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory()
        .createAttributeHandleSet();
    attributes.add(numberOfQueuesHandle);

    rtiamb.subscribeObjectClassAttributes(simObjectClassHandle, attributes);

    int freeCashRegisterHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FreeCashRegister");
    rtiamb.publishInteractionClass(freeCashRegisterHandle);
  }

  private void advanceTime(double timestep) throws RTIexception {
    // request the advance
    cashRegisterAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(cashRegisterAmbassador.federateTime + timestep);
    rtiamb.timeAdvanceRequest(newTime);
    while (cashRegisterAmbassador.isAdvancing) {
      rtiamb.tick();
    }
  }

  private double randomTime() {
    Random r = new Random();
    return 1 + (9 * r.nextDouble());
  }

  private LogicalTime convertTime(double time) {
    // PORTICO SPECIFIC!!
    return new DoubleTime(time);
  }

  private LogicalTimeInterval convertInterval(double time) {
    // PORTICO SPECIFIC!!
    return new DoubleTimeInterval(time);
  }

  private void log(String message) {
    System.out.println("CashRegisterFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new CashRegisterFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }
}
