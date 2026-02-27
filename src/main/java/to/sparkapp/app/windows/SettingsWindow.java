package to.sparkapp.app.windows;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import to.sparkapp.app.ui.settings.SettingsPanel;

public class SettingsWindow {

    private static final int TOPBAR_HEIGHT = 48;
    private static final double LERP_SPEED = 0.22;

    private final Window owner;
    private final SettingsPanel settingsPanel;
    private Stage window;
    private Rectangle contentClip;

    private double progress = 0.0;
    private double targetProgress = 0.0;
    private AnimationTimer animTimer;
    private double targetHeight = 0;

    public SettingsWindow(Window owner, SettingsPanel settingsPanel) {
        this.owner = owner;
        this.settingsPanel = settingsPanel;
    }

    public void open() {
        if (window == null) createWindow();

        var w = Double.isNaN(owner.getWidth()) ? 820.0 : owner.getWidth();

        settingsPanel.setPrefWidth(w);
        settingsPanel.setMinWidth(w);
        settingsPanel.setMaxWidth(w);
        settingsPanel.applyCss();
        settingsPanel.layout();

        var contentHeight = settingsPanel.prefHeight(w);
        var maxHeight = (Double.isNaN(owner.getHeight()) ? 700.0 : owner.getHeight()) - TOPBAR_HEIGHT;
        targetHeight = Math.min(contentHeight, maxHeight);

        var x = Double.isNaN(owner.getX()) ? 0.0 : owner.getX();
        var y = (Double.isNaN(owner.getY()) ? 0.0 : owner.getY()) + TOPBAR_HEIGHT;

        if (!window.isShowing()) {
            window.setX(x);
            window.setY(y);
            window.setWidth(w);
            window.setHeight(targetHeight);
            progress = 0.0;
            applyClip(0.0, w);
            settingsPanel.setOpacity(0.0);
            window.show();
        } else {
            window.setX(x);
            window.setWidth(w);
            window.setHeight(targetHeight);
            applyClip(targetHeight * progress, w);
        }

        window.toFront();
        targetProgress = 1.0;
        animTimer.start();
    }

    public void close() {
        if (window == null) return;
        targetProgress = 0.0;
        animTimer.start();
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

        owner.xProperty().addListener((obs, o, n) -> syncPosition());
        owner.yProperty().addListener((obs, o, n) -> syncPosition());
        owner.widthProperty().addListener((obs, o, newW) -> {
            if (window != null && window.isShowing() && isOpen()) {
                window.setWidth(newW.doubleValue());
                applyClip(targetHeight * progress, newW.doubleValue());
            }
        });

        var scrollPane = new ScrollPane(settingsPanel);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        contentClip = new Rectangle(820, 0);
        scrollPane.setClip(contentClip);

        var scene = new Scene(scrollPane, Color.TRANSPARENT);
        window.setScene(scene);

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
    }

    private void applyClip(double clipH, double clipW) {
        contentClip.setWidth(clipW);
        contentClip.setHeight(clipH);
    }

    private void syncPosition() {
        if (window != null && window.isShowing() && isOpen()) {
            window.setX(owner.getX());
            window.setY(owner.getY() + TOPBAR_HEIGHT);
        }
    }

    private void tick() {
        double diff = targetProgress - progress;

        if (Math.abs(diff) < 0.004) {
            progress = targetProgress;
            animTimer.stop();
            if (progress <= 0.0) {
                window.hide();
                return;
            }
        } else {
            progress += diff * LERP_SPEED;
        }

        double clipH = targetHeight * progress;
        double clipW = window.getWidth() > 0 ? window.getWidth() : 820;
        applyClip(clipH, clipW);

        double opacity = progress < 0.5 ? 0.0 : (progress - 0.5) / 0.5;
        settingsPanel.setOpacity(Math.min(1.0, opacity));
    }
}
