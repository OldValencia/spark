package io.loom.app.windows;

import io.loom.app.ui.Theme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SplashScreen extends Stage {

    private static final double WIDTH = 400;
    private static final double HEIGHT = 250;
    private static final int RADIUS = 20;

    private final Label statusLabel;

    public SplashScreen() {
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(true);
        this.setWidth(WIDTH);
        this.setHeight(HEIGHT);
        this.centerOnScreen();

        var contentPane = new BorderPane();
        contentPane.setPadding(new Insets(30, 40, 30, 40));

        var bg = Theme.toHex(Theme.BG_BAR);
        var border = Theme.toHex(Theme.BORDER);
        contentPane.setStyle(
                "-fx-background-color: " + bg + "; " +
                        "-fx-background-radius: " + RADIUS + "px; " +
                        "-fx-border-radius: " + RADIUS + "px; " +
                        "-fx-border-color: " + border + "; " +
                        "-fx-border-width: 1px;"
        );

        contentPane.setTop(createLogoPanel());

        var titleLabel = new Label("Loom");
        titleLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Theme.TEXT_PRIMARY);
        titleLabel.setPadding(new Insets(10, 0, 20, 0));
        BorderPane.setAlignment(titleLabel, Pos.CENTER);
        contentPane.setCenter(titleLabel);

        var progressPanel = new VBox(10);
        progressPanel.setAlignment(Pos.CENTER);

        statusLabel = new Label("Initializing browser engine...");
        statusLabel.setFont(Font.font("SansSerif", 12));
        statusLabel.setTextFill(Theme.TEXT_SECONDARY);

        var progressBar = new ProgressBar();
        progressBar.setPrefWidth(WIDTH - 80);
        progressBar.setPrefHeight(6);
        // Indeterminate mode in JavaFX
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setStyle("-fx-accent: " + Theme.toHex(Theme.ACCENT) + "; " +
                "-fx-control-inner-background: " + Theme.toHex(Theme.BG_DEEP) + ";");

        progressPanel.getChildren().addAll(statusLabel, progressBar);
        contentPane.setBottom(progressPanel);

        var scene = new Scene(contentPane, WIDTH, HEIGHT, Color.TRANSPARENT);
        this.setScene(scene);
    }

    private VBox createLogoPanel() {
        var panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPrefSize(WIDTH - 80, 60);

        var iconStream = getClass().getResourceAsStream("/app-icons/icon.png");
        if (iconStream != null) {
            try {
                var image = new Image(iconStream, 48, 48, true, true);
                var iconView = new ImageView(image);
                panel.getChildren().add(iconView);
            } catch (Exception e) {
                addFallbackLogo(panel);
            }
        } else {
            addFallbackLogo(panel);
        }

        return panel;
    }

    private void addFallbackLogo(VBox panel) {
        var logoLabel = new Label("L");
        logoLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 48));
        logoLabel.setTextFill(Theme.ACCENT);
        panel.getChildren().add(logoLabel);
    }

    public void updateStatus(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    public void showSplash() {
        Platform.runLater(this::show);
    }

    public void hideSplash() {
        Platform.runLater(this::hide);
    }
}
