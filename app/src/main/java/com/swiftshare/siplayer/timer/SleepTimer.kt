package com.swiftshare.siplayer.timer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SleepTimer(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {

    private var timerJob: Job? = null
    private var onFinishCallback: (() -> Unit)? = null

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _stopAtEndOfSong = MutableStateFlow(false)
    val stopAtEndOfSong: StateFlow<Boolean> = _stopAtEndOfSong.asStateFlow()

    fun start(minutes: Int, onFinish: () -> Unit) {
        cancel()
        if (minutes <= 0) return

        onFinishCallback = onFinish
        _stopAtEndOfSong.value = false
        _remainingSeconds.value = minutes * 60
        _isTimerRunning.value = true

        timerJob = scope.launch {
            try {
                while (isActive && _remainingSeconds.value > 0) {
                    delay(1000)
                    _remainingSeconds.value -= 1
                }
                if (_remainingSeconds.value <= 0) {
                    _isTimerRunning.value = false
                    val cb = onFinishCallback
                    onFinishCallback = null
                    cb?.invoke()
                }
            } catch (_: CancellationException) {
                // Expected cancellation
            }
        }
    }

    fun startEndOfSong(onFinish: () -> Unit) {
        cancel()
        onFinishCallback = onFinish
        _stopAtEndOfSong.value = true
        _isTimerRunning.value = true
    }

    fun notifySongCompleted() {
        if (_stopAtEndOfSong.value && _isTimerRunning.value) {
            val cb = onFinishCallback
            cancel()
            cb?.invoke()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        onFinishCallback = null
        _remainingSeconds.value = 0
        _isTimerRunning.value = false
        _stopAtEndOfSong.value = false
    }
}
