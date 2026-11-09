package to.sparkapp.app.windows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("native")
@Timeout(20)
class QuickAccessPillTest {
    @Test
    void slowApproachPreviewsThePillEvenWhenQuickOpeningIsEnabled() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(100, 150_000_000));
                assertEquals(0, fixture.opens.get());
                assertEquals(1, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void quickApproachOpensDirectlyWithoutPreviewingThePill() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(1, fixture.opens.get());
                assertEquals(0, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void disablingQuickOpeningKeepsFastMovementAsAPillPreview() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, false)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(0, fixture.opens.get());
                assertEquals(1, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void fastMovementOutsideTheCenterDoesNotOpenSpark() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX() + 300, fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(0, fixture.opens.get());
                assertEquals(0, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void quickOpeningUsesAWiderZoneThanThePillPreview() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                for (var offset : new int[]{-180, 180}) {
                    fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY() + 200, null);
                    fixture.pill.handleHiddenPointer(fixture.centerX() + offset, fixture.edgeY(),
                            fixture.approach(100, 150_000_000));
                    assertEquals(0, fixture.visiblePills());
                    fixture.pill.handleHiddenPointer(fixture.centerX() + offset, fixture.edgeY(),
                            fixture.approach(200, 100_000_000));
                }
                assertEquals(2, fixture.opens.get());
            }
            return null;
        });
    }

    @Test
    void visiblePreviewDoesNotBlockTheNextQuickApproach() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(100, 150_000_000));
                assertEquals(1, fixture.visiblePills());
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(1, fixture.opens.get());
            }
            return null;
        });
    }

    @Test
    void anOpenedWindowRequiresLeavingTheEdgeBeforeTheNextGesture() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                fixture.pill.windowShown();
                fixture.pill.handleHiddenPointer(fixture.centerX() + 180, fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(1, fixture.opens.get());
            }
            return null;
        });
    }

    @Test
    void returningFromOutsideTheEdgeAllowsAnotherGesture() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                fixture.pill.windowShown();
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY() + 200, null);
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(2, fixture.opens.get());
            }
            return null;
        });
    }

    @Test
    void fullscreenAppKeepsTheTopEdgeQuiet() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.fullscreen.setFullscreen(true);
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(100, 150_000_000));
                assertEquals(0, fixture.opens.get());
                assertEquals(0, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void turningTheFullscreenOptionOffKeepsTheGestureOverFullscreenApps() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(true, true)) {
                fixture.fullscreen.setFullscreen(true);
                fixture.preferences.setTopEdgeQuietInFullscreen(false);
                fixture.pill.handleHiddenPointer(fixture.centerX(), fixture.edgeY(),
                        fixture.approach(200, 100_000_000));
                assertEquals(1, fixture.opens.get());
            }
            return null;
        });
    }

    @Test
    void visiblePillStepsAsideWhileAFullscreenAppFillsItsMonitor() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(false, false)) {
                fixture.pill.refreshPill();
                assertEquals(1, fixture.visiblePills());
                assertTrue(fixture.fullscreen.isWatching());
                assertEquals(1, fixture.fullscreen.shownPills(), "The pill's monitor must be remembered");

                fixture.fullscreen.setFullscreen(true);
                assertEquals(0, fixture.visiblePills());

                fixture.fullscreen.setFullscreen(false);
                assertEquals(1, fixture.visiblePills());
            }
            return null;
        });
    }

    @Test
    void visiblePillIgnoresFullscreenAppsWhenTheOptionIsOff() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(false, false)) {
                fixture.preferences.setTopEdgeQuietInFullscreen(false);
                fixture.fullscreen.setFullscreen(true);
                fixture.pill.refreshPill();
                assertEquals(1, fixture.visiblePills());
                assertFalse(fixture.fullscreen.isWatching());
            }
            return null;
        });
    }

    @Test
    void openingSparkStopsWatchingOtherApps() throws Exception {
        JavaFxTestRuntime.call(() -> {
            try (var fixture = new QuickAccessPillFixture(false, false)) {
                fixture.pill.refreshPill();
                fixture.pill.windowShown();
                assertFalse(fixture.fullscreen.isWatching());
                assertEquals(0, fixture.visiblePills());
            }
            return null;
        });
    }
}
