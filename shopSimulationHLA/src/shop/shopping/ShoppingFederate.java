package shop.shopping;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import shop.models.Customer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShoppingFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private ShoppingAmbassador shoppingAmbassador;
  private List<Customer> customersList = new ArrayList<>();
  private int maxNumberOfProducts = 50;
  private  int minNumberOfProducts = 5;

  public void runFederate() throws RTIexception {

    rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

    try {
      File fom = new File("shop-simulation.fed");
      rtiamb.createFederationExecution("Shop-Federation", fom.toURI().toURL());
      log("Created Federation");
    } catch (FederationExecutionAlreadyExists exists) {
      log("Didn't create federation, it already existed");
    } catch (MalformedURLException urle) {
      log("Exception processing fom: " + urle.getMessage());
      urle.printStackTrace();
      return;
    }

    shoppingAmbassador = new ShoppingAmbassador(this);
    rtiamb.joinFederationExecution("ShoppingFederate", "Shop-Federation", shoppingAmbassador);
    log("Joined Federation as ShoppingFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (shoppingAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (shoppingAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    while (shoppingAmbassador.running) {
      double timeToAdvance = shoppingAmbassador.federateTime + shoppingAmbassador.federateLookahead;
      advanceTime(timeToAdvance);
      for(int i = 0; i < customersList.size(); i++) {
        if(customersList.get(i).getShoppingEndTime() <= shoppingAmbassador.federateTime) {
          try {
            endShoppingInteraction(customersList.get(i), timeToAdvance);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      }

      if (!shoppingAmbassador.externalEvents.isEmpty()) {
        shoppingAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : shoppingAmbassador.externalEvents) {
          shoppingAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.ADD) {
            this.customerStartShopping(externalEvent.getCustomerId());
          }
        }
        shoppingAmbassador.externalEvents.clear();
      }
      if (shoppingAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += shoppingAmbassador.federateLookahead;
        shoppingAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void endShoppingInteraction(Customer customer, double timeToAdvance) throws RTIexception {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    byte[] id = EncodingHelpers.encodeInt(customer.getId());
    byte[] shoppingTime = EncodingHelpers.encodeDouble(customer.getShoppingTime());
    byte[] privilege = EncodingHelpers.encodeBoolean(customer.isPrivilege());
    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStopShopping");

    parameters.add(rtiamb.getParameterHandle("id", interactionHandle), id);
    parameters.add(rtiamb.getParameterHandle("shoppingTime", interactionHandle), shoppingTime);
    parameters.add(rtiamb.getParameterHandle("privilege", interactionHandle), privilege);

    LogicalTime time = convertTime(timeToAdvance + shoppingAmbassador.federateLookahead);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
    log("Klient id: " + customer.getId() + " zakonczyl zakupy");
    customersList.remove(customer);
  }


  private void waitForUser() {
    log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    try {
      reader.readLine();
    } catch (Exception e) {
      log("Error while waiting for user input: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void enableTimePolicy() throws RTIexception {
    LogicalTime currentTime = convertTime(shoppingAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(shoppingAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (shoppingAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (shoppingAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int stopCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStopShopping");
    rtiamb.publishInteractionClass(stopCustomerHandle);

    int customerStartShoppingHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStartShopping");
    shoppingAmbassador.addCustomerHandle = customerStartShoppingHandle;
    rtiamb.subscribeInteractionClass(customerStartShoppingHandle);
  }

  private void advanceTime(double timeToAdvance) throws RTIexception {
    // request the advance
    shoppingAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(timeToAdvance);
    rtiamb.timeAdvanceRequest(newTime);
    while (shoppingAmbassador.isAdvancing) {
      rtiamb.tick();
    }
  }

  private double randomTime() {
    Random r = new Random();
    return 1 + (9 * r.nextDouble());
  }

  private LogicalTime convertTime(double time) {
    // PORTICO SPECIFIC!!
    return new DoubleTime(time);
  }

  private LogicalTimeInterval convertInterval(double time) {
    // PORTICO SPECIFIC!!
    return new DoubleTimeInterval(time);
  }

  private void log(String message) {
    System.out.println("ShoppingFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new ShoppingFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }

  private void customerStartShopping(int id) {
    Customer customer = new Customer(id, randomPrivilege(), randomShoppingTime());
    customer.setShoppingEndTime(customer.getShoppingTime() + shoppingAmbassador.federateTime);
    customersList.add(customer);
    log("Klient rozpoczyna zakupy: id " + customer.getId() + (customer.isPrivilege() ? " uprzywilejowany":"") + " czas zakupow: " + randomShoppingTime());
  }

  private boolean randomPrivilege() {
    Random random = new Random();
    int randomInt = random.nextInt(10);
    return randomInt == 3;
  }

  private double randomShoppingTime() {
    Random random = new Random();
    return random.nextInt(maxNumberOfProducts) + minNumberOfProducts;
  }
}
