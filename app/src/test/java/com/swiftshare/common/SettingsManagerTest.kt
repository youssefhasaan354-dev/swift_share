package com.swiftshare.common

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = InMemoryEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class InMemoryEditor(private val backingMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearRequested) backingMap.clear()
                for ((k, v) in pending) {
                    if (v == null) backingMap.remove(k) else backingMap[k] = v
                }
            }
        }
    }

    @Before
    fun setUp() {
        SettingsManager.customPrefsForTest = InMemorySharedPreferences()
        SettingsManager.init(null)
    }

    @After
    fun tearDown() {
        SettingsManager.customPrefsForTest = null
    }

    @Test(timeout = 60000)
    fun testInitialDefaults() {
        assertEquals(ThemeMode.SYSTEM, SettingsManager.themeMode.value)
        assertTrue(SettingsManager.notificationsEnabled.value)
        assertTrue(SettingsManager.soundEnabled.value)
        assertTrue(SettingsManager.destinationPath.value.contains("SwiftShare"))
    }

    @Test(timeout = 60000)
    fun testThemeModePersistence() {
        SettingsManager.setThemeMode(null, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, SettingsManager.themeMode.value)

        SettingsManager.setThemeMode(null, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, SettingsManager.themeMode.value)

        SettingsManager.setThemeMode(null, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, SettingsManager.themeMode.value)
    }

    @Test(timeout = 60000)
    fun testNotificationAndSoundToggles() {
        SettingsManager.setNotificationsEnabled(null, false)
        assertFalse(SettingsManager.notificationsEnabled.value)

        SettingsManager.setSoundEnabled(null, false)
        assertFalse(SettingsManager.soundEnabled.value)

        SettingsManager.setNotificationsEnabled(null, true)
        assertTrue(SettingsManager.notificationsEnabled.value)

        SettingsManager.setSoundEnabled(null, true)
        assertTrue(SettingsManager.soundEnabled.value)
    }

    @Test(timeout = 60000)
    fun testDestinationPathUpdate() {
        val customFolder = tempFolder.newFolder("MyCustomDownloads")
        SettingsManager.setDestinationPath(null, customFolder.absolutePath)

        assertEquals(customFolder.absolutePath, SettingsManager.destinationPath.value)
        assertTrue(File(SettingsManager.destinationPath.value).exists())
    }
}

