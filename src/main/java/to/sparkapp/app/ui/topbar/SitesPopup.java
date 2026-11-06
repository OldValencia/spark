package to.sparkapp.app.ui.topbar;

import javafx.application.Platform;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Screen;
import to.sparkapp.app.config.AiConfiguration;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.dialogs.ProviderEditDialog;
import to.sparkapp.app.ui.topbar.components.AiDock;
import to.sparkapp.app.ui.topbar.components.ShellIcon;
import to.sparkapp.app.utils.UrlUtils;

import java.util.Locale;
import java.util.Comparator;

/** An owned native popup, so the list remains above the embedded browser. */
final class SitesPopup extends Popup {
    private final AiDock dock;
    private final AiConfiguration configuration;
    private final Stage owner;
    private final Runnable onChanged;
    private final AppPreferences preferences;
    private final TextField search = new TextField();
    private final ListView<AiConfiguration.AiConfig> list = new ListView<>();
    private final Label hint = new Label();
    private final VBox root = new VBox();
    private String draggedId;
    private double maxListHeight = 324;
    private String dropTargetId;
    private boolean dropAfter;
    private boolean preserveScroll;

    SitesPopup(Stage owner, AiDock dock, AiConfiguration configuration, AppPreferences preferences, Runnable onChanged) {
        this.owner = owner; this.dock = dock; this.configuration = configuration; this.onChanged = onChanged;
        this.preferences = preferences;
        setAutoHide(true); setHideOnEscape(true);
        root.getStyleClass().add("shell-popup");
        root.getStylesheets().add(getClass().getResource("/css/shell.css").toExternalForm());
        root.setPrefWidth(360);
        var heading = new Label("YOUR SITES");
        heading.getStyleClass().add("shell-hint");
        search.setPromptText("Find a site…"); search.setAccessibleText("Search sites");
        hint.getStyleClass().add("shell-hint"); hint.setWrapText(true);
        list.setFixedCellSize(54);
        list.setCellFactory(view -> new SiteCell());
        list.setPlaceholder(new Label("No matching sites"));
        list.setOnKeyPressed(e -> {
            var selected = list.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && selected != null) { choose(selected); e.consume(); }
        });
        var add = new Button("Add any website", ShellIcon.create("plus"));
        add.getStyleClass().add("shell-row"); add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> edit(null));
        root.getChildren().addAll(heading, search, hint, list, add);
        getContent().add(root);
        search.textProperty().addListener((o, before, text) -> refresh());
        setOnShowing(e -> { search.clear(); refresh(); });
        setOnShown(e -> search.requestFocus());
        setOnHidden(e -> { draggedId = null; resetPreview(); });
        list.setOnDragExited(e -> {
            if (!list.getBoundsInLocal().contains(list.sceneToLocal(e.getSceneX(), e.getSceneY()))) resetPreview();
        });
        list.setOnDragOver(e -> {
            if (draggedId == null || !search.getText().isBlank()) return;
            e.acceptTransferModes(TransferMode.MOVE); previewReorder(e.getSceneY());
            var position = list.sceneToLocal(e.getSceneX(), e.getSceneY());
            int target = -1;
            for (int i = 0; i < list.getItems().size(); i++) if (list.getItems().get(i).id().equals(dropTargetId)) target = i;
            if (target >= 0 && position.getY() < 30) list.scrollTo(Math.max(0, target - 1));
            else if (target >= 0 && position.getY() > list.getHeight() - 30) list.scrollTo(Math.min(list.getItems().size() - 1, target + 1));
            e.consume();
        });
        list.setOnDragDropped(e -> {
            if (draggedId == null || dropTargetId == null) return;
            var id = draggedId; var target = dropTargetId; boolean after = dropAfter;
            draggedId = null; resetPreview();
            preserveScroll = true;
            try { dock.reorder(id, target, after); } finally { preserveScroll = false; }
            e.setDropCompleted(true); e.consume();
        });
        dock.setOnOrderChanged(this::refresh);
    }

    void open(Node anchor) {
        if (isShowing()) { hide(); return; }
        root.setPrefWidth(Math.min(360, Math.max(280, owner.getWidth() - 20)));
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            var screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1).stream().findFirst().orElse(Screen.getPrimary());
            maxListHeight = Math.max(54, Math.min(324, screen.getVisualBounds().getHeight() - 180));
            show(owner, Math.min(bounds.getMinX(), owner.getX() + owner.getWidth() - root.getPrefWidth() - 8), bounds.getMaxY() + 8);
        }
    }

    private void refresh() {
        var bar = list.lookupAll(".scroll-bar").stream().filter(n -> n instanceof ScrollBar b && b.getOrientation() == Orientation.VERTICAL)
                .map(n -> (ScrollBar) n).findFirst().orElse(null);
        double scroll = bar == null ? 0 : bar.getValue();
        boolean restore = preserveScroll;
        var query = search.getText().trim().toLowerCase(Locale.ROOT);
        var filtered = dock.getDockItems().stream().map(item -> item.getConfig())
                .filter(site -> (site.name() + " " + site.url()).toLowerCase(Locale.ROOT).contains(query)).toList();
        list.setItems(FXCollections.observableArrayList(filtered));
        list.setPrefHeight(Math.min(maxListHeight, Math.max(60, filtered.size() * 54)));
        hint.setText(query.isEmpty() ? "Drag a grip to reorder · Alt + ↑ / ↓ on a grip" : "Clear search to reorder all sites");
        if (restore && bar != null) Platform.runLater(() -> bar.setValue(scroll));
    }

    private void choose(AiConfiguration.AiConfig site) { dock.selectSite(site.id()); hide(); }

    private void previewReorder(double sceneY) {
        var cells = list.lookupAll(".list-cell").stream().filter(n -> n instanceof SiteCell)
                .map(n -> (SiteCell) n).filter(cell -> !cell.isEmpty() && cell.getItem() != null)
                .sorted(Comparator.comparingInt(SiteCell::getIndex)).toList();
        if (cells.isEmpty()) return;
        SiteCell target = cells.getLast();
        dropAfter = true;
        for (var cell : cells) {
            double originalTop = cell.localToScene(0, 0).getY() - cell.getTranslateY();
            if (sceneY < originalTop + cell.getHeight() / 2) { target = cell; dropAfter = false; break; }
        }
        dropTargetId = target.getItem().id();
        int from = -1;
        for (int i = 0; i < list.getItems().size(); i++) if (list.getItems().get(i).id().equals(draggedId)) from = i;
        if (from < 0) return;
        int to = target.getIndex() + (dropAfter ? 1 : 0);
        if (to > from) to--;
        for (var cell : cells) {
            int i = cell.getIndex(); double shift = 0;
            if (from < to && i > from && i <= to) shift = -list.getFixedCellSize();
            else if (from > to && i >= to && i < from) shift = list.getFixedCellSize();
            cell.animateTo(shift);
            boolean isTarget = cell.getItem().id().equals(dropTargetId);
            cell.pseudoClassStateChanged(cell.before, isTarget && !dropAfter);
            cell.pseudoClassStateChanged(cell.after, isTarget && dropAfter);
        }
    }

    private void resetPreview() {
        dropTargetId = null;
        list.lookupAll(".list-cell").stream().filter(n -> n instanceof SiteCell).map(n -> (SiteCell) n).forEach(SiteCell::resetPosition);
    }

    private void edit(AiConfiguration.AiConfig site) {
        hide();
        var dialog = new ProviderEditDialog(owner, site);
        dialog.showAndWait();
        if (!dialog.isConfirmed()) return;
        if (!dialog.isDeleted() && site != null && !site.url().equals(dialog.getProviderUrl())) {
            var warning = new Alert(Alert.AlertType.CONFIRMATION,
                    "Changing the address replaces this site's page. Its unsent text will be lost.", ButtonType.CANCEL, ButtonType.OK);
            warning.initOwner(owner); warning.setHeaderText("Change site address?");
            if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        var name = dialog.getProviderName(); var url = dialog.getProviderUrl();
        var color = dialog.getProviderColor(); var auto = dialog.isUseIconColor();
        var manual = dialog.isManualColor();
        var remove = dialog.isDeleted();
        var task = new Thread(() -> {
            try {
                var manager = configuration.getCustomProvidersManager();
                if (remove && site != null) manager.deleteProvider(site.id());
                else if (site == null) manager.addCustomProvider(name, url, color);
                else manager.updateProvider(site.id(), name, url, color, auto, manual);
                Platform.runLater(onChanged);
            } catch (RuntimeException error) {
                Platform.runLater(() -> {
                    var alert = new Alert(Alert.AlertType.ERROR, "Could not save this site. Please try again.");
                    alert.initOwner(owner); alert.show();
                });
            }
        }, "site-save");
        task.setDaemon(true); task.start();
    }

    private final class SiteCell extends ListCell<AiConfiguration.AiConfig> {
        private final PseudoClass before = PseudoClass.getPseudoClass("drop-before");
        private final PseudoClass after = PseudoClass.getPseudoClass("drop-after");
        private Timeline movement;
        private double targetOffset;

        void animateTo(double offset) {
            if (targetOffset == offset) return;
            targetOffset = offset;
            if (movement != null) movement.stop();
            if (preferences.isReducedMotion()) { setTranslateY(offset); return; }
            movement = new Timeline(new KeyFrame(Duration.millis(150), new KeyValue(translateYProperty(), offset, Interpolator.EASE_BOTH)));
            movement.play();
        }

        void resetPosition() {
            if (movement != null) movement.stop();
            targetOffset = 0; setTranslateY(0); setOpacity(1); clearMarker();
        }

        private void clearMarker() { pseudoClassStateChanged(before, false); pseudoClassStateChanged(after, false); }

        @Override protected void updateItem(AiConfiguration.AiConfig site, boolean empty) {
            super.updateItem(site, empty); resetPosition();
            if (empty || site == null) { setGraphic(null); setText(null); return; }
            var grip = new Button("⠿"); grip.getStyleClass().add("shell-button");
            grip.setUserData(site.id());
            grip.setAccessibleText("Reorder " + site.name()); grip.setDisable(!search.getText().isBlank());
            grip.setOnDragDetected(e -> {
                draggedId = site.id();
                var board = grip.startDragAndDrop(TransferMode.MOVE);
                board.setDragView(getGraphic().snapshot(null, null), e.getX(), e.getY());
                setOpacity(0);
                var content = new ClipboardContent(); content.putString(site.id()); board.setContent(content); e.consume();
            });
            grip.setOnDragDone(e -> { draggedId = null; resetPreview(); });
            grip.setOnKeyPressed(e -> {
                if (!e.isAltDown() || (e.getCode() != KeyCode.UP && e.getCode() != KeyCode.DOWN)) return;
                int index = getIndex(), target = index + (e.getCode() == KeyCode.UP ? -1 : 1);
                if (target >= 0 && target < list.getItems().size()) {
                    dock.reorder(site.id(), list.getItems().get(target).id(), target > index);
                    list.getSelectionModel().select(target); list.scrollTo(target);
                    Platform.runLater(() -> list.lookupAll(".shell-button").stream()
                            .filter(node -> site.id().equals(node.getUserData())).findFirst().ifPresent(Node::requestFocus));
                }
                e.consume();
            });
            var name = new Label(site.name() + (site.id().equals(dock.getSelectedId()) ? "  ✓" : ""));
            name.getStyleClass().add("shell-site-name"); name.setMaxWidth(210);
            var domain = new Label(UrlUtils.host(site.url())); domain.setMaxWidth(210); domain.getStyleClass().add("shell-site-domain");
            var meta = new VBox(3, name, domain); meta.setMinWidth(0);
            var select = new Button(); select.getStyleClass().add("shell-row");
            var image = site.icon() == null ? null : AiDock.ICON_CACHE.get(site.icon());
            Node icon;
            if (image != null && image.getWidth() > 1) {
                var view = new ImageView(image); view.setFitWidth(20); view.setFitHeight(20); view.setPreserveRatio(true); icon = view;
            } else icon = new Label("↗");
            var content = new HBox(10, icon, meta); content.setAlignment(Pos.CENTER_LEFT);
            select.setGraphic(content); select.setMaxWidth(Double.MAX_VALUE); select.setMinWidth(0);
            select.setAccessibleText("Open " + site.name()); select.setOnAction(e -> choose(site));
            var edit = new Button(); edit.setGraphic(ShellIcon.create("pencil-square")); edit.getStyleClass().add("shell-button");
            edit.setAccessibleText("Edit " + site.name()); edit.setOnAction(e -> edit(site));
            var row = new HBox(3, grip, select, edit); row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(select, Priority.ALWAYS);
            setGraphic(row);
        }
    }
}
