# WebView2 extension UI experiment

This records the initial isolated experiment. The subsequent experimental application integration for 0.3.3 is documented in [extensions.md](extensions.md).

This is an isolated feasibility probe, not an Extensions feature in Spark. It does not modify Spark's native library or use the application's browser profile. No Bitwarden account was entered.

## Reproduce

On Windows with the .NET 10 SDK and Evergreen WebView2 Runtime, run from the repository root:

```powershell
dotnet run --project tools/extensions-probe/ExtensionsProbe.csproj
```

The SDK restores the official Microsoft WebView2 NuGet package. The hidden WinForms host creates a fresh profile beneath `build/extensions-probe-data/`, installs a local Manifest V3 fixture, and opens its popup and options pages by their declared paths. The fixture accesses a temporary loopback HTTP page. Source and output are confined to the experiment; browser profile data is not shared with Spark.

To inspect a real extension, pass the directory containing its unpacked `manifest.json`:

```powershell
dotnet run --project tools/extensions-probe/ExtensionsProbe.csproj -- build/extensions-probe/bitwarden
```

The optional real-extension probe expects a Manifest V3 `action.default_popup`. It loads that declared page, records its initial visible text, and saves a screenshot to `build/extensions-probe-data/real-popup.png`. It does not interact with accounts. Downloaded extension packages and browser profiles stay out of Git.

## Observed results

Tested with WebView2 Runtime `156.0.4314.58`, SDK `1.0.3595.46`, and the official Bitwarden `dist-chrome-2026.10.2.zip` browser release.

- Installing an unpacked extension with `AreBrowserExtensionsEnabled = true` succeeds.
- Navigating to `chrome-extension://<id>/<declared-popup>` opens the fixture's HTML with access to `chrome.runtime` and `chrome.storage`.
- Navigating to the fixture's declared `options_ui.page` succeeds.
- `chrome.tabs.query({})` sees both the website and the separate WebView used to display the extension page.
- Explicitly targeting the website's tab ID with `chrome.scripting.executeScript` fills the local input successfully.
- **The popup's `chrome.tabs.query({active:true,currentWindow:true})` returns the extension page itself**, not the website. Each of these WebViews has its own browser window ID, even though both controls are hosted in the same native form.
- Calling `chrome.action.openPopup()` fails with **`Error: Browser window has no toolbar.`**
- Bitwarden installs and renders its actual welcome screen, including Create account and Log in. Login, vault unlock, saving credentials, and Bitwarden autofill were not tested.
- This Bitwarden manifest has no `options_page` or `options_ui`; its settings belong to its own application UI. A generic manager cannot assume every extension declares a separate options page.

The .NET probe emits an SDK assembly-reference warning involving WindowsBase; the tested WinForms/WebView2 operations complete successfully.

## Consequences for Spark

A generic Extensions manager can import unpacked packages, display manifest metadata and icons, list installed extensions, enable/disable them, and remove them. A Settings action can open a declared options page when one exists. Opening the declared popup as a document is also possible, but it does not reproduce browser toolbar action semantics.

The unresolved issue is the relationship between an extension's UI and the active website. Rendering Bitwarden's login screen is not evidence that its normal autofill workflow will work. Do not claim full extension compatibility, rewrite extension scripts, or introduce extension-specific exceptions based on this result. The correct active-tab context must be established through a supported general mechanism before treating this as a browser-like extension host.

At the time of this probe, Spark's native wrapper passed null environment options. Integration required enabling extensions before environment creation and rebuilding the native fork. No production implementation was added during the initial experiment; the subsequent experimental integration is documented in extensions.md.

## References

- [WebView2 extension installation and missing UI entry points](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2profile7)
- [WebView2 environment extension opt-in](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2environmentoptions6)
- [Chrome action and popup semantics](https://developer.chrome.com/docs/extensions/reference/api/action)
- [Official Bitwarden browser release](https://github.com/bitwarden/clients/releases/tag/browser-v2026.10.2)
