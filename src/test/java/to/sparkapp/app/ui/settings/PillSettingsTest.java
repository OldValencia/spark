package to.sparkapp.app.ui.settings;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.settings.components.GeneralSettingsCard;
import to.sparkapp.app.ui.settings.components.PillSettingsCard;
import to.sparkapp.app.ui.settings.components.SettingsCard;
import to.sparkapp.app.ui.settings.components.SettingsToggleRow;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
@Timeout(20)
class PillSettingsTest {
    @Test
    void pillSettingsStayTogetherAndQuickOpeningRequiresAutoHide() throws Exception {
        JavaFxTestRuntime.call(() -> {
            assertEquals("true", System.getProperty("spark.nativeTests"));
            var preferences = new AppPreferences();
            var previousPill = preferences.isTopPill();
            var previousQuickOpen = preferences.isFastTopEdgeReveal();
            var previousHover = preferences.isTopEdgeReveal();
            var previousMotion = preferences.isReducedMotion();
            var previousUpdates = preferences.isCheckUpdatesOnStartupEnabled();
            preferences.setCheckUpdatesOnStartup(false);
            preferences.setTopPill(true);
            var changes = new AtomicInteger();
            var panel = new SettingsPanel(preferences, null);
            panel.setOnPillSettingsChanged(changes::incrementAndGet);

            try {
                var pill = card(panel, PillSettingsCard.class);
                var autoHide = row(pill, "Auto-hide top-edge pill");
                var hover = row(pill, "Open on top-edge hover");
                var quickOpen = row(pill, "Quick upward gesture");
                assertTrue(quickOpen.isDisabled());
                click(quickOpen);
                assertFalse(preferences.isFastTopEdgeReveal());
                assertEquals(0, changes.get());

                click(autoHide);
                assertFalse(quickOpen.isDisabled());
                click(quickOpen);
                assertTrue(preferences.isFastTopEdgeReveal());
                assertTrue(quickOpen.getToggle().isSelected());
                click(autoHide);
                assertTrue(quickOpen.isDisabled());
                assertFalse(quickOpen.getToggle().isSelected());
                assertFalse(preferences.isFastTopEdgeReveal());
                assertEquals(3, changes.get());

                click(hover);
                assertEquals(!previousHover, preferences.isTopEdgeReveal());
                assertEquals(4, changes.get());
                var general = card(panel, GeneralSettingsCard.class);
                click(row(general, "Reduce motion"));
                assertEquals(!previousMotion, preferences.isReducedMotion());
                assertEquals(4, changes.get());

                var reopened = card(new SettingsPanel(preferences, null), PillSettingsCard.class);
                assertTrue(row(reopened, "Quick upward gesture").isDisabled());
                assertFalse(row(reopened, "Quick upward gesture").getToggle().isSelected());
            } finally {
                preferences.setTopPill(previousPill);
                preferences.setFastTopEdgeReveal(previousQuickOpen);
                preferences.setTopEdgeReveal(previousHover);
                preferences.setReducedMotion(previousMotion);
                preferences.setCheckUpdatesOnStartup(previousUpdates);
                preferences.flush();
            }
            return null;
        });
    }

    private <T extends SettingsCard> T card(SettingsPanel panel, Class<T> type) {
        return panel.getChildren().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private SettingsToggleRow row(SettingsCard card, String title) {
        return card.getChildren().stream().filter(SettingsToggleRow.class::isInstance).map(
                SettingsToggleRow.class::cast).filter(
                row -> title.equals(row.getToggle().getAccessibleText())).findFirst().orElseThrow();
    }

    private void click(SettingsToggleRow row) {
        row.getToggle().fireEvent(
                new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
    }
}
