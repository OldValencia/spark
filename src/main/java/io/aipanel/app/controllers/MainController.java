package io.aipanel.app.controllers;

import io.aipanel.app.AIPanelApplication;
import io.aipanel.app.config.AiConfiguration;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.springframework.stereotype.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

@Controller
@RequiredArgsConstructor
public class MainController implements Initializable {

    @FXML
    private Pane browserAnchor;
    @FXML
    private ComboBox<AiConfiguration.AiConfig> aiSelectBox;
    @FXML
    private HBox topHBox;

    private final AiConfiguration aiConfiguration;

    private CefBrowser cefBrowser;
    private CefClient client;
    private JFrame browserWindow;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupAiSelection();
        setupWindowDragging();

        var startUrl = "https://google.com";
        if (!aiConfiguration.getConfigurations().isEmpty()) {
            startUrl = aiConfiguration.getConfigurations().getFirst().getUrl();
        }
        var finalStartUrl = startUrl;

        new Thread(() -> {
            try {
                initChromium(finalStartUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void initChromium(String startUrl) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        var builder = new CefAppBuilder();
        builder.setInstallDir(new File(System.getProperty("user.home"), ".aipanel/jcef-bundle"));
        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.getCefSettings().log_severity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
        builder.getCefSettings().cache_path = new File(System.getProperty("user.home"), ".aipanel/cache").getAbsolutePath();
        builder.getCefSettings().persist_session_cookies = true;

        var app = builder.build();
        client = app.createClient();
        cefBrowser = client.createBrowser(startUrl, false, false);
        var browserUI = cefBrowser.getUIComponent();

        SwingUtilities.invokeLater(() -> {
            browserWindow = new JFrame();
            browserWindow.setUndecorated(true);
            browserWindow.setBackground(new Color(30, 30, 30));
            browserWindow.setLayout(new BorderLayout());
            browserWindow.add(browserUI, BorderLayout.CENTER);
            browserWindow.setType(java.awt.Window.Type.UTILITY);

            // ФИКС ИСЧЕЗНОВЕНИЯ: Браузер ВСЕГДА сверху, как и само приложение.
            // Теперь он не провалится под другие окна.
            browserWindow.setAlwaysOnTop(true);

            browserWindow.setVisible(false);
        });

        Platform.runLater(() -> {
            var syncTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    updateBrowserPosition();
                }
            };
            syncTimer.start();

            // Хук на закрытие
            topHBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                        if (newWin != null) {
                            newWin.setOnCloseRequest(e -> closeApp());
                        }
                    });
                }
            });
        });
    }

    private void updateBrowserPosition() {
        if (browserWindow == null || browserAnchor == null || browserAnchor.getScene() == null) return;

        var fxWindow = browserAnchor.getScene().getWindow();

        // Единственная причина скрыть браузер - сворачивание приложения
        if (fxWindow instanceof Stage stage && stage.isIconified()) {
            SwingUtilities.invokeLater(() -> {
                if (browserWindow.isVisible()) browserWindow.setVisible(false);
            });
            return;
        }

        if (!fxWindow.isShowing()) return;

        var point = browserAnchor.localToScreen(0, 0);
        if (point == null) return;

        var x = (int) point.getX();
        var y = (int) point.getY();
        var w = (int) browserAnchor.getWidth();
        var h = (int) browserAnchor.getHeight();

        SwingUtilities.invokeLater(() -> {
            if (browserWindow != null) {
                if (w > 0 && h > 0) {
                    if (!browserWindow.isVisible()) browserWindow.setVisible(true);

                    browserWindow.setBounds(x, y, w, h);

                    try {
                        browserWindow.setShape(new RoundRectangle2D.Double(0, 0, w, h, 10, 10));
                    } catch (Exception e) { /* ignore */ }

                    // Мы НЕ вызываем toFront(), чтобы не ломать перетаскивание.
                    // Но мы проверяем статус AlwaysOnTop, чтобы он случайно не слетел.
                    if (!browserWindow.isAlwaysOnTop() && aiSelectBox != null && !aiSelectBox.isShowing()) {
                        browserWindow.setAlwaysOnTop(true);
                    }
                }
            }
        });
    }

    // --- Селектор и перетаскивание ---

    private void setupAiSelection() {
        var configurations = FXCollections.observableArrayList(aiConfiguration.getConfigurations());
        aiSelectBox.setItems(configurations);
        aiSelectBox.setCellFactory(param -> new AiListCell());
        aiSelectBox.setButtonCell(new AiListCell());
        if (!configurations.isEmpty()) aiSelectBox.setValue(configurations.getFirst());

        aiSelectBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (cefBrowser != null && newValue != null) cefBrowser.loadURL(newValue.getUrl());
        });

        // "Умный" переключатель слоев для выпадающего списка
        aiSelectBox.showingProperty().addListener((obs, oldVal, isShowing) -> {
            SwingUtilities.invokeLater(() -> {
                if (browserWindow != null) {
                    // Если список открыт -> выключаем OnTop у браузера, чтобы список был виден
                    // Если список закрыт -> включаем OnTop обратно, чтобы браузер не пропадал
                    browserWindow.setAlwaysOnTop(!isShowing);
                }
            });
        });
    }

    private static class AiListCell extends ListCell<AiConfiguration.AiConfig> {
        @Override
        protected void updateItem(AiConfiguration.AiConfig item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getName());
                try {
                    if (item.getIcon() != null) {
                        var p = "/icons/" + item.getIcon();
                        var u = AIPanelApplication.class.getResource(p);
                        if (u != null) {
                            var iv = new ImageView(new Image(u.toExternalForm()));
                            iv.setFitHeight(18);
                            iv.setFitWidth(18);
                            setGraphic(iv);
                        }
                    }
                } catch (Exception e) {
                }
                setContentDisplay(ContentDisplay.LEFT);
                setGraphicTextGap(10);
            }
        }
    }

    private void setupWindowDragging() {
        topHBox.setOnMousePressed(event -> {
            if (topHBox.getScene() != null && topHBox.getScene().getWindow() != null) {
                var stage = (Stage) topHBox.getScene().getWindow();
                topHBox.setUserData(new double[]{event.getScreenX() - stage.getX(), event.getScreenY() - stage.getY()});
            }
        });

        topHBox.setOnMouseDragged(event -> {
            if (topHBox.getScene() != null && topHBox.getScene().getWindow() != null) {
                var stage = (Stage) topHBox.getScene().getWindow();
                double[] offset = (double[]) topHBox.getUserData();
                if (offset != null) {
                    stage.setX(event.getScreenX() - offset[0]);
                    stage.setY(event.getScreenY() - offset[1]);
                    // Мгновенный апдейт
                    updateBrowserPosition();
                }
            }
        });
    }

    private void closeApp() {
        if (browserWindow != null) browserWindow.dispose();
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void handleCloseButtonAction() {
        closeApp();
    }
}