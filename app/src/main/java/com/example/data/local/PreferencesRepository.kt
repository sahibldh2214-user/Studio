package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SortOrder
import com.example.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_tv_prefs")

data class VaultUiPreferences(
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val studioSplitPane: Boolean = true,
    val transferPort: Int = 8787,
    val transferPin: String = "4829"
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val STUDIO_SPLIT = booleanPreferencesKey("studio_split_pane")
        val TRANSFER_PORT = intPreferencesKey("transfer_port")
        val TRANSFER_PIN = stringPreferencesKey("transfer_pin")
    }

    val preferencesFlow: Flow<VaultUiPreferences> = context.vaultPrefsDataStore.data.map { prefs ->
        val viewMode = runCatching {
            ViewMode.valueOf(prefs[Keys.VIEW_MODE] ?: ViewMode.GRID.name)
        }.getOrDefault(ViewMode.GRID)
        val sortOrder = runCatching {
            SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: SortOrder.NAME_ASC.name)
        }.getOrDefault(SortOrder.NAME_ASC)
        VaultUiPreferences(
            viewMode = viewMode,
            sortOrder = sortOrder,
            studioSplitPane = prefs[Keys.STUDIO_SPLIT] ?: true,
            transferPort = prefs[Keys.TRANSFER_PORT] ?: 8787,
            transferPin = prefs[Keys.TRANSFER_PIN] ?: "4829"
        )
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.vaultPrefsDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.vaultPrefsDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun setStudioSplitPane(enabled: Boolean) {
        context.vaultPrefsDataStore.edit { it[Keys.STUDIO_SPLIT] = enabled }
    }

    suspend fun setTransferPin(pin: String) {
        context.vaultPrefsDataStore.edit { it[Keys.TRANSFER_PIN] = pin }
    }
}
