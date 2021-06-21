package shop.waitingqueue;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import shop.Properties;
import shop.models.Customer;
import shop.models.WaitingQueue;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

public class WaitingQueueFederate {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private WaitingQueueAmbassador waitingQueueAmbassador;
  private final double timeStep = 1.0;
  private int numberOfQueues = 0; //wartosc startowa - pierwszy klient -> otworzenie pierwszej kasy/kolejki
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
            if (!freeCashRegisterIds.contains(externalEvent.getIdCashRegister())) {
              freeCashRegisterIds.add(externalEvent.getIdCashRegister());
            }
          }
        }
        waitingQueueAmbassador.externalEvents.clear();
      }

      for (int i = 0; i < freeCashRegisterIds.size(); i++) {
        if (waitingQueueList.get(freeCashRegisterIds.get(i) - 1).isNotEmpty()) {
          Customer customer = waitingQueueList.get(freeCashRegisterIds.get(i) - 1).getFirstCustomer();
          startCustomerService(customer, freeCashRegisterIds.get(i), timeToAdvance);

          sendWaitingTime(waitingQueueAmbassador.federateTime - customer.getShoppingEndTime(), timeToAdvance);
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

  private void updateHLAObject(double time) throws RTIexception, IOException {
    List<Integer> queuesSizes = waitingQueueList.stream().map(WaitingQueue::getSize).collect(Collectors.toList());
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(queuesSizes);

    SuppliedAttributes attributes =
        RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

    int classHandle = rtiamb.getObjectClass(waitingQueueHLAObject);
    int numberOfQueuesHandle = rtiamb.getAttributeHandle("numberOfQueues", classHandle);
    int queuesSizesHandle = rtiamb.getAttributeHandle("queuesSizes", classHandle);
    byte[] numberOfQueuesValue = EncodingHelpers.encodeInt(numberOfQueues);
    byte[] queuesSizesValue = bos.toByteArray();

    attributes.add(numberOfQueuesHandle, numberOfQueuesValue);
    attributes.add(queuesSizesHandle, queuesSizesValue);
    LogicalTime logicalTime = convertTime(time);
    rtiamb.updateAttributeValues(waitingQueueHLAObject, attributes, "actualize number of queues".getBytes(), logicalTime);
  }


  private void addToShortesQueue(Customer customer) throws Exception {
    customer.setShoppingEndTime(waitingQueueAmbassador.federateTime);
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
    waitingQueueList.add(new WaitingQueue(numberOfQueues, Properties.MAX_QUEUE_SIZE));
    System.out.println("Nowa kolejka");
  }

  private void sendWaitingTime(double waitingTime, double time) throws RTIexception {
    SuppliedParameters parameters =
        RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
    byte[] waitingTimeByteList = EncodingHelpers.encodeDouble(waitingTime);

    int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendWaitingTime");
    int waitingTimeHandle = rtiamb.getParameterHandle("waitingTime", interactionHandle);

    parameters.add(waitingTimeHandle, waitingTimeByteList);

    LogicalTime logicalTime = convertTime(time + waitingQueueAmbassador.federateLookahead);
    rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), logicalTime);
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
    int queuesSizesHandle = rtiamb.getAttributeHandle("queuesSizes", classHandle);

    AttributeHandleSet attributes =
        RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
    attributes.add(numberOfQueuesHandle);
    attributes.add(queuesSizesHandle);

    rtiamb.publishObjectClass(classHandle, attributes);

    int customerStopShoppingHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CustomerStopShopping");
    waitingQueueAmbassador.customerStopShoppingHandle = customerStopShoppingHandle;
    rtiamb.subscribeInteractionClass(customerStopShoppingHandle);

    int freeCashRegisterHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FreeCashRegister");
    waitingQueueAmbassador.freeCashRegisterHandle = freeCashRegisterHandle;
    rtiamb.subscribeInteractionClass(freeCashRegisterHandle);

    int startCustomerServiceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.StartCustomerService");
    rtiamb.publishInteractionClass(startCustomerServiceHandle);

    int sendWaitingTime = rtiamb.getInteractionClassHandle("InteractionRoot.SendWaitingTime");
    rtiamb.publishInteractionClass(sendWaitingTime);
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
