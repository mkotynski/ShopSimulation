package shop.cashregister;

import shop.models.Customer;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {ADD, START_SERVICE}

  private int numberOfQueues;
  private Customer customer;
  private EventType eventType;
  private Double time;

  public ExternalEvent(int numberOfQueues, EventType eventType, Double time) {
    this.numberOfQueues = numberOfQueues;
    this.eventType = eventType;
    this.time = time;
  }

  public ExternalEvent(Customer customer, EventType eventType, Double time) {
    this.customer = customer;
    this.eventType = eventType;
    this.time = time;
  }

  public Customer getCustomer() {
    return customer;
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
