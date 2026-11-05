# Browser layer and memory review

Baseline reviewed after commit `de2b108`. The findings below describe that baseline. Implemented follow-ups for visibility, background memory reduction, process-failure recovery, and native API/thread contracts are summarized at the end. Automatic page eviction remains explicitly out of scope.

## Assessment

The Java/JNA wrapper around native `webview` and WebView2 is a reasonable basis for this Windows utility. Retaining callbacks against garbage collection, owning native loops on dedicated threads, lazy site creation, profile-wide clearing, explicit controller closure, and process-level shutdown tests are useful safeguards. A complete UI or browser-stack rewrite is not justified by the evidence gathered here.

The layer is still tightly coupled: `WebviewManager` coordinates native handles, JavaScript injection, lifecycle, visibility, and FX delivery. `isStarting` actually stays true for the entire browser-thread lifetime. A typed lifecycle and a single owner-thread boundary would make future native changes safer.

## Findings and priorities

### 1. Native visibility is not currently propagated to WebView2

`WebviewManager.hibernate()` and `setVisible()` change the HWND using Win32 utilities. They do not call `ICoreWebView2Controller::put_IsVisible`. The native library sets controller visibility to true during creation. The controller therefore lacks an explicit visibility signal when Spark hides or switches away from a site.

Use the controller property on the browser thread, independently of detaching the HWND. Microsoft recommends this for hidden/minimized hosts. This is primarily correct resource-state handling, not a promise of a large RAM reduction. The experiment below did not show a meaningful immediate private-memory improvement from this alone. Hidden-page timers were throttled, so real-site streaming and visibility-change behavior need testing before enabling it by default.

### 2. Renderer/process failures are not observed

`WindowsBrowser.observe()` registers NavigationStarting and NavigationCompleted only. No ProcessFailed handler is installed. A renderer failure after successful navigation is therefore not explicitly mapped to the existing error/retry UI. Add process-failure handling and test recovery of an isolated renderer. Recovery must not silently reload unrelated healthy sites or pretend a crashed page's unsent draft survived.

### 3. Native API contracts and thread ownership need tightening

Most `WebviewNative` methods return `void`, while the bundled webview 0.12.0 API returns `webview_error_t`. Failures from dispatch, bind, navigation, and evaluation cannot be checked at this boundary. In particular, callback retention is updated before bind success is known. Match the C ABI explicitly, including opaque pointer types, and check results before publishing state or replacing retained callbacks.

`WebviewManager.eval()` and `loadURL()` also expose direct native access using repeated reads of a volatile `webview` field. The API does not enforce the owning thread, and those reads can race disposal for an off-thread caller. Route native operations through a captured, validated owner-thread instance. Existing controlled call sites passing tests do not prove the public boundary safe for future callers.

### 4. DLL loading conflicts between app/test processes

`WebviewNative.runSetup()` rewrites a common temporary `webview.dll`, changes the global `jna.library.path`, then uses both System.load and Native.load. The shared-path extraction warning was reproduced while the IntelliJ app was running. Select a version/content-hash and architecture-specific immutable path, extract atomically, and load by its absolute path. Do not interpret a localized exception message to decide whether extraction failure is safe to ignore.

The OS normally shares a loaded DLL image; using two loading APIs does not establish two copies of the engine in RAM. This is a correctness/development-workflow improvement, not a large-memory-savings claim.

### 5. Bridge, callback, and scheduler overhead can be bounded

- `SparkJsApi` accepts arbitrary-size JSON payloads and forwards every recognized call to the FX queue. The startup queue and native dispatch collection also have no explicit backlog bound. Add small command schemas, length limits, and coalescing for redundant zoom/state changes. This addresses overload robustness, not a measured normal-use leak.
- Each profile-clear completion handler is retained in `WindowsBrowser.handlers` until that browser closes. Retaining callbacks through native use is required, but completed one-shot operations need not accumulate forever. A proper COM callback lifetime is preferable to freeing a callback during Invoke; current AddRef/Release stubs intentionally do not manage ownership counts.
- Each visited site creates its own navigator scheduler, partly for a fixed delayed zoom update despite native navigation events already existing. Consolidate unnecessary delayed work first; a shared scheduler can then reduce thread count if still needed.

These are smaller than browser-page memory. There is no evidence here of a Java heap leak explaining hundreds of MiB.

### 6. Authentication classification and navigation policy should be separate

The same `isAuthUrl` predicate is used for focus/history decisions and for allowing in-WebView navigation. Its generic `/login`, `/oauth`, etc. path rules also match unrelated hosts. Treating a page as authentication-related is not the same as trusting it as an allowed cross-origin authentication destination. Separate those decisions and add tests for external `/login` links and custom-site authentication. This finding is about navigation correctness, not memory.

## Isolated memory experiment

Two local HTTP origins were loaded into separate native WebViews with a shared disposable user-data folder. Each page contained 20,000 paragraph nodes, a referenced and filled 30 MiB typed array, an unfinished textarea, and a timer. The probe used JDK 21, a 24 MiB initial/96 MiB maximum Java heap, G1, and a 64 MiB metaspace limit. It did not launch the full JavaFX application or any signed-in website.

Samples include the probe JVM and its WebView2 descendants. Private bytes represent private committed allocations, not resident physical RAM; summed working sets include shared-page double counting. Sampling was sequential with roughly one second of settling per phase. It is a diagnostic microbenchmark, not a production guarantee or randomized performance study.

Repeat-run observations:

| State | Summed private bytes | Summed working set |
| --- | ---: | ---: |
| HWND hidden; controller retains default visibility | 531 MiB | 673 MiB |
| Controller hidden; normal memory target | 530 MiB | 678 MiB |
| Controller hidden; low memory target | 530 MiB | 359 MiB |
| Controller visible again; normal memory target | 535 MiB | 434 MiB |

Draft text, DOM, the referenced typed arrays and their contents, and timer progress survived all phases. Hidden timers ran less frequently. The experiment did not test real provider streaming, uploads, foreground focus, or memory pressure caused by other apps.

Both WebViews reported the same browser-process ID (`33752` in this run). The stack already shares a browser process in this configuration. Different origins still have renderer costs. Reworking environment creation must not be advertised as eliminating an entire browser process per site without evidence of duplication in the target configuration.

The probe's Java heap used about 11 MiB, with 24 MiB committed; non-heap usage was about 17 MiB. These figures do not include all native allocations in the host process. Further shrinking the 96 MiB maximum heap would not reclaim the renderer allocations and could reduce robustness instead.

## Recommended memory work

1. Add correctly sequenced controller visibility and validate background-network behavior on real sites.
2. Offer or evaluate native `MemoryUsageTargetLevel.Low` for inactive pages, returning to Normal before display. This is not page eviction or reconstruction. Microsoft documents continued script execution, but the policy can discard caches and page memory out to disk, adding CPU or restore latency. The measured benefit here was resident working set, not private committed memory. Handle older runtimes without the interface gracefully.
3. Measure private bytes, working sets, browser/renderer counts and restoration latency in an installed/release-like process, separately for the host and WebView2 children. Avoid using only the IDE process or test-worker total as application RAM.
4. Optimize JVM/JavaFX overhead only after those measurements. Do not promise a saving from switching JavaFX to Compose, changing GC, or unifying ObjectMapper instances. The heap limit is already small, and substantial live web content has an unavoidable memory floor.

Do not unload visited pages, periodically refresh sites, clear sessions automatically, disable browser security/site isolation, or use repeated System.gc/working-set trimming to manufacture a lower headline number. Memory-target policy should be evaluated explicitly against the accepted draft-preservation workflow.

## Primary references

- [WebView2 performance guidance](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/performance)
- [WebView2 process model and shared environments](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/process-model)
- [Controller visibility](https://learn.microsoft.com/en-us/dotnet/api/microsoft.web.webview2.core.corewebview2controller.isvisible)
- [Memory usage target and its tradeoffs](https://learn.microsoft.com/en-us/dotnet/api/microsoft.web.webview2.core.corewebview2.memoryusagetargetlevel)
- [Bundled webview 0.12.0 C API and implementation](https://github.com/webview/webview/blob/0.12.0/core/include/webview/webview.h)
- [Chromium memory metrics on Windows](https://blogs.windows.com/msedgedev/2021/01/13/investigate-microsoft-edge-memory-usage/)

## Implementation follow-up

Native visibility and background Low/Normal memory targets are now implemented. The Browser setting defaults on and can be disabled without reloading pages. Optional-interface absence falls back to normal operation, and setter failure disables memory targeting for that browser. Restoring Normal happens before controller/HWND visibility; stale presentation tasks cannot win over a newer hide.

Native tests verify a chunked streaming response completing while hidden/Low, preserved drafts/scroll/live JS data, actual native target getter values, and return to Normal. The production-method synthetic probe measured 677 → 357 MiB summed working set while private bytes stayed approximately 527 MiB. This is paging/cache behavior, not a guarantee that real websites use half as much committed memory. Process-failure recovery and ABI/thread-contract follow-ups are described below. DLL loading, one-shot COM callback lifetime and navigation policy remain separate future work.

The subsequent ProcessFailed follow-up is now implemented and tested with actual isolated renderer and browser-process termination. Recovery requires Retry and affects the selected failed view; a browser-process failure can legitimately mark multiple shared-process views as affected. Native destruction was also moved outside the dispatch monitor to fix a reproducible recovery deadlock. See the P1 validation notes for the recovery tests and their limits. The ABI/thread-contract follow-up is described next; DLL loading, one-shot COM callback lifetime and navigation policy remain future work.


### Native API and thread contracts

The JNA declarations now follow the bundled [webview 0.12.0 C header](https://github.com/webview/webview/blob/0.12.0/core/include/webview/webview.h): opaque handles and callback context use Pointer, binding IDs use String (`const char *`), debug/promise status use int, and error-returning functions return int. Wrapper operations check negative error codes; binding registration additionally rejects DUPLICATE (positive 1) without replacing the retained original callback. Failed dispatch submissions remove their unused callback shield. Binding exceptions become JSON-encoded promise rejections, and callback execution failures are logged within the Java/native boundary.

Browser and COM access enforce the creating thread. Dispatch and asynchronous evaluation remain available to callers from other threads. Manager navigation goes through dispatch; queued work checks the captured view is still current, and evaluation avoids repeated volatile reads. The short destroyed-state lock and native cleanup outside it are preserved. Native library initialization is lazy so fake-API unit tests do not load a browser DLL.

Validation: 17 unit tests and 5 native tests pass on Windows/JDK 21. Six new unit tests inject native errors and exercise duplicate-binding retention, failed dispatch cleanup, teardown, string IDs/JSON rejection, event-loop failure and foreign-thread access. A new real-WebView2 test verifies rejected promises, successful null results and that a rejected duplicate registration leaves the original callback usable. Existing hidden-streaming, session-clearing, rapid-restore, process-failure recovery and natural application exit tests pass. Other operating systems have not been exercised. This is a native-boundary correctness change, not a measured RAM optimization.
