package to.sparkapp.app.ui.dialogs.components;

import java.util.function.Consumer;

import javafx.scene.control.Button;

public final class ProviderRemovalButton extends Button {
    private boolean awaitingConfirmation;

    public ProviderRemovalButton(Runnable confirmRemoval, Consumer<String> showWarning) {
        super("Remove site");
        getStyleClass().add("quiet-button");
        setOnAction(event -> {
            if (awaitingConfirmation) {
                confirmRemoval.run();

                return;
            }

            awaitingConfirmation = true;
            setText("Confirm removal");
            showWarning.accept("This removes the site and its unsent text. Click again to confirm.");
        });
    }
}
