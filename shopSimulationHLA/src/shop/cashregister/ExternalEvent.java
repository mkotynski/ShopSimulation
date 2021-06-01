package shop.cashregister;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {ADD}

  private int numberOfQueues;
  private EventType eventType;
  private Double time;

  public ExternalEvent(int numberOfQueues, EventType eventType, Double time) {
    this.numberOfQueues = numberOfQueues;
    this.eventType = eventType;
    this.time = time;
  }

  public EventType getEventType() {
    return eventType;
  }

  public int getNumberOfQueues() {
    return numberOfQueues;
  }

  public Double getTime() {
    return time;
  }

  public static class ExternalEventComparator implements Comparator<ExternalEvent> {

    @Override
    public int compare(ExternalEvent o1, ExternalEvent o2) {
      return o1.time.compareTo(o2.time);
    }
  }

}
