# Application icons

`src/main/resources/app-icons/icon.svg` is the source for the approved Curious Sparky icon.
The same vector geometry is used at every size, including 16 px.

Regenerate the background SVG, transparent 512 px PNG, Windows ICO and macOS ICNS
with Node.js and ImageMagick on PATH:

```sh
node tools/icons/generate-icons.mjs
node src/installer/windows/generate-art.mjs
```

The ICO includes 16, 20, 24, 32, 40, 48, 64, 128 and 256 px frames.
The ICNS includes standard and Retina PNG entries through 1024 px.
Intermediate PNGs stay under `build/icon-assets/`.
JavaFX windows use the PNG; platform installers use ICO, ICNS or PNG.
The tray uses `app-icons/tray/icon-*.png` (16 to 64 px) and draws the frame that matches its size and
the display scale, because shrinking the 512 px PNG to tray size drops the outline and the edges of the eyes.
