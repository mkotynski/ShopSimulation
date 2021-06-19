package shop.gui;

import java.util.Comparator;
import java.util.List;

public class ExternalEvent {

  public enum EventType {UPDATE_NUMBER_OF_QUEUE, UPDATE_QUEUES_SIZES}

  private int numberOfQueue;
  List<Integer> queuesSizes;
  private ExternalEvent.EventType eventType;
  private Double time;

  public ExternalEvent(int numberOfQueue, ExternalEvent.EventType eventType, Double time) {
    this.numberOfQueue = numberOfQueue;
    this.eventType = eventType;
    this.time = time;
  }

  public ExternalEvent(List<Integer> queuesSizes, ExternalEvent.EventType eventType, Double time) {
    this.queuesSizes = queuesSizes;
    this.eventType = eventType;
    this.time = time;
  }

  public List<Integer> getQueuesSizes() {
    return queuesSizes;
  }

  public ExternalEvent.EventType getEventType() {
    return eventType;
  }

  public int getNumberOfQueues() {
    return numberOfQueue;
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
