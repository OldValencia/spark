# Proposed browser contexts inside one Spark window

Status: design following the Gemini multi-account report; not implemented.

The current WebView2 default popup implementation preserves browser relationships, but a website can leave its application in the popup. Gemini account selection demonstrates this: shared cookies make the account available, yet selecting it opens a second window instead of replacing the page shown by Spark.

## Avoid inferring that sign-in has finished

A return to the configured host does not prove authentication has completed. It may be an intermediate callback, a POST response, or a page that still needs to notify its opener. Closing that context and reopening its URL in another WebView can consume a one-time callback twice, change the request method, or lose the page's state. There is no general website-independent completion event exposed to Spark.

Instead, keep each browsing context and display the currently active context in the existing Spark viewport. The user sees one application window. Ordinary navigation stays in the active context; a requested new window creates a child context sharing the opener's WebView2 environment/profile. A site-controlled close restores the previous context. If a site keeps the child open as its application page, that page remains visible in the same Spark window. No URL handoff or authentication detector is needed.

## Native implementation boundaries

- Handle `NewWindowRequested` and take a deferral while creating a fresh controller in the opener's environment/profile. Use `put_NewWindow` with that new, unnavigated CoreWebView2; do not point it at the existing main CoreWebView2.
- Render the child controller in the same viewport HWND and show one context at a time. Preserve the opener and let WebView2 execute the original request, including POST bodies, redirects and browser security policies.
- Install required page scripts before completing the new-window assignment. Route loading, source changes, focus, bounds, zoom and shell commands to the active context. Background contexts must not overwrite the active page's UI or remembered address.
- Handle `WindowCloseRequested` by disposing that context and revealing its parent. Provide an explicit way to close a retained child; do not infer a close from a host/path match. A page request to close the root must not terminate Spark.
- Keep live contexts when hiding Spark or switching sites. Close all owned controllers and resolve pending creation deferrals when removing a site or shutting down. Browser-process failure must invalidate the whole affected stack safely.
- Leave native WebAuthn and platform dialogs to WebView2/Windows. Keep ordinary browser security behavior, including COOP and `noopener`. Some provider policies or runtime limitations may still prevent embedded sign-in.

## Required regression cases

Use local fixtures for a child that stays open on the configured site, a cross-origin sign-in that returns without calling `window.close`, a popup that reports through `postMessage` and closes itself, a blank popup navigated later, POST forms targeting a new context, nested contexts, and back/close behavior. Also verify resize, focus, site switching, background work, crash recovery, pending creation during shutdown and virtual WebAuthn in a child context. Real Gemini multi-account and Windows Hello remain manual acceptance checks.

This is a broader implementation than cancelling a popup and navigating the original page to its URL. The native model should be validated before claiming the Gemini report or general popup return behavior is fixed.

References: [NewWindowRequested arguments and restrictions](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2newwindowrequestedeventargs), [controller bounds, visibility and lifecycle](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2controller).
