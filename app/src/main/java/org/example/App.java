package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public class App extends Application {

    // Thread-safe queue to store stock price data along with the timestamp
    private static final Queue<StockData> stockQueue = new ConcurrentLinkedQueue<>();

    // XYChart Series to plot the stock price on the graph
    private XYChart.Series<Number, Number> series;

    // Time tick counter for the X-axis (used for time intervals). This is only modified on the FX thread.
    private int timeTick = 0;

    // Scheduler for background polling
    private ScheduledExecutorService scheduler;

    // polling interval (seconds) - Alpha Vantage free tier allows 5 calls per minute; so use 12s to be safe
    private static final long POLL_INTERVAL_SECONDS = 12;

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

        // Start the scheduled task that queries the stock price every POLL_INTERVAL_SECONDS
        startStockPriceQuery();
    }

    @Override
    public void stop() throws Exception {
        // Shutdown scheduled executor cleanly when the application stops
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
        super.stop();
    }

    private void startStockPriceQuery() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stock-price-poller");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                double price = queryStockPrice();
                StockData stockData = new StockData(price, new Date());  // Create a StockData object with price and timestamp

                // Add the stock data to the queue
                stockQueue.add(stockData);

                // Update the graph on the JavaFX Application Thread (must be done on the UI thread)
                Platform.runLater(() -> updateGraph(stockData));
            } catch (Exception e) {
                // Log the error to console and optionally show on UI later
                System.err.println("Error while querying or updating stock price: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // Method to query stock price using the Alpha Vantage API
    private double queryStockPrice() throws Exception {
        // Try environment variable first, then api_key.txt in working directory.
        String apiKey = System.getenv("ALPHA_VANTAGE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            try {
                apiKey = Files.readString(Paths.get("api_key.txt"), StandardCharsets.UTF_8).trim();
            } catch (IOException ioe) {
                throw new IOException("API key not provided. Set environment variable ALPHA_VANTAGE_API_KEY or place api_key.txt in working directory.", ioe);
            }
        }

        // Use AAPL symbol for Apple stock (or change as needed)
        String urlString = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=" + apiKey;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JavaFX-Stock-Viewer/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int respCode = conn.getResponseCode();
            InputStream is = (respCode >= 200 && respCode < 300) ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }

            String response = builder.toString();
            System.out.println("Raw API Response: " + response);

            JSONObject jsonObject = new JSONObject(response);
            JSONObject globalQuote = jsonObject.optJSONObject("Global Quote");
            if (globalQuote == null) {
                throw new IOException("API response did not contain 'Global Quote'. Full response: " + response);
            }

            String priceStr = globalQuote.optString("05. price", "").trim();
            if (priceStr.isEmpty()) {
                throw new IOException("API response 'Global Quote' did not contain '05. price'. Full response: " + response);
            }

            // Remove grouping commas (if any) and parse
            priceStr = priceStr.replaceAll(",", "");
            try {
                return Double.parseDouble(priceStr);
            } catch (NumberFormatException nfe) {
                throw new IOException("Unable to parse price value: '" + priceStr + "'. Full response: " + response, nfe);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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
