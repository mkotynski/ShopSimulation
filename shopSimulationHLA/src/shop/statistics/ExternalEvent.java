package shop.statistics;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {UPDATE_QUEUE_SIZE, WAITING_TIME}

  private int queueSize;
  private EventType eventType;
  private Double time;
  private double waitingTime;

  public ExternalEvent(int queueSize, EventType eventType, Double time) {
    this.queueSize = queueSize;
    this.eventType = eventType;
    this.time = time;
  }

  public ExternalEvent(double waitingTime, EventType eventType, Double time) {
    this.waitingTime = waitingTime;
    this.eventType = eventType;
    this.time = time;
  }

  public double getWaitingTime() {
    return waitingTime;
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
