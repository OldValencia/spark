# Spark README tour

Recorded on Windows in Spark 0.3.3 by `.\gradlew.bat readmeCapture` (see [Recreate the media](#recreate-the-media)).
The application UI and browser pages are actual screen captures of a running Spark instance, with a
prepared backdrop and English chapter captions. The video has no audio stream.

| File | Format | Size | Duration |
| --- | --- | --- | --- |
| `spark-tour.mp4` | H.264, yuv420p, 1200 × 900, 30 fps | 1,260,212 bytes | 45.4 s |
| `spark-tour.gif` | 960 × 720, 12 fps, 128-color palette | 4,163,972 bytes | approximately 45.4 s |
| `spark-tour-poster.png` | Still frame at 2.2 s, 1200 × 900 | 128,776 bytes | — |

The README displays the animated GIF and links to the MP4 and still image. The assets are local to the
repository; no external video host or GitHub attachment upload is required for the preview.

## What is shown

1. Open the window with ChatGPT.
2. Switch between the loaded ChatGPT and Wikipedia pages.
3. Open Your sites and search for “wiki”.
4. Change between the S and M window presets.
5. Open Extensions and open Dark Reader's popover, which identifies the open Wikipedia page.
6. Open Settings and scroll through the preferences.
7. Hide Spark and bring it back on the same page.

## How it was captured

The capture runs a real Spark instance in a disposable profile under `build/readme-capture/`: a separate
settings directory (`spark.dataDir`) and WebView2 user data folder. No installed application profile,
account, or browser session is read. The site collection is Spark's default list plus Wikipedia, whose icon
comes from the same favicon service Spark uses when a site is added. ChatGPT and Wikipedia are real public
websites, signed out; ChatGPT's optional cookies are rejected before the take, and no messages are sent.

The extensions are the official sample packages fetched by `tools/extensions/download-samples.ps1`
(Bitwarden, uBlock Origin, I don't care about cookies, and Dark Reader). They are imported into the
disposable profile before Spark starts.

The real mouse pointer is moved and clicked with `java.awt.Robot`; text is entered through the clipboard so
the result does not depend on the keyboard layout, and the previous clipboard text is restored. FFmpeg
records only the backdrop region with GDI capture (`gdigrab`, pointer visible, `-an`). The backdrop and
Spark are kept above other windows during the capture, so no other desktop content is recorded.

## Recreate the media

Requirements: Windows, FFmpeg on PATH, and the sample packages:

```powershell
./tools/extensions/download-samples.ps1
.\gradlew.bat readmeCapture
```

Do not use the mouse or keyboard while it runs (about two minutes). The results are written to
`build/readme-capture/output/`; review them, then copy the video files here and the screenshots to
[`docs/screenshots`](../screenshots/README.md). `-PreadmeMode=tour-only` records only the video and
`-PreadmeMode=screenshots` takes only the screenshots.

The GIF and poster are derived from the MP4 by the capture task, equivalent to:

```powershell
ffmpeg -y -i docs/media/spark-tour.mp4 -vf "fps=12,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" -loop 0 docs/media/spark-tour.gif
ffmpeg -y -ss 2.2 -i docs/media/spark-tour.mp4 -frames:v 1 docs/media/spark-tour-poster.png
```

Check the streams with `ffprobe -show_streams docs/media/spark-tour.mp4`. There should be one video stream
and no audio stream.
