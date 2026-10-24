package com.swiftshare.webshare

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingAuthRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val clientIp: String,
    val userAgent: String,
    val pinEntered: String? = null,
    val onApprove: () -> Unit,
    val onReject: () -> Unit
)

class WebAuthManager {

    companion object {
        const val COOKIE_NAME = "swiftshare_session"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 60_000L // 60 seconds
    }

    private val secureRandom = SecureRandom()

    // Current 6-digit PIN shown on phone
    private val _currentPin = MutableStateFlow(generateSixDigitPin())
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    // One-time QR token
    private val _qrToken = MutableStateFlow(UUID.randomUUID().toString())
    val qrToken: StateFlow<String> = _qrToken.asStateFlow()

    // Authorized session cookies -> creation timestamp
    private val authorizedSessions = ConcurrentHashMap<String, Long>()

    // IP -> Pair(failedCount, lockoutUntilTimestamp)
    private val ipRateLimits = ConcurrentHashMap<String, Pair<Int, Long>>()

    // StateFlow for the phone UI to show "Allow this device?" dialog
    private val _pendingAuthRequest = MutableStateFlow<PendingAuthRequest?>(null)
    val pendingAuthRequest: StateFlow<PendingAuthRequest?> = _pendingAuthRequest.asStateFlow()

    fun refreshPin(): String {
        val newPin = generateSixDigitPin()
        _currentPin.value = newPin
        return newPin
    }

    fun refreshQrToken(): String {
        val token = UUID.randomUUID().toString()
        _qrToken.value = token
        return token
    }

    private fun generateSixDigitPin(): String {
        val pin = 100_000 + secureRandom.nextInt(900_000)
        return pin.toString()
    }

    fun isIpLockedOut(clientIp: String): Boolean {
        val entry = ipRateLimits[clientIp] ?: return false
        val (attempts, lockoutUntil) = entry
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            return true
        }
        if (lockoutUntil in 1..now) {
            // Lockout expired, reset attempts
            ipRateLimits.remove(clientIp)
            return false
        }
        return attempts >= MAX_FAILED_ATTEMPTS
    }

    fun recordFailedAttempt(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        val current = ipRateLimits[clientIp]
        val attempts = (current?.first ?: 0) + 1
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            ipRateLimits[clientIp] = Pair(attempts, now + LOCKOUT_DURATION_MS)
            return true // is now locked out
        } else {
            ipRateLimits[clientIp] = Pair(attempts, 0L)
            return false
        }
    }

    fun resetRateLimit(clientIp: String) {
        ipRateLimits.remove(clientIp)
    }

    fun createAuthorizedSession(): String {
        val sessionId = UUID.randomUUID().toString()
        authorizedSessions[sessionId] = System.currentTimeMillis()
        return sessionId
    }

    fun isValidSession(cookieValue: String?): Boolean {
        if (cookieValue.isNullOrBlank()) return false
        return authorizedSessions.containsKey(cookieValue)
    }

    fun verifyPin(clientPin: String?): Boolean {
        if (clientPin.isNullOrBlank()) return false
        return clientPin.trim() == _currentPin.value.trim()
    }

    fun verifyQrToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return token.trim() == _qrToken.value.trim()
    }

    fun requestDeviceApproval(
        clientIp: String,
        userAgent: String,
        pin: String? = null,
        onApproved: () -> Unit,
        onRejected: () -> Unit
    ) {
        _pendingAuthRequest.value = PendingAuthRequest(
            clientIp = clientIp,
            userAgent = userAgent,
            pinEntered = pin,
            onApprove = {
                _pendingAuthRequest.value = null
                onApproved()
            },
            onReject = {
                _pendingAuthRequest.value = null
                onRejected()
            }
        )
    }

    fun clearPendingRequest() {
        _pendingAuthRequest.value = null
    }

    fun revokeSession(sessionId: String) {
        authorizedSessions.remove(sessionId)
    }

    fun clearAllSessions() {
        authorizedSessions.clear()
        ipRateLimits.clear()
        _pendingAuthRequest.value = null
    }
}
