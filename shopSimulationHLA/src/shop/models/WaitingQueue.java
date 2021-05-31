package shop.models;

import java.util.LinkedList;
import java.util.stream.Collectors;

public class WaitingQueue {
  int id;
  LinkedList<Customer> customers;

  public WaitingQueue(int id) {
    this.id = id;
    this.customers = new LinkedList<>();
  }

  public LinkedList<Customer> getCustomers() {
    return customers;
  }

  public int getId() {
    return id;
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

  //TODO zmienic na poll - remove rzuca wyjatek gdy kolejka pusta natomiast poll zwraca null
  public Customer getFirstCustomer() {
      return this.customers.remove();
  }

  public int getSize() {
    return customers.size();
  }
}
