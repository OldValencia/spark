# Third-party software

Spark's MIT license covers its own code and artwork. It does not replace the licenses below. License texts and notices are in `third-party-licenses/`; installed distributions include this document and those texts under `lib/legal/` (the packaged application's `app/legal/` directory).

## Browser bridge and icons

The Java browser integration originated in [webview_java](https://github.com/OldValencia/webview_java), based on Casterlabs' SDK. It has since been substantially rewritten. The native browser libraries derive from [webview](https://github.com/webview/webview). Both are MIT-licensed; the original notices for Alex Bowles / Casterlabs, Serge Zaitsev and Steffen André Langnes are preserved in `webview-java-MIT.md`. Bundled native build provenance is documented in [docs/native/README.md](docs/native/README.md).

[Heroicons](https://github.com/tailwindlabs/heroicons), commit `616b7a4dbbf3d011760af8066262cd5c6b3868f3`, is MIT-licensed. Its notice is preserved in `Heroicons-MIT.txt` and the icon resources. Spark's mascot, pin and coffee glyphs are original project artwork.

## Runtime dependencies

Versions below correspond to the resolved runtime classpath. Libraries are shipped as separate, unmodified JARs. Embedded license and NOTICE files are also copied into `jar-notices/` where available.

| Component | Version | License used | Source |
| --- | --- | --- | --- |
| Jackson annotations | 2.21 | Apache-2.0 | [Jackson](https://github.com/FasterXML/jackson-annotations/tree/jackson-annotations-2.21) |
| Jackson core and databind | 2.21.0 | Apache-2.0 | [Core](https://github.com/FasterXML/jackson-core), [databind](https://github.com/FasterXML/jackson-databind) |
| JSVG | 2.0.0 | MIT | [JSVG](https://github.com/weisJ/jsvg/tree/v2.0.0) |
| JNA and JNA platform | 5.14.0 | Apache-2.0, selected from Apache-2.0 / LGPL-2.1-or-later | [JNA](https://github.com/java-native-access/jna/tree/5.14.0) |
| SLF4J API | 2.0.17 | MIT | [SLF4J](https://github.com/qos-ch/slf4j/tree/v_2.0.17) |
| Logback classic and core | 1.5.27 | EPL-2.0, selected from EPL-2.0 / LGPL-2.1 | [Logback source](https://github.com/qos-ch/logback/tree/v_1.5.27) |
| JNativeHook | 2.2.2 | LGPL-3.0-or-later | [JNativeHook source and build files](https://github.com/kwhat/jnativehook/tree/2.2.2) |
| JavaFX base, graphics, controls and swing | 21.0.2 | GPL-2.0 with the Classpath exception; individual files may carry additional notices | [OpenJFX source](https://github.com/openjdk/jfx21u/tree/21.0.2%2B5) |

JNativeHook is copyright (C) 2006–2021 Alexander Barker. Its LGPL permission is stated in the version 2.2.2 source headers. Its native code uses [libuiohook at commit 23acecfe207f8a8b5161bec97a8a6fd6ad0aea88](https://github.com/kwhat/libuiohook/tree/23acecfe207f8a8b5161bec97a8a6fd6ad0aea88); that submodule's license documents are preserved as well. The library is used without modifications. LGPL-3.0 and GPL-3.0 texts are included. Users may replace its JAR with an interface-compatible version, including one built from modified library sources. Spark imposes no restriction on reverse engineering needed to debug such modifications. Retain this permission and the library's notices when redistributing a combined application.

Logback's copyright and dual-license notice are preserved in `logback-LICENSE.txt`; its selected EPL-2.0 text is included. Corresponding library source is available at the versioned upstream link above. JNA's Apache license selection and full text are preserved separately. The JavaFX GPL text includes its Classpath exception, which permits independent modules under their own licenses. The graphics module's JPEG and Mesa notices are preserved under `javafx-21.0.2/`.

## Packaging and source availability

The installer also contains a Java runtime. Preserve that runtime's `legal/` directory and vendor notices. WebView2 itself is a separately installed Microsoft runtime; Spark's MIT license grants no rights to it.

When publishing binaries, provide the exact corresponding sources and required build information for copyleft libraries, including JavaFX native components and JNativeHook's bundled native code, using the distribution mechanism required by their licenses. Keep their upstream notices, native dependency notices and any runtime vendor source instructions. The versioned links here identify source projects; this inventory alone is not a complete source distribution or a written source offer. Verify these materials for each release and target platform.

Lombok and JUnit are build/test dependencies and are not part of Spark's runtime distribution. Site favicons are retrieved from the configured websites; they are not original Spark artwork.
