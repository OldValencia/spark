# Troubleshooting and tips

## The site takes a few seconds to appear after I open Spark

If Spark shows a black window for a few seconds before the site appears again, check
**Settings → Browser → Reduce background memory**.

This setting is off by default. When you turn it on, Windows may reclaim memory from pages once Spark has
been hidden for 3 minutes. Hiding and showing Spark more often than that stays instant. The trade-off is that
after a longer break the page has to be redrawn when you open Spark again, which is what you see as the
delay.

In our tests, the setting saves about 100 MB on average. If that amount isn't critical on your PC, turn it
off for a smoother experience: Spark then reopens with the page ready.

## Windows warns that Spark is from an unknown publisher

The installer is not code-signed, so Windows SmartScreen may show **Windows protected your PC**. Choose
**More info → Run anyway** to continue. Download Spark only from the
[official releases page](https://github.com/OldValencia/spark/releases).

## Moving Spark to another PC

Open **Three dots → Settings → Backup → Export…** and save the ZIP file. It contains your sites, their icons
and your preferences. Sign-ins and website data, extensions and the pages you had open are not included.

On the other PC, install Spark, quit it (right-click the tray icon → **Exit Spark**), and copy the files
from the archive into `%USERPROFILE%\Documents\OldValencia\Spark`, replacing the files that are already
there. Then start Spark and sign in to your sites again. The same steps are in `README.txt` inside the archive.
