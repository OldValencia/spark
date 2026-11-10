package to.sparkapp.app.ui.dialogs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class ShellMessageDialogConfirmTest {
    @Test
    void aQuestionRunsItsActionOnlyFromItsMainButtonAndEnterDoesNotPressIt() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 300, 200));
            owner.show();
            var confirmed = new AtomicInteger();
            var dialog = new ShellMessageDialog(owner, "Sign out of Claude?", "Cancel")
                    .text("Clear the cookies and website storage of Claude.")
                    .confirm("Sign out", confirmed::incrementAndGet);

            try {
                var buttons = dialog.getScene().getRoot().lookupAll(".button").stream()
                        .map(Button.class::cast)
                        .toList();
                var cancel = buttons.stream().filter(button -> button.getText().equals("Cancel")).findFirst()
                        .orElseThrow();
                var signOut = buttons.stream().filter(button -> button.getText().equals("Sign out")).findFirst()
                        .orElseThrow();

                assertTrue(signOut.getStyleClass().contains("primary-button"));
                assertTrue(cancel.isCancelButton(), "Escape cancels");
                assertTrue(buttons.stream().noneMatch(Button::isDefaultButton), "Enter does not sign out");

                cancel.fire();

                assertEquals(0, confirmed.get());

                signOut.fire();

                assertEquals(1, confirmed.get());
            } finally {
                dialog.close();
                owner.close();
            }

            return null;
        });
    }
}
