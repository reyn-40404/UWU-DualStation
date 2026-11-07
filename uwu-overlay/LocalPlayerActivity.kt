package com.uwu.dualstation

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class LocalPlayerActivity : Activity() {
    private lateinit var libVlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var danmakuView: TextView
    private lateinit var timeView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var danmaku: List<Danmaku> = emptyList()
    private var speedIndex = 0
    private val speeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("1.0×", "1.25×", "1.5×", "2.0×")

    private data class Danmaku(val timeMs: Long, val text: String)

    private val ticker = object : Runnable {
        override fun run() {
            if (!::player.isInitialized) return
            val now = player.time.coerceAtLeast(0L)
            val duration = player.length.coerceAtLeast(0L)
            if (!dragging && duration > 0L) {
                seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar.progress = now.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            timeView.text = "${formatTime(now)} / ${formatTime(duration)}"
            danmakuView.text = danmaku.asSequence()
                .filter { kotlin.math.abs(it.timeMs - now) <= 1800L }
                .takeLast(5)
                .joinToString("\n") { it.text }
            playButton.text = if (player.isPlaying) "暫停" else "播放"
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)?.let(Uri::parse)
        if (videoUri == null) {
            finish()
            return
        }
        val danmakuUri = intent.getStringExtra(EXTRA_DANMAKU_URI)?.let(Uri::parse)
        danmaku = danmakuUri?.let { readDanmaku(it) }.orEmpty()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))

        danmakuView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            setPadding(20, 24, 20, 24)
        }
        root.addView(danmakuView, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 12)
            setBackgroundColor(0x99000000.toInt())
        }
        timeView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f }
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { dragging = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (seekBar != null) player.time = seekBar.progress.toLong()
                    dragging = false
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && seekBar != null) timeView.text = "${formatTime(progress.toLong())} / ${formatTime(player.length.coerceAtLeast(0L))}"
                }
            })
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playButton = Button(this).apply {
            text = "暫停"
            setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        }
        val speedButton = Button(this).apply {
            text = speedLabels[speedIndex]
            setOnClickListener {
                speedIndex = (speedIndex + 1) % speeds.size
                player.rate = speeds[speedIndex]
                text = speedLabels[speedIndex]
            }
        }
        val danmakuStatus = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            text = if (danmaku.isEmpty()) "未找到可讀取的 XML 彈幕" else "已載入 ${danmaku.size} 則 XML 彈幕"
            setPadding(10, 0, 0, 0)
        }
        buttons.addView(playButton)
        buttons.addView(speedButton)
        buttons.addView(danmakuStatus)
        controls.addView(timeView)
        controls.addView(seekBar)
        controls.addView(buttons)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        libVlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        player = MediaPlayer(libVlc)
        player.attachViews(videoLayout, null, false, false)
        val media = Media(libVlc, videoUri)
        player.media = media
        media.release()
        player.play()
        handler.post(ticker)
    }

    private fun readDanmaku(uri: Uri): List<Danmaku> {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "UTF-8")
                val result = mutableListOf<Danmaku>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && result.size < 20000) {
                    if (event == XmlPullParser.START_TAG && parser.name == "d") {
                        val seconds = parser.getAttributeValue(null, "p")
                            ?.substringBefore(",")?.toDoubleOrNull()
                        val content = parser.nextText().trim()
                        if (seconds != null && content.isNotEmpty()) {
                            result.add(Danmaku((seconds * 1000.0).toLong(), content.take(120)))
                        }
                    }
                    event = parser.next()
                }
                result.sortedBy { it.timeMs }
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return String.format("%d:%02d", total / 60L, total % 60L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::player.isInitialized) {
            player.stop()
            player.detachViews()
            player.release()
        }
        if (::libVlc.isInitialized) libVlc.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_URI = "com.uwu.dualstation.extra.VIDEO_URI"
        const val EXTRA_DANMAKU_URI = "com.uwu.dualstation.extra.DANMAKU_URI"
    }
}
