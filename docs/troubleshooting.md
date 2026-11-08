# Troubleshooting and tips

## The site takes a few seconds to appear after I open Spark

If Spark shows a black window for a few seconds before the site appears again, turn off
**Settings → Browser → Reduce background memory**.

This setting is on by default. While Spark is hidden, it lets Windows reclaim memory from the hidden pages.
The trade-off is that the page has to be redrawn when you open Spark again, which is what you see as the
delay.

In our tests, the setting saves about 100 MB on average. If that amount isn't critical on your PC, turn it
off for a smoother experience: Spark then reopens with the page ready.

## Windows warns that Spark is from an unknown publisher

The installer is not code-signed, so Windows SmartScreen may show **Windows protected your PC**. Choose
**More info → Run anyway** to continue. Download Spark only from the
[official releases page](https://github.com/OldValencia/spark/releases).
