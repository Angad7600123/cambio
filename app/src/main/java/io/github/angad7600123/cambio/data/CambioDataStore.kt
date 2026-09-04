package io.github.angad7600123.cambio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single [DataStore] instance backing everything the app persists: the cached
 * rate table, the selected currency pair, theme settings and calculation history.
 *
 * One store keeps reads consistent and means the widget and the activity observe
 * exactly the same state, so a currency changed in one appears in the other.
 */
private const val DATA_STORE_NAME = "cambio"

val Context.cambioDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
