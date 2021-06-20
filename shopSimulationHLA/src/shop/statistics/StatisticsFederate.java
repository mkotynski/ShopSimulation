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
  private final double timeStep = 1.0;
  private int customerNumber = 0;
  private double waitingTimeSum = 0;

  private int statisticsHLAObject = 0;

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
    rtiamb.joinFederationExecution("StatisticsFederate", "Shop-Federation", statisticsAmbassador);
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

    registerObject();

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
          if (externalEvent.getEventType() == ExternalEvent.EventType.WAITING_TIME) {
            customerNumber++;
            waitingTimeSum += externalEvent.getWaitingTime();
            updateHLAObject(waitingTimeSum / customerNumber, timeToAdvance + statisticsAmbassador.federateLookahead);
          }
        }
        statisticsAmbassador.externalEvents.clear();
      }


      if (statisticsAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += statisticsAmbassador.federateLookahead;
        statisticsAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void registerObject() throws RTIexception {
    int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Statistics");
    this.statisticsHLAObject = rtiamb.registerObjectInstance(classHandle);
  }

  private void updateHLAObject(double avgWaitingTime, double time) throws RTIexception {
    SuppliedAttributes attributes =
        RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

    int classHandle = rtiamb.getObjectClass(statisticsHLAObject);
    int avgWaitingTImeHandle = rtiamb.getAttributeHandle("avgWaitingTime", classHandle);
    byte[] avgWaitingTimeValue = EncodingHelpers.encodeDouble(avgWaitingTime);

    attributes.add(avgWaitingTImeHandle, avgWaitingTimeValue);
    LogicalTime logicalTime = convertTime(time);
    rtiamb.updateAttributeValues(statisticsHLAObject, attributes, "actualize average waiting time".getBytes(), logicalTime);
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

    int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Statistics");
    int avgWaitingTimeHandle = rtiamb.getAttributeHandle("avgWaitingTime", classHandle);

    AttributeHandleSet attributesStats =
        RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
    attributesStats.add(avgWaitingTimeHandle);

    rtiamb.publishObjectClass(classHandle, attributesStats);

    int waitingTimeHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendWaitingTime");
    statisticsAmbassador.waitingTimeHandle = waitingTimeHandle;
    rtiamb.subscribeInteractionClass(waitingTimeHandle);
  }

  private void advanceTime(double timeToAdvance) throws RTIexception {
    // request the advance
    statisticsAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(timeToAdvance);
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
