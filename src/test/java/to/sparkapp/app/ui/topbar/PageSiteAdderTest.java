package to.sparkapp.app.ui.topbar;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import to.sparkapp.app.browser.PageNames;

class PageSiteAdderTest {
    private static final String ADDRESS = "https://www.rp.pl/polityka/art123-some-article";
    private static final String ARTICLE = "Sejm votes on the budget after a long night of debate";

    @Test
    void theNameASiteGivesItselfComesFirst() {
        var names = new PageNames(ARTICLE + " - rp.pl", "Rzeczpospolita");

        assertEquals("Rzeczpospolita", PageSiteAdder.suggestedName(names, ADDRESS));
    }

    @Test
    void withoutASiteNameTheEndOfTheTitleNamesTheSite() {
        assertEquals("Example News", name(ARTICLE + " | Example News"));
        assertEquals("Google Accounts", name("Sign in - Google Accounts"));
        assertEquals("Example News", name(ARTICLE + " — Example News"));
    }

    @Test
    void aShortTitleWithoutSeparatorsIsUsedAsItIs() {
        assertEquals("Example Docs", name("  Example Docs "));
    }

    @Test
    void otherwiseTheHostNamesTheSite() {
        assertEquals("rp.pl", name(ARTICLE));
        assertEquals("rp.pl", name(""));
        assertEquals("rp.pl", name(ADDRESS), "A page without a title reports its address instead");
        assertEquals("rp.pl", PageSiteAdder.suggestedName(new PageNames(ARTICLE, "  "), ADDRESS));
    }

    private static String name(String title) {
        return PageSiteAdder.suggestedName(new PageNames(title, ""), ADDRESS);
    }
}
