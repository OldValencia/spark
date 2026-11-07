# Website navigation and sign-in

Spark hosts websites; it is not their OAuth client. It cannot exchange a provider's tokens or make a system-browser login create a Spark session. Websites and WebView2 retain responsibility for authentication.

## Browser behavior

There is no authentication-domain allowlist or login-path classifier. Navigation does not depend on whether a URL contains `login`, `logowanie`, or any other word. Normal links, forms and redirects stay in their browser context. Spark no longer cancels cross-host anchor clicks, reissues them as GET requests, or sends an unfamiliar redirect to the default browser followed by a forced navigation home.

`window.open`, a blank window that is navigated later, and `target="_blank"` are handled by WebView2. Its native popup behavior preserves the browsing context, opener relationship where the page permits it, and browser profile. Site-supplied `noopener`, COOP and other browser security policies still apply. Spark does not install its application bridge in these native popup windows or manage their contents. A middle-click explicitly opens an HTTP(S) link in the default browser; that browser has a separate login session.

The options menu offers **Back** and **Return to site**. Alt-arrow browser history shortcuts are no longer blocked by Spark's page script. Returning to the configured start address is an explicit action and can discard the currently displayed page, just like navigating normally.

## Focus and platform dialogs

Outside-click hiding recognizes windows owned by Spark and WebView2 processes descended from Spark. It also waits while a visible Spark/browser owner is disabled by a native modal dialog. An off-site top-level page suppresses outside-click hiding and releases always-on-top when unfocused; that rule describes a navigation context, not an inference that the page is a login form.

Spark leaves `navigator.credentials`, WebAuthn requests and platform authentication dialogs untouched. No JavaScript credential API wrappers, injected password UI or copied browser cookies are used. Native ownership/modal checks cannot identify every third-party broker that opens an independent, unowned, non-modal window. **Window size → Hide on outside click** can be turned off for such a flow. Windows Hello, security keys, passkey-provider plugins, external applications and identity-provider policies require real-device validation; this change does not guarantee support where WebView2 or the provider disallows embedded login.

## Remembered addresses

Automatic restore remembers only addresses belonging to the configured site's host or subdomains, without any query or fragment. All parameter names are treated alike. This avoids storing temporary parameters without trying to infer their language or meaning. A site that uses query strings or hash routes to identify its pages will restore the previous parameter-free address or its configured start address instead. During the running session, the original URL and page remain intact.

A parameter-free path is treated as an ordinary page even if it happens to be a login page. Spark does not claim it can identify every secret that a website might place in a path. Previously remembered parameterized addresses are ignored on restore and pruned when the site collection loads. Configured start addresses remain explicit user settings.

## Verification

The native popup fixture uses two local hosts, an HttpOnly cookie, a draft in the opener and a cross-host callback. It checks `window.open`, an anchor with `target="_blank"` and explicit `rel="opener"`, cookie sharing, `postMessage`, `window.close`, and a POST form to `/logowanie` followed by a redirect to an arbitrary callback path. These use the shipped page script and actual WebView2, with a disposable profile.

The passkey fixture enables a virtual CTAP2 authenticator through WebView2's DevTools protocol, then calls the untouched `navigator.credentials.create()` and `get()` on localhost. It verifies the credential ID, assertion signature presence and relying-party origin. This is a browser-level passkey test, not a Windows Hello hardware test or full server-side assertion verification. The virtual authenticator and protocol helper exist only in test code.

Two existing tests are tagged `flaky` and excluded from the default native task: physical site-list dragging and the two-JVM DLL cache test whose temporary DLL deletion intermittently fails on Windows. Run them explicitly with `gradlew.bat nativeTest -PincludeFlaky`; their quarantine is not a bug fix.

On Windows/JDK 21, the complete default run passed 45 ordinary tests and 30 native tests, and `installDist` succeeded. The older early-presentation/recovery fixtures now publish their test state through a parameter-free fixture path, since query strings are deliberately excluded from preferences; their draft, scroll, stream and crash-recovery assertions remain intact. The existing worker-shutdown `Chrome_WidgetWin_0 / 1411` diagnostic can still appear.

Primary references: [WebView2 new-window behavior](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2newwindowrequestedeventargs), [WebAuthn virtual authenticators](https://learn.microsoft.com/en-us/microsoft-edge/devtools/webauthn/), [Windows WebAuthn APIs](https://learn.microsoft.com/en-us/windows/security/identity-protection/hello-for-business/webauthn-apis).
