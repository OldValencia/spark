# Bundled native WebView libraries

The current libraries were built by the webview fork's **Build native libraries** GitHub Actions workflow from commit `a706ea8219c712653fd3f794259cb32a423a5243`. Native binaries are built in GitHub Actions; this application repository consumes the resulting archive.

The archive was copied to `src/main/resources/webview/` with its original structure:

- `natives/`: Windows x86/x64/ARM64, universal macOS x64/ARM64 resources, and GNU/Linux x64/ARM64 resources.
- `metadata/`: six target manifests identifying the source commit, resource paths and SHA-256 hashes.
- `SHA256SUMS`: checksums for the seven libraries produced by that workflow.

All seven files match their manifests, architecture headers and SHA256SUMS. Windows ARM64 uses the `aarch64/windows_nt` directory. The existing eight legacy resources outside the manifest (including musl and older 32-bit Linux resources) were not rebuilt by this workflow; the manifest does not claim otherwise. Both macOS resources contain the same universal binary.

The C API remains webview 0.12.0. The Windows x64 DLL SHA-256 is `65f872a2c566f5d2be7e8642a93e85ca7e99593d46542c7387b4bc2aaca6aed1`. The metadata is the source of truth for the remaining hashes. The source changes include dispatch-submission cleanup, immediate HRESULT propagation, GetMessageW error handling and termination of the correct browser thread.

The former local source-patch snapshot is no longer needed: the native fork commit and the shipped manifests identify the actual CI artifacts. Native build configuration and platform requirements are documented in the fork's `docs/native-libraries.md`; consult its workflow run for build logs and platform test results.

## Application integration validation

On Windows/JDK 21, 27 unit tests and 7 native integration tests pass with the bundled Windows x64 DLL. Coverage includes repeated session clearing without completion-handler accumulation, navigation failure after controller Close, preserved background streaming/drafts, renderer/browser crash recovery, two independent JVMs sharing the DLL cache, and natural application exit. All browser tests use disposable profiles and local fixtures, not user sessions.

The other new binaries were checked against their architecture headers and SHA-256 manifests in this workspace; macOS/Linux and Windows ARM64 were not executed here. The known Gradle-worker Chrome_WidgetWin_0 / 1411 diagnostic can still appear at worker shutdown and is not suppressed. No new RAM-saving claim is made for the native library update itself.
