package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public class App extends Application {

    // Queue to store stock price data along with the timestamp
    private static final Queue<StockData> stockQueue = new LinkedList<>();
    
    // XYChart Series to plot the stock price on the graph
    private XYChart.Series<Number, Number> series;
    
    // Time tick counter for the X-axis (used for time intervals)
    private int timeTick = 0;

    public static void main(String[] args) {
        launch(args);  // Launch the JavaFX application
    }

    @Override
    public void start(Stage stage) {
        // Create the X-axis for the chart (time axis)
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time");

        // Create the Y-axis for the chart (stock price axis)
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Stock Price");

        // Create the LineChart with the defined axes
        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Real-Time Stock Price");

        // Create a series to plot the stock price data on the graph
        series = new XYChart.Series<>();
        series.setName("Stock Price");

        // Add the series to the chart
        lineChart.getData().add(series);

        // Create the Scene and set it on the Stage
        Scene scene = new Scene(lineChart, 800, 600);
        stage.setScene(scene);
        stage.setTitle("Real-Time Stock Price Tracker");
        stage.show();

        // Start the thread that queries the stock price every 5 seconds
        startStockPriceQuery();
    }

    private void startStockPriceQuery() {
        // Create a new thread that will continuously query the stock price
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    // Query the stock price and store the data
                    double price = queryStockPrice();
                    StockData stockData = new StockData(price, new Date());  // Create a StockData object with price and timestamp

                    // Add the stock data to the queue
                    stockQueue.add(stockData);

                    // Update the graph on the JavaFX Application Thread (must be done on the UI thread)
                    Platform.runLater(() -> updateGraph(stockData));

                    // Sleep for 5 seconds before querying again
                    TimeUnit.SECONDS.sleep(5);
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();  // Interrupt the thread in case of an error
                }
            }
        });

        // Start the thread
        thread.start();
    }

    // Method to query stock price using the Alpha Vantage API
    private double queryStockPrice() throws Exception {
        // Replace with your actual API key from Alpha Vantage
        String apiKey = Files.readAllLines(Paths.get("api_key.txt")).get(0);  // Enter your API key here

        // Use MSFT symbol for Microsoft stock (or any stock you want to track)
        URL url = new URL("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=" + apiKey);


        // Open a connection to the API and read the response
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);  // Append each line of the response to the builder
        }
        reader.close();  // Close the reader after reading the response

        // Print the raw API response for debugging purposes
        System.out.println("Raw API Response: " + builder.toString());

        // Parse the JSON response and extract the stock price
        JSONObject jsonObject = new JSONObject(builder.toString());
        // Extract the stock price from the "Global Quote" section of the response
        return jsonObject.getJSONObject("Global Quote").getDouble("05. price");
    }

    // Method to update the graph with the new stock price data
    private void updateGraph(StockData stockData) {
        // Add a new data point to the series (timeTick is incremented to represent time intervals)
        series.getData().add(new XYChart.Data<>(timeTick++, stockData.getPrice()));

        // Keep the graph updated with the latest 100 data points
        if (series.getData().size() > 100) {
            series.getData().remove(0);  // Remove the oldest data point if there are more than 100
        }
    }

    // Class to store stock price and timestamp
    private static class StockData {
        private final double price;  // Stock price
        private final Date timestamp;  // Timestamp of when the price was retrieved

        public StockData(double price, Date timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        // Getter for stock price
        public double getPrice() {
            return price;
        }

        // Getter for timestamp
        public Date getTimestamp() {
            return timestamp;
        }
    }
}
