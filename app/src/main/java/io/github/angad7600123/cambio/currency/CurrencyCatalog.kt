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
 *
 * [supplementedName] covers the handful of codes the platform's data serves badly.
 * It is a *fallback*, not an override: a device that names a currency properly, and
 * in the user's own language, keeps its own answer.
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
            displayName = nameFor(code, platform),
            symbol = platform?.getSymbol(locale) ?: code,
            // getDefaultFractionDigits() returns -1 for pseudo-currencies such as XDR;
            // treat those as 2 so amounts still render sensibly.
            minorUnits = platform?.defaultFractionDigits?.takeIf { it >= 0 } ?: DEFAULT_MINOR_UNITS,
            flag = flagFor(code),
        )
    }

    /** Looks up both platform names and lets [supplementedName] decide between them. */
    private fun nameFor(code: String, platform: Currency?): String = supplementedName(
        code = code,
        platformName = platform?.getDisplayName(locale)?.takeIf { it != code },
        twinName = INDISTINCT_FROM[code]?.let { twin ->
            runCatching { Currency.getInstance(twin).getDisplayName(locale) }.getOrNull()
        },
    )

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

        /**
         * Codes whose platform name may be word-for-word another currency's.
         *
         * Offshore and onshore renminbi are the same money in two markets separated
         * by capital controls, so they share a name but not a rate — and a picker
         * offering "Chinese Yuan" twice, at two different rates, is unusable.
         */
        val INDISTINCT_FROM = mapOf("CNH" to "CNY")

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

/**
 * Chooses the best name for a currency, given what the platform offered.
 *
 * Prefers the platform's name, which is localised and kept current by the system.
 * Falls back to [SUPPLEMENTARY_NAMES] in the two cases where it is not usable, both
 * of which were seen on a real device:
 *
 * - the platform has **no** name, so the code was rendered as its own title. Six
 *   codes in the rate feed are outside ISO 4217 entirely.
 * - the platform's name is **word for word** another currency's. Older platform data
 *   calls CNH plain "Chinese Yuan", exactly as it calls CNY, putting two rows with
 *   different rates under one identical name.
 *
 * Separated from the catalog so both branches can be tested. The desktop JDK used by
 * unit tests does not know CNH at all, so a test going through the platform could
 * only ever reach the first case — and the second is the one that was reported.
 *
 * @param platformName what the platform calls it, or null if it has no name for it.
 * @param twinName the name of the currency this one may be confused with, if any.
 */
internal fun supplementedName(code: String, platformName: String?, twinName: String?): String {
    if (platformName != null && platformName != twinName) return platformName
    return SUPPLEMENTARY_NAMES[code] ?: platformName ?: code
}

/**
 * Names for codes the platform's own data serves badly.
 *
 * English only, and deliberately so: this is reached only where the platform
 * offered nothing usable, and a name in the wrong language beats a bare code
 * or two identical rows. Everything else stays localised by the system.
 *
 * The six non-ISO codes are all pegged one-to-one to a neighbour — the Crown
 * Dependency pounds to sterling, the Pacific dollars to the Australian
 * dollar, the Faroese króna to the Danish — which is why ISO 4217 never gave
 * them codes and the platform has nothing to say about them.
 */
private val SUPPLEMENTARY_NAMES = mapOf(
    "CNH" to "Chinese Yuan (offshore)",
    "FOK" to "Faroese Króna",
    "GGP" to "Guernsey Pound",
    "IMP" to "Isle of Man Pound",
    "JEP" to "Jersey Pound",
    "KID" to "Kiribati Dollar",
    "TVD" to "Tuvaluan Dollar",
)
