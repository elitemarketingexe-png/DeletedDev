package com.unshoo.pixelmusic.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val PLAYER_STYLE_MODE = stringPreferencesKey("player_style_mode_v1")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val ALBUM_ART_COLOR_ACCURACY = intPreferencesKey("album_art_color_accuracy_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val COLOR_PALETTE_PREFERENCE = stringPreferencesKey("color_palette_preference")
        val APP_FONT_PREFERENCE = stringPreferencesKey("app_font_preference")
        val PITCH_BLACK_ENABLED = booleanPreferencesKey("pitch_black_enabled")
    }

    val appThemeModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM
    }

    val pitchBlackEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.PITCH_BLACK_ENABLED] ?: false
    }

    val appFontModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_FONT_PREFERENCE] ?: AppFontMode.APP_DEFAULT
    }

    val playerThemePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.ALBUM_ART
    }

    val playerStyleModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_STYLE_MODE] ?: PlayerStyleMode.GRADIENT
    }

    val colorPalettePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.COLOR_PALETTE_PREFERENCE] ?: "SAGE"
    }

    val albumArtPaletteStyleFlow: Flow<AlbumArtPaletteStyle> = dataStore.data.map { preferences ->
        AlbumArtPaletteStyle.fromStorageKey(preferences[Keys.ALBUM_ART_PALETTE_STYLE])
    }

    val albumArtColorAccuracyFlow: Flow<Int> = dataStore.data.map { preferences ->
        AlbumArtColorAccuracy.clamp(preferences[Keys.ALBUM_ART_COLOR_ACCURACY] ?: AlbumArtColorAccuracy.DEFAULT)
    }



    suspend fun setAppFontMode(fontMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_FONT_PREFERENCE] = fontMode
        }

    suspend fun setPlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
        }

    suspend fun setPlayerStyleMode(styleMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_STYLE_MODE] = styleMode
        }

    suspend fun setAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_THEME_MODE] = themeMode
        }

    suspend fun setColorPalettePreference(palette: String) =
        dataStore.edit { preferences ->
            preferences[Keys.COLOR_PALETTE_PREFERENCE] = palette
        }

    suspend fun initializeAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_THEME_MODE] == null) {
                preferences[Keys.APP_THEME_MODE] = themeMode
            }
        }

    suspend fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        }

    suspend fun setAlbumArtColorAccuracy(level: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(level)
        }

    suspend fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) = dataStore.edit { preferences ->
        preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(accuracyLevel)
    }



    suspend fun setPitchBlackEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.PITCH_BLACK_ENABLED] = enabled
        }
}
