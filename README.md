# Real-Time Stock Market Risk Visualization Tool

A small JavaFX application that polls Alpha Vantage for a stock's latest price and plots it in real time.

This branch includes:
- Refactoring: extraction of AlphaVantageClient for HTTP + JSON parsing
- App improvements: better logging, configuration, and safer UI updates
- This README with run instructions

## Prerequisites

- Java 11+ (or newer; JavaFX should be compatible with your JDK)  
- JavaFX on the module path (or use a JavaFX-enabled JDK / SDK distribution). If using OpenJFX, install the JavaFX SDK appropriate to your platform.
- Maven or Gradle to build, or run from an IDE that supports JavaFX.

## Getting an API key

1. Get a free API key from Alpha Vantage: https://www.alphavantage.co/support/#api-key
2. Provide the API key to the application using one of:
   - Environment variable: `ALPHA_VANTAGE_API_KEY`
   - Or create a file named `api_key.txt` in the working directory containing the key.

Note: The free Alpha Vantage tier has rate limits (typically 5 calls per minute). The app defaults to a 12 second poll interval to stay comfortably within that limit. If you change polling, respect the rate limits.

## Run (example)

With Maven (adjust JavaFX module flags as needed):
- Set API key (example on macOS / Linux):
  export ALPHA_VANTAGE_API_KEY=your_key_here
- Run:
  mvn -Dexec.mainClass="org.example.App" exec:java

Or via `java` (if you build a jar and include JavaFX on classpath/module-path):
  java -jar myapp.jar

You can also pass runtime overrides via system properties:
- `-Dstock.symbol=AAPL`         (default: AAPL)
- `-Dpoll.interval.seconds=12`  (default: 12)
- `-Dmax.points=100`            (default: 100)

Example (run with custom symbol & interval):
  mvn -Dstock.symbol=MSFT -Dpoll.interval.seconds=15 -Dexec.mainClass="org.example.App" exec:java

## Building

Use your preferred tool (Maven/Gradle/IDE). Ensure JavaFX dependencies are set when building/running outside of an IDE.

## Testing

- Unit tests can be added for AlphaVantageClient by mocking HTTP responses
