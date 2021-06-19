package shop.statistics;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class StatisticsFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private StatisticsAmbassador statisticsAmbassador;
  private final double timeStep = 10.0;
  private int stock = 10;
  private int storageHlaHandle;

  public void runFederate() throws RTIexception {

    rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

    try {
      File fom = new File("shop-simulation.fed");
      rtiamb.createFederationExecution("", fom.toURI().toURL());
      log("Created Federation");
    } catch (FederationExecutionAlreadyExists exists) {
      log("Didn't create federation, it already existed");
    } catch (MalformedURLException urle) {
      log("Exception processing fom: " + urle.getMessage());
      urle.printStackTrace();
      return;
    }

    statisticsAmbassador = new StatisticsAmbassador();
    rtiamb.joinFederationExecution("StatisticsFederate", "ShoppingFederation", statisticsAmbassador);
    log("Joined Federation as StatisticsFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (statisticsAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (statisticsAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    while (statisticsAmbassador.running) {
      double timeToAdvance = statisticsAmbassador.federateTime + timeStep;
      advanceTime(timeToAdvance);

      if (!statisticsAmbassador.externalEvents.isEmpty()) {
        statisticsAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : statisticsAmbassador.externalEvents) {
          statisticsAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.UPDATE_QUEUE_SIZE) {
            //TODO odbior danych z eventu
          }
        }
        statisticsAmbassador.externalEvents.clear();
      }


      if (statisticsAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += statisticsAmbassador.federateLookahead;
        //TODO SREDNI CZAS OCZEKIWANIA
//        updateHLAObject(timeToAdvance);
        statisticsAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
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
    LogicalTime currentTime = convertTime(statisticsAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(statisticsAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (statisticsAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (statisticsAmbassador.isConstrained == false) {
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
  }

  private void advanceTime(double timestep) throws RTIexception {
    log("requesting time advance for: " + timestep);
    // request the advance
    statisticsAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(statisticsAmbassador.federateTime + timestep);
    rtiamb.timeAdvanceRequest(newTime);
    while (statisticsAmbassador.isAdvancing) {
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
    System.out.println("StatisticsFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new StatisticsFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }
}
