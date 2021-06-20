package shop.statistics;

import shop.customer.CustomerFederate;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;
import shop.models.Customer;

import java.util.ArrayList;

public class StatisticsAmbassador extends NullFederateAmbassador {
  protected double grantedTime = 0.0;
  protected double federateTime = 0.0;
  protected double federateLookahead = 1.0;

  protected boolean isRegulating = false;
  protected boolean isConstrained = false;
  protected boolean isAdvancing = false;

  protected boolean isAnnounced = false;
  protected boolean isReadyToRun = false;

  protected boolean running = true;

  protected int waitingQueueHandle = 0;
  protected int waitingTimeHandle = 1;

  protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

  int numberOfQueues = 0;


  public StatisticsAmbassador() {
  }

  private double convertTime(LogicalTime logicalTime) {
    // PORTICO SPECIFIC!!
    return ((DoubleTime) logicalTime).getTime();
  }

  private void log(String message) {
    System.out.println("StatisticsAmbassador: " + message);
  }

  public void synchronizationPointRegistrationFailed(String label) {
    log("Failed to register sync point: " + label);
  }

  public void synchronizationPointRegistrationSucceeded(String label) {
    log("Successfully registered sync point: " + label);
  }

  public void announceSynchronizationPoint(String label, byte[] tag) {
    log("Synchronization point announced: " + label);
    if (label.equals(CustomerFederate.READY_TO_RUN))
      this.isAnnounced = true;
  }

  public void federationSynchronized(String label) {
    log("Federation Synchronized: " + label);
    if (label.equals(CustomerFederate.READY_TO_RUN))
      this.isReadyToRun = true;
  }

  public void timeRegulationEnabled(LogicalTime theFederateTime) {
    this.federateTime = convertTime(theFederateTime);
    this.isRegulating = true;
  }

  public void timeConstrainedEnabled(LogicalTime theFederateTime) {
    this.federateTime = convertTime(theFederateTime);
    this.isConstrained = true;
  }

  public void timeAdvanceGrant(LogicalTime theTime) {
    this.federateTime = convertTime(theTime);
    this.isAdvancing = false;
  }

  public void reflectAttributeValues(int theObject,
                                     ReflectedAttributes theAttributes, byte[] tag) {
    reflectAttributeValues(theObject, theAttributes, tag, null, null);
  }

  public void reflectAttributeValues( int theObject,
                                      ReflectedAttributes theAttributes,
                                      byte[] tag,
                                      LogicalTime theTime,
                                      EventRetractionHandle retractionHandle ) {

    try {
      double time = convertTime(theTime);
      int queueSize = EncodingHelpers.decodeInt(theAttributes.getValue(0));
      if (numberOfQueues < queueSize) {
        externalEvents.add(new ExternalEvent(queueSize, ExternalEvent.EventType.UPDATE_QUEUE_SIZE, time));
        numberOfQueues++;
      }
    } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
      arrayIndexOutOfBounds.printStackTrace();
    }
  }

  @Override
  public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) {
    System.out.println("Pojawil sie nowy obiekt typu WaitingQueue");
    waitingQueueHandle = theObject;
  }

  public void receiveInteraction(int interactionClass,
                                 ReceivedInteraction theInteraction,
                                 byte[] tag) {
    // just pass it on to the other method for printing purposes
    // passing null as the time will let the other method know it
    // it from us, not from the RTI
    receiveInteraction(interactionClass, theInteraction, tag, null, null);
  }

  public void receiveInteraction(int interactionClass,
                                 ReceivedInteraction theInteraction,
                                 byte[] tag,
                                 LogicalTime theTime,
                                 EventRetractionHandle eventRetractionHandle) {
    if (interactionClass == waitingTimeHandle) {
      try {
        double waitingTime = EncodingHelpers.decodeDouble(theInteraction.getValue(0));

        double time = convertTime(theTime);
        externalEvents.add(new ExternalEvent(waitingTime, ExternalEvent.EventType.WAITING_TIME, time));
      } catch (ArrayIndexOutOfBounds ignored) {
      }
    }
  }

}
