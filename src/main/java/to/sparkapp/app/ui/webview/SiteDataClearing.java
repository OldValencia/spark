package to.sparkapp.app.ui.webview;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import to.sparkapp.app.browser.WebviewManager;
import to.sparkapp.app.config.AppPreferences;

@RequiredArgsConstructor
final class SiteDataClearing {
    private final BrowserSessions sessions;
    private final AppPreferences preferences;
    private final Supplier<Window> owner;
    private final BooleanSupplier disposed;
    @Getter
    private boolean clearing;

    void clear(WebviewManager browser) {
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear cookies and website storage for all sites in Spark? This signs you out and reloads open sites. Unsent text will be lost.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(owner.get());
        confirmation.setTitle("Clear site data");
        confirmation.setHeaderText("Sign out of all sites");

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        clearing = true;
        sessions.forEach(WebviewManager::stopLoading);
        browser.clearBrowsingData(this::complete);
    }

    private void complete(boolean success) {
        clearing = false;

        if (disposed.getAsBoolean()) {
            return;
        }

        if (success) {
            preferences.clearSiteHistory();
            sessions.forEach(WebviewManager::reload);
        }

        var result = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        result.initOwner(owner.get());
        result.setTitle("Clear site data");
        result.setHeaderText(success ? "Site data cleared" : "Site data could not be cleared");
        result.setContentText(
                success ? "Cookies and stored website data have been cleared for the Spark browser profile." : "Your data has not been reported as cleared. Please try again or check the application log.");
        result.show();
    }
}
