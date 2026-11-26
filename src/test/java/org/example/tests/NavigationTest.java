package org.example.tests;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;
@Epic("Web form automation") @Feature("Navigation")
public class NavigationTest extends BaseTest {
 private String destination() { return java.net.URI.create(FORM_URL).resolve("./").toString(); }
 @Test(groups="ui", description="Web form has the expected title") public void testPageTitle() { Assert.assertEquals(driver.getTitle(), "Web form"); }
 @Test(groups="ui", description="Navigate to the parent page") public void testNavigateTo() { driver.navigate().to(destination()); page.waitForUrl(destination()); Assert.assertEquals(driver.getCurrentUrl(), destination()); }
 @Test(groups="ui", description="Back returns to the exact form URL") public void testNavigateBack() { driver.navigate().to(destination()); driver.navigate().back(); page.waitForUrl(FORM_URL); Assert.assertEquals(driver.getCurrentUrl(), FORM_URL); }
 @Test(groups="ui", description="Forward restores the exact destination") public void testNavigateForwardAndBack() { driver.navigate().to(destination()); driver.navigate().back(); page.waitForUrl(FORM_URL); driver.navigate().forward(); page.waitForUrl(destination()); Assert.assertEquals(driver.getCurrentUrl(), destination()); }
 @Test(groups="ui", description="Refresh preserves the form URL") public void testNavigateRefresh() { driver.navigate().refresh(); page.field("my-text"); Assert.assertEquals(driver.getCurrentUrl(), FORM_URL); }
}
