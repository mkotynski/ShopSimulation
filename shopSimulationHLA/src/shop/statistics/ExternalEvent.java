package shop.statistics;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {UPDATE_QUEUE_SIZE}

  private int queueSize;
  private EventType eventType;
  private Double time;

  public ExternalEvent(int queueSize, EventType eventType, Double time) {
    this.queueSize = queueSize;
    this.eventType = eventType;
    this.time = time;
  }

  public EventType getEventType() {
    return eventType;
  }

  public int getQueueSize() {
    return queueSize;
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
