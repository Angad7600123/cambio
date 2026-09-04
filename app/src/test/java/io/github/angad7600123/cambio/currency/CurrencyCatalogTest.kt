package io.github.angad7600123.cambio.currency

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for currency metadata resolution.
 *
 * The catalog reads the platform's ISO 4217 data, so these assert the mapping and
 * the fallback behaviour rather than a hand-maintained table of names.
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
}
