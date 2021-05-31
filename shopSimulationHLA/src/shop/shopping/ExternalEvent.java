package shop.shopping;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {ADD}

  private int customerId;
  private EventType eventType;
  private Double time;

  public ExternalEvent(int customerId, EventType eventType, Double time) {
    this.customerId = customerId;
    this.eventType = eventType;
    this.time = time;
  }

  public EventType getEventType() {
    return eventType;
  }

  public int getCustomerId() {
    return customerId;
  }

  public double getTime() {
    return time;
  }

  public static class ExternalEventComparator implements Comparator<ExternalEvent> {

    @Override
    public int compare(ExternalEvent o1, ExternalEvent o2) {
      return o1.time.compareTo(o2.time);
    }
  }

}
