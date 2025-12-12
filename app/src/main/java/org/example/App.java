package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX Application that periodically queries a stock price and plots it on a LineChart.
 *
 * Improvements made compared to the original:
 *  - Separated API client logic into AlphaVantageClient for single-responsibility and testability.
 *  - Uses java.time.Instant instead of Date.
 *  - Uses java.util.logging.Logger.
 *  - Allows configuration via environment variables/system properties.
 *  - Better error handling and informative messages.
 */
public class App extends Application {

    private static final Logger logger = Logger.getLogger(App.class.getName());

    // Thread-safe queue to store stock price data (keeps a short history if needed)
    private final Queue<StockData> stockQueue = new ConcurrentLinkedQueue<>();

    // Chart series shown in UI; must only be modified on JavaFX thread
    private XYChart.Series<Number, Number> series;

    // X-axis tick counter (updated only on FX thread)
    private int timeTick = 0;

    // Background scheduler
    private ScheduledExecutorService scheduler;

    // Defaults (can be overridden using environment variables or system properties)
    private static final String DEFAULT_SYMBOL = "AAPL";
    private static final long DEFAULT_POLL_INTERVAL_SECONDS = 12;
    private static final int DEFAULT_MAX_POINTS = 100;

    // Configurable values
    private String symbol;
    private long pollIntervalSeconds;
    private int maxPoints;

    // API client (extracted to a separate class for clarity & testability)
    private AlphaVantageClient apiClient;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Constructor used during normal run; for tests, a different ctor or setter could inject a mock AlphaVantageClient.
     */
    public App() {
        // Default values; overridden in init() from environment or system properties
        this.symbol = DEFAULT_SYMBOL;
        this.pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
        this.maxPoints = DEFAULT_MAX_POINTS;
    }

    @Override
    public void init() {
        // Read configuration (order: system properties -> env -> defaults)
        symbol = System.getProperty("stock.symbol", System.getenv().getOrDefault("STOCK_SYMBOL", DEFAULT_SYMBOL));
        pollIntervalSeconds = Long.parseLong(System.getProperty("poll.interval.seconds",
                System.getenv().getOrDefault("POLL_INTERVAL_SECONDS", String.valueOf(DEFAULT_POLL_INTERVAL_SECONDS))));
        maxPoints = Integer.parseInt(System.getProperty("max.points",
                System.getenv().getOrDefault("MAX_POINTS", String.valueOf(DEFAULT_MAX_POINTS))));

        // Resolve API key (ENV first, then file)
        String apiKey = AlphaVantageClient.resolveApiKey();
        if (apiKey == null) {
            final String msg = "Alpha Vantage API key not found. Set ALPHA_VANTAGE_API_KEY or place api_key.txt in working directory.";
            logger.severe(msg);
            // UI thread not yet available; but we'll show an alert in start() and stop init so App can show useful error
            apiClient = null;
            return;
        }

        apiClient = new AlphaVantageClient(apiKey, logger);
    }

    @Override
    public void start(Stage stage) {
        if (apiClient == null) {
            // Inform the user that API key is missing and exit the application gracefully
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Configuration Error");
            alert.setHeaderText("Missing API Key");
            alert.setContentText("Alpha Vantage API key is missing. Set ALPHA_VANTAGE_API_KEY or create api_key.txt in the working directory.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        // Create axes
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (ticks)");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (USD)");

        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle(String.format("Real-Time Stock Price (%s)", symbol));

        series = new XYChart.Series<>();
        series.setName(symbol);

        lineChart.getData().add(series);

        Scene scene = new Scene(lineChart, 900, 600);
        stage.setScene(scene);
        stage.setTitle("Real-Time Stock Price Tracker");
        stage.show();

        // Start periodic polling task
        startStockPriceQuery();
    }

    @Override
    public void stop() throws Exception {
        // Shutdown scheduler on application exit
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Scheduler did not terminate within timeout.");
                }
            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, "Interrupted while waiting for scheduler termination", ie);
                Thread.currentThread().interrupt();
            }
        }
        super.stop();
    }

    /**
     * Start the scheduler that polls the API at a fixed interval.
     */
    private void startStockPriceQuery() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stock-price-poller");
            t.setDaemon(true);
            return t;
        });

        final Runnable poller = () -> {
            try {
                double price = apiClient.fetchGlobalQuotePrice(symbol);
                StockData stockData = new StockData(price, Instant.now());
                stockQueue.add(stockData);

                // Update UI on JavaFX thread; keep the update minimal and bounded
                Platform.runLater(() -> updateGraph(stockData));
            } catch (IOException ioe) {
                // Log and continue; do not crash the scheduler thread on API errors
                logger.log(Level.WARNING, "Error querying stock price: " + ioe.getMessage(), ioe);
            } catch (Exception ex) {
                // Catch-all to avoid the scheduled executor silently dying
                logger.log(Level.SEVERE, "Unexpected error in poller", ex);
            }
        };

        // Use scheduleAtFixedRate because we want regular ticks; pollIntervalSeconds is configurable
        scheduler.scheduleAtFixedRate(poller, 0, Math.max(1, pollIntervalSeconds), TimeUnit.SECONDS);
    }

    /**
     * Update the chart series with a new data point. Must be executed on the JavaFX Application Thread.
     *
     * @param stockData the latest stock data point
     */
    private void updateGraph(StockData stockData) {
        if (series == null) return; // defensive

        series.getData().add(new XYChart.Data<>(timeTick++, stockData.getPrice()));

        // Keep the series bounded to maxPoints to avoid memory growth
        while (series.getData().size() > maxPoints) {
            series.getData().remove(0);
        }
    }

    // Simple immutable container for a sample
    private static class StockData {
        private final double price;
        private final Instant timestamp;

        public StockData(double price, Instant timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        public double getPrice() {
            return price;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
