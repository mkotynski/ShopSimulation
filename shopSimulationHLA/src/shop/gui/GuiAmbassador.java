package shop.gui;

import shop.customer.CustomerFederate;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class GuiAmbassador extends NullFederateAmbassador {
  protected double grantedTime = 0.0;
  protected double federateTime = 0.0;
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

  private List<Integer> queuesSizesList = new ArrayList<>();

  public GuiAmbassador() {
  }

  private double convertTime(LogicalTime logicalTime) {
    // PORTICO SPECIFIC!!
    return ((DoubleTime) logicalTime).getTime();
  }

  private void log(String message) {
    System.out.println("GuiAmbassador: " + message);
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

  @SuppressWarnings("unchecked")
  public void reflectAttributeValues( int theObject,
                                      ReflectedAttributes theAttributes,
                                      byte[] tag,
                                      LogicalTime theTime,
                                      EventRetractionHandle retractionHandle ) {

    try {
      double time = convertTime(theTime);
      List<Integer> queuesSizes;
      int newNumberOfQueue = EncodingHelpers.decodeInt(theAttributes.getValue(0));
      byte[] queuesSizesValue = theAttributes.getValue(1);

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(queuesSizesValue));
      try{
        queuesSizes = (List<Integer>) ois.readObject();
      } finally {
        ois.close();
      }

      if(queuesSizesList.size() == queuesSizes.size()) {
        for(int i=0 ; i < queuesSizesList.size(); i++) {
          if(!queuesSizesList.get(i).equals(queuesSizes.get(i))) {
            queuesSizesList = queuesSizes;
            externalEvents.add(new ExternalEvent(queuesSizes, ExternalEvent.EventType.UPDATE_QUEUES_SIZES, time));
            break;
          }
        }
      } else {
        externalEvents.add(new ExternalEvent(queuesSizes, ExternalEvent.EventType.UPDATE_QUEUES_SIZES, time));
      }

      if (numberOfQueues < newNumberOfQueue) {
        externalEvents.add(new ExternalEvent(newNumberOfQueue, ExternalEvent.EventType.UPDATE_NUMBER_OF_QUEUE, time));
        numberOfQueues++;
      }
    } catch (ArrayIndexOutOfBounds | IOException | ClassNotFoundException arrayIndexOutOfBounds) {
      arrayIndexOutOfBounds.printStackTrace();
    }
  }

  @Override
  public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) {
    System.out.println("Pojawil sie nowy obiekt typu WaitingQueue");
    waitingQueueHandle = theObject;
  }
}
