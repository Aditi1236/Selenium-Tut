package org.example.listeners;

import io.qameta.allure.Allure;
import org.example.config.BrowserConfig;
import org.openqa.selenium.*;
import org.testng.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class ReportListener implements ISuiteListener, ITestListener {
    private static final Path RESULTS = Path.of("target", "allure-results");
    @Override public void onStart(ISuite suite) {
        try {
            Files.createDirectories(RESULTS);
            // Fresh launch results; retain history outside Maven's clean directory.
            try (var paths = Files.list(RESULTS)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) Files.delete(file);
            }
            Path history = Path.of(".reports", "allure", "history");
            if (Files.isDirectory(history)) {
                Files.createDirectories(RESULTS.resolve("history"));
                try (var paths = Files.list(history)) {
                    for (Path file : paths.filter(Files::isRegularFile).toList()) Files.copy(file, RESULTS.resolve("history").resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (InputStream categories = getClass().getResourceAsStream("/categories.json")) {
                if (categories != null) Files.copy(categories, RESULTS.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            writeEnvironment(null);
        } catch (IOException e) { throw new UncheckedIOException("Unable to prepare report output", e); }
    }
    public static synchronized void environment(WebDriver driver) { writeEnvironment(driver); }
    private static void writeEnvironment(WebDriver driver) {
        Properties values = new Properties();
        values.setProperty("Browser", BrowserConfig.browser());
        values.setProperty("Headless", System.getProperty("headless", "false"));
        values.setProperty("Java", System.getProperty("java.version"));
        values.setProperty("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        values.setProperty("Target", BrowserConfig.url());
        values.setProperty("Window", System.getProperty("windowSize", "1440x1000"));
        if (driver instanceof HasCapabilities) values.setProperty("Browser.version", ((HasCapabilities) driver).getCapabilities().getBrowserVersion());
        try {
            Files.createDirectories(RESULTS);
            try (Writer out = Files.newBufferedWriter(RESULTS.resolve("environment.properties"))) { values.store(out, "Execution environment"); }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public void onTestStart(ITestResult result) {
        String name = result.getMethod().getDescription();
        if (name != null) Allure.getLifecycle().updateTestCase(test -> test.setName(name));
        String feature = result.getTestClass().getRealClass().getSimpleName().replace("Test", "");
        Allure.story(feature);
        System.out.println("[TEST] START: " + result.getMethod().getMethodName());
    }
    @Override public void onTestSuccess(ITestResult result) {
        System.out.println("[TEST] PASS: " + result.getMethod().getMethodName());
    }
    @Override public void onTestFailure(ITestResult result) {
        System.out.println("[TEST] FAIL: " + result.getMethod().getMethodName());
    }
    @Override public void onTestSkipped(ITestResult result) {
        System.out.println("[TEST] SKIP: " + result.getMethod().getMethodName());
    }
}
