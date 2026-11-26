package org.example.tests;
import org.example.config.BrowserConfig;
import org.example.pages.WebFormPage;
import org.example.utils.DriverManager;
import org.example.listeners.ScreenshotListener;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;
public class BaseTest {
    protected static final String FORM_URL = BrowserConfig.url();
    protected WebDriver driver;
    protected WebFormPage page;
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        driver = BrowserConfig.createDriver();
        DriverManager.setDriver(driver);
        org.example.listeners.ReportListener.environment(driver);
        page = new WebFormPage(driver).open();
    }
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try { if (!result.isSuccess()) ScreenshotListener.capture(result); }
        finally {
            try { if (driver != null) driver.quit(); }
            finally { DriverManager.removeDriver(); driver = null; }
        }
    }
}
