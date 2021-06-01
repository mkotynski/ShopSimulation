package shop.models;

public class CashRegister {
  int id;
  boolean busy;

  public CashRegister(int id) {
    this.id = id;
    this.busy = false;
  }

  public int getId() {
    return id;
  }

  public boolean isBusy() {
    return busy;
  }

  public boolean isFree() {
    return !busy;
  }

  public void setBusy(boolean busy) {
    this.busy = busy;
  }
}
