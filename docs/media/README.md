# Spark README tour

Recorded on Windows in Spark 0.3.1. The application UI and browser pages are actual
screen captures, with a prepared backdrop and English chapter captions. The video
has no audio stream. No application code was changed for the recording.

| File | Format | Size | Duration |
| --- | --- | --- | --- |
| `spark-tour.mp4` | H.264, yuv420p, 1200 × 900, 30 fps | 653,827 bytes | 30.47 s |
| `spark-tour.gif` | 960 × 720, 12 fps, 128-color palette | 2,275,401 bytes | approximately 30.5 s |
| `spark-tour-poster.png` | Still frame, 1200 × 900 | 133,746 bytes | — |

The README displays the animated GIF and links to the MP4 and still image. The
assets are local to the repository; no external video host or GitHub attachment
upload is required for the preview.

## What is shown

1. Open the docked window.
2. Switch between loaded ChatGPT and Wikipedia pages.
3. Open Your sites, search, and fill an Add site form.
4. Change between S and M window presets.
5. Open Settings and scroll through the preferences.
6. Increase and reset page zoom.
7. Drag the window away from the top edge.
8. Hide, restore the same page and placement, then hide again.

The site collection was prepared for the recording: ChatGPT, Wikipedia, Claude and
Gemini. Only ChatGPT and Wikipedia were opened. These were real public websites in
a disposable WebView2 profile. Optional ChatGPT cookies were rejected before the
take. No account was signed in, no messages were sent, and existing application or
browser sessions were not opened. The Docs form is an unsaved example and is
cancelled in the video.

Pages were loaded before the take so network waits do not dominate the tour. The
capture region contains only the prepared backdrop, Spark and its owned dialogs.
The recorder uses GDIGRAB with mouse capture enabled and `-an` for video only.

## Recreate the preview from the MP4

With FFmpeg on PATH, run from the repository root:

```powershell
ffmpeg -y -i docs/media/spark-tour.mp4 -vf "fps=12,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" -loop 0 docs/media/spark-tour.gif
ffmpeg -y -ss 2.2 -i docs/media/spark-tour.mp4 -frames:v 1 docs/media/spark-tour-poster.png
```

Check the streams with `ffprobe -show_streams docs/media/spark-tour.mp4`.
There should be one video stream and no audio stream.
