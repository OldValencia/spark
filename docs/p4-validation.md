# P4 audit: memory, background work and remaining scope

Checked on Windows x64 with JDK 21.0.11 and the bundled WebView2 integration on 2026-11-07. P3 has been manually accepted by the project owner. P4 is partially complete: the current policy preserves pages and reduces their background working set; it does not suspend or evict them.

## Process measurements

The probe ran the actual MainWindow from `installDist` JARs with the application's launcher JVM options, except the packaged CDS archive path. It used a disposable preferences directory and WebView2 profile, without signing in. This was a release-like probe, not an installed EXE benchmark. ChatGPT, Wikipedia and Claude reached a completed navigation without a reported load error; that does not verify authenticated functionality or prove the pages displayed more than an access challenge.

Each phase settled for six seconds, then sampled CPU for three seconds. The process list contained the probe JVM and its descendants before starting the PowerShell memory sampler. It included WebView2 renderer, browser, GPU and utility processes, plus the console host; it excluded Gradle and the sampler. Memory was read with Windows `Get-Process` immediately after the CPU interval. CPU is the summed process CPU-time delta as a percentage of one logical core. No forced GC or working-set trimming was used.

| Scenario | Processes | Working set, MiB | Private bytes, MiB | CPU, % of one core |
| --- | ---: | ---: | ---: | ---: |
| One site, visible | 8 | 680.5 | 654.9 | 0.00 |
| One site, hidden | 8 | 567.0 | 540.3 | 0.52 |
| Three sites, visible | 12 | 1150.8 | 980.4 | 2.60 |
| Three sites, hidden | 12 | 256.6 | 975.9 | 1.04 |
| Three sites, restored | 12 | 382.5 | 973.9 | 2.08 |
| Local streamed response, visible | 9 | 758.4 | 679.2 | 2.60 |
| Local streamed response, hidden | 9 | 703.9 | 582.8 | 2.08 |

These are sequential snapshots from one run per scenario, not averages or performance guarantees. Summing working sets counts shared pages more than once. Private bytes measure private committed memory, not physical residency. The three-site result shows substantial paging out with nearly unchanged private commitment; it does not mean the pages' live data shrank by 894 MiB. The smaller streaming reduction also shows why one idle result cannot predict every workload. A measured 0.00% means no CPU increment was observed during that short interval.

The local fixture streamed 70 event-stream chunks over about 35 seconds. Its fetch reader updated the page for each chunk. The final chunk arrived while Spark was hidden, and a JavaScript check confirmed `draft=unfinished`, `last=true`, `hidden=true`. This validates a controlled background stream, not a paid or authenticated AI generation. The stream process also retained the initial signed-out ChatGPT session, so its totals are not a single bare local WebView.

Restoring the three-site window dispatched the warm browser presentation in 5–31 ms and completed native focus in 37 ms in this run. Those are application log timings, not first-painted-frame measurements. The temporary probes and detailed process output are under ignored `build/readme-demo/` and `build/licensing/p4-*.log` in the audit workspace.

## Original plan checked against the code

| Plan item | Result |
| --- | --- |
| Whole-process memory and CPU | Initial signed-out and controlled-stream measurements above; long sessions, authenticated generations and broader hardware coverage remain unmeasured. |
| Native suspend/resume | WebView2 exposes `ICoreWebView2_3.TrySuspend` and `Resume`. Spark's wrapper currently exposes visibility and memory-target control, with no suspend implementation. Runtime support was not probed via QueryInterface in this audit. |
| Preserve active work | Hidden/inactive sessions use native visibility and Low memory target. No automatic navigation, unloading or suspension. The background stream, draft, scroll and live JS-data integration test passes. |
| Mapper, timers and JVM tuning | No new tuning justified by this small dataset. `SparkJsApi` already shares its ObjectMapper. Navigation schedulers and unbounded bridge/dispatch queues remain candidates for a separate bounded-work review. |
| Kotlin/Compose | No demonstrated requirement for a migration. Keep the current shell. |

[Microsoft's native API documentation](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2_3) describes actual suspension. Spark's `hibernate()` name does not imply that API is used. Adding suspension needs a separate policy for in-flight work; window hiding alone is not enough information to safely stop an arbitrary website.

Existing recovery, atomic/hash-addressed DLL extraction, one-shot browsing-data callback cleanup and native owner-thread checks remain in place. These are robustness improvements, not evidence of reduced total memory by themselves.

## Verification and limitations

The 45 ordinary tests pass. Two complete native runs each executed 29 tests with 28 passing: one run missed a physical drag in `SiteListReorderTest`; another passed that drag test but failed JUnit's temporary-directory cleanup after `NativeLibraryLoadingTest`, with Windows denying deletion of native DLLs. The latter test's two child processes both exited successfully. Focused reruns of the drag/mascot pair and the two-JVM DLL test passed. This audit does not claim a clean full native suite or a proven fix for either intermittent failure. The existing Gradle-worker `Chrome_WidgetWin_0 / 1411` shutdown diagnostic can still appear; the separate natural application-exit test passes.

`installDist` succeeds, including the new legal-notice directory. The process probes exit naturally. No production Java behavior or JVM tuning was changed for this audit. Clean installer upgrade, authenticated uploads/downloads and generation, and long-running resource growth remain release checks. macOS/Linux were not run.

## Custom sites and authentication

`WebviewNavigator.AUTH_DOMAINS` contains common identity providers as well as AI-specific origins. It does not validate the configured site list. Ordinary HTTP(S) custom sites remain supported. The real limitation is that `isAuthUrl` is used both for UI/history classification and permission to stay inside the WebView across origins.

For example, an unfamiliar `https://identity.example/realms/team/protocol/openid-connect/auth` is not recognized by the current patterns and can be sent to the system browser. Conversely, `https://unrelated.example/login` is classified as auth by its path alone. A same-host application login generally works, while an unlisted cross-origin SSO route may break. The README's custom-site support should not be read as a promise that every authentication flow works.

A follow-up should separate UI classification from navigation policy and add per-site trusted authentication origins, with explicit handling of unknown cross-origin login routes. Expanding an AI-domain list indefinitely or allowing every origin does not resolve that policy problem. No navigation-policy change is included in this audit.

Opening a URL in the default browser is only a handoff of the address. It does not share Spark's WebView2 cookies or login session. It can still be a useful optional action, but cannot promise seamless continuation; exporting session cookies is not needed for that feature.
