# Bundled native WebView libraries

## Current CI bundle

All libraries were built by the webview fork's **Build native libraries** GitHub Actions workflow from commit `bc1a6ed23b61b9233251c6e84bdf8c001e7359f0`. The archive was copied to `src/main/resources/webview/` with its original structure:

- `natives/`: Windows x86/x64/ARM64, universal macOS x64/ARM64 resources, and GNU/Linux x64/ARM64 resources.
- `metadata/`: six target manifests identifying the source commit, resource paths, and SHA-256 hashes. The metadata is the source of truth for the hashes.
- `SHA256SUMS`: checksums for the seven libraries produced by that workflow.

All seven files match `SHA256SUMS` and their architecture headers. Windows ARM64 uses the `aarch64/windows_nt` directory. The legacy resources outside the manifest (including `natives/arm/windows_nt`, musl, and older 32-bit Linux resources) are not rebuilt by this workflow; the manifest does not claim otherwise. Both macOS resources contain the same universal binary. The C API remains webview 0.12.0.

The source history is kept as fork commits, so no patch snapshots are stored here:

- `a706ea8`: dispatch-submission cleanup, immediate HRESULT propagation, `GetMessageW` error handling, and termination of the correct browser thread.
- `981b2e9`: browser extensions are enabled when Windows WebView2 environments are created.
- `bc1a6ed`: lifecycle fixes in `win32_edge.hh`:
  - `ICoreWebView2` is no longer AddRef'd twice, so destroyed browsers (including every closed extension popup) no longer leak their core object and handlers.
  - WebMessageReceived and PermissionRequested keep separate registration tokens and are removed on destruction; the controller is closed before release.
  - Initialization fails fast on `ERROR_INVALID_STATE` and `E_ABORT` instead of pumping messages indefinitely; creation retries are limited to five (about 1 s instead of up to 12 s on the UI thread).
  - `ICoreWebView2Settings` is released; COM `Release` decrements once before testing the count.
  - Nested message loops re-post a consumed `WM_QUIT`; initialization callbacks are detached when `embed()` returns, so they cannot touch its stack after an early exit; a constructor failure releases created windows.
  - Navigation, script, and HTML calls report `WEBVIEW_ERROR_INVALID_STATE` after teardown instead of dereferencing a released browser.

The Windows builds use WebView2 SDK `1.0.3595.46` and the static MSVC runtime. Native build configuration and platform requirements are documented in the fork's `docs/native-libraries.md`; consult its workflow run for build logs and platform test results.

## Application integration validation

The Windows x64 library is exercised by the application's `nativeTest` task with disposable profiles and local fixtures, not user sessions. macOS, Linux, and Windows x86/ARM64 libraries were checked against their hashes and architecture headers but not executed here. The known Gradle-worker Chrome_WidgetWin_0 / 1411 diagnostic can still appear at worker shutdown and is not suppressed.
