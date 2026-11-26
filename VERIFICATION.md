# Verification record

Verified on 2 November 2026 on Windows 11 with Java 26.0.1.

| Check | Result |
|---|---|
| Selenium version | 4.52.0; current Edge driver discovery verified |
| Full Chrome suite launched through dashboard | 30 passed, 0 failed/broken/skipped |
| Full run identifier | `f6f2997b5bfc4cf7984c31a04e760283` |
| Allure report generation | Exit code 0; archived report opens |
| Backend regressions | 10 passed |
| Intentional failure probe | Expected assertion failure; evidence verifier passed |
| Failure evidence | Linked diagnostic summary, sanitized URL, masked PNG |
| Redaction | Entered values, query token and fragment absent from JSON/text evidence |
| Allure history across Maven clean | 9 prior entries retained; 10 entries after report |
| Chrome UI | Passed at 320, 390, 768, 1024, 1440 pixels |
| Edge UI | Passed at the same five widths |
| Interactions | Search, status filters, dialogs, focus across polling, connection loss/recovery |
| Concurrent run protection | Valid-token second request rejected with HTTP 409 |
| Edge functional demo | Navigation, form controls and both submissions passed |
| Firefox functional demo | Navigation, form controls and both submissions passed using Selenium Manager runtime |

The probe is intentionally excluded from the normal suite. Its expected Maven
failure is distinct from a failed verification. Capture occurs inside the active
teardown fixture so Allure links files instead of leaving orphan attachments.

The complete dashboard run took 149.6 seconds including Maven,
browser lifecycle, reporting and archiving. This is one observed run, not a
performance guarantee. The bundled native-control form was the test target.

## Evidence

- `.dashboard/verification/final-verification.json`
- `.dashboard/verification/failure-probe/results` and `report`
- `.dashboard/verification/history-verification.json`
- `.dashboard/verification/ui-full-run.json`
- `.dashboard/qa/chrome` and `.dashboard/qa/edge`
- `.dashboard/runs/f6f2997b5bfc4cf7984c31a04e760283`

## Remaining environment boundaries

The server is designed for localhost operation, rather than public multi-user
hosting. The GitHub Actions workflow is supplied but has not been executed on a
hosted GitHub runner in this session. Public target pages can still fail due to
network, CDN or browser resource conditions; these are reported as failures.
