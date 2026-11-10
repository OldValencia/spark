# README illustrations

These self-contained SVGs are editorial illustrations, not application screenshots.
Sparky's geometry follows MascotFace and MascotGears in the JavaFX shell. The
tour labels the shell's controls; it does not replace the website's own UI.

- `hero.svg`: project banner with Sparky, Spark's mascot.
- `tour.svg`: annotated header guide.
- `guide-*.svg`: one card at the top of each README section, with Sparky's pose, a line of
  its own, and a badge for the topic. The cards are listed in `generate-assets.mjs`.

Sparky's poses follow the app: `normal`, `curious` (looking at something), `gears`
(settings and loading), `sad` (MascotFace's frown) and `sleepy` (its doze).

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
