package shop.customer;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class CustomerFederate {

  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private CustomerAmbassador customerAmbassador;

  private int numberOfCustomers = 0;

  public void runFederate() throws RTIexception {

    rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

    try {
//      File fom = new File("shop-simulation.fed");
      rtiamb.createFederationExecution("Shop-Federation", (new File("shop-simulation.fed")).toURI().toURL());
      log("Created Federation");
    } catch (FederationExecutionAlreadyExists exists) {
      log("Didn't create federation, it already existed");
    } catch (MalformedURLException urle) {
      log("Exception processing fom: " + urle.getMessage());
      urle.printStackTrace();
      return;
    }

    customerAmbassador = new CustomerAmbassador();
    rtiamb.joinFederationExecution("CustomerFederate", "Shop-Federation", customerAmbassador);
    log("Joined Federation as CustomerFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (customerAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (customerAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    while (customerAmbassador.running) {
      generateNewClient(customerAmbassador.federateTime + customerAmbassador.federateLookahead);
      advanceTime(randomTime());
      rtiamb.tick();
    }
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
    LogicalTime currentTime = convertTime(customerAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(customerAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (customerAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (customerAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int addCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStartShopping");
    rtiamb.publishInteractionClass(addCustomerHandle);
  }

  private void advanceTime(double timestep) throws RTIexception {
    //     log("requesting time advance for: " + timestep);
    // request the advance
    customerAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(customerAmbassador.federateTime + timestep);
    rtiamb.timeAdvanceRequest(newTime);
    while (customerAmbassador.isAdvancing) {
      rtiamb.tick();
    }
  }

  private double randomTime() {
    Random r = new Random();
    return (10 + (9 * r.nextDouble()));
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
    System.out.println("CustomerFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new CustomerFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }


  private void generateNewClient(double timeStep) throws RTIexception {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    numberOfCustomers++;
    byte[] id = EncodingHelpers.encodeInt(numberOfCustomers);
    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStartShopping");
    int customerHandle = rtiamb.getParameterHandle("id", interactionHandle);

    parameters.add(customerHandle, id);

    LogicalTime time = convertTime(timeStep);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
    log("Nowy klient, id: " + numberOfCustomers + " czas: " + customerAmbassador.federateTime);
  }
}
