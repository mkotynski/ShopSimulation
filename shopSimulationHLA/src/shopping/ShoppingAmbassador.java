package shopping;

import customer.CustomerFederate;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;

public class ShoppingAmbassador extends NullFederateAmbassador {
  protected double federateTime = 0.0;
  protected double federateLookahead = 1.0;

  protected boolean isRegulating = false;
  protected boolean isConstrained = false;
  protected boolean isAdvancing = false;

  protected boolean isAnnounced = false;
  protected boolean isReadyToRun = false;

  protected boolean running = true;

  public ShoppingAmbassador() {
  }

  private double convertTime(LogicalTime logicalTime) {
    // PORTICO SPECIFIC!!
    return ((DoubleTime) logicalTime).getTime();
  }

  private void log(String message) {
    System.out.println("ShoppingAmbassador: " + message);
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

  public void discoverObjectInstance(int theObject,
                                     int theObjectClass,
                                     String objectName) {
    log("Discoverd Object: handle=" + theObject + ", classHandle=" +
        theObjectClass + ", name=" + objectName);
  }

  public void reflectAttributeValues(int theObject,
                                     ReflectedAttributes theAttributes,
                                     byte[] tag) {
    reflectAttributeValues(theObject, theAttributes, tag, null, null);
  }

  public void reflectAttributeValues(int theObject,
                                     ReflectedAttributes theAttributes,
                                     byte[] tag,
                                     LogicalTime theTime,
                                     EventRetractionHandle retractionHandle) {
    StringBuilder builder = new StringBuilder("Reflection for object:");

    // print the handle
    builder.append(" handle=" + theObject);
    // print the tag
    builder.append(", tag=" + EncodingHelpers.decodeString(tag));
    // print the time (if we have it) we'll get null if we are just receiving
    // a forwarded call from the other reflect callback above
    if (theTime != null) {
      builder.append(", time=" + convertTime(theTime));
    }

    // print the attribute information
    builder.append(", attributeCount=" + theAttributes.size());
    builder.append("\n");
    for (int i = 0; i < theAttributes.size(); i++) {
      try {
        // print the attibute handle
        builder.append("\tattributeHandle=");
        builder.append(theAttributes.getAttributeHandle(i));
        // print the attribute value
        builder.append(", attributeValue=");
        builder.append(
            EncodingHelpers.decodeString(theAttributes.getValue(i)));
        builder.append("\n");
      } catch (ArrayIndexOutOfBounds aioob) {
        // won't happen
      }
    }

    log(builder.toString());
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
    StringBuilder builder = new StringBuilder("Interaction Received:");

    // print the handle
    builder.append(" handle=" + interactionClass);
    // print the tag
    builder.append(", tag=" + EncodingHelpers.decodeString(tag));
    // print the time (if we have it) we'll get null if we are just receiving
    // a forwarded call from the other reflect callback above
    if (theTime != null) {
      builder.append(", time=" + convertTime(theTime));
    }

    // print the parameer information
    builder.append(", parameterCount=" + theInteraction.size());
    builder.append("\n");
    for (int i = 0; i < theInteraction.size(); i++) {
      try {
        // print the parameter handle
        builder.append("\tparamHandle=");
        builder.append(theInteraction.getParameterHandle(i));
        // print the parameter value
        builder.append(", paramValue=");
        builder.append(
            EncodingHelpers.decodeString(theInteraction.getValue(i)));
        builder.append("\n");
      } catch (ArrayIndexOutOfBounds aioob) {
        // won't happen
      }
    }

    log(builder.toString());
  }

  public void removeObjectInstance(int theObject, byte[] userSuppliedTag) {
    log("Object Removed: handle=" + theObject);
  }

  public void removeObjectInstance(int theObject,
                                   byte[] userSuppliedTag,
                                   LogicalTime theTime,
                                   EventRetractionHandle retractionHandle) {
    log("Object Removed: handle=" + theObject);
  }
}
