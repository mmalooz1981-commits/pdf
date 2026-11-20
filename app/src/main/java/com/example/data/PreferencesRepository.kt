package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "mawid_allawz_pdf_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RECENT_FILES = stringPreferencesKey("recent_files_json")
        val SAVED_SIGNATURES = stringPreferencesKey("saved_signatures_json")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val recentFilesFlow: Flow<List<RecentPdfRecord>> = context.dataStore.data.map { prefs ->
        parseRecentFiles(prefs[Keys.RECENT_FILES].orEmpty())
    }

    val savedSignaturesFlow: Flow<List<SavedSignatureRecord>> = context.dataStore.data.map { prefs ->
        parseSignatures(prefs[Keys.SAVED_SIGNATURES].orEmpty())
            .filter { File(it.filePath).exists() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun addRecentFile(record: RecentPdfRecord) {
        context.dataStore.edit { prefs ->
            val current = parseRecentFiles(prefs[Keys.RECENT_FILES].orEmpty())
                .filterNot { it.uriString == record.uriString }
            val updated = (listOf(record) + current).take(15)
            prefs[Keys.RECENT_FILES] = serializeRecentFiles(updated)
        }
    }

    suspend fun removeRecentFile(uriString: String) {
        context.dataStore.edit { prefs ->
            val updated = parseRecentFiles(prefs[Keys.RECENT_FILES].orEmpty())
                .filterNot { it.uriString == uriString }
            prefs[Keys.RECENT_FILES] = serializeRecentFiles(updated)
        }
    }

    suspend fun clearRecentFiles() {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECENT_FILES] = "[]"
        }
    }

    suspend fun addSavedSignature(record: SavedSignatureRecord) {
        context.dataStore.edit { prefs ->
            val current = parseSignatures(prefs[Keys.SAVED_SIGNATURES].orEmpty())
                .filterNot { it.id == record.id }
            val updated = (listOf(record) + current).take(20)
            prefs[Keys.SAVED_SIGNATURES] = serializeSignatures(updated)
        }
    }

    suspend fun deleteSavedSignature(id: String) {
        context.dataStore.edit { prefs ->
            val current = parseSignatures(prefs[Keys.SAVED_SIGNATURES].orEmpty())
            current.firstOrNull { it.id == id }?.let { rec ->
                runCatching { File(rec.filePath).delete() }
            }
            val updated = current.filterNot { it.id == id }
            prefs[Keys.SAVED_SIGNATURES] = serializeSignatures(updated)
        }
    }

    private fun parseRecentFiles(json: String): List<RecentPdfRecord> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                RecentPdfRecord(
                    uriString = obj.optString("uriString"),
                    displayName = obj.optString("displayName"),
                    sizeBytes = obj.optLong("sizeBytes"),
                    pageCount = obj.optInt("pageCount"),
                    lastOpenedMillis = obj.optLong("lastOpenedMillis")
                )
            }.filter { it.uriString.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun serializeRecentFiles(records: List<RecentPdfRecord>): String {
        val array = JSONArray()
        records.forEach { rec ->
            array.put(JSONObject().apply {
                put("uriString", rec.uriString)
                put("displayName", rec.displayName)
                put("sizeBytes", rec.sizeBytes)
                put("pageCount", rec.pageCount)
                put("lastOpenedMillis", rec.lastOpenedMillis)
            })
        }
        return array.toString()
    }

    private fun parseSignatures(json: String): List<SavedSignatureRecord> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                SavedSignatureRecord(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    filePath = obj.optString("filePath"),
                    createdAtMillis = obj.optLong("createdAtMillis")
                )
            }.filter { it.id.isNotBlank() && it.filePath.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun serializeSignatures(records: List<SavedSignatureRecord>): String {
        val array = JSONArray()
        records.forEach { rec ->
            array.put(JSONObject().apply {
                put("id", rec.id)
                put("name", rec.name)
                put("filePath", rec.filePath)
                put("createdAtMillis", rec.createdAtMillis)
            })
        }
        return array.toString()
    }
}
