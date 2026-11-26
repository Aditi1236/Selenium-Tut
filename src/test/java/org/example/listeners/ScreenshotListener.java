package org.example.listeners;

import io.qameta.allure.Allure;
import org.example.utils.DriverManager;
import org.openqa.selenium.*;
import org.testng.*;
import java.io.ByteArrayInputStream;
import java.net.URI;

/** Capture inside the active teardown fixture, before quitting the browser.
 * Test listeners can run after Allure has closed the test case, creating orphan files.
 * BaseTest owns capture timing; diagnostics omit page source and entered values.
 */
public class ScreenshotListener implements ITestListener, IConfigurationListener {
    public static void capture(ITestResult result) {
        if (Boolean.TRUE.equals(result.getAttribute("evidenceCaptured")) || !DriverManager.markEvidence()) return;
        result.setAttribute("evidenceCaptured", true);
        WebDriver driver = DriverManager.getDriver();
        Throwable failure = result.getThrowable();
        StringBuilder details = new StringBuilder("Test: ").append(result.getMethod().getMethodName());
        if (failure != null) {
            details.append("\nException: ").append(failure.getClass().getName());
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (frame.getClassName().startsWith("org.example")) details.append("\n at ").append(frame);
            }
        }
        Allure.addAttachment("Diagnostic summary (values omitted)", details.toString());
        if (driver == null) return;
        // A renderer/session timeout is evidence that the browser is unhealthy.
        // Further screenshot and DOM commands would only repeat that wait.
        if (failure instanceof org.openqa.selenium.TimeoutException
                || failure instanceof org.openqa.selenium.NoSuchSessionException
                || failure instanceof org.openqa.selenium.remote.UnreachableBrowserException) {
            System.out.println("[EVIDENCE] Browser is unresponsive; retained diagnostic summary without further browser commands.");
            return;
        }
        try {
            URI url = URI.create(driver.getCurrentUrl());
            String safeUrl = new URI(url.getScheme(), null, url.getHost(), url.getPort(), url.getPath(), null, null).toString();
            Allure.addAttachment("Current URL (query omitted)", safeUrl);
            // Hide all form values during screenshots, including non-password fields.
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("document.querySelectorAll('input,textarea').forEach(e=>{e.dataset.evidenceStyle=e.style.visibility;e.style.visibility='hidden'})");
            try {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment("Failure screenshot (inputs hidden)", "image/png", new ByteArrayInputStream(png), "png");
            } finally {
                js.executeScript("document.querySelectorAll('[data-evidence-style]').forEach(e=>{e.style.visibility=e.dataset.evidenceStyle;delete e.dataset.evidenceStyle})");
            }
        } catch (Exception ignored) { System.out.println("[EVIDENCE] Browser evidence unavailable; diagnostic summary retained."); }
    }
}
