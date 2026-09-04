package io.github.angad7600123.cambio.currency

import java.util.Currency
import java.util.Locale

/**
 * Resolves a currency code into displayable [CurrencyInfo].
 *
 * Names, symbols and minor-unit counts come from the platform's own ISO 4217 data
 * (`java.util.Currency`) rather than a table maintained in this repository. That
 * keeps the data correct as the platform updates it, and means names are already
 * localised to the device.
 *
 * A code the platform does not recognise still resolves — to a fallback whose name
 * is the code itself — so an unfamiliar currency appearing in an API response can
 * never crash or silently vanish from the picker.
 */
class CurrencyCatalog(private val locale: Locale = Locale.getDefault()) {
    private val cache = HashMap<String, CurrencyInfo>()

    fun infoFor(code: String): CurrencyInfo {
        val normalized = code.uppercase(Locale.ROOT)
        return cache.getOrPut(normalized) { resolve(normalized) }
    }

    /** Resolves many codes at once, sorted alphabetically by code. */
    fun infoForAll(codes: Collection<String>): List<CurrencyInfo> = codes.map(::infoFor).sortedBy { it.code }

    private fun resolve(code: String): CurrencyInfo {
        val platform = runCatching { Currency.getInstance(code) }.getOrNull()

        return CurrencyInfo(
            code = code,
            displayName = platform?.getDisplayName(locale)?.takeIf { it != code } ?: code,
            symbol = platform?.getSymbol(locale) ?: code,
            // getDefaultFractionDigits() returns -1 for pseudo-currencies such as XDR;
            // treat those as 2 so amounts still render sensibly.
            minorUnits = platform?.defaultFractionDigits?.takeIf { it >= 0 } ?: DEFAULT_MINOR_UNITS,
            flag = flagFor(code),
        )
    }

    /**
     * Derives a flag emoji from the currency code.
     *
     * ISO 4217 codes are built from the ISO 3166 country code plus a currency
     * letter, so the first two characters map directly onto the regional-indicator
     * symbols that render as a flag. Codes with no country behind them are handled
     * by [FLAGLESS_PREFIXES] and by [SPECIAL_FLAGS].
     */
    private fun flagFor(code: String): String {
        SPECIAL_FLAGS[code]?.let { return it }
        if (code.length < 2) return ""
        val region = code.take(2).uppercase(Locale.ROOT)
        if (region.any { it !in 'A'..'Z' }) return ""
        if (region[0] in FLAGLESS_PREFIXES) return ""

        return buildString {
            region.forEach { appendCodePoint(REGIONAL_INDICATOR_BASE + (it - 'A')) }
        }
    }

    private companion object {
        const val DEFAULT_MINOR_UNITS = 2

        /** Unicode REGIONAL INDICATOR SYMBOL LETTER A. */
        const val REGIONAL_INDICATOR_BASE = 0x1F1E6

        /**
         * `X`-prefixed codes are supranational (XAF, XOF, XDR, and the metals XAU
         * and XAG). They have no country, so no flag.
         */
        val FLAGLESS_PREFIXES = setOf('X')

        /** Codes whose first two letters are not the country that issues them. */
        val SPECIAL_FLAGS = mapOf(
            "EUR" to "🇪🇺", // EU
            "GBP" to "🇬🇧", // GB
            "CHF" to "🇨🇭", // CH
            "ANG" to "🇨🇼", // CW (Netherlands Antillean guilder)
            "AED" to "🇦🇪", // AE
            "ZAR" to "🇿🇦", // ZA
            "DKK" to "🇩🇰", // DK
            "CZK" to "🇨🇿", // CZ
            "SEK" to "🇸🇪", // SE
        )
    }
}
