# Windows installer artwork

- `banner.bmp` (493 × 58): the top strip of the inner installer pages.
- `dialog.bmp` (493 × 312): the welcome and finish pages.

WiX draws its own black text over both bitmaps, so the areas behind the text stay white.
`createInstaller` passes them to jpackage through a copy of the JDK's `main.wxs` with two
`WixVariable` lines (see `prepareInstallerResources` in `build.gradle`).

Regenerate after changing `generate-art.mjs` (requires ImageMagick):

```sh
node src/installer/windows/generate-art.mjs
```
