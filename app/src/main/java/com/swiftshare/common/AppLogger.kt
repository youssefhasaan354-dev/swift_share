package com.swiftshare.common

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppLogger {
    private const val DEFAULT_TAG = "SwiftShare"

    private val _userVisibleErrors = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val userVisibleErrors: SharedFlow<String> = _userVisibleErrors.asSharedFlow()

    fun d(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Exception) {
            println("DEBUG [$tag]: $message")
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: Exception) {
            println("INFO [$tag]: $message")
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            Log.w(tag, message, throwable)
        } catch (_: Exception) {
            println("WARN [$tag]: $message, error=${throwable?.message}")
        }
    }

    fun e(
        tag: String = DEFAULT_TAG,
        message: String,
        throwable: Throwable? = null,
        notifyUser: Boolean = false
    ) {
        try {
            Log.e(tag, message, throwable)
        } catch (_: Exception) {
            println("ERROR [$tag]: $message, error=${throwable?.message}")
        }

        if (notifyUser) {
            val userMsg = if (message.isNotBlank()) message else (throwable?.localizedMessage ?: "Unknown error occurred")
            _userVisibleErrors.tryEmit(userMsg)
        }
    }
}
