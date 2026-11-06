package to.sparkapp.app.ui.topbar.utils;

import java.util.ArrayList;

import javafx.animation.PauseTransition;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.ui.topbar.components.AiDock;

@Slf4j
public final class SiteOrderDiagnostics {
    private SiteOrderDiagnostics() {
    }

    public static void record(AiDock dock) {
        var settled = new PauseTransition(Duration.millis(200));
        settled.setOnFinished(event -> logLayout(dock));
        settled.play();
    }

    private static void logLayout(AiDock dock) {
        var tabs = dock.getDockItems();
        var gaps = new ArrayList<Double>();
        var offsets = new ArrayList<Double>();
        var widths = new ArrayList<Double>();

        for (var index = 0; index < tabs.size(); index++) {
            var tab = tabs.get(index);
            offsets.add(tab.getTranslateX());
            widths.add(tab.getWidth());

            if (index > 0) {
                var previous = tabs.get(index - 1).getBoundsInParent();
                gaps.add(tab.getBoundsInParent().getMinX() - previous.getMaxX());
            }
        }

        log.info("Site order layout: tabs={}, gaps={}, offsets={}, widths={}, expectedGap={}, viewportWidth={}, contentWidth={}",
                tabs.size(), gaps, offsets, widths, dock.getDockContainer().getSpacing(),
                dock.getScrollPane().getViewportBounds().getWidth(), dock.getDockContainer().getWidth());
    }
}
