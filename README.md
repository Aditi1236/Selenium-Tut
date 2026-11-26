# SeleniumTut

A local browser automation workspace built with Java 17+, Selenium WebDriver,
TestNG, Allure 2, and a dependency-free Python dashboard. The demo interacts with
Selenium's public web form; the default suite contains 30 cases (22 original
checks plus eight input-boundary, upload, and keyboard cases).

## Prerequisites

- JDK 17 or newer, with `JAVA_HOME` pointing to a JDK. Tested locally on Java 26.
- Maven 3.9+ on `PATH`.
- Python 3.10+ for the local dashboard and its backend checks.
- Chrome, Edge, or Firefox installed for the browser you choose. Chrome is default.
- Internet access for Maven dependencies, Selenium Manager driver downloads, and
  the public sample website. No browser is needed for the smoke tests.

## Start the dashboard

From PowerShell:

```powershell
cd "C:\Users\ADITI TOAMAR\Downloads\SeleniumTut\SeleniumTut"
python dashboard/server.py
```

Open **http://127.0.0.1:8765**. Keep that terminal running; use Ctrl+C to stop it.
To use a different port: `python dashboard/server.py --port 8766`.

Choose browser, test scope, viewport, and headless mode, then select **Run tests**.
Select **Use local sample** for the bundled native-control form, which avoids
external CDN loading failures. The public Selenium URL remains the default.
The dashboard shows actual Allure result records as they arrive, live Maven
output, summary metrics, failure details, screenshots, recent runs, and a link
to the generated Allure report. Progress counts completed results rather than
estimating a percentage. Duration includes Maven and report generation.

Each dashboard launch runs the predefined Maven test goal followed by report
generation. A failed test run still generates a report when results exist.
Screenshots and the complete report become available after the run is archived.
The dashboard starts empty until its first run; command-line runs are not
silently imported into dashboard history.

## Run from Maven

```powershell
# Browser demo (opens Chrome, exercises the form, then closes Chrome)
mvn compile exec:java

# Complete suite, headless Chrome
mvn clean test "-Dheadless=true"

# No-browser smoke tests
mvn test "-Dgroups=smoke"

# Browser tests with Edge
mvn test "-Dgroups=ui" "-Dbrowser=edge" "-Dheadless=true"

# Firefox at a custom viewport
mvn test "-Dbrowser=firefox" "-Dheadless=true" "-DwindowSize=1280x800"

# Element and navigation timeouts
mvn test "-Dtimeout=20" "-DpageTimeout=60" "-Dheadless=true"

# Generate a report, then serve it locally
mvn allure:report
python -m http.server 8767 --bind 127.0.0.1 --directory .reports/allure
```

Open http://127.0.0.1:8767 for the generated report. `mvn allure:serve` is also
available, but the durable history workflow uses `mvn allure:report`.

| Property | Default | Accepted values |
|---|---|---|
| `browser` | `chrome` | `chrome`, `edge`, `firefox` |
| `headless` | `false` in Maven; on in dashboard | `true`, `false` |
| `windowSize` | `1440x1000` | `WIDTHxHEIGHT`, 320–3840 wide, 320–2160 high |
| `baseUrl` | Selenium public web-form URL | HTTP(S) form URL without credentials, query, or fragment |
| `timeout` | `15` | Element waits, 1–120 seconds |
| `pageTimeout` | `45` | Page load, 1–180 seconds |
| `groups` | All | `smoke` or `ui` for a subset |

`baseUrl` must point to a compatible copy of Selenium's sample form with the
same controls and success page. It does not make this a general website crawler.
Navigation checks use the form's parent directory and exact expected URLs.

## Structure and design

- `src/main/java/org/example/config/BrowserConfig.java`: validates configuration
  and creates Chrome, Edge, or Firefox with shared viewport and timeout settings.
- `src/main/java/org/example/pages/WebFormPage.java`: shared locators, explicit
  waits, form actions, uploads, and result handling.
- `src/main/java/org/example/SeleniumTut.java`: runnable browser demo.
- `src/test/java/org/example/tests`: independent TestNG cases with a fresh driver
  for every invocation; boundary values come from a data provider.
- `src/test/java/org/example/listeners`: environment metadata, readable report
  names, history restoration, and evidence capture before driver cleanup.
- `dashboard/server.py`: Python standard-library HTTP server and Maven runner.
- `dashboard/static`: buildless, responsive HTML/CSS/JavaScript UI; no CDN assets.
- `.github/workflows/tests.yml`: headless Chrome CI against the bundled local form, backend checks, cached report
  history, and report/evidence artifacts even after test failures.

The frontend uses real result files rather than parsing console test counts.
Unknown totals are not presented as fake progress percentages. Search and filters
operate on the selected run, and the details dialog supports keyboard dismissal.
The backend invokes Maven's Java launcher directly without a shell. It validates
all options, enforces same-origin run tokens and Host checks, binds to localhost,
and permits only one dashboard process and one active dashboard run per project.
Do not run separate Maven test commands while a dashboard run is in progress:
both intentionally use the same project output directory.

## Reporting and durable history

- Raw launch results and screenshots: `target/allure-results`.
- Surefire test output: `target/surefire-reports`.
- Durable Allure report and trend history: `.reports/allure`.
- Dashboard launch archives: `.dashboard/runs/<run-id>`.

Generate `mvn allure:report` after each command-line test run. Before the next
suite begins, its listener copies the prior report's `history` directory into
new results. Since `.reports` and `.dashboard` are outside `target`, `mvn clean`
does not remove them. The first report has no trend history; subsequent reports
can compare prior runs. CI restores and saves this history between workflow runs.
Generated data is ignored by Git. Archives remain local until you remove them.

Environment metadata includes browser version (when a browser ran), Java, OS,
target URL, viewport, and headless mode. Categories separate driver setup errors,
timeouts, assertion mismatches, and other automation errors.

Failure evidence includes a screenshot with all form inputs hidden, a URL with
credentials/query/fragment removed, and diagnostic exception type/source locations.
Entered passwords are never printed, used as Allure step parameters, or included
in assertion messages. Page source and browser console logs are deliberately not
collected because they can contain application secrets. Use public test fixtures;
application-provided exception text in third-party tooling may contain sensitive
content, so do not point the demo at production credentials.

## Verification

```powershell
python -m unittest discover -s dashboard/tests -v
node --check dashboard/static/app.js
mvn clean test "-Dheadless=true"
```

Optional dashboard browser acceptance check (server must already be running;
run after any other Maven suite finishes):

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=.dashboard/classpath.txt"
$dashboardClasspath = "target/classes;" + (Get-Content .dashboard/classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" "-Djdk.net.unixdomain.tmpdir=." --class-path $dashboardClasspath dashboard/tests/DashboardCheck.java --launch
```

This checks desktop/mobile overflow, executes a real smoke run from the UI,
rejects a concurrent run, checks metrics, filters and the details dialog, and
saves screenshots and JSON evidence under `.dashboard/qa/<browser>`.

## Troubleshooting

- **Java not found / wrong JDK:** check `JAVA_HOME`, then `mvn -version`.
- **Driver download or browser startup fails:** ensure the selected browser is
  installed and your network can reach Selenium Manager's driver download hosts.
  Browser setup errors are real failures, not silently skipped passes.
- **Windows loopback error:** `.mvn/jvm.config` and the test JVM use
  `-Djdk.net.unixdomain.tmpdir=.`. Add the same VM option for an IDE launch.
- **Chrome DevTools version warning:** this suite uses standard WebDriver APIs,
  not CDP; a missing matching DevTools module does not invalidate passing tests.
- **Report unavailable:** inspect the execution log and run `mvn allure:report`.
  Initial dependency downloads may take time. Report-generation failure is shown
  separately from test results.
- **Dashboard connection lost:** keep its Python terminal open, then refresh.
- **Another dashboard already running:** reuse that process or stop it with
  Ctrl+C. A project-level OS lock prevents competing dashboard instances.
- **Port busy:** use another `--port`; only one dashboard can own this project.

Chrome has passed the complete 30-case suite and dashboard acceptance. Edge has
passed dashboard acceptance and the browser demo. Firefox's functional demo
also passed using the runtime obtained by Selenium Manager. The CI workflow must run in your GitHub repository to
verify its hosted environment.

To explicitly verify failure evidence, run the opt-in diagnostic below. It is
excluded from the normal suite and **intentionally exits with a test failure**:

```powershell
mvn test "-Dtest=FailureEvidenceProbe" "-Dheadless=true"
mvn allure:report
```

Inspect its diagnostic summary, sanitized URL, and masked screenshot in Allure.
Then rerun the regular suite to return the latest report to normal results.


## Repeatable local form checks

With the dashboard running, use its **Use local sample** button or run:

```powershell
mvn clean test "-Dheadless=true" "-DbaseUrl=http://127.0.0.1:8765/fixture/web-form.html"
```

Without the dashboard, serve the fixture in another terminal:

```powershell
python -m http.server 8768 --bind 127.0.0.1 --directory dashboard/tests/fixtures
```

Then run:

```powershell
mvn clean test "-Dheadless=true" "-DbaseUrl=http://127.0.0.1:8768/web-form.html"
```

The fixture preserves Selenium's native form markup and GET submission. External
scripts/styles were removed and a small local stylesheet added; the original
JavaScript datepicker is outside this fixture's coverage. Source attribution and
the Apache-2.0 license are included in `dashboard/tests/fixtures`.

Public-site verification can encounter intermittent renderer/CDN timeouts. These
are reported as setup failures. Browser transport waits are bounded, unresponsive
sessions skip additional evidence commands, and TestNG continues independent cases
following setup failures. The normal suite does not automatically retry failures.


## Completed verification and current dependencies

The current Selenium version is **4.52.0**. This replaces the older 4.25.0 driver
manager, which attempted to discover Edge drivers on Microsoft's retired CDN.
Java 17 compilation, TestNG 7.10.2, Allure 2.29.0 and AspectJ 1.9.26 are retained.

The verification performed on 2 November 2026 includes:

- All 30 cases passed in headless Chrome against the bundled form, launched from
  the actual dashboard; report generation and archived report access passed.
- Ten backend regressions passed, including failure-report archiving, incomplete
  result writes, stale launch results, request protection and status classification.
- The intentional failure probe produced one expected assertion failure, with
  linked diagnostic summary, sanitized URL and masked screenshot. Entered values,
  query token and fragment were absent from the recorded JSON/text evidence.
- Allure trend entries were retained across `mvn clean` and a subsequent run/report.
- Chrome and Edge dashboard checks cover 320, 390, 768, 1024 and 1440 pixel widths,
  search/status filters, dialogs, focus preservation across polling, connection
  loss/recovery and archived Allure navigation.
- Edge and Firefox demonstrations completed navigation, text entry, selections
  and both submission methods successfully.

Run the **complete** dashboard acceptance rather than only a smoke launch:

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=.dashboard/classpath.txt"
$dashboardClasspath = "target/classes;" + (Get-Content .dashboard/classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" "-Djdk.net.unixdomain.tmpdir=." --class-path $dashboardClasspath dashboard/tests/DashboardCheck.java --launch-all
```

Use `--launch` for a three-case smoke launch, or `--inspect` to inspect an already
completed run without starting another Maven suite. Add `"-Dbrowser=edge"` before
`--class-path` to run the UI checks in Edge. The server must remain running.

Verify deliberate failure evidence (the Maven test command intentionally fails;
the following verifier must pass):

```powershell
mvn test "-Dtest=FailureEvidenceProbe" "-Dheadless=true" "-DbaseUrl=http://127.0.0.1:8765/fixture/web-form.html"
mvn allure:report
python dashboard/tests/verify_evidence.py
```

Evidence is captured inside the active teardown fixture, before driver cleanup.
Capturing in the post-test listener can create orphan files after Allure closes
its test record; the teardown timing avoids that problem.

To check history continuity, first generate a baseline report, then:

```powershell
python dashboard/tests/verify_history.py --snapshot
mvn clean test "-Dheadless=true" "-DbaseUrl=http://127.0.0.1:8765/fixture/web-form.html"
mvn allure:report
python dashboard/tests/verify_history.py
```

Machine-readable verification and screenshots are stored under
`.dashboard/verification` and `.dashboard/qa`. See `VERIFICATION.md` for the final
validation record. The application remains a localhost tool; public multi-user
hosting and authentication are outside its deployment model.
