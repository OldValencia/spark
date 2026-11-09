package to.sparkapp.app.windows;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.support.JavaFxTestRuntime;

@Tag("native")
@Timeout(20)
class TraySiteMenuTest {
    @Test
    void listsSitesInTabOrderAndOpensTheChosenOne() throws Exception {
        JavaFxTestRuntime.start();
        var opened = new LinkedBlockingQueue<String>();
        var menu = new TraySiteMenu(opened::add);

        assertFalse(menu.isEnabled(), "Nothing to open before the sites are known");

        menu.showSites(List.of(site("claude", "Claude"), site("chatgpt", "ChatGPT")));
        menu.showSites(List.of(site("gemini", "Gemini"), site("claude", "Claude"), site("chatgpt", "ChatGPT")));

        assertTrue(menu.isEnabled());
        assertEquals(3, menu.getItemCount(), "Refreshing replaces the items");
        assertEquals("Gemini", menu.getItem(0).getLabel());
        assertEquals("ChatGPT", menu.getItem(2).getLabel());

        var claude = menu.getItem(1);
        claude.getActionListeners()[0].actionPerformed(new ActionEvent(claude, ActionEvent.ACTION_PERFORMED, ""));

        assertEquals("claude", opened.poll(5, TimeUnit.SECONDS));

        menu.showSites(List.of());
        assertFalse(menu.isEnabled());
    }

    private static AiConfig site(String id, String name) {
        return new AiConfig(id, name, "https://example.com/" + id, null, null);
    }
}
