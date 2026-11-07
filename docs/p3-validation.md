# P3 interaction polish

The desktop implementation adds a short reveal and hide transition, menu reveals,
an optional vector mascot, and presentation of parsed documents before navigation
has finished. Extensions and the P4 audit remain deferred.

## Behavior

- Docked windows move from the upper edge in 200 ms and hide in 140 ms. Floating
  windows use an 8 px movement. The native browser moves with its host window;
  its document is not scaled or replaced with a screenshot.
- Reopening during a hide cancels the pending hide callback. Dragging and resizing
  settle the transition. Saved placement uses the resting coordinates.
- Size, options and site popups reveal with a 5 px movement and opacity transition
  over 150 ms. Dismissal remains immediate so actions are not delayed.
- The mascot uses local JavaFX shapes in the existing 30 × 30 slot. It blinks,
  tilts and briefly hops in response to showing the window, hovering or clicking.
  Pointer reactions cover both the shell and the native website area. A temporary
  mouse-motion listener reuses the application's existing native hook while the
  window is visible and the animated mascot is enabled. It coalesces movement into
  one pending sample with a 24 ms delay; JavaFX Robot provides screen coordinates
  compatible with screenToLocal on mixed-DPI displays. Outside the window, the gaze
  returns to neutral. JavaFX scene events remain a fallback if the native hook is
  unavailable. There is no idle polling or page-content observation. Hiding,
  minimizing, disabling, reduced motion and disposal remove the native listener
  and stop pending samples. Disabling preserves the header layout.
- Reduce motion skips window/menu movement, stops the loading ring and keeps the
  mascot still. Changes from Settings apply to the current interface immediately.
- On Windows, native DOMContentLoaded identifies the current navigation by ID.
  Spark exposes the parsed page without waiting for NavigationCompleted. That
  event still governs the loading/error state. Parsing does not guarantee that a
  website has finished client-side hydration or is fully interactive.
- Show page is available during loading, including before DOM readiness. Slow
  navigation still has explicit recovery actions. Completion does not reload or
  refocus a page that Spark already presented.

## Loading and cache investigation

BrowserCacheTest starts two separate JVMs against one disposable browser profile
and a local HTTP server. Each visit executes a script with Cache-Control:
public, max-age=3600. The HTML uses no-store. The test observes the spawned process
tree and waits for its children to exit between visits, excluding reuse of a live
browser process as the explanation.

Observed: two HTML requests and one script request. The script executes in both
JVMs. WebView2 therefore retains and reuses eligible resources from disk with the
bundled native wrapper. This does not measure speed on real AI providers, prove
their resources are cacheable, or verify a user's installed profile location.
There are no new cache overrides, automatic clears, prefetches or request blockers.

EarlyPagePresentationTest serves a textarea and holds an image response open. It
verifies that Spark removes its overlay while native navigation is still pending,
then enters a draft and verifies that the draft survives navigation completion.
Existing integration tests cover live switching, repeated restores, streaming,
navigation errors, renderer/browser recovery, and application shutdown.

An additional HTML cache is not recommended for this iteration: a saved document
does not preserve a running application's DOM/JS state or replace its API calls.
Any network filtering experiment should separately compare real sites, including
authentication, uploads and streaming, with an immediate per-site bypass.

References: [navigation event ordering](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/navigation-events),
[DOMContentLoaded API](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2_2),
[profile storage](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/user-data-folder),
[performance guidance](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/performance).

## Validation and remaining review

Run `gradlew.bat test nativeTest installDist` on Windows with Evergreen WebView2.
The final run passed 45 ordinary tests and 27 native tests and rebuilt installDist.
On November 7, the native-pointer follow-up passed the ordinary test suite plus
MascotNativePointerTest, PointerDeliveryTest and WindowMotionTest, then rebuilt
installDist. MascotNativePointerTest moves the actual pointer over a real local
WebView2 page and verifies opposite gaze directions without JavaFX mouse movement
events. It also checks listener removal on reduced motion, disabling and hiding.
The pointer is restored and the disposable browser is closed after the test.
The header and mascot were also reviewed in a 2× offscreen JavaFX snapshot at
[docs/previews/0.3.0-p3-topbar.png](previews/0.3.0-p3-topbar.png). This captures the
actual header controls, not a native browser page or animation frames.
All browser fixtures use disposable profiles and local pages; installed sessions
are not opened. WindowMotionTest checks cancellation of a pending hide, resting
coordinates and immediate reduced-motion hiding.

The Gradle native worker can still emit the previously documented
Chrome_WidgetWin_0 / 1411 diagnostic at process teardown. The separate application
shutdown test verifies natural exit without that diagnostic.

Manual acceptance remains necessary for native animation appearance, focus during
rapid hotkeys, real provider hydration/authentication, mixed DPI and vertically
arranged monitors. In particular, a full docked reveal moves an actual OS window
above the selected monitor's work area; clipping at adjacent monitor boundaries
has not been verified. Do not treat a successful build as final visual acceptance.

The browser study in design_proposal_p3.html is illustrative. Its floating fade,
thin loading line and extension suggestions are not a statement that those exact
details are implemented in the native shell.
