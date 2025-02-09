# Real-Time Stock Price Tracker

## 📌 Introduction
The **Real-Time Stock Price Tracker** is a JavaFX application that retrieves and visualizes live stock prices using the **Alpha Vantage API**. The application fetches stock prices every 5 seconds and plots them on a real-time line chart.

## 📖 Table of Contents
- [Installation](#installation)
- [Usage](#usage)
- [Features](#features)
- [Dependencies](#dependencies)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Contributors](#contributors)
- [License](#license)

## ⚙️ Installation
1. **Clone the repository**  
   ```sh
   git clone https://github.com/your-repo/stock-price-tracker.git
   cd stock-price-tracker
   ```
2. **Install JavaFX**  
   Make sure you have Java 11+ installed along with JavaFX SDK. You can download JavaFX from:  
   [https://gluonhq.com/products/javafx/](https://gluonhq.com/products/javafx/)  
   
3. **Set up API Key**  
   - Get a free API key from [Alpha Vantage](https://www.alphavantage.co/support/#api-key).  
   - Save the key in a file named `api_key.txt` in the root directory.

4. **Run the application**  
   ```sh
   javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml App.java
   java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml App
   ```

## 🚀 Usage
- Launch the application to start tracking stock prices.
- The stock price data updates automatically every **5 seconds**.
- View the real-time price movement in the **line chart**.

## ✨ Features
- 📈 **Real-Time Stock Prices**: Fetches stock prices using the Alpha Vantage API.
- 📊 **Live Graphing**: Displays stock prices dynamically on a **JavaFX LineChart**.
- ⏳ **Automatic Updates**: Refreshes every **5 seconds**.
- 💾 **Data Management**: Maintains the latest 100 data points to keep the graph clean.

## 📦 Dependencies
- JavaFX
- Alpha Vantage API (for stock price data)
- JSON (for parsing API response)

## 🔧 Configuration
- Modify the stock symbol in the `queryStockPrice()` method to track a different stock.
- Adjust the refresh rate in the `startStockPriceQuery()` method (`TimeUnit.SECONDS.sleep(5);`).

## 🛠 Troubleshooting
**Issue:** "Error fetching stock prices"  
✅ **Solution:** Ensure your API key is correct and not exceeding the free request limit.

**Issue:** "JavaFX runtime error"  
✅ **Solution:** Check that your JavaFX SDK is correctly installed and added to your classpath.

## 👥 Contributors
- **Your Name** @SoftwareChorepgrapher

## 📜 License
This project is licensed under the **MIT License**.

---

📌 **Note**: Make sure you update the paths in the `javac` and `java` commands with your JavaFX SDK location.
