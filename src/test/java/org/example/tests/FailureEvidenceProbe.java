package org.example.tests;

import org.example.listeners.ReportListener;
import org.example.listeners.ScreenshotListener;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Opt-in diagnostic only: deliberately fails to verify screenshots and attachments.
 * Not listed in testng.xml. Run with -Dtest=FailureEvidenceProbe -Dheadless=true.
 */
@Listeners({ReportListener.class, ScreenshotListener.class})
public class FailureEvidenceProbe extends BaseTest {
    @Test(description="Intentional failure to verify evidence capture")
    public void verifyFailureEvidence() {
        page.fill("my-text", "Public evidence fixture");
        page.fill("my-password", "Demo-only-value");
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "history.replaceState({}, '', location.pathname + '?token=private-fixture-token#private-fragment')");
        Assert.fail("Intentional diagnostic probe; excluded from the normal suite");
    }
}
