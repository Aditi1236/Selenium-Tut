package org.example.tests;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.nio.file.Files;
import java.nio.file.Path;

@Epic("Web form automation")
@Feature("Input boundaries and accessibility")
public class AdvancedInputTest extends BaseTest {
    @DataProvider(name = "textCases")
    public Object[][] textCases() {
        return new Object[][] {
            {"Empty", ""},
            {"Long", "a".repeat(2000)},
            {"Unicode", "नमस्ते 世界 café"},
            {"Special characters", "<script> & \\\"quotes\\\" ' + = %"}
        };
    }

    @Test(groups = "ui", dataProvider = "textCases",
            description = "Text input preserves boundary values")
    public void textBoundaries(String scenario, String value) {
        Allure.parameter("Scenario", scenario);
        page.fill("my-text", value);
        Assert.assertTrue(value.equals(page.value("my-text")),
                "Text must round-trip unchanged for " + scenario);
        page.submit();
        Assert.assertEquals(page.result(), "Received!");
    }

    @Test(groups = "ui", description = "Disabled input rejects typing")
    public void disabledInput() {
        WebElement input = page.field("my-disabled");
        Assert.assertFalse(input.isEnabled());
        Assert.expectThrows(ElementNotInteractableException.class, () -> input.sendKeys("blocked"));
        Assert.assertEquals(input.getDomProperty("value"), "");
    }

    @Test(groups = "ui", description = "Read-only input retains its original value")
    public void readonlyInput() {
        WebElement input = page.field("my-readonly");
        String before = input.getDomProperty("value");
        Assert.assertNotNull(input.getDomAttribute("readonly"));
        input.sendKeys("change");
        Assert.assertEquals(input.getDomProperty("value"), before);
    }

    @Test(groups = "ui", description = "File upload retains the selected filename")
    public void uploadFile() throws Exception {
        Path file = Files.createTempFile("selenium-upload-", ".txt");
        try {
            Files.writeString(file, "Public automation fixture");
            page.upload(file);
            Assert.assertTrue(page.value("my-file").endsWith(file.getFileName().toString()));
            page.submit();
            Assert.assertEquals(page.result(), "Received!");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(groups = "ui", description = "Tab moves keyboard focus to the password field")
    public void keyboardNavigation() {
        page.field("my-text").click();
        page.field("my-text").sendKeys(Keys.TAB);
        Assert.assertEquals(driver.switchTo().activeElement().getDomAttribute("name"), "my-password");
    }
}
