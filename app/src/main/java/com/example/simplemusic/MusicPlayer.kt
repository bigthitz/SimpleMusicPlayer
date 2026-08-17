package com.example.simplemusic

import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放状态
 */
enum class PlayState {
    IDLE, LOADING, PLAYING, PAUSED, ERROR
}

/**
 * 音乐播放器
 * 使用 Android MediaPlayer 实现流媒体播放
 */
object MusicPlayer {

    private const val TAG = "MusicPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── 状态 ──

    private val _playState = MutableStateFlow(PlayState.IDLE)
    val playState: StateFlow<PlayState> = _playState.asStateFlow()

    private val _currentSong = MutableStateFlow<SongInfo?>(null)
    val currentSong: StateFlow<SongInfo?> = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── 播放控制 ──

    /** 播放歌曲（自动获取URL） */
    suspend fun play(song: SongInfo) {
        _currentSong.value = song
        _playState.value = PlayState.LOADING
        _errorMessage.value = null

        // 获取播放 URL
        val urlResult = MusicApi.getSongUrl(song.hash)
        if (urlResult == null || urlResult.url.isEmpty()) {
            _playState.value = PlayState.ERROR
            _errorMessage.value = "获取播放地址失败"
            return
        }

        playUrl(urlResult.url, song)
    }

    /** 直接播放 URL */
    private fun playUrl(url: String, song: SongInfo) {
        release()

        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(url)
            setOnPreparedListener { mp ->
                _duration.value = mp.duration.toLong()
                _playState.value = PlayState.PLAYING
                mp.start()
                startProgressUpdater()
            }
            setOnCompletionListener {
                _playState.value = PlayState.IDLE
                stopProgressUpdater()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                _playState.value = PlayState.ERROR
                _errorMessage.value = "播放出错"
                stopProgressUpdater()
                true
            }
            prepareAsync()
        }
    }

    /** 暂停 */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playState.value = PlayState.PAUSED
                stopProgressUpdater()
            }
        }
    }

    /** 继续播放 */
    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && _playState.value == PlayState.PAUSED) {
                it.start()
                _playState.value = PlayState.PLAYING
                startProgressUpdater()
            }
        }
    }

    /** 切换播放/暂停 */
    fun togglePlayPause() {
        when (_playState.value) {
            PlayState.PLAYING -> pause()
            PlayState.PAUSED -> resume()
            else -> {}
        }
    }

    /** 跳转到指定位置 */
    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPosition.value = positionMs
    }

    /** 释放资源 */
    fun release() {
        stopProgressUpdater()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        _playState.value = PlayState.IDLE
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    // ── 进度更新 ──

    private fun startProgressUpdater() {
        stopProgressUpdater()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPosition.value = mp.currentPosition.toLong()
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
        progressJob = null
    }

    /** 格式化时长 */
    fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val min = seconds / 60
        val sec = seconds % 60
        return "%02d:%02d".format(min, sec)
    }
}