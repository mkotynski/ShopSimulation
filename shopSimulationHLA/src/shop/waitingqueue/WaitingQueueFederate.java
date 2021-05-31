package shop.waitingqueue;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import shop.models.Customer;
import shop.models.WaitingQueue;
import shop.waitingqueue.ExternalEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class WaitingQueueFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private WaitingQueueAmbassador waitingQueueAmbassador;
  private final double timeStep = 10.0;
  private int numberOfQueues = 5;
  private List<WaitingQueue> waitingQueueList;

  public void runFederate() throws Exception {

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

    waitingQueueAmbassador = new WaitingQueueAmbassador();
    rtiamb.joinFederationExecution("WaitingQueueFederate", "Shop-Federation", waitingQueueAmbassador);
    log("Joined Federation as WaitingQueueFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (waitingQueueAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (waitingQueueAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    createWaitingQueue(numberOfQueues);

    while (waitingQueueAmbassador.running) {
      double timeToAdvance = waitingQueueAmbassador.federateTime + timeStep;
      advanceTime(timeStep);

      if (!waitingQueueAmbassador.externalEvents.isEmpty()) {
        waitingQueueAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : waitingQueueAmbassador.externalEvents) {
          waitingQueueAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.ADD) {
            this.addToShortesQueue(externalEvent.getCustomer());
          }
        }
        waitingQueueAmbassador.externalEvents.clear();
      }
      if (waitingQueueAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += waitingQueueAmbassador.federateLookahead;
        waitingQueueAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void addToShortesQueue(Customer customer) throws Exception {
    WaitingQueue shortestQueue = waitingQueueList.stream()
        .min(Comparator.comparingInt(WaitingQueue::getSize))
        .orElseThrow(() -> new Exception("Brak kolejek!!!"));
    shortestQueue.addCustomer(customer);
    log("Klient id: " + customer.getId() + " dodany do kolejki nr: " + shortestQueue.getId());
  }

  private void createWaitingQueue(int numberOfQueues) {
    waitingQueueList = new ArrayList<>(numberOfQueues);
    for (int i = 0; i < numberOfQueues; i++) {
      waitingQueueList.add(new WaitingQueue(i));
    }
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
    LogicalTime currentTime = convertTime(waitingQueueAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(waitingQueueAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (waitingQueueAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (waitingQueueAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int customerStopShoppingHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStopShopping");
    waitingQueueAmbassador.customerStopShoppingHandle = customerStopShoppingHandle;
    rtiamb.subscribeInteractionClass(customerStopShoppingHandle);
  }

  private void advanceTime(double timestep) throws RTIexception {
    // request the advance
    waitingQueueAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(waitingQueueAmbassador.federateTime + timestep);
    rtiamb.timeAdvanceRequest(newTime);
    while (waitingQueueAmbassador.isAdvancing) {
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
    System.out.println("WaitingQueueFederate  : " + message);
  }

  public static void main(String[] args) throws Exception {
    try {
      new WaitingQueueFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }
}
