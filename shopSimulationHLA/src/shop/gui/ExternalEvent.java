package shop.gui;

import java.util.Comparator;
import java.util.List;

public class ExternalEvent {

  public enum EventType {UPDATE_VALUES}

  List<Integer> queuesSizes;
  private ExternalEvent.EventType eventType;
  private Double time;
  private double avgWaitingTime;

  public ExternalEvent(List<Integer> queuesSizes, ExternalEvent.EventType eventType, Double time, double avgWaitingTime) {
    this.queuesSizes = queuesSizes;
    this.eventType = eventType;
    this.time = time;
    this.avgWaitingTime = avgWaitingTime;
  }

  public double getAvgWaitingTime() {
    return avgWaitingTime;
  }

  public List<Integer> getQueuesSizes() {
    return queuesSizes;
  }

  public ExternalEvent.EventType getEventType() {
    return eventType;
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
