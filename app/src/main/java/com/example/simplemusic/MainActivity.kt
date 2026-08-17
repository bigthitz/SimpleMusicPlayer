package com.example.simplemusic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etApiUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var rvSongs: RecyclerView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvNowPlaying: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val songAdapter = SongAdapter()
    private val playlist = mutableListOf<SongInfo>()
    private var currentIndex = -1
    private var isSeeking = false
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        observePlayer()
        setupListeners()
    }

    private fun initViews() {
        etApiUrl = findViewById(R.id.etApiUrl)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        rvSongs = findViewById(R.id.rvSongs)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvDuration = findViewById(R.id.tvDuration)
        seekBar = findViewById(R.id.seekBar)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        rvSongs.layoutManager = LinearLayoutManager(this)
        rvSongs.adapter = songAdapter
    }

    private fun observePlayer() {
        lifecycleScope.launch {
            MusicPlayer.playState.collectLatest { state ->
                when (state) {
                    PlayState.PLAYING -> btnPlayPause.text = "⏸"
                    PlayState.PAUSED -> btnPlayPause.text = "▶"
                    PlayState.LOADING -> btnPlayPause.text = "…"
                    else -> btnPlayPause.text = "▶"
                }
            }
        }

        lifecycleScope.launch {
            MusicPlayer.currentSong.collectLatest { song ->
                tvNowPlaying.text = if (song != null) {
                    "${song.title} - ${song.author}"
                } else {
                    "未播放"
                }
            }
        }

        lifecycleScope.launch {
            MusicPlayer.currentPosition.collectLatest { pos ->
                if (!isSeeking) {
                    tvCurrentTime.text = MusicPlayer.formatTime(pos)
                }
            }
        }

        lifecycleScope.launch {
            MusicPlayer.duration.collectLatest { dur ->
                tvDuration.text = MusicPlayer.formatTime(dur)
                if (dur > 0) {
                    seekBar.max = dur.toInt()
                }
            }
        }

        lifecycleScope.launch {
            MusicPlayer.errorMessage.collectLatest { error ->
                if (error != null) {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        // 连接 API
        btnConnect.setOnClickListener {
            val baseUrl = etApiUrl.text.toString().trim().trimEnd('/')
            if (baseUrl.isEmpty()) {
                Toast.makeText(this, "请输入 API 地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectApi(baseUrl)
        }

        // 搜索
        btnSearch.setOnClickListener { performSearch() }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        // 播放/暂停
        btnPlayPause.setOnClickListener {
            if (MusicPlayer.playState.value == PlayState.IDLE && playlist.isNotEmpty()) {
                lifecycleScope.launch {
                    MusicPlayer.play(playlist[0])
                    currentIndex = 0
                }
            } else {
                MusicPlayer.togglePlayPause()
            }
        }

        // 上一首
        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                lifecycleScope.launch { MusicPlayer.play(playlist[currentIndex]) }
            }
        }

        // 下一首
        btnNext.setOnClickListener {
            if (currentIndex < playlist.size - 1) {
                currentIndex++
                lifecycleScope.launch { MusicPlayer.play(playlist[currentIndex]) }
            }
        }

        // 进度条
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = MusicPlayer.formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                isSeeking = false
                MusicPlayer.seekTo(seekBar.progress.toLong())
            }
        })

        // 进度条实时更新
        lifecycleScope.launch {
            MusicPlayer.currentPosition.collectLatest { pos ->
                if (!isSeeking) {
                    seekBar.progress = pos.toInt()
                }
            }
        }
    }

    private fun connectApi(baseUrl: String) {
        tvStatus.text = "连接中..."
        lifecycleScope.launch {
            ApiClient.apiBaseUrl = baseUrl
            val result = ApiClient.registerDevice()
            if (result.isSuccess) {
                tvStatus.text = "已连接 | dfid: ${ApiClient.device.dfid}"
                Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
            } else {
                tvStatus.text = "连接失败: ${result.exceptionOrNull()?.message}"
                Toast.makeText(this@MainActivity, "连接失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSearch() {
        val keyword = etSearch.text.toString().trim()
        if (keyword.isEmpty()) return
        if (ApiClient.apiBaseUrl.isEmpty()) {
            Toast.makeText(this, "请先连接 API", Toast.LENGTH_SHORT).show()
            return
        }

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val result = MusicApi.search(keyword)
            if (result != null) {
                val songs = result.lists ?: result.list ?: emptyList()
                playlist.clear()
                playlist.addAll(songs)
                songAdapter.submitList(songs)
                currentIndex = -1
            } else {
                Toast.makeText(this@MainActivity, "搜索失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 歌曲列表 Adapter ──

    inner class SongAdapter : RecyclerView.Adapter<SongAdapter.ViewHolder>() {

        private var songs = listOf<SongInfo>()

        fun submitList(list: List<SongInfo>) {
            songs = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(songs[position])
        }

        override fun getItemCount(): Int = songs.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            private val tvArtist: TextView = view.findViewById(R.id.tvArtist)
            private val tvDuration: TextView = view.findViewById(R.id.tvDuration)

            fun bind(song: SongInfo) {
                tvTitle.text = song.title
                tvArtist.text = song.author
                tvDuration.text = MusicPlayer.formatTime(song.duration * 1000L)
                itemView.setOnClickListener {
                    val idx = bindingAdapterPosition
                    if (idx != RecyclerView.NO_POSITION) {
                        currentIndex = idx
                        lifecycleScope.launch { MusicPlayer.play(song) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.release()
    }
}