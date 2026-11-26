package org.example.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.edge.*;
import org.openqa.selenium.firefox.*;

public final class BrowserConfig {
    private BrowserConfig() {}
    public static String browser() {
        String value = System.getProperty("browser", "chrome").toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("chrome", "edge", "firefox").contains(value))
            throw new IllegalArgumentException("browser must be chrome, edge, or firefox");
        return value;
    }
    public static boolean headless() {
        String value = System.getProperty("headless", "false");
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("headless must be true or false");
        return Boolean.parseBoolean(value);
    }
    public static String url() {
        String value = System.getProperty("baseUrl", "https://www.selenium.dev/selenium/web/web-form.html");
        URI uri = URI.create(value);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL without credentials, query, or fragment");
        return value;
    }
    public static Duration timeout() { return Duration.ofSeconds(integer("timeout", 15, 1, 120)); }
    public static Duration pageTimeout() { return Duration.ofSeconds(integer("pageTimeout", 45, 1, 180)); }
    private static int integer(String key, int fallback, int min, int max) {
        int value = Integer.parseInt(System.getProperty(key, "" + fallback));
        if (value < min || value > max) throw new IllegalArgumentException(key + " out of range");
        return value;
    }
    public static WebDriver createDriver() {
        String size = System.getProperty("windowSize", "1440x1000");
        if (!size.matches("[0-9]{3,4}x[0-9]{3,4}")) throw new IllegalArgumentException("windowSize must be WIDTHxHEIGHT");
        String[] dimensions = size.split("x");
        int width = Integer.parseInt(dimensions[0]), height = Integer.parseInt(dimensions[1]);
        if (width < 320 || height < 320 || width > 3840 || height > 2160) throw new IllegalArgumentException("windowSize outside supported range");
        url(); timeout(); pageTimeout();
        org.openqa.selenium.remote.http.ClientConfig client = org.openqa.selenium.remote.http.ClientConfig.defaultConfig()
                .connectionTimeout(Duration.ofSeconds(15)).readTimeout(pageTimeout().plusSeconds(10));
        WebDriver driver;
        switch (browser()) {
            case "edge":
                EdgeOptions edge = new EdgeOptions(); edge.setPageLoadStrategy(PageLoadStrategy.EAGER);
                if (headless()) edge.addArguments("--headless=new");
                edge.addArguments("--disable-notifications", "--disable-gpu", "--window-size=" + width + "," + height);
                driver = new EdgeDriver(EdgeDriverService.createDefaultService(), edge, client); break;
            case "firefox":
                FirefoxOptions firefox = new FirefoxOptions(); firefox.setPageLoadStrategy(PageLoadStrategy.EAGER);
                if (headless()) firefox.addArguments("-headless");
                driver = new FirefoxDriver(new GeckoDriverService.Builder().build(), firefox, client); break;
            default:
                ChromeOptions chrome = new ChromeOptions(); chrome.setPageLoadStrategy(PageLoadStrategy.EAGER);
                if (headless()) chrome.addArguments("--headless=new");
                chrome.addArguments("--disable-notifications", "--disable-gpu", "--window-size=" + width + "," + height);
                driver = new ChromeDriver(ChromeDriverService.createDefaultService(), chrome, client);
        }
        try {
            if (!headless() || browser().equals("firefox")) {
                driver.manage().window().setSize(new Dimension(width, height));
            }
            driver.manage().timeouts().pageLoadTimeout(pageTimeout());
            return driver;
        } catch (RuntimeException failure) {
            try { driver.quit(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}

