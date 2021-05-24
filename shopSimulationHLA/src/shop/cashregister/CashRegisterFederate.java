package shop.cashregister;

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

public class CashRegisterFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private CashRegisterAmbassador fedamb;
  private final double timeStep = 10.0;
  private int stock = 10;
  private int storageHlaHandle;

  public void runFederate() throws RTIexception {

    rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

    try {
      File fom = new File("shop-simulation.fed");
      rtiamb.createFederationExecution("", fom.toURI().toURL());
      log("Created Federation");
    } catch (FederationExecutionAlreadyExists exists) {
      log("Didn't create federation, it already existed");
    } catch (MalformedURLException urle) {
      log("Exception processing fom: " + urle.getMessage());
      urle.printStackTrace();
      return;
    }

    fedamb = new CashRegisterAmbassador();
    rtiamb.joinFederationExecution("CashRegisterFederate", "ExampleFederation", fedamb);
    log("Joined Federation as CashRegisterFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (fedamb.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (fedamb.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    while (fedamb.running) {
      advanceTime(randomTime());
      sendInteraction(fedamb.federateTime + fedamb.federateLookahead);
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
    LogicalTime currentTime = convertTime(fedamb.federateTime);
    LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (fedamb.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (fedamb.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int addProductHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddProduct");
    rtiamb.publishInteractionClass(addProductHandle);
  }

  private void sendInteraction(double timeStep) throws RTIexception {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    Random random = new Random();
    byte[] quantity = EncodingHelpers.encodeInt(random.nextInt(10) + 1);

    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddProduct");
    int quantityHandle = rtiamb.getParameterHandle("quantity", interactionHandle);

    parameters.add(quantityHandle, quantity);

    LogicalTime time = convertTime(timeStep);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
  }

  private void advanceTime(double timestep) throws RTIexception {
    log("requesting time advance for: " + timestep);
    // request the advance
    fedamb.isAdvancing = true;
    LogicalTime newTime = convertTime(fedamb.federateTime + timestep);
    rtiamb.timeAdvanceRequest(newTime);
    while (fedamb.isAdvancing) {
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
    System.out.println("CashRegisterFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new CashRegisterFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }
}
