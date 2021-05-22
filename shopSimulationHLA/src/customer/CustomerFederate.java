package customer;

import hla.rti.RTIambassador;
import hla.rti.jlc.RtiFactoryFactory;
import hla.rti.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

public class CustomerFederate {

  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private CustomerAmbassador fedamb;
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
      log( "Exception processing fom: " + urle.getMessage() );
      urle.printStackTrace();
      return;
    }

    fedamb = new CustomerAmbassador();
//    rtiamb.joinFederationExecution("CustomerFederate, ")

  }

  private void waitForUser()
  {
    log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
    BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
    try
    {
      reader.readLine();
    }
    catch( Exception e )
    {
      log( "Error while waiting for user input: " + e.getMessage() );
      e.printStackTrace();
    }
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
}
