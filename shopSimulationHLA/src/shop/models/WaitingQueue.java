package shop.models;

import java.util.LinkedList;
import java.util.stream.Collectors;

public class WaitingQueue {
  int id;
  LinkedList<Customer> customers;
  int maxQueueSize;

  public WaitingQueue(int id, int maxQueueSize) {
    this.id = id;
    this.customers = new LinkedList<>();
    this.maxQueueSize = maxQueueSize;
  }

  public LinkedList<Customer> getCustomers() {
    return customers;
  }

  public int getId() {
    return id;
  }

  public int getMaxQueueSize() {
    return maxQueueSize;
  }

  public boolean isFull() {
    return this.maxQueueSize <= getSize();
  }

  public boolean isNotEmpty() {
    return !this.customers.isEmpty();
  }

  public void addCustomer(Customer customer) {
    if (customer.isPrivilege()) {
      addPrivilegedCustomer(customer);
    } else {
      this.customers.add(customer);
    }
  }

  private void addPrivilegedCustomer(Customer customer) {
    LinkedList<Customer> privilegedCustomers = this.customers.stream().filter(Customer::isPrivilege).collect(Collectors.toCollection(LinkedList::new));
    LinkedList<Customer> unprivilegedCustomers = this.customers.stream().filter(Customer::isUnprivilege).collect(Collectors.toCollection(LinkedList::new));
    privilegedCustomers.add(customer);
    privilegedCustomers.addAll(unprivilegedCustomers);
    this.customers = privilegedCustomers;
  }

  public Customer getFirstCustomer() {
    return this.customers.remove();
  }

  public int getSize() {
    return customers.size();
  }
}
