package org.example.tests;
import io.qameta.allure.*;
import org.openqa.selenium.Keys;
import org.testng.Assert;
import org.testng.annotations.Test;
@Epic("Web form automation") @Feature("Form submission")
public class FormSubmitTest extends BaseTest {
 @Test(groups="ui",description="Submit the form using Enter") public void testSubmitWithEnterKey() { page.fill("my-text","Hello Selenium"); page.field("my-text").sendKeys(Keys.ENTER); Assert.assertEquals(page.result(),"Received!"); }
 @Test(groups="ui",description="Submit the form using the button") public void testSubmitWithButton() { page.fill("my-text","Submit via button"); page.submit(); Assert.assertEquals(page.result(),"Received!"); }
 @Test(groups="ui",description="Submit a completed form") public void testFullFormSubmit() { page.fill("my-text","Full form test"); page.fill("my-password","Demo-only-value"); page.fill("my-textarea","TestNG + Allure automation"); page.selectCheckbox("my-check-1"); page.selectRadio("my-radio-1"); page.submit(); Assert.assertEquals(page.result(),"Received!"); }
}
