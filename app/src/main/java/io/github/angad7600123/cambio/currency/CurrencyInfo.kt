package io.github.angad7600123.cambio.currency

/**
 * Display metadata for one currency.
 *
 * @property code the ISO 4217 alphabetic code, e.g. `USD`.
 * @property displayName localised full name, e.g. "US Dollar".
 * @property symbol localised symbol, e.g. `$`. Falls back to [code] when the
 *   platform has no symbol for it.
 * @property minorUnits number of decimal places the currency is normally quoted
 *   to: 2 for USD, 0 for JPY, 3 for KWD.
 * @property flag regional-indicator flag emoji, or an empty string when the code
 *   has no country (such as the `X`-prefixed supranational codes).
 */
data class CurrencyInfo(
    val code: String,
    val displayName: String,
    val symbol: String,
    val minorUnits: Int,
    val flag: String,
) {
    /** True when the platform had no ISO 4217 record and we fell back to the code. */
    val isFallback: Boolean get() = displayName == code
}
