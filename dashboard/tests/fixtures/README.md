# Local Selenium form fixture

Source: https://www.selenium.dev/selenium/web/web-form.html and
https://www.selenium.dev/selenium/web/submitted-form.html, downloaded 2026-11-02.
Selenium is licensed under Apache-2.0; see SELENIUM-LICENSE.txt.

The native form markup and submission behavior are preserved. External styles,
JavaScript, and the JavaScript datepicker initializer were removed to make the
fixture independent of CDN availability. A small local stylesheet was added.
This fixture verifies native controls; it does not test the original datepicker.

Serve this directory on localhost for deterministic/offline test execution.
