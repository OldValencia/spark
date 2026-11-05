# P0/P1 implementation and validation

## Behavior

Product decisions confirmed by the user:

- Automatically focusing a website's editor is optional convenience, not a P1 acceptance requirement. Clicking the field after opening Spark is acceptable.
- Keeping visited pages alive without automatic eviction is a required workflow: write part of a message, hide Spark, find more information elsewhere, reopen it, and continue writing. Increased memory usage is an accepted cost of preserving that context. Do not add idle timers, memory-pressure eviction, or automatic unloading on hide.

- Initialization queues browser work until the native loop is ready instead of polling every 50 ms.
- On Windows, loading follows WebView2 navigation events and navigation IDs. Success reveals the page; failure offers Retry. After 20 seconds the user can retry or reveal a slow page. The fixed 1,200 ms mask is removed.
- Hotkey timing begins on the native input callback and reaches the browser thread through the FX queue. Logs distinguish restore dispatch from a native focus request accepted by WebView2; neither guarantees that an arbitrary website focuses its editor.
- Restore resolves the current JavaFX HWND immediately. Only an unavailable host schedules a bounded retry. There is no unconditional 100 ms wait.
- JavaFX can destroy its HWND when hidden. Spark detaches its browser child first, preserving viewport, DOM, and requests, then attaches it to the current host on restore. Stale queued focus requests are discarded.
- Sites load lazily and stay alive after visiting. Switching back does not navigate again. There is no automatic eviction: it could destroy unsent drafts or interrupt arbitrary websites. Removing a site releases its browser; quitting closes all browsers.
- Page history is keyed by stable site ID, including sites sharing a host. Background sites cannot replace the active-site selection. Disabling history clears remembered addresses. History is separate from in-memory DOM state.
- Windows clearing calls `ICoreWebView2Profile2::ClearBrowsingDataAll`, waits for completion, and reports success or failure. A confirmation explains sign-out and lost unsent text. It is not exposed to page JavaScript and never runs automatically.
- A filesystem lock prevents concurrent launches from creating two instances. Activation before the main window callback is installed is retained for later delivery.

## Validation

Run with JDK 21 and, for native tests, Windows with the WebView2 Runtime:

```powershell
./gradlew.bat test nativeTest --offline --no-daemon
```

`test` excludes the native tag. `nativeTest` creates a fresh profile on every run. `spark.dataDir` redirects preferences and the process lock; `WEBVIEW2_USER_DATA_FOLDER` redirects browser storage. No test uses external sites or signed-in accounts.

Coverage:

- Existing URL, authentication, JSON backup, and write-failure regressions.
- Independent per-site URLs, restoration from disk, host changes, and disabling history.
- Native navigation and profile-wide clearing of HttpOnly cookies, localStorage, sessionStorage, and IndexedDB across localhost and 127.0.0.1.
- A real JavaFX host with native browsers: draft, scroll, background activity, an in-flight HTTP request, live site switching, and 12 rapid hide/restore cycles without additional page navigation.
- Failed navigation, return to a cached page, removing/recreating site browsers, second-instance activation, and repeated shutdown during native initialization.

## Measurements and limits

### Native API/thread contract follow-up

JNA now matches the bundled webview 0.12.0 pointer, C-string, integer and error-result types. Native failures are checked, duplicate binding registration preserves the original callback reference, failed dispatch submissions release unused callbacks, and binding errors use JSON serialization. Direct native/COM access requires the browser's creating thread. Manager navigation is dispatched, while submitted tasks validate the captured view before running. Native teardown remains outside the dispatch monitor.

The Windows/JDK 21 validation run passed 17 unit tests and 5 native tests (`test nativeTest --offline --no-daemon`). Six fake-API tests cover failure paths without opening a DLL or profile. A new real-WebView2 test checks promise rejection containing backslashes, quotes and a newline, null success results, and the original binding after duplicate registration fails. Existing navigation, profile clearing, background streaming/state preservation, renderer/browser process recovery and normal application exit remain covered. The test worker can still emit the documented class-unregister diagnostic at its own termination; this is not suppressed. macOS/Linux were not tested.

### Process-failure recovery follow-up

The browser bridge now observes WebView2 ProcessFailed. Main renderer termination shows a persistent error and Retry reloads that site. Browser-process termination and renderer unresponsiveness request recreation of that site's native view instead of navigating a dead controller. Auxiliary/subframe failures are logged without replacing the main page. Recovery is explicit: there is no automatic refresh of cached sites.

A failed view may have lost unsent text; the error message explains this. A shared browser-process crash can affect several sites, and each affected site remains in error until explicitly retried. Healthy sites are not deliberately reloaded. Native presentation/focus operations avoid a dead browser connection, and late navigation completions cannot hide the failure.

Recreation exposed a dispatch/destruction deadlock: native destruction can synchronously communicate with the FX host while the host tries to acquire the dispatch monitor. The wrapper now marks the instance destroyed under a short lock, releases that lock before native cleanup, and discards queued actions against a destroyed view. Callback storage remains pinned until native destruction returns.

The native FX integration test deliberately navigates the isolated active renderer to `edge://crash`, checks the error/Retry path, and verifies an independent origin's draft survives. It also terminates the browser PID read from that test WebView, after verifying it belongs to the current test worker's descendants. It verifies no silent navigation, a new browser PID after Retry, and separate recovery of another affected cached site. No user browser process or profile is targeted. The unresponsive-renderer and auxiliary-process branches are not covered by an actual hang/auxiliary-crash injection in this run.

### Background memory policy follow-up

The Browser setting **Reduce background memory** defaults to enabled. Inactive sites and a hidden Spark window request the native Low memory target. Before showing a page, the browser thread restores Normal and controller visibility, then shows the HWND. Generation checks prevent stale queued shows from overriding a newer hide. A page covered only by the loading overlay is hidden at the controller level but is not put in Low solely because it is loading.

Turning the setting off applies Normal to already open background pages without reloading them. Older runtimes returning E_NOINTERFACE continue without the optional memory target. A failed target setter disables that optimization for the affected browser rather than blocking page display. No suspend, automatic refresh, page eviction, or cookie clearing is involved.

`BackgroundMemoryTest` checks actual native getter values, switches to hidden/Low during an in-flight chunked event-stream response, and requires the response to finish before restoration. It also checks unfinished text, scroll, and a referenced 30 MiB typed array, plus disabling Low while still hidden and restoring Normal/visible. The settings persistence regression and the existing rapid-restore/native-shutdown tests also pass.

A separate synthetic two-page probe through the implemented `setPresentation` method measured 677 MiB summed working set in hidden/Normal and 357 MiB in hidden/Low, with private bytes 527 and 526 MiB respectively. Restoring Normal/visible measured 432 MiB working set. Both pages retained their state. This is resident-memory relief in a local fixture, not a reduction of live committed data or a guaranteed saving on real provider sites. Hidden timers can be throttled; account-specific streaming still warrants normal user testing.

Representative local-fixture run on the development machine:

| Measurement | Observed |
| --- | --- |
| Creating an off-screen JavaFX host through completed local navigation | 514 ms |
| Returning from the warm show/restore call | 23 ms |
| Summed working set of the test JVM and WebView2 descendants, one site | 510 MiB |
| Same test process tree, two live sites | 570 MiB |

These are diagnostic observations, not performance guarantees. The cold measurement excludes JVM and JavaFX toolkit startup. The warm measurement covers the call, not hardware input-to-photon or keyboard readiness. Memory includes test infrastructure and can count shared pages multiple times; it is not private memory or production AI-site usage. Tests print fresh values on each run.

The Logitech mapping, foreground behavior while another app owns focus, account-specific login flows, file pickers, and mixed-DPI multi-monitor behavior still require interactive validation. The browser preserves its focused element; Spark does not assume a universal editor selector for arbitrary sites.

### Class-unregister diagnostic investigation

The observed message is `Failed to unregister class Chrome_WidgetWin_0. Error = 1411`. Win32 error 1411 means the window class does not exist; it is not error 1412 (a class still has windows). The message itself therefore does not establish a leaked live window.

The investigation used `webview` 0.12.0 and the installed WebView2 Runtime 156.0.4314.58, isolated browser profiles, local HTTP fixtures, and captured stdout/stderr. Closing the IntelliJ application eliminated the shared-DLL extraction warning but did not eliminate this diagnostic.

| Experiment | Result |
| --- | --- |
| Direct DLL calls from Python: one, sequential, and parallel browsers, including explicit DLL unloading | No class-unregister message observed |
| Standalone Java: browser creation, navigation, COM events, clearing, repeated destruction | No message observed with natural JVM exit |
| The actual native test bodies run directly through `java.exe`, with normal JavaFX shutdown | No message observed |
| The same test bodies followed by `System.exit(0)`, including the case without JavaFX | Message 1411 reproduced |
| Gradle test worker teardown | Message can still occur after tests have passed |

This isolates a reproducible trigger to JVM termination via `System.exit` rather than authorization, clearing, or JavaFX alone. It does not identify the exact internal Chromium teardown defect. The Chromium source shows that the diagnostic comes from its window-class registrar; the application does not unregister `Chrome_WidgetWin_0` itself.

The normal Spark exit path previously called `System.exit(0)` immediately after `Platform.exit()`. Its fallback thread was also non-daemon, which prevented natural process termination. The exit path now waits for browser shutdown, requests JavaFX exit, and lets the JVM terminate naturally. The named fallback thread is daemon and still forces exit if the application fails to stop within its existing timeout.

The bridge also explicitly closes `ICoreWebView2Controller` before destroying the host HWNDs and releases its retained core reference in `finally`. The bundled `webview` 0.12.0 destructor releases COM references without calling controller Close explicitly. Microsoft recommends explicit Close to break reference cycles. This cleanup improvement alone did not eliminate the test-worker message and is not presented as its sole cause or fix.

`ApplicationShutdownTest` launches a separate process containing the real `MainWindow`, settings, tray/hotkey setup, a local site, and the normal shutdown action. It checks exit code 0, completed browser shutdown, and the absence of both forced-exit and class-unregister messages. Existing navigation, clearing, rapid-restore, and startup-shutdown tests continue to pass. The Gradle worker can still emit the diagnostic at its own termination; output is not filtered or suppressed.

References:

- [Win32 error codes 1411 and 1412](https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--1300-1699-)
- [Chromium class registrar](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/ui/gfx/win/window_impl.cc)
- [Bundled webview 0.12.0 implementation](https://github.com/webview/webview/blob/0.12.0/core/include/webview/webview.h)
- [Microsoft guidance for controller Close](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2controller#close)

## Native API references

COM interface IDs, signatures, and vtable slots follow the official Microsoft.Web.WebView2 1.0.2903.40 SDK header. Other platforms retain document-ready fallback behavior and do not claim native profile-wide clearing.

- [webview native handle API](https://github.com/webview/webview/blob/master/core/include/webview/api.h)
- [WebView2 profile clearing](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2profile2)
- [WebView2 navigation events](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/navigation-events)
