package to.sparkapp.app.ui.webview;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.util.Duration;
import lombok.Getter;
import to.sparkapp.app.browser.WebviewManager;

final class BrowserLoadingView {
    private final Supplier<WebviewManager> browser;
    private final BooleanSupplier visible;
    private final Runnable syncBounds;
    private final Supplier<Runnable> editSite;
    private final BooleanSupplier reducedMotion;
    private WebviewManager presented;
    private final PauseTransition loadTimeout = new PauseTransition(Duration.seconds(20));
    @Getter
    private final WebViewLoadingOverlay overlay = new WebViewLoadingOverlay();
    private final ReadOnlyBooleanWrapper pageLoading = new ReadOnlyBooleanWrapper();

    BrowserLoadingView(Supplier<WebviewManager> browser, BooleanSupplier visible, Runnable syncBounds,
                       Supplier<Runnable> editSite, BooleanSupplier reducedMotion) {
        this.browser = browser;
        this.visible = visible;
        this.syncBounds = syncBounds;
        this.editSite = editSite;
        this.reducedMotion = reducedMotion;
    }

    /** Whether the site on screen is loading a page; failed loads and hidden windows do not count. */
    ReadOnlyBooleanProperty pageLoadingProperty() {
        return pageLoading.getReadOnlyProperty();
    }

    void refresh() {
        loadTimeout.stop();

        if (!visible.getAsBoolean()) {
            pageLoading.set(false);

            return;
        }

        var active = browser.get();
        pageLoading.set(active.isLoading() && active.getLoadError() == null);

        if (active.getLoadError() != null) {
            presented = null;
            active.setVisible(false);
            overlay.showError(active.getLoadError(), () -> browser.get().reload(), null, editSite.get());
        } else if (active.isLoading() && !active.isPagePresentable()) {
            presented = null;
            active.setVisible(false);
            overlay.activate(null, 0, null);
            overlay.showLoading(this::showPage, reducedMotion.getAsBoolean());
            loadTimeout.setOnFinished(event -> showTimeout());
            loadTimeout.playFromStart();
        } else {
            overlay.deactivate();
            syncBounds.run();
            presentPage();
        }
    }

    private void showTimeout() {
        if (!visible.getAsBoolean() || !browser.get().isLoading() || browser.get().isPagePresentable()) {
            return;
        }

        overlay.showError("Loading is taking longer than expected. You can retry or show the page.",
                () -> browser.get().reload(), this::showPage, editSite.get());
    }

    private void showPage() {
        browser.get().allowPagePresentation();
        syncBounds.run();
        presentPage();
    }

    private void presentPage() {
        overlay.deactivate();
        browser.get().setVisible(true);

        if (presented != browser.get()) {
            browser.get().focusBrowser();
            presented = browser.get();
        }
    }

    void pause() {
        pageLoading.set(false);
        presented = null;
        overlay.pauseAnimation();
        loadTimeout.stop();
    }

    void stop() {
        pageLoading.set(false);
        overlay.deactivate();
        loadTimeout.stop();
    }
}
