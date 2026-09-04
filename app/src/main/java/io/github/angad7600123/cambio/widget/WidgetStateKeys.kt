package io.github.angad7600123.cambio.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Keys for the widget's own scratch state.
 *
 * The widget keeps only the expression it is currently showing. Everything else it
 * needs — the selected currency pair and the cached rate table — comes from the
 * same repositories the app uses, so the two stay in step: change a currency in
 * the app and the widget follows.
 */
internal object WidgetStateKeys {
    val EXPRESSION: Preferences.Key<String> = stringPreferencesKey("widget_expression")
    val JUST_EVALUATED: Preferences.Key<String> = stringPreferencesKey("widget_just_evaluated")
    val ERROR: Preferences.Key<String> = stringPreferencesKey("widget_error")
}
