package org.example.tests;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;
@Epic("Web form automation") @Feature("Input fields")
public class InputFieldTest extends BaseTest {
 @Test(groups="ui",description="Text field accepts input") public void testTextBoxInput() { page.fill("my-text","Hello Selenium"); Assert.assertEquals(page.value("my-text"),"Hello Selenium"); }
 @Test(groups="ui",description="Clear replaces existing input") public void testTextBoxClearAndRetype() { page.fill("my-text","Initial text"); page.fill("my-text","Selenium WebDriver with Java"); Assert.assertEquals(page.value("my-text"),"Selenium WebDriver with Java"); }
 @Test(groups="ui",description="Password field accepts input and remains masked") public void testPasswordInput() { String sample="Demo-only-value"; page.fill("my-password",sample); Assert.assertTrue(sample.equals(page.value("my-password")),"Password input must retain its value"); Assert.assertEquals(page.field("my-password").getDomAttribute("type"),"password"); }
 @Test(groups="ui",description="Textarea preserves multiple lines") public void testTextAreaInput() { String text="This is a textarea.\nSelenium is typing here."; page.fill("my-textarea",text); Assert.assertEquals(page.value("my-textarea"),text); }
 @Test(groups="ui",description="Text field is visible and enabled") public void testTextBoxProperties() { Assert.assertTrue(page.field("my-text").isDisplayed()); Assert.assertTrue(page.field("my-text").isEnabled()); Assert.assertEquals(page.field("my-text").getTagName(),"input"); }
}
