package to.sparkapp.app.ui.tutorial;

import javafx.scene.Node;

/**
 * One stop of the tour.
 *
 * @param target         the control the step talks about
 * @param primary        label of the main button: it goes on, or finishes on the last step
 * @param secondary      label of the second button, which ends the tour, or {@code null}
 * @param opensShortcut  whether the main button opens the shortcut settings after the tour
 */
record TutorialStep(Node target, String title, String text, String primary, String secondary,
                    boolean opensShortcut) {
}
