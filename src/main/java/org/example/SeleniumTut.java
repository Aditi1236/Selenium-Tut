package org.example;

import org.example.config.BrowserConfig;
import org.example.pages.WebFormPage;
import org.openqa.selenium.*;

public class SeleniumTut {
    public static void main(String[] args) {
        WebDriver driver = BrowserConfig.createDriver();
        try {
            WebFormPage page = new WebFormPage(driver).open();
            System.out.println("Page title: " + driver.getTitle());
            String destination = java.net.URI.create(BrowserConfig.url()).resolve("./").toString();
            driver.navigate().to(destination); page.waitForUrl(destination);
            driver.navigate().back(); page.waitForUrl(BrowserConfig.url());
            driver.navigate().forward(); page.waitForUrl(destination);
            driver.navigate().back(); page.waitForUrl(BrowserConfig.url());
            driver.navigate().refresh();
            page.fill("my-text", "Hello Selenium");
            page.fill("my-text", "Selenium WebDriver with Java");
            page.fill("my-password", "Demo-only-value");
            page.fill("my-textarea", "This is a textarea.\nSelenium is typing here.");
            page.selectCheckbox("my-check-1"); page.selectRadio("my-radio-2");
            page.dropdown().selectByVisibleText("Two");
            page.field("my-text").sendKeys(Keys.ENTER);
            System.out.println("Result (Enter key): " + page.result());
            page.open(); page.fill("my-text", "Submit via button"); page.submit();
            System.out.println("Result (Submit button): " + page.result());
        } finally { driver.quit(); System.out.println("Browser closed."); }
    }
}
