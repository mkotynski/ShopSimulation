package shop.models;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Collectors;

public class WaitingQueue {
  Queue<Customer> customers;

  public WaitingQueue(Queue<Customer> customers) {
    this.customers = customers;
  }

  public Queue<Customer> getCustomers() {
    return customers;
  }

  public void addCustomer(Customer customer) {
    this.customers.add(customer);
  }

  public void addPrivilegedCustomer(Customer customer) {
    Queue<Customer> privilegedCustomers = this.customers.stream().filter(Customer::isPrivilege).collect(Collectors.toCollection(PriorityQueue::new));
    Queue<Customer> unprivilegedCustomers = this.customers.stream().filter(c -> !customer.isPrivilege()).collect(Collectors.toCollection(PriorityQueue::new));
    privilegedCustomers.add(customer);
    privilegedCustomers.addAll(unprivilegedCustomers);
    this.customers = privilegedCustomers;
  }

  //TODO zmienic na poll - remove rzuca wyjatek gdy kolejka pusta natomiast poll zwraca null
  public Customer getFirstCustomer() {
      return this.customers.remove();
  }
}
