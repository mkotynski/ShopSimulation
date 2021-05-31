package shop.models;

public class Customer {
  private int id;
  private boolean privilege;
  private double shoppingTime;
  private double shoppingEndTime;

  public Customer(int id, boolean privilage, double shoppingTime) {
    this.id = id;
    this.privilege = privilage;
    this.shoppingTime = shoppingTime;
  }

  public int getId() {
    return id;
  }

  public boolean isPrivilege() {
    return privilege;
  }

  public boolean isUnprivilege() {
    return !privilege;
  }

  public void setPrivilege(boolean privilege) {
    this.privilege = privilege;
  }

  public double getShoppingTime() {
    return shoppingTime;
  }

  public void setShoppingTime(double shoppingTime) {
    this.shoppingTime = shoppingTime;
  }

  public double getShoppingEndTime() {
    return shoppingEndTime;
  }

  public void setShoppingEndTime(double shoppingEndTime) {
    this.shoppingEndTime = shoppingEndTime;
  }
}
