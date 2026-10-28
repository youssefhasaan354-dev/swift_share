package com.swiftshare.safebox

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom

object SafeBoxAuthManager {
    private const val PREFS_NAME = "swiftshare_safebox_auth"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_RECOVERY_QUESTION = "recovery_question"
    private const val KEY_RECOVERY_SALT = "recovery_salt"
    private const val KEY_RECOVERY_HASH = "recovery_hash"

    // Optional injected prefs for unit tests
    var customPrefsForTest: SharedPreferences? = null

    private fun getPrefs(context: Context?): SharedPreferences {
        customPrefsForTest?.let { return it }
        requireNotNull(context) { "Context cannot be null when customPrefsForTest is not set" }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPinSet(context: Context?): Boolean {
        return getPrefs(context).contains(KEY_PIN_HASH)
    }

    fun setPin(context: Context?, pin: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val saltHex = bytesToHex(salt)
        val hash = hashText(pin, saltHex)
        getPrefs(context).edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun verifyPin(context: Context?, pin: String): Boolean {
        val prefs = getPrefs(context)
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val computedHash = hashText(pin, saltHex)
        return savedHash == computedHash
    }

    fun changePin(context: Context?, oldPin: String, newPin: String): Boolean {
        if (!verifyPin(context, oldPin)) return false
        setPin(context, newPin)
        return true
    }

    fun setRecoveryQuestion(context: Context?, question: String, answer: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val saltHex = bytesToHex(salt)
        val hash = hashText(answer.trim().lowercase(), saltHex)
        getPrefs(context).edit()
            .putString(KEY_RECOVERY_QUESTION, question.trim())
            .putString(KEY_RECOVERY_SALT, saltHex)
            .putString(KEY_RECOVERY_HASH, hash)
            .apply()
    }

    fun getRecoveryQuestion(context: Context?): String? {
        return getPrefs(context).getString(KEY_RECOVERY_QUESTION, null)
    }

    fun isRecoveryConfigured(context: Context?): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_RECOVERY_QUESTION) && prefs.contains(KEY_RECOVERY_HASH)
    }

    fun verifyRecoveryAnswer(context: Context?, answer: String): Boolean {
        val prefs = getPrefs(context)
        val saltHex = prefs.getString(KEY_RECOVERY_SALT, null) ?: return false
        val savedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        val computedHash = hashText(answer.trim().lowercase(), saltHex)
        return savedHash == computedHash
    }

    fun resetPinWithRecovery(context: Context?, answer: String, newPin: String): Boolean {
        if (!verifyRecoveryAnswer(context, answer)) return false
        setPin(context, newPin)
        return true
    }

    /**
     * Performs secure zero-overwrite shredding before deleting the file from disk.
     */
    fun secureShred(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    secureShred(child)
                }
            }
            return file.delete()
        }

        return try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val zeroBlock = ByteArray(64 * 1024)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBlock.size.toLong(), length - written).toInt()
                        raf.write(zeroBlock, 0, toWrite)
                        written += toWrite
                    }
                    raf.fd.sync()
                }
            }
            file.delete()
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun hashText(text: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray())
        val digest = md.digest(text.toByteArray())
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun canAuthenticateBiometrics(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun promptBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SafeBox Vault")
            .setSubtitle("Confirm your fingerprint or face to open")
            .setNegativeButtonText("Use PIN")
            .build()

        prompt.authenticate(promptInfo)
    }
}
