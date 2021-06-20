package shop.gui;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Random;

public class GuiFederate extends Application {
  public static final String READY_TO_RUN = "ReadyToRun";

  private RTIambassador rtiamb;
  private GuiAmbassador guiAmbassador;
  private final double timeStep = 1.0;

  NumberAxis xAxisTime = new NumberAxis();
  NumberAxis yAxisTime = new NumberAxis();
  NumberAxis xAxisAvgTime = new NumberAxis();
  NumberAxis yAxisAvgTime = new NumberAxis();
  CategoryAxis xAxisQueueSize = new CategoryAxis();
  NumberAxis yAxisQueueSize = new NumberAxis();

  LineChart<Number, Number> numberOfQueuesChart = new LineChart<>(xAxisTime, yAxisTime);
  LineChart<Number, Number> avgWaitingTimeChart = new LineChart<>(xAxisAvgTime, yAxisAvgTime);

  BarChart<String, Number> queuesSizesBarChart = new BarChart<>(xAxisQueueSize, yAxisQueueSize);

  LineChart.Series<Number, Number> numberOfQueuesSeries = new LineChart.Series<>();
  LineChart.Series<Number, Number> avgWaitingTimeSeries = new LineChart.Series<>();
  BarChart.Series<String, Number> queuesSizeSeries = new XYChart.Series<>();

  String avgTimeString = "Sredni czas oczekiwania: ";
  String numberOfQueueString = "Ilosc kolejek: ";

  Label avgTimeLabel = new Label(avgTimeString);
  Label numberOfQueueLabel = new Label(numberOfQueueString);

  private int numberOfQueue = 0;

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

    guiAmbassador = new GuiAmbassador();
    rtiamb.joinFederationExecution("GuiFederate", "Shop-Federation", guiAmbassador);
    log("Joined Federation as GuiFederate");

    rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

    while (guiAmbassador.isAnnounced == false) {
      rtiamb.tick();
    }

    waitForUser();

    rtiamb.synchronizationPointAchieved(READY_TO_RUN);
    log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
    while (guiAmbassador.isReadyToRun == false) {
      rtiamb.tick();
    }

    enableTimePolicy();
    log("Time Policy Enabled");

    publishAndSubscribe();
    log("Published and Subscribed");

    createGui();

    while (guiAmbassador.running) {
      double timeToAdvance = guiAmbassador.federateTime + timeStep;
      advanceTime(timeToAdvance);

      if (!guiAmbassador.externalEvents.isEmpty()) {
        guiAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
        for (ExternalEvent externalEvent : guiAmbassador.externalEvents) {
          guiAmbassador.federateTime = externalEvent.getTime();
          if (externalEvent.getEventType() == ExternalEvent.EventType.UPDATE_VALUES) {
            updateGUI(externalEvent.getQueuesSizes(), externalEvent.getAvgWaitingTime());
          }
        }
        guiAmbassador.externalEvents.clear();
      }


      if (guiAmbassador.grantedTime == timeToAdvance) {
        timeToAdvance += guiAmbassador.federateLookahead;
        guiAmbassador.federateTime = timeToAdvance;
      }
      rtiamb.tick();
    }
  }

  private void createGui() {
    String numberOfQueuesTitle = "Ilosc kolejek";
    String queuesSizesTitle = "Dlugosci kolejek";
    String avgWaitingTimeTitle = "Sredni czas oczekiwania";

    numberOfQueuesChart.setTitle(numberOfQueuesTitle);
    queuesSizesBarChart.setTitle(queuesSizesTitle);
    avgWaitingTimeChart.setTitle(avgWaitingTimeTitle);

    numberOfQueuesSeries.setName(numberOfQueuesTitle);
    queuesSizeSeries.setName(queuesSizesTitle);
    avgWaitingTimeSeries.setName(avgWaitingTimeTitle);

    avgWaitingTimeChart.setCreateSymbols(false);
    queuesSizesBarChart.setAnimated(false);

    Platform.setImplicitExit(false);

    Platform.runLater(() -> {
      Stage stage = new Stage();
      stage.setTitle("Shop");
      GridPane layout = new GridPane();

      layout.add(avgTimeLabel, 1, 1);
      layout.add(numberOfQueueLabel, 1, 2);
      layout.add(numberOfQueuesChart, 1, 4);
      layout.add(queuesSizesBarChart, 2, 4);
      layout.add(avgWaitingTimeChart, 1,5);

      Scene scene = new Scene(layout);
      stage.setScene(scene);
      stage.show();
    });

    numberOfQueuesChart.getData().add(numberOfQueuesSeries);
    queuesSizesBarChart.getData().add(queuesSizeSeries);
    avgWaitingTimeChart.getData().add(avgWaitingTimeSeries);
  }

  public void updateGUI(List<Integer> queuesSizes, double avgWaitingTime) {
    //log("aaa" + String.valueOf(queueSize)+" "+String.valueOf(avgWaitingTime));
    runThread(queuesSizes, avgWaitingTime);
  }

  private void runThread(List<Integer> queuesSizes, double avgWaitingTime) {
    Platform.runLater(() -> {
      if(!queuesSizes.isEmpty()) {
        if (numberOfQueue != queuesSizes.size()) {
          numberOfQueuesSeries.getData().add(new LineChart.Data(guiAmbassador.federateTime, queuesSizes.size()));
          numberOfQueue = queuesSizes.size();
        }

        numberOfQueueLabel.setText(numberOfQueueString + numberOfQueue);

        queuesSizeSeries.getData().clear();
        for (int i = 0; i < queuesSizes.size(); i++) {
          queuesSizeSeries.getData().add(new XYChart.Data<>("Kolejka " + (i + 1), queuesSizes.get(i)));
        }
      }

      if(avgWaitingTime != -1) {
        avgWaitingTimeSeries.getData().add(new LineChart.Data(guiAmbassador.federateTime, avgWaitingTime));
        avgTimeLabel.setText(avgTimeString + avgWaitingTime);
      }
    });
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
    LogicalTime currentTime = convertTime(guiAmbassador.federateTime);
    LogicalTimeInterval lookahead = convertInterval(guiAmbassador.federateLookahead);

    this.rtiamb.enableTimeRegulation(currentTime, lookahead);

    while (guiAmbassador.isRegulating == false) {
      rtiamb.tick();
    }

    this.rtiamb.enableTimeConstrained();

    while (guiAmbassador.isConstrained == false) {
      rtiamb.tick();
    }
  }

  private void publishAndSubscribe() throws RTIexception {
    int simObjectClassWaitingQueueHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingQueue");
    int numberOfQueuesHandle = rtiamb.getAttributeHandle("numberOfQueues", simObjectClassWaitingQueueHandle);
    int queuesSizesHandle = rtiamb.getAttributeHandle("queuesSizes", simObjectClassWaitingQueueHandle);

    AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory()
        .createAttributeHandleSet();
    attributes.add(numberOfQueuesHandle);
    attributes.add(queuesSizesHandle);
    guiAmbassador.waitingQueueHandle = simObjectClassWaitingQueueHandle;

    rtiamb.subscribeObjectClassAttributes(simObjectClassWaitingQueueHandle, attributes);

    int simObjectClassStatisticsHandle = rtiamb.getObjectClassHandle("ObjectRoot.Statistics");
    int avgWaitingTimeHandle = rtiamb.getAttributeHandle("avgWaitingTime", simObjectClassStatisticsHandle);

    AttributeHandleSet attributesStats = RtiFactoryFactory.getRtiFactory()
        .createAttributeHandleSet();
    attributesStats.add(avgWaitingTimeHandle);

    guiAmbassador.avgWaitingTimeHandle = simObjectClassStatisticsHandle;
    rtiamb.subscribeObjectClassAttributes(simObjectClassStatisticsHandle, attributesStats);
  }

  private void advanceTime(double timeToAdvance) throws RTIexception {
    // request the advance
    guiAmbassador.isAdvancing = true;
    LogicalTime newTime = convertTime(timeToAdvance);
    rtiamb.timeAdvanceRequest(newTime);
    while (guiAmbassador.isAdvancing) {
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
    System.out.println("GuiFederate  : " + message);
  }

  public static void main(String[] args) {
    try {
      new GuiFederate().runFederate();
    } catch (RTIexception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void start(Stage primaryStage) throws Exception {
    try {
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
