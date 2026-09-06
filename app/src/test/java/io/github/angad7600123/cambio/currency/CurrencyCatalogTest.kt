package io.github.angad7600123.cambio.currency

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for currency metadata resolution.
 *
 * The catalog reads the platform's ISO 4217 data, so these assert the mapping and
 * the fallback behaviour rather than a hand-maintained table of names. The
 * supplementary table is the exception, and is tested for being a *fallback* — it
 * must fill gaps without displacing a name the platform already gives.
 */
class CurrencyCatalogTest {

    private val catalog = CurrencyCatalog(Locale.US)

    @Test
    fun `resolves a well-known currency`() {
        val usd = catalog.infoFor("USD")
        assertEquals("USD", usd.code)
        assertEquals(2, usd.minorUnits)
        assertFalse(usd.isFallback)
        assertTrue(usd.displayName.isNotBlank())
    }

    @Test
    fun `knows currencies with no minor units`() = assertEquals(0, catalog.infoFor("JPY").minorUnits)

    @Test
    fun `knows currencies with three minor units`() = assertEquals(3, catalog.infoFor("KWD").minorUnits)

    @Test
    fun `an unknown code resolves to a fallback instead of throwing`() {
        val unknown = catalog.infoFor("ZZZ")
        assertEquals("ZZZ", unknown.code)
        assertEquals("ZZZ", unknown.displayName)
        assertTrue(unknown.isFallback)
        // A sane default so amounts still render.
        assertEquals(2, unknown.minorUnits)
    }

    @Test
    fun `codes are normalised to upper case`() = assertEquals("USD", catalog.infoFor("usd").code)

    @Test
    fun `derives a flag from the country prefix`() {
        // US -> regional indicators U+1F1FA U+1F1F8
        assertEquals("🇺🇸", catalog.infoFor("USD").flag)
        assertEquals("🇮🇳", catalog.infoFor("INR").flag)
    }

    @Test
    fun `uses the special mapping for the euro`() = assertEquals("🇪🇺", catalog.infoFor("EUR").flag)

    @Test
    fun `supranational codes get no flag rather than a nonsense one`() {
        // XAF, XOF and the metals have no country behind them.
        assertEquals("", catalog.infoFor("XAF").flag)
        assertEquals("", catalog.infoFor("XAU").flag)
        assertEquals("", catalog.infoFor("XDR").flag)
    }

    @Test
    fun `resolves an entire rate table without failing on any code`() {
        // The real API returns ~166 codes; none of them may throw.
        val codes = listOf(
            "USD", "EUR", "INR", "JPY", "KWD", "GBP", "ANG", "XCD", "BTN",
            "CLF", "MRU", "STN", "VES", "ZWL", "ZZZ", "", "A",
        )
        val resolved = catalog.infoForAll(codes)
        assertEquals(codes.size, resolved.size)
    }

    @Test
    fun `results are sorted by code`() {
        val resolved = catalog.infoForAll(listOf("USD", "AED", "INR"))
        assertEquals(listOf("AED", "INR", "USD"), resolved.map { it.code })
    }

    @Test
    fun `repeated lookups return the cached instance`() = assertTrue(catalog.infoFor("USD") === catalog.infoFor("USD"))

    // region Supplementary names

    @Test
    fun `codes missing from ISO 4217 still get a real name`() {
        // These six are in the rate feed but not in ISO 4217, so the platform has no
        // name for them and they rendered with their own code as their title.
        listOf("FOK", "GGP", "IMP", "JEP", "KID", "TVD").forEach { code ->
            val info = catalog.infoFor(code)
            assertNotEquals(code, info.displayName, "$code fell back to its own code")
            assertFalse(info.isFallback, "$code should not be a fallback")
        }
    }

    @Test
    fun `offshore renminbi is distinguishable from onshore`() {
        // The reported bug: two rows reading "Chinese Yuan", at two different rates.
        assertNotEquals(catalog.infoFor("CNY").displayName, catalog.infoFor("CNH").displayName)
    }

    @Test
    fun `offshore renminbi is still recognisably the yuan`() {
        assertTrue(catalog.infoFor("CNH").displayName.contains("Yuan", ignoreCase = true))
    }

    @Test
    fun `the pegged issues keep the flag of their own territory`() {
        assertEquals("🇯🇪", catalog.infoFor("JEP").flag)
        assertEquals("🇮🇲", catalog.infoFor("IMP").flag)
        assertEquals("🇹🇻", catalog.infoFor("TVD").flag)
    }

    @Test
    fun `the pegged issues get their neighbour's minor units`() {
        // All six track a two-decimal currency, which the default already gives them.
        listOf("FOK", "GGP", "IMP", "JEP", "KID", "TVD").forEach {
            assertEquals(2, catalog.infoFor(it).minorUnits, it)
        }
    }

    @Test
    fun `a supplementary name never displaces the platform's own`() {
        // The table is a fallback. A device that names a currency itself — localised,
        // and kept current by the system — must keep its own answer.
        val platformName = java.util.Currency.getInstance("CNY").getDisplayName(Locale.US)
        assertEquals(platformName, catalog.infoFor("CNY").displayName)
    }

    @Test
    fun `an unknown code with no supplementary name is still a fallback`() {
        assertTrue(catalog.infoFor("QQQ").isFallback)
    }

    // endregion

    // region Name choice
    //
    // Exercised directly rather than through the catalog. The desktop JDK these tests
    // run on does not know CNH at all, so going through the platform can only ever
    // reach the "no name" branch — and the collision branch is the reported bug.

    @Test
    fun `a platform name is preferred over the supplementary one`() {
        assertEquals(
            "Chinese Yuan (offshore)",
            supplementedName("CNH", platformName = "Chinese Yuan (offshore)", twinName = "Chinese Yuan"),
        )
    }

    @Test
    fun `a localised platform name is kept rather than replaced with English`() {
        assertEquals(
            "yuan chinois (extraterritorial)",
            supplementedName("CNH", platformName = "yuan chinois (extraterritorial)", twinName = "yuan chinois"),
        )
    }

    @Test
    fun `a platform name identical to its twin's is replaced`() {
        // Exactly the device state that was reported: two rows both reading
        // "Chinese Yuan", at rates 0.39% apart.
        assertEquals(
            "Chinese Yuan (offshore)",
            supplementedName("CNH", platformName = "Chinese Yuan", twinName = "Chinese Yuan"),
        )
    }

    @Test
    fun `a missing platform name falls back to the supplementary one`() {
        assertEquals("Jersey Pound", supplementedName("JEP", platformName = null, twinName = null))
    }

    @Test
    fun `a code with neither a platform nor a supplementary name keeps the code`() {
        assertEquals("ZZZ", supplementedName("ZZZ", platformName = null, twinName = null))
    }

    @Test
    fun `an ordinary currency is untouched by the twin check`() {
        assertEquals("US Dollar", supplementedName("USD", platformName = "US Dollar", twinName = null))
    }

    @Test
    fun `a collision with no supplementary name keeps the platform name`() {
        // Better a duplicated name than none: the code beneath it still separates them.
        assertEquals("Some Dollar", supplementedName("AAA", platformName = "Some Dollar", twinName = "Some Dollar"))
    }

    // endregion
}
