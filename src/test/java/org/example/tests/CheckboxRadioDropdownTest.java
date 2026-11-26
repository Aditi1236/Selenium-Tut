package org.example.tests;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;
@Epic("Web form automation") @Feature("Selection controls")
public class CheckboxRadioDropdownTest extends BaseTest {
 @Test(groups="ui",description="Select the first checkbox") public void testCheckboxSelect() { page.selectCheckbox("my-check-1"); Assert.assertTrue(page.control("my-check-1").isSelected()); }
 @Test(groups="ui",description="Second checkbox starts unchecked") public void testCheckbox2DefaultUnchecked() { Assert.assertFalse(page.control("my-check-2").isSelected()); }
 @Test(groups="ui",description="Select the second radio button") public void testRadioButtonSelect() { page.selectRadio("my-radio-2"); Assert.assertTrue(page.control("my-radio-2").isSelected()); }
 @Test(groups="ui",description="Radio buttons are mutually exclusive") public void testRadioButtonMutualExclusion() { page.selectRadio("my-radio-1"); Assert.assertTrue(page.control("my-radio-1").isSelected()); Assert.assertFalse(page.control("my-radio-2").isSelected()); page.selectRadio("my-radio-2"); Assert.assertTrue(page.control("my-radio-2").isSelected()); Assert.assertFalse(page.control("my-radio-1").isSelected()); }
 @Test(groups="ui",description="Select dropdown option by text") public void testDropdownSelectByText() { page.dropdown().selectByVisibleText("Two"); Assert.assertEquals(page.dropdown().getFirstSelectedOption().getText(),"Two"); }
 @Test(groups="ui",description="Select dropdown option by index") public void testDropdownSelectByIndex() { page.dropdown().selectByIndex(1); Assert.assertEquals(page.dropdown().getFirstSelectedOption().getText(),"One"); }
}
