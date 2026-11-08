# Extension active-tab context investigation

Investigated on 2026-11-07 with WebView2 Runtime 157.0.4341.42 and SDK 1.0.3595.46. The initial investigation did not modify the application or installed extension packages. A subsequent experimental query adapter is now implemented; see [current behavior and limitations](extensions.md#experimental-active-site-context). The findings below record the original feasibility investigation.

Follow-up: managed extensions now receive a general startup bootstrap before their background code captures APIs. Prepared uBlock and Dark Reader menus identify the local test website, and the uBlock site power switch changes the preference for that website. Original files are archived and installed paths remain stable. This does not establish complete ad-blocking or password-manager compatibility; see [preparation behavior](extensions.md#preparing-installed-startup-files).

## Findings

There are two separate compatibility problems:

1. A declared popup opened as a standalone WebView document is itself a tab in a separate WebView2 browser window. A popup-side `tabs.query({active:true,currentWindow:true})` returns that document, not Spark's website.
2. The browser windows exposed by the isolated WebView2 test have type `popup`. Dark Reader 4.9.135's background service worker explicitly queries active windows of type `normal`, then `app`, with and without `lastFocusedWindow`. All these queries miss the WebView2 windows. Its `getTabURL(null)` fallback is `about:blank`, which explains its `about:` site label. This is independent of the popup-side current-window problem.

Dark Reader computes its active-tab data in `background/index.js`, then returns it to its UI through extension messaging. Injecting an override only into its popup would not affect those background queries. Other extensions may resolve their current tab in the popup, an MV2 background page, or an MV3 service worker; all these contexts must be considered.

## Controlled probe

The temporary probe is under ignored `local/extensions-context-probe/`; it uses a new disposable browser profile, a local HTTP page, and a synthetic MV3 extension. The website and extension document are hosted in two WebViews in the same native form. It exercises the real extension APIs, not mocked tab responses.

Run from the project root:

```powershell
dotnet run --project local/extensions-context-probe/ExtensionsProbe.csproj
```

The captured log is `build/extensions-context-probe.log`; a compact report is `build/reports/extensions/context.json`.

| Operation | Observed result |
| --- | --- |
| `tabs.query({})` | Finds the local website and popup document with different real tab IDs and window IDs. |
| Popup `tabs.query({active:true,currentWindow:true})` | Selects its own extension document. |
| Worker query with `lastFocusedWindow`, without a window-type filter | Selects the website in this particular hidden-form setup. This is an observation, not a guarantee for Spark's focus lifecycle. |
| Worker query restricted to `windowType:'normal'` or `'app'` | Returns an empty list. Both exposed windows have type `popup`. |
| `tabs.update(siteTabId,{active:true})` | Succeeds but leaves popup-side `currentWindow` pointing to its own document. |
| `windows.update(siteWindowId,{focused:true})` | Succeeds but does not change popup-side `currentWindow` or the window types. |
| `tabs.move(siteTabId,{windowId:popupWindowId,index:0})` | Fails: tabs can only be moved to and from normal windows. |
| `scripting.executeScript` with the real website tab ID | Successfully fills an input on the disposable local test page. This does not validate a password manager's autofill workflow. |
| `action.openPopup()` | Fails with `Browser window has no toolbar`. |

## Implications

An address string alone is insufficient. An extension needs a coherent real target tab ID, URL, window association, and event state so its commands affect the website. Native window ownership, parenting, activation, and shared profiles do not by themselves provide toolbar action semantics.

The inspected public extension management APIs expose installation, listing, enable/disable, and removal. No supported active-tab association or toolbar-popup host API was found. The native webview fork wraps Microsoft's WebView2 Runtime; changing that wrapper is not equivalent to modifying Chromium's internal extension tab/window implementation.

A possible general compatibility layer would map Spark's selected browsing context to its real WebView2 tab ID and consistently adapt tab/window queries in extension documents and background contexts. Commands should retain real tab IDs; callback and Promise forms, navigation events, MV2 backgrounds, MV3 worker restarts, and original extension permissions all matter. Injection before a worker's startup code and reliable reinjection after worker restarts are unresolved feasibility requirements. A popup-only script is insufficient. This approach has not been implemented or shown to provide general extension compatibility.

Before production work, a bounded isolated prototype should prove that an unchanged extension can discover the current website from both popup and worker contexts, operate on it, and retain the behavior across site switches, navigation, popup reopen, and worker restart. It must also verify action/activeTab permission behavior rather than broadly granting host access. No extension-name-specific branches or package edits should be needed.

## References

- [WebView2 extension management and missing UI entry points](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2profile7)
- [Microsoft's open discussion of missing extension tab/window integration](https://github.com/MicrosoftEdge/WebView2Feedback/issues/3258)
- [Chrome current-window semantics](https://developer.chrome.com/docs/extensions/reference/api/windows#the-current-window)
- [Chrome tabs API and permissions](https://developer.chrome.com/docs/extensions/reference/api/tabs)
- [The activeTab permission](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab)
