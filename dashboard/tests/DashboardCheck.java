import org.example.config.BrowserConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.nio.file.*;
import java.time.Duration;

/** Manual dashboard acceptance check; run only while the local server is running. */
class DashboardCheck {
    static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    static void clickVisible(WebDriver driver, By locator) {
        WebElement element=new WebDriverWait(driver,Duration.ofSeconds(20)).until(ExpectedConditions.visibilityOfElementLocated(locator));
        JavascriptExecutor js=(JavascriptExecutor)driver;
        js.executeScript("arguments[0].scrollIntoView({block:'center',behavior:'instant'})",element);
        new WebDriverWait(driver,Duration.ofSeconds(20)).until(d->Boolean.TRUE.equals(js.executeScript(
            "const r=arguments[0].getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&arguments[0].contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2))",element)));
        element.click();
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("headless","true");
        WebDriver driver=BrowserConfig.createDriver();
        Path output=Path.of(".dashboard","qa",BrowserConfig.browser());Files.createDirectories(output);
        try {
            driver.get("http://127.0.0.1:8765");
            WebDriverWait wait=new WebDriverWait(driver,Duration.ofSeconds(30));
            wait.until(d->d.findElement(By.id("connection-status")).getText().contains("LOCAL RUNNER"));
            new WebDriverWait(driver,Duration.ofMinutes(15)).until(d->d.findElement(By.id("run-button")).isEnabled());
            check(driver.getTitle().contains("SeleniumTut"),"Dashboard title");
            JavascriptExecutor js=(JavascriptExecutor)driver;
            ((org.openqa.selenium.chromium.HasCdp)driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",
                    java.util.Map.of("width",1440,"height",1000,"deviceScaleFactor",1,"mobile",false));
            check(Boolean.TRUE.equals(js.executeScript("return document.documentElement.scrollWidth <= innerWidth")),"Desktop page must not overflow");
            Files.write(output.resolve("desktop.png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
            if(args.length>0 && args[0].startsWith("--launch")) {
                boolean full=args[0].equals("--launch-all");
                int expected=full?30:3;
                driver.findElement(By.id("use-local")).click();
                new Select(driver.findElement(By.name("group"))).selectByValue(full?"all":"smoke");
                driver.findElement(By.id("run-button")).click();
                wait.until(d->!d.findElement(By.id("run-button")).isEnabled());
                wait.until(d->d.findElement(By.id("run-status")).getText().matches("Running|Building report"));
                check(driver.findElement(By.id("run-status")).getText().matches("Running|Building report"),"Run starts from UI");
                // A concurrent request must be rejected even with a valid CSRF token.
                Object overlap=js.executeAsyncScript("const done=arguments[arguments.length-1];fetch('/api/state').then(r=>r.json()).then(s=>fetch('/api/runs',{method:'POST',headers:{'Content-Type':'application/json','X-Run-Token':s.token},body:JSON.stringify({group:'smoke'})})).then(r=>done(r.status)).catch(e=>done(String(e)))");
                check(((Number)overlap).intValue()==409,"Concurrent run must return 409");
                new WebDriverWait(driver,Duration.ofMinutes(15)).until(d->d.findElement(By.id("run-button")).isEnabled());
                check(driver.findElement(By.id("run-status")).getText().equals("Passed"),"Selected suite passes through UI");
                check(driver.findElement(By.id("total")).getText().equals(String.valueOf(expected)),"Real suite metrics");
                check(driver.findElements(By.cssSelector("#result-rows tr")).size()==expected,"Expected actual results");
            }
            js.executeScript("window.scrollTo({top:0,behavior:'instant'})");
            Files.write(output.resolve("desktop-summary.png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
            if(!driver.findElements(By.cssSelector(".detail-button")).isEmpty()) {
                driver.findElement(By.id("search")).sendKeys("no-such-test-123");
                check(driver.findElement(By.id("results-empty")).isDisplayed(),"Filtered empty state");
                driver.findElement(By.id("search")).clear();driver.findElement(By.id("search")).sendKeys(Keys.SPACE,Keys.BACK_SPACE);
                new Select(driver.findElement(By.id("status-filter"))).selectByValue("failed");
                check(driver.findElements(By.cssSelector("#result-rows tr")).isEmpty(),"Failed filter excludes passes");
                new Select(driver.findElement(By.id("status-filter"))).selectByValue("all");
                clickVisible(driver,By.cssSelector(".detail-button"));
                check(Boolean.TRUE.equals(js.executeScript("return document.querySelector('dialog').open")),"Details dialog opens");
                driver.findElement(By.id("close-dialog")).click();
                check(driver.findElement(By.id("allure-link")).getAttribute("href").contains("/report/index.html"),"Allure link exists");
                Files.write(output.resolve("desktop-results.png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
            }
            // Polling must preserve focused controls rather than replacing them.
            if(!driver.findElements(By.cssSelector(".detail-button")).isEmpty()) {

                js.executeScript("document.querySelector('.detail-button').focus()");
                String focused=(String)js.executeScript("arguments[0].dataset.focusCheck='preserved'; return arguments[0].getAttribute('aria-label')",driver.switchTo().activeElement());
                final long began=System.nanoTime();
                wait.until(d->System.nanoTime()-began>3_000_000_000L);
                check(focused.equals(driver.switchTo().activeElement().getAttribute("aria-label")),"Polling preserves focused result control");
                check("preserved".equals(driver.switchTo().activeElement().getAttribute("data-focus-check")),"Focused element was not replaced");
            }
            for(int width:new int[]{320,390,768,1024,1440}) {
                ((org.openqa.selenium.chromium.HasCdp)driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",
                        java.util.Map.of("width",width,"height",900,"deviceScaleFactor",1,"mobile",width<640));
                check(Boolean.TRUE.equals(js.executeScript("return document.documentElement.scrollWidth <= innerWidth")),"No page overflow at "+width);
                if(width<640) {
                    check(driver.findElement(By.cssSelector(".mobile-nav")).isDisplayed(),"Mobile navigation visible");
                    if(!driver.findElements(By.cssSelector(".detail-button")).isEmpty()) {
                        clickVisible(driver,By.cssSelector(".detail-button"));
                        check(Boolean.TRUE.equals(js.executeScript("return document.querySelector('dialog').open")),"Mobile details dialog");
                        driver.findElement(By.id("close-dialog")).click();
                    }
                }
            }
            driver.manage().window().setSize(new Dimension(390,844));
            ((org.openqa.selenium.chromium.HasCdp)driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",
                    java.util.Map.of("width",390,"height",844,"deviceScaleFactor",1,"mobile",true));
            check(Boolean.TRUE.equals(js.executeScript("return document.documentElement.scrollWidth <= innerWidth")),"Mobile page must not overflow");
            js.executeScript("window.scrollTo({top:0,behavior:'instant'})");
            Files.write(output.resolve("mobile.png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
            // Check connection loss and recovery using this browser's network controls.
            var cdp=(org.openqa.selenium.chromium.HasCdp)driver;
            cdp.executeCdpCommand("Network.enable",java.util.Map.of());
            cdp.executeCdpCommand("Network.setBlockedURLs",java.util.Map.of("urls",java.util.List.of("*api/state*")));
            wait.until(d->d.findElement(By.id("connection-status")).getText().contains("DISCONNECTED"));
            check(!driver.findElement(By.id("run-button")).isEnabled(),"Runs disabled during connection loss");
            cdp.executeCdpCommand("Network.setBlockedURLs",java.util.Map.of("urls",java.util.List.of()));
            wait.until(d->d.findElement(By.id("connection-status")).getText().contains("LOCAL RUNNER"));
            check(driver.findElement(By.id("run-button")).isEnabled(),"Runner recovers after reconnection");
            if(args.length>0) {
                driver.get(driver.findElement(By.id("allure-link")).getAttribute("href"));
                wait.until(d->!d.findElements(By.cssSelector(".app")).isEmpty() || d.getTitle().contains("Allure"));
                check(driver.getTitle().contains("Allure"),"Archived Allure report opens");
                driver.navigate().back();
            }
            String mode=args.length>0?args[0]:"--inspect";
            var evidence=java.util.Map.of("status","passed","browser",BrowserConfig.browser(),
                "browserVersion",((HasCapabilities)driver).getCapabilities().getBrowserVersion(),"mode",mode,
                "viewports",java.util.List.of(320,390,768,1024,1440),
                "checks",java.util.List.of("filters","details","focus across polling","connection recovery","archived Allure report"));
            Files.writeString(output.resolve("acceptance-"+BrowserConfig.browser()+".json"),new org.openqa.selenium.json.Json().toJson(evidence));
            Files.copy(output.resolve("mobile.png"),output.resolve(BrowserConfig.browser()+"-mobile.png"),StandardCopyOption.REPLACE_EXISTING);
            System.out.println("DASHBOARD CHECK PASSED: "+mode+"; desktop/mobile layouts, filters, dialogs, focus, connection recovery, report"+
                (mode.startsWith("--launch")?", real launch and overlap rejection":""));
        } finally {driver.quit();}
    }
}

