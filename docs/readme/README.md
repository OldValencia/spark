# README illustrations

These self-contained SVGs are editorial illustrations, not application screenshots.
The mascot geometry follows MascotFace and MascotGears in the JavaFX shell. The
tour labels the shell's controls; it does not replace the website's own UI.

- `hero.svg`: project banner with the mascot.
- `tour.svg`: annotated header guide.
- `guide-sites.svg`, `guide-window.svg`, `guide-settings.svg`, `guide-extensions.svg`:
  short mascot captions for the corresponding parts of the README.

Regenerate after changing their source:

```sh
node docs/readme/generate-assets.mjs
```

The illustrations use system fonts and local vector shapes. They contain no
external images, scripts, fonts, or network requests.

The video and screenshots are actual captures, recorded by `.\gradlew.bat readmeCapture`;
their provenance and how to recreate them are documented in [media](../media/README.md)
and [screenshots](../screenshots/README.md). Older review captures are in
[previews](../previews/README.md).
