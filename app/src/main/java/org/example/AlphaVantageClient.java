package org.example;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small, focused client for Alpha Vantage Global Quote retrieval.
 * This class isolates the HTTP and JSON parsing logic so it can be tested separately.
 */
public class AlphaVantageClient {

    private final String apiKey;
    private final Logger logger;
    private static final String BASE_URL = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE";

    public AlphaVantageClient(String apiKey, Logger logger) {
        this.apiKey = apiKey;
        this.logger = logger != null ? logger : Logger.getLogger(AlphaVantageClient.class.getName());
    }

    /**
     * Fetches the latest price for a symbol from Alpha Vantage.
     *
     * @param symbol the stock symbol (e.g., AAPL)
     * @return price as double
     * @throws IOException on network/parse errors or when API indicates a problem (e.g., rate limiting)
     */
    public double fetchGlobalQuotePrice(String symbol) throws IOException {
        String urlString = String.format("%s&symbol=%s&apikey=%s", BASE_URL, symbol, apiKey);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JavaFX-Stock-Viewer/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int resp = conn.getResponseCode();
            InputStream is = (resp >= 200 && resp < 300) ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            String response = sb.toString();
            logger.fine("AlphaVantage raw response: " + response);

            JSONObject root = new JSONObject(response);

            // Alpha Vantage sometimes returns a "Note" (rate limit exceeded) or "Error Message"
            if (root.has("Note")) {
                String note = root.optString("Note", "No detail provided");
                logger.log(Level.WARNING, "Alpha Vantage Note: " + note);
                throw new IOException("Alpha Vantage rate limit or notice: " + note);
            }
            if (root.has("Error Message")) {
                String emsg = root.optString("Error Message", "No detail provided");
                logger.log(Level.WARNING, "Alpha Vantage Error Message: " + emsg);
                throw new IOException("Alpha Vantage returned an error: " + emsg);
            }

            JSONObject globalQuote = root.optJSONObject("Global Quote");
            if (globalQuote == null) {
                throw new IOException("API response did not contain 'Global Quote'. Full response: " + response);
            }

            String priceStr = globalQuote.optString("05. price", "").trim();
            if (priceStr.isEmpty()) {
                throw new IOException("API response 'Global Quote' did not contain '05. price'. Full response: " + response);
            }

            // Remove grouping commas for parsing
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

    /**
     * Helper used by App.init() to find the API key from env or api_key.txt file.
     *
     * @return API key string or null if none found
     */
    public static String resolveApiKey() {
        String envKey = System.getenv("ALPHA_VANTAGE_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }

        try {
            String fileKey = Files.readString(Paths.get("api_key.txt"), StandardCharsets.UTF_8).trim();
            if (!fileKey.isEmpty()) {
                return fileKey;
            }
        } catch (IOException ioe) {
            // no-op; return null below
        }

        return null;
    }
}
