package org.example.tests;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.example.config.BrowserConfig;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.net.URI;
import java.lang.reflect.Modifier;

@Epic("Web form automation")
@Feature("Smoke & sanity")
public class SmokeTest {
    @Test(groups = "smoke", description = "Demo exposes a runnable main entry point")
    public void verifyMainClassExists() throws Exception {
        var main = org.example.SeleniumTut.class.getDeclaredMethod("main", String[].class);
        Assert.assertTrue(Modifier.isPublic(main.getModifiers()));
        Assert.assertTrue(Modifier.isStatic(main.getModifiers()));
        Assert.assertEquals(main.getReturnType(), void.class);
    }

    @Test(groups = "smoke", description = "Configured target is a valid HTTP or HTTPS URL")
    public void verifyFormUrlFormat() {
        URI target = URI.create(BrowserConfig.url());
        Assert.assertTrue("https".equals(target.getScheme()) || "http".equals(target.getScheme()));
        Assert.assertNotNull(target.getHost());
        Assert.assertNull(target.getUserInfo());
    }

    @Test(groups = "smoke", description = "Java runtime meets the Java 17 requirement")
    public void verifyJavaVersion() {
        Assert.assertTrue(Runtime.version().feature() >= 17);
    }
}
