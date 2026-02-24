package to.sparkapp.app.windows;

import to.sparkapp.app.ui.settings.SettingsPanel;
import to.sparkapp.app.utils.SystemUtils;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class SettingsWindow {

    private static final int TOPBAR_HEIGHT = 48;
    private static final double LERP_SPEED = 0.3;

    private final Window owner;
    private final SettingsPanel settingsPanel;
    private Stage window;

    private double progress = 0.0;
    private double targetProgress = 0.0;
    private AnimationTimer animTimer;

    private double targetHeight = 0;

    public SettingsWindow(Window owner, SettingsPanel settingsPanel) {
        this.owner = owner;
        this.settingsPanel = settingsPanel;
    }

    public void open() {
        if (window == null) {
            createWindow();
        }

        // Add protection against NaN during layout initialization
        var w = Double.isNaN(owner.getWidth()) ? 820.0 : owner.getWidth();

        settingsPanel.setPrefWidth(w);
        settingsPanel.setMinWidth(w);
        settingsPanel.setMaxWidth(w);
        settingsPanel.applyCss();
        settingsPanel.layout();

        var contentHeight = settingsPanel.prefHeight(w);
        var maxHeight = (Double.isNaN(owner.getHeight()) ? 700.0 : owner.getHeight()) - TOPBAR_HEIGHT;
        this.targetHeight = Math.min(contentHeight, maxHeight);

        var x = Double.isNaN(owner.getX()) ? 0.0 : owner.getX();
        var y = (Double.isNaN(owner.getY()) ? 0.0 : owner.getY()) + TOPBAR_HEIGHT;

        if (SystemUtils.isMac()) {
            if (animTimer != null) {
                animTimer.stop();
            }

            window.setX(x);
            window.setY(y);
            window.setWidth(w);
            window.setHeight(targetHeight);
            settingsPanel.setOpacity(1.0);
            window.show();
            window.toFront();

            progress = 1.0;
            targetProgress = 1.0;
        } else {
            if (!window.isShowing()) {
                window.setX(x);
                window.setY(y);
                window.setWidth(w);
                window.setHeight(0);
                window.show();
            } else {
                window.setWidth(w);
            }
            window.toFront();

            targetProgress = 1.0;
            animTimer.start();
        }
    }

    public void close() {
        if (window == null) {
            return;
        }

        if (SystemUtils.isMac()) {
            if (animTimer != null) {
                animTimer.stop();
            }
            window.hide();
            progress = 0.0;
            targetProgress = 0.0;
        } else {
            targetProgress = 0.0;
            animTimer.start();
        }
    }

    public boolean isOpen() {
        return window != null && targetProgress > 0.5;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    private void createWindow() {
        window = new Stage();
        window.initOwner(owner);
        window.initStyle(StageStyle.TRANSPARENT);
        window.setAlwaysOnTop(true);

        owner.xProperty().addListener((obs, oldVal, newVal) -> syncPosition());
        owner.yProperty().addListener((obs, oldVal, newVal) -> syncPosition());
        owner.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (window != null && window.isShowing() && isOpen()) {
                window.setWidth(newVal.doubleValue());
            }
        });

        ScrollPane scrollPane = new ScrollPane(settingsPanel);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene scene = new Scene(scrollPane, Color.TRANSPARENT);
        window.setScene(scene);

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
    }

    private void syncPosition() {
        if (window != null && window.isShowing() && isOpen()) {
            window.setX(owner.getX());
            window.setY(owner.getY() + TOPBAR_HEIGHT);
        }
    }

    private void tick() {
        double diff = targetProgress - progress;

        if (Math.abs(diff) < 0.01) {
            progress = targetProgress;
            animTimer.stop();
            if (progress <= 0) {
                window.hide();
            }
        } else {
            progress += diff * LERP_SPEED;
        }

        double currentH = targetHeight * progress;

        if (window.isShowing()) {
            window.setHeight(currentH);
        }

        settingsPanel.setOpacity(progress > 0.8 ? 1.0 : 0.0);
    }
}
