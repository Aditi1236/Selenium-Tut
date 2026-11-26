package org.example.pages;

import io.qameta.allure.Allure;
import org.example.config.BrowserConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.nio.file.Path;

/** Shared form actions. Step titles intentionally exclude input values. */
public class WebFormPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    public WebFormPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, BrowserConfig.timeout());
    }

    private void step(String title, Runnable action) {
        if (Allure.getLifecycle().getCurrentTestCase().isPresent()) {
            Allure.step(title, action::run);
        } else {
            action.run();
        }
    }

    public WebFormPage open() {
        step("Open the web form", () -> {
            driver.get(BrowserConfig.url());
            field("my-text");
        });
        return this;
    }

    public WebElement field(String name) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.name(name)));
    }

    public WebElement control(String id) {
        return wait.until(ExpectedConditions.elementToBeClickable(By.id(id)));
    }

    public void fill(String name, String value) {
        step("Fill " + name + " (value omitted)", () -> {
            WebElement element = field(name);
            element.clear();
            if (!value.isEmpty()) element.sendKeys(value);
        });
    }

    public String value(String name) {
        return field(name).getDomProperty("value");
    }

    public void selectCheckbox(String id) {
        step("Select checkbox " + id, () -> {
            WebElement element = control(id);
            if (!element.isSelected()) element.click();
        });
    }

    public void selectRadio(String id) {
        step("Select radio button " + id, () -> control(id).click());
    }

    public Select dropdown() {
        return new Select(field("my-select"));
    }

    public void submit() {
        step("Submit the form", () -> wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector("button[type='submit']"))).click());
    }

    public String result() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("message"))).getText();
    }

    public void upload(Path file) {
        step("Choose the public upload fixture", () ->
                driver.findElement(By.name("my-file")).sendKeys(file.toAbsolutePath().toString()));
    }

    public void waitForUrl(String url) {
        wait.until(ExpectedConditions.urlToBe(url));
    }
}
