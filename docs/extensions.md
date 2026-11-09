# Experimental extensions in 0.3.3

Extensions currently require Windows x64 and the patched native library documented in [native/README.md](native/README.md). Spark uses a general WebView2 integration and startup adapter. There are no extension-name-specific branches or source patches for Bitwarden, uBlock Origin, cookie extensions, or Dark Reader.

On macOS, Linux, and other non-Windows systems, the Extensions menu item is disabled with a Windows-only tooltip. The Extensions window and startup synchronization are not initialized. Service operations reject requests before opening files or dispatching browser work, and the popup host returns before creating a window or touching Windows native APIs. Windows detection does not mistake Darwin for Windows.

## Using Extensions

Open **Three dots → Extensions**, directly below Settings. The owned window uses the same surface as Settings.

- **Chrome Web Store**: paste a store page link or the 32-letter extension ID and press **Install**. Spark downloads the current package through the store's update service (the same request Chrome makes), records its update service for **Check for updates**, and updates an existing installation in place.
- **Import extensions** accepts ZIP and CRX2/CRX3 packages and extracts them into stable directories. They are installed as unpacked extensions, not as store-managed installations.
- **Open extensions folder** opens `Documents/OldValencia/Spark/extensions` on Windows. Put each manually supplied unpacked extension in its own subfolder containing `manifest.json`.
- **Refresh** discovers new or changed folders and synchronizes them with the current WebView2 profile. Opening the window shows the last synchronized list immediately and verifies it in the background. Startup synchronization runs five seconds after the browser is ready, because WebView2 restores installed extensions itself. Reload a site after adding an extension to ensure its content scripts run.
- Changes are detected from file sizes and modification times, so unchanged folders are not read in full. Folders recorded by earlier builds with content hashes are verified by content once and then switch to metadata fingerprints without reinstalling.
- Deleting an extension folder manually unloads that extension from the profile on the next refresh. Only extensions installed by Spark are considered (their IDs are kept in `.spark-installed.json`); built-in WebView2 components, such as the PDF viewer, are never removed.
- Each row shows a manifest icon, name, version, and actual enabled state or installation error. Click its icon to open its declared popup. **Settings** opens `options_ui.page` or `options_page`; if neither is declared, it opens the extension's own popup, where settings may be available.
- Enable/disable and removal use WebView2's extension API. Disabled states survive refresh and restart. Removal archives files under the adjacent `extensions-removed` folder. If Windows still holds files, a persisted tombstone excludes the folder immediately and retries the archive operation on a later refresh.

Extensions without a declared popup have no clickable popup action. Toolbar click handlers, side panels, native messaging, and the full browser toolbar environment are not implemented. Updating keeps the extension's folder path, so its browser ID and stored data (for example `chrome.storage`) are preserved; the previous folder is kept in `extensions-removed`, and the saved enabled state is restored after the new version is prepared and reloaded.

- **Check for updates** asks each extension's update service for a newer version, using the Chrome Web Store protocol with the store ID read from the CRX header at import time (kept in the extension's `.spark-source.json`). Rows with a newer version show an **Update to …** button that downloads and installs it. Extensions imported before this feature, or imported from release ZIP archives without an update service, are not checked.
- **Import extensions** with a newer package of an installed extension updates it in place instead of creating a second copy. Packages are matched by store ID when both have one, otherwise by extension name. This is how release-archive extensions such as Bitwarden or uBlock Origin are updated, and importing a store CRX once records its update service for future checks.

## Known popup limitation

Popup and settings documents are opened in an owned native WebView using the installed extension's origin and shared browser profile. Spark page scripts and its Java bridge are not installed in that host. Closing its owner also closes the extension window and terminates its browser loop.

The document appears in an undecorated, rounded popover beside the clicked icon or Settings button, with a 6 px inset on every side. A document-creation script measures the page after loading and fonts, then reports changes through `chrome.webview.postMessage` when a `ResizeObserver` on the root, body, or the body's direct children fires. Reports are debounced (60 ms, 50 ms for the first one) and sent only when the size changes, so an idle popup executes no measurement script. Internal scrolling panels and clipped inactive tabs retain their own layout constraints. Changes animate over 160 ms; reports arriving during the animation are ignored and the page is asked to report again when it ends. If no pushed report arrives (for example, if messaging is unavailable), the host falls back to polling until two consecutive results agree. A 200 ms host timer performs only native viewport and focus checks. While the Extensions window is open, one hidden popup browser is created in advance under a Windows message-only parent, so it never appears as a separate window; so clicking an extension does not wait for WebView2 controller creation (in tests, about 145 ms to a visible popup instead of 225–320 ms); it is released when the window closes. Sizes are limited to 800 × 720 logical pixels and the available monitor area; viewport-relative layouts retain their starting width when they do not declare an intrinsic width. The native viewport is synchronized with the inset area in physical pixels, including display scaling. Frame styles are cleared again after the native library's queued default sizing so it cannot restore a non-client border and clip the document. The rounded window region uses the requested size because JavaFX applies native bounds asynchronously. There are no extension-specific sizing rules. Clicking outside, switching to another window, or pressing Escape dismisses the popover. Clicking the icon or Settings button of the open popover closes it instead of opening it again. Native child focus and owned dialogs are considered when checking focus.

This is a document host, not a Chromium toolbar action host. An extension querying its active browser window may see its own interface rather than the site in Spark. Site-specific controls and password autofill can therefore be incorrect or unavailable. A welcome screen opening successfully is not evidence that an entire extension works. `chrome.action.openPopup()` has returned `Browser window has no toolbar` in the isolated probe.

The hidden loading host starts with a 420 × 600 content viewport, limited by the monitor area, before its first measurement. A tiny loading viewport can cause responsive extension UIs to choose a compact, viewport-relative layout and remain stuck at that height. Measurement waits for document completion and font loading, so an unstyled document cannot shrink the host during startup. Fixed-size documents still shrink to their natural size after loading.

Popup diagnostics are enabled at INFO level in the ordinary `logs/app-*.log` file. Search for `Extension popup [session]` to correlate opening, CSS/DOM dimensions, native viewport bounds, requested host sizes, errors, and closure. Measurements are logged only when changed and at most once per second. Diagnostics exclude page text, input values, vault contents, and navigated URLs. The optional package preview reports whether visible controls appeared, so loading-screen captures are distinguishable from interactive UI captures.

## Experimental active-site context

Opening an extension from the manager obtains the selected Spark WebView's real window ID with `Browser.getWindowForTarget`. That is the only DevTools Protocol call; no debugging sessions, target discovery, or polling are used, and no debugging network port is opened. No extension names are used by the adapter.

The popup document receives a general tab/window query adapter at document creation. It announces the window ID over a same-origin `BroadcastChannel` (`spark.extensionContext`). The prepared startup bootstrap in the extension's service worker or background page listens on that channel. A freshly started worker asks for the current context on startup and holds its window queries for at most 150 ms until an open popup answers, so the first request of a newly started worker already uses the selected site. Without a popup, queries continue natively after that short wait. Closing the popup announces that no site is selected, restoring native queries; the host waits at most 300 ms for that before closing.

Current-window, last-focused-window, and implicit active-tab queries (`active: true` without any window criteria) are directed to that real window. Queries that explicitly set `currentWindow: false` or another window ID are preserved. The adapter treats the selected WebView as a normal browser window for those queries and window-description APIs. It preserves native tab IDs, native permission checks, and native messaging/scripting commands. Both callback and Promise callers are supported where the native API supports them. Selecting another Spark site and reopening the popup supplies the new real window ID, including when both sites have identical URLs.

Dark Reader 4.9.135 and prepared uBlock Origin 1.75.4 were verified to show the mapped local website host. Clicking uBlock's power switch was also verified to change its background filtering preference for that exact website. A synthetic extension verifies real-tab DOM modification, current/last-focused window queries, navigation, duplicate-URL site switching, the first request after a worker restart, and restoring native worker queries.

This remains a partial compatibility layer. It requires the prepared startup bootstrap, which synchronization adds to every managed extension with background code. Toolbar action events, automatic `activeTab` grants, and a complete virtual tab/window event model are not implemented.

The popup host cancels navigation to Chromium's unavailable `chrome-search:` new-tab page, preserving its extension document instead of displaying an internal URL error. This policy applies to every extension popup. Bitwarden account workflows, actual advertisement filtering, filter-list coverage, and complete compatibility with arbitrary extensions remain unverified.

The targeted sample report is `build/reports/extensions/context-samples.json`.

## Preparing installed startup files

Startup synchronization and Refresh prepare both newly imported and existing managed extensions. Spark first creates a separate staging copy, adds its common bootstrap before the declared background code, and archives the original directory under the adjacent `extensions-originals` folder. It then replaces only changed startup files in the original installed path. Keeping that path retains the unpacked extension ID. A native regression test verifies that reinstalling at the same path preserves the ID and `chrome.storage.local`; enabled and disabled states are also verified. This is not a claim that every password manager's entire account state has been validated.

Preparation supports MV2 background script arrays, MV2 background HTML pages with a head element, classic MV3 workers, and module MV3 workers. Original worker URLs, relative imports, and directive prologues are preserved. Source archives and archived original directories remain unchanged. Existing enabled extensions are temporarily disabled while their files are prepared, then reinstalled and restored to their saved state. No uninstall is performed as part of preparation.

The `.spark-bootstrap.json` marker records hashes of changed files. Repeated Refresh does not insert duplicate hooks. If a new extension version is copied over a prepared folder, its startup files no longer reference the bootstrap; they are prepared again automatically. Prepared startup files that still contain the bootstrap but were edited by hand are reported as an error requesting reimport rather than being patched again. Copy preparation failures leave installed files untouched; commit failures attempt to restore archived originals. The archive is retained for recovery, and temporary staging directories are removed. Preparation does not run on unsupported platforms.

After the first preparation, reload the open website so content scripts and the extension's page state are initialized. Extensions that perform asynchronous initialization may need a moment before opening their menus. The source/archive preparation does not add permissions or edit any extension's site-filtering logic.

Preparation stages only the manifest and declared startup entry, rather than copying and deleting the entire package as temporary work. Original archives still contain every original file. Overlapping Refresh requests for the same browser share the existing synchronization, including opening the manager during startup. The manager shows an indeterminate spinner and the current extension/stage while waiting. INFO timing entries distinguish file verification, browser installation, per-extension synchronization, and the complete refresh.

## Sample packages and validation

The sample packages are downloaded from official distribution sources and are not committed or bundled in Spark:

```powershell
./tools/extensions/download-samples.ps1
$env:SPARK_EXTENSION_SAMPLES = '1'
./gradlew.bat nativeTest --tests to.sparkapp.app.browser.ExtensionSamplesTest
```

The sample report takes about 45 seconds, so it runs only when `SPARK_EXTENSION_SAMPLES=1` is set; a regular
`nativeTest` skips it.

Packages are cached under ignored `local/extensions-samples/`, so a Gradle clean does not remove them. The optional sample tests skip when the packages are absent. There are no network downloads inside the tests themselves. The sample report is written to `build/reports/extensions/samples.json`; visible popovers are captured under `build/previews/extension-*.png` and their dimensions and visibility recorded in the report. A capture may show an extension's loading screen rather than a ready interface. The UI test creates `build/previews/extensions-manager.png`.

| Package tested | Source | Observed result |
| --- | --- | --- |
| Bitwarden 2026.10.2 | Official GitHub browser release | Installed; popup has extension runtime access and displays onboarding/login. No account login, vault unlock, credential saving, or Bitwarden autofill was tested. |
| uBlock Origin 1.75.4 | Official GitHub Chromium archive | Prepared popup identifies the mapped local website; its power switch changes filtering preference for that website. Internal new-tab navigation is canceled by the generic popup host. Actual advertisement filtering and filter-list coverage remain unverified. |
| I don't care about cookies 3.6.5 | Google Chrome extension update service | Installed; popup and options open. The generic local fixture's cookie element was not hidden; banner dismissal on supported sites remains unverified. |
| Dark Reader 4.9.135 | Google Chrome extension update service | Installed; popup/options open; content scripts inject Dark Reader styles and change the local website background from white to dark. The popup's site selection remains subject to the active-window limitation. |

The local website fixture and its mock advertising script are served entirely from mapped local files; no advertising code is fetched by the fixture. Extensions themselves may make their own ordinary update/onboarding requests. All browser tests use disposable profiles, not the installed user's accounts.

Generic tests cover package traversal rejection, CRX extraction, manifest handling, native installation/listing/toggle/removal, persistent disabled state, the owned management window, preparation idempotence and original archives, rejected startup paths, and cached API references across all four background startup forms. Application changes remain uncommitted until explicitly requested.

WebView2 extension processes can outlive their test JVM and retain profile files. The `nativeTest` task therefore finalizes with cleanup scoped to that run's disposable browser directory, including when tests fail. `clean` first checks for orphaned WebView2 processes under this workspace's `build/native-tests` directory and releases them. Its orphan check skips profiles whose host process is still alive; ordinary Spark, browser, and other application profiles are outside the allowed cleanup scope. The script is `tools/testing/stop-test-webviews.ps1`.

Validation passed for 54 unit tests and 20 native C++ tests. Unit coverage includes simulated macOS, Darwin, Linux, and unknown platforms rejecting extension operations without touching browser or filesystem dependencies. The six targeted native checks cover extension operations, popup ownership/teardown, the four packages, persistent management state, the real Extensions window, and the existing menu check. The popup, management, and menu checks also passed after adding platform guards. A full native run retained four failures in `InlineBrowserPagesTest`, `InlinePageNavigationTest` (two cases), and `PasskeyTest`; all four were reproduced with the original unmodified DLL. They are not marked fixed or hidden by the extension work.

The startup-adapter change passed 57 unit tests and eight targeted native checks. These include the four background startup forms, prepared Dark Reader/uBlock menus and the uBlock site switch, native query restoration, migration at a stable installed path, storage retention, and the existing popup/management windows. `build` and `installDist` completed successfully. The previously reproduced unrelated native failures above remain outside this change.

The review changes (metadata fingerprints, cached manager list, stale extension removal, pushed popup measurement, channel-based site context, and the native lifecycle fixes) pass 61 unit tests and `build`. All native `Extension*` browser tests pass together, as do the management, stale-removal, and Extensions window tests, with the rebuilt DLL. Earlier full `nativeTest` runs showed context tests timing out depending on test order. The cause was the test harness, not the extension code: when every browser in the JVM is closed and a new one is created immediately, it can attach to a WebView2 browser process that is still shutting down, and newly installed extension service workers there never start (reproduced without any Spark scripts, with both the previous and the rebuilt DLL). Spark keeps its main browser open for the whole session, and repeated popup opening was verified to keep working. The native test harness now keeps one hidden browser alive for the test JVM, and `ExtensionBootstrapTest` keeps one site browser across its four startup forms. A full `nativeTest` run now has only the four unrelated failures. The four unrelated failures listed above are unchanged.

## Importing prepared packages from the command line

After `installDist`, the small generic helper can populate a chosen extensions folder without launching the app:

```powershell
javac -cp 'build/install/Spark/lib/*' -d build/extensions-importer tools/extensions/ImportPackages.java
java -cp 'build/extensions-importer;build/install/Spark/lib/*' ImportPackages '<extensions-folder>' '<package.zip>'
```

The four requested packages have been imported into the local user's Spark extensions folder for manual testing. Restart Spark using the 0.3.3 build to use its updated DLL. The management UI is populated from the folder, not from hardcoded sample rows.

## Sources

- [WebView2 profile extension operations and UI limitations](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2profile7)
- [WebView2 environment opt-in](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2environmentoptions6)
- [Chrome extension manifest](https://developer.chrome.com/docs/extensions/reference/manifest)
- [Bitwarden release](https://github.com/bitwarden/clients/releases/tag/browser-v2026.10.2)
- [uBlock Origin release](https://github.com/gorhill/uBlock/releases/tag/1.75.4)
