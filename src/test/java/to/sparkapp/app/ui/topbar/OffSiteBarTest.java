package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.support.JavaFxTestRuntime;
import to.sparkapp.app.ui.webview.OffSitePage;

@Tag("native")
@Timeout(20)
class OffSiteBarTest {
    @Test
    void theBarShowsWhereThePageIsAndLeadsBackToTheSite() throws Exception {
        JavaFxTestRuntime.call(() -> {
            var page = new SimpleObjectProperty<OffSitePage>();
            var returns = new AtomicInteger();
            var added = new AtomicReference<OffSitePage>();
            var bar = new OffSiteBar(page, returns::incrementAndGet, added::set);

            assertFalse(bar.isVisible());
            assertFalse(bar.isManaged(), "The page keeps its full height while it is on its site");

            page.set(new OffSitePage("ChatGPT", "https://www.docs.example.org/guide?id=7"));

            assertTrue(bar.isVisible());
            assertEquals("docs.example.org", address(bar));
            assertFalse(button(bar, "Open in browser").isDisabled());

            button(bar, "Add as a site").fire();

            assertEquals(page.get(), added.get(), "The page on screen is offered as a new site");

            var back = button(bar, "Back to ChatGPT");
            back.fire();

            assertEquals(1, returns.get());

            page.set(new OffSitePage("ChatGPT", null));

            assertEquals("Page opened by ChatGPT", address(bar));
            assertTrue(button(bar, "Open in browser").isDisabled(), "A page without an address cannot be opened");
            assertTrue(button(bar, "Add as a site").isDisabled());

            page.set(null);

            assertFalse(bar.isVisible());
            return null;
        });
    }

    private static String address(OffSiteBar bar) {
        return ((Label) bar.lookup(".off-site-address")).getText();
    }

    private static Button button(OffSiteBar bar, String name) {
        return bar.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> name.equals(button.getAccessibleText()) || name.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }
}
