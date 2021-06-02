package shop.waitingqueue;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import shop.models.Customer;
import shop.models.WaitingQueue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

public class WaitingQueueFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private WaitingQueueAmbassador waitingQueueAmbassador;
  private final double timeStep = 1.0;
  private int numberOfQueues = 0; //wartosc startowa - pierwszy klient -> otworzenie pierwszej kasy/kolejki
  private int maxQueueSize = 20;
  List<Integer> freeCashRegisterIds = new ArrayList<>();
  private List<WaitingQueue> waitingQueueList = new ArrayList<>();
  private int waitingQueueHLAObject = 0;

  public void runFederate() throws Exception {

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

    waitingQueueAmbassador = new WaitingQueueAmbassador();
    rtiamb.joinFederationExecution("WaitingQueueFederate", "Shop-Federation", waitingQueueAmbassador);
    log("Joined Federation as WaitingQueueFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (waitingQueueAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (waitingQueueAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    registerObject();

    createWaitingQueue();

    while (waitingQueueAmbassador.running) {
      double timeToAdvance = waitingQueueAmbassador.federateTime + timeStep;
      advanceTime(timeToAdvance);

      if (!waitingQueueAmbassador.externalEvents.isEmpty()) {
        waitingQueueAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : waitingQueueAmbassador.externalEvents) {
          waitingQueueAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.ADD) {
            this.addToShortesQueue(externalEvent.getCustomer());
          }
          if (externalEvent.getEventType() == ExternalEvent.EventType.FREE) {
            log("war " + !freeCashRegisterIds.contains(externalEvent.getIdCashRegister()));
            freeCashRegisterIds.forEach(e -> System.out.print(e + ", "));
            if (!freeCashRegisterIds.contains(externalEvent.getIdCashRegister())) {
              freeCashRegisterIds.add(externalEvent.getIdCashRegister());
            }
          }
        }
        waitingQueueAmbassador.externalEvents.clear();
      }

      for(int i=0; i < freeCashRegisterIds.size(); i++) {
        if (waitingQueueList.get(freeCashRegisterIds.get(i)-1).isNotEmpty()) {
          startCustomerService(waitingQueueList.get(freeCashRegisterIds.get(i)-1).getFirstCustomer(), freeCashRegisterIds.get(i), timeToAdvance);
          freeCashRegisterIds.remove(freeCashRegisterIds.get(i));
        }
      }

      if (waitingQueueAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += waitingQueueAmbassador.federateLookahead;
        updateHLAObject(timeToAdvance);
        waitingQueueAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void startCustomerService(Customer customer, int idCashRegister, double time) throws RTIinternalError, NameNotFound, FederateNotExecutionMember, InteractionClassNotDefined, RestoreInProgress, InteractionClassNotPublished, SaveInProgress, InvalidFederationTime, ConcurrentAccessAttempted, InteractionParameterNotDefined {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    byte[] customerId = EncodingHelpers.encodeInt(customer.getId());
    byte[] shoppingTime = EncodingHelpers.encodeDouble(customer.getShoppingTime());
    byte[] queueId = EncodingHelpers.encodeInt(idCashRegister);

    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.StartCustomerService");
    int customerIdHandle = rtiamb.getParameterHandle("customerId", interactionHandle);
    int shoppingTimeHandle = rtiamb.getParameterHandle("shoppingTime", interactionHandle);
    int queueIdHandle = rtiamb.getParameterHandle("queueId", interactionHandle);

    parameters.add(customerIdHandle, customerId);
    parameters.add(shoppingTimeHandle, shoppingTime);
    parameters.add(queueIdHandle, queueId);

    LogicalTime logicalTime = convertTime(time + waitingQueueAmbassador.federateLookahead);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), logicalTime);
    log("Klient zajął kase, id: " + customer.getId() + " kasa nr: " + idCashRegister);
  }

  private void registerObject() throws RTIexception {
    int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingQueue");
    this.waitingQueueHLAObject = rtiamb.registerObjectInstance(classHandle);
  }

  private void updateHLAObject(double time) throws RTIexception {
    SuppliedAttributes attributes =
        RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

    int classHandle = rtiamb.getObjectClass(waitingQueueHLAObject);
    int numberOfQueuesHandle = rtiamb.getAttributeHandle("numberOfQueues", classHandle);
    byte[] numberOfQueuesValue = EncodingHelpers.encodeInt(numberOfQueues);

    attributes.add(numberOfQueuesHandle, numberOfQueuesValue);
    LogicalTime logicalTime = convertTime(time);
    rtiamb.updateAttributeValues(waitingQueueHLAObject, attributes, "actualize number of queues".getBytes(), logicalTime);
  }


  private void addToShortesQueue(Customer customer) throws Exception {
    if (waitingQueueList.stream().filter(WaitingQueue::isFull).count() == numberOfQueues) {
      createWaitingQueue();
    }
    WaitingQueue shortestQueue = waitingQueueList.stream()
        .min(Comparator.comparingInt(WaitingQueue::getSize))
        .orElseThrow(() -> new Exception("Brak kolejek!!!"));
    shortestQueue.addCustomer(customer);
    log("Klient id: " + customer.getId() + " dodany do kolejki nr: " + shortestQueue.getId());
  }

  private void createWaitingQueue() {
    numberOfQueues++;
    waitingQueueList.add(new WaitingQueue(numberOfQueues, maxQueueSize));
    System.out.println("Nowa kolejka");
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
    LogicalTime currentTime = convertTime(waitingQueueAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(waitingQueueAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (waitingQueueAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (waitingQueueAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingQueue");
    int numberOfQueuesHandle = rtiamb.getAttributeHandle("numberOfQueues", classHandle);

    AttributeHandleSet attributes =
        RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
    attributes.add(numberOfQueuesHandle);

    rtiamb.publishObjectClass(classHandle, attributes);

    int customerStopShoppingHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStopShopping");
    waitingQueueAmbassador.customerStopShoppingHandle = customerStopShoppingHandle;
    rtiamb.subscribeInteractionClass(customerStopShoppingHandle);

    int freeCashRegisterHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FreeCashRegister");
    waitingQueueAmbassador.freeCashRegisterHandle = freeCashRegisterHandle;
    rtiamb.subscribeInteractionClass(freeCashRegisterHandle);

    int startCustomerServiceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.StartCustomerService");
    rtiamb.publishInteractionClass(startCustomerServiceHandle);
  }

  private void advanceTime(double timeToAdvance) throws RTIexception {
    // request the advance
    waitingQueueAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(timeToAdvance);
    rtiamb.timeAdvanceRequest(newTime);
    while (waitingQueueAmbassador.isAdvancing) {
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
    System.out.println("WaitingQueueFederate  : " + message);
  }

  public static void main(String[] args) throws Exception {
    try {
      new WaitingQueueFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }
}
