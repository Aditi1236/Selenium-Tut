package org.example.utils;

import org.openqa.selenium.WebDriver;

/**
 * DriverManager — thread-safe WebDriver holder using ThreadLocal.
 *
 * Why ThreadLocal?
 *   - Allows the ScreenshotListener (which runs on the same thread as the test)
 *     to retrieve the active WebDriver instance without being passed it directly.
 *   - Makes the design parallel-safe if tests are ever run in parallel.
 *
 * Usage:
 *   DriverManager.setDriver(driver);   // in @BeforeMethod
 *   DriverManager.getDriver();         // anywhere on the same thread
 *   DriverManager.removeDriver();      // in @AfterMethod (prevent memory leaks)
 */
public class DriverManager {

    private static final ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> evidenceCaptured = ThreadLocal.withInitial(() -> false);

    public static boolean markEvidence() {
        if (evidenceCaptured.get()) return false;
        evidenceCaptured.set(true);
        return true;
    }

    private DriverManager() {
        // Utility class — do not instantiate
    }

    /** Store the WebDriver instance for the current thread. */
    public static void setDriver(WebDriver driver) {
        driverThreadLocal.set(driver);
        evidenceCaptured.set(false);
    }

    /** Retrieve the WebDriver instance for the current thread. */
    public static WebDriver getDriver() {
        return driverThreadLocal.get();
    }

    /** Remove the WebDriver reference to prevent memory leaks after a test completes. */
    public static void removeDriver() {
        driverThreadLocal.remove();
        evidenceCaptured.remove();
    }
}
