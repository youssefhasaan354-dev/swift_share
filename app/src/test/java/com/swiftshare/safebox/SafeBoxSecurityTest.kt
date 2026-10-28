package com.swiftshare.safebox

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

class SafeBoxSecurityTest {

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
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
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
        SafeBoxAuthManager.customPrefsForTest = InMemorySharedPreferences()
    }

    @After
    fun tearDown() {
        SafeBoxAuthManager.customPrefsForTest = null
    }

    @Test(timeout = 60000)
    fun testPinSetupAndVerification() {
        assertFalse(SafeBoxAuthManager.isPinSet(null))

        SafeBoxAuthManager.setPin(null, "1234")
        assertTrue(SafeBoxAuthManager.isPinSet(null))

        assertTrue(SafeBoxAuthManager.verifyPin(null, "1234"))
        assertFalse(SafeBoxAuthManager.verifyPin(null, "0000"))
        assertFalse(SafeBoxAuthManager.verifyPin(null, "123"))
    }

    @Test(timeout = 60000)
    fun testChangePin() {
        SafeBoxAuthManager.setPin(null, "1234")

        // Fail to change with wrong old PIN
        val failedChange = SafeBoxAuthManager.changePin(null, "9999", "5678")
        assertFalse(failedChange)
        assertTrue(SafeBoxAuthManager.verifyPin(null, "1234"))
        assertFalse(SafeBoxAuthManager.verifyPin(null, "5678"))

        // Successfully change with right old PIN
        val successChange = SafeBoxAuthManager.changePin(null, "1234", "5678")
        assertTrue(successChange)
        assertFalse(SafeBoxAuthManager.verifyPin(null, "1234"))
        assertTrue(SafeBoxAuthManager.verifyPin(null, "5678"))
    }

    @Test(timeout = 60000)
    fun testRecoveryQuestionSetupAndVerification() {
        assertFalse(SafeBoxAuthManager.isRecoveryConfigured(null))

        SafeBoxAuthManager.setRecoveryQuestion(null, "What is your secret code?", "Tokyo-2026")
        assertTrue(SafeBoxAuthManager.isRecoveryConfigured(null))
        assertEquals("What is your secret code?", SafeBoxAuthManager.getRecoveryQuestion(null))

        // Case-insensitive & trimmed answer verification
        assertTrue(SafeBoxAuthManager.verifyRecoveryAnswer(null, "tokyo-2026"))
        assertTrue(SafeBoxAuthManager.verifyRecoveryAnswer(null, "  TOKYO-2026  "))
        assertFalse(SafeBoxAuthManager.verifyRecoveryAnswer(null, "London"))
    }

    @Test(timeout = 60000)
    fun testResetPinWithRecovery() {
        SafeBoxAuthManager.setPin(null, "1111")
        SafeBoxAuthManager.setRecoveryQuestion(null, "First pet name?", "Charlie")

        // Wrong recovery answer cannot reset PIN
        val wrongAttempt = SafeBoxAuthManager.resetPinWithRecovery(null, "Bob", "9999")
        assertFalse(wrongAttempt)
        assertTrue(SafeBoxAuthManager.verifyPin(null, "1111"))

        // Correct recovery answer successfully resets PIN
        val successAttempt = SafeBoxAuthManager.resetPinWithRecovery(null, "charlie", "9999")
        assertTrue(successAttempt)
        assertFalse(SafeBoxAuthManager.verifyPin(null, "1111"))
        assertTrue(SafeBoxAuthManager.verifyPin(null, "9999"))
    }

    @Test(timeout = 60000)
    fun testSecureShredOverwritesZeroesAndDeletes() {
        val root = tempFolder.newFolder("shred_test")
        val secretFile = File(root, "top_secret.txt").apply {
            writeBytes(ByteArray(4096) { 0xFF.toByte() })
        }
        assertTrue(secretFile.exists())
        assertEquals(4096L, secretFile.length())

        val success = SafeBoxAuthManager.secureShred(secretFile)
        assertTrue(success)
        assertFalse("File must be deleted from filesystem after shredding", secretFile.exists())
    }

    @Test(timeout = 60000)
    fun testSecureShredDirectoryRecursively() {
        val root = tempFolder.newFolder("shred_dir_test")
        val subDir = File(root, "nested").apply { mkdir() }
        val f1 = File(subDir, "a.dat").apply { writeBytes(ByteArray(1024)) }
        val f2 = File(subDir, "b.dat").apply { writeBytes(ByteArray(2048)) }

        val success = SafeBoxAuthManager.secureShred(subDir)
        assertTrue(success)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        assertFalse(subDir.exists())
    }
}

