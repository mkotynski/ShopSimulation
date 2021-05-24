package shop.models;

public class Customer {
  private int id;
  private boolean privilege;

  public Customer(int id, boolean privilage) {
    this.id = id;
    this.privilege = privilage;
  }

  public int getId() {
    return id;
  }

  public boolean isPrivilege() {
    return privilege;
  }
}
