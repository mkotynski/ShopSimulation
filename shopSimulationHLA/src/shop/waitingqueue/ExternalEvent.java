package shop.waitingqueue;

import shop.models.Customer;

import java.util.Comparator;

public class ExternalEvent {

  public enum EventType {ADD, FREE}

  private Customer customer;
  private EventType eventType;
  private Double time;
  private int idCashRegister;

  public ExternalEvent(Customer customer, EventType eventType, Double time) {
    this.customer = customer;
    this.eventType = eventType;
    this.time = time;
  }

  public ExternalEvent(int idCashRegister, EventType eventType, Double time) {
    this.idCashRegister = idCashRegister;
    this.eventType = eventType;
    this.time = time;
  }

  public EventType getEventType() {
    return eventType;
  }

  public int getIdCashRegister() {
    return idCashRegister;
  }

  public void setIdCashRegister(int idCashRegister) {
    this.idCashRegister = idCashRegister;
  }

  public Customer getCustomer() {
    return customer;
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
