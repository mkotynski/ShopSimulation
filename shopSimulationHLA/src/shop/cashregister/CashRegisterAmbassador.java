package shop.cashregister;

import shop.customer.CustomerFederate;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;
import shop.cashregister.ExternalEvent;

import java.util.ArrayList;

public class CashRegisterAmbassador extends NullFederateAmbassador {
  protected double federateTime = 0.0;
  protected double grantedTime = 0.0;
  protected double federateLookahead = 1.0;

  protected boolean isRegulating = false;
  protected boolean isConstrained = false;
  protected boolean isAdvancing = false;

  protected boolean isAnnounced = false;
  protected boolean isReadyToRun = false;

  protected boolean running = true;

  protected int waitingQueueHandle;

  protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

  int numberOfQueues = 0;

  public CashRegisterAmbassador() {
  }

  private double convertTime(LogicalTime logicalTime) {
    // PORTICO SPECIFIC!!
    return ((DoubleTime) logicalTime).getTime();
  }

  private void log(String message) {
    System.out.println("CashRegisterAmbassador: " + message);
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
    this.grantedTime = convertTime(theTime);
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
        externalEvents.add(new ExternalEvent(queueSize, ExternalEvent.EventType.ADD, time));
        numberOfQueues++;
      }
    } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
      arrayIndexOutOfBounds.printStackTrace();
    }
  }

  @Override
  public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
    System.out.println("Pojawil sie nowy obiekt typu WaitingQueue");
    waitingQueueHandle = theObject;
  }

}
