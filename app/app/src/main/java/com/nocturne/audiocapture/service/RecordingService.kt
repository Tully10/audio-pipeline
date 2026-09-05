package com.nocturne.audiocapture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.nocturne.audiocapture.MainActivity
import com.nocturne.audiocapture.data.AppDatabase
import com.nocturne.audiocapture.data.ChunkEntity
import com.nocturne.audiocapture.upload.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.format.DateTimeFormatter

class RecordingService : Service() {

    companion object {
        const val TAG = "RecordingService"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE = "com.nocturne.audiocapture.ACTION_PAUSE"
        const val ACTION_RESUME = "com.nocturne.audiocapture.ACTION_RESUME"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    @Volatile private var cachedPendingCount = 0
    @Volatile private var chunkOffsetSeconds = 0.0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentChunkFile: File? = null
    private var currentChunkOutputStream: FileOutputStream? = null
    private var currentChunkSeq = 0
    private var chunkStartTimeMs = 0L
    private var chunkDataSize = 0
    private val currentMarkers = JSONArray()

    private lateinit var prefs: SharedPreferences
    private lateinit var uploadManager: UploadManager
    private lateinit var db: AppDatabase
    private lateinit var audioManager: AudioManager

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { lockToBuiltInMic() }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE -> { isPaused = true; updateNotification() }
                ACTION_RESUME -> { isPaused = false; updateNotification() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        db = AppDatabase.getInstance(this)
        uploadManager = UploadManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            headsetReceiver, IntentFilter("com.nocturne.audiocapture.HEADSET_CHANGED")
        )
        ContextCompat.registerReceiver(
            this, controlReceiver,
            IntentFilter().apply { addAction(ACTION_PAUSE); addAction(ACTION_RESUME) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { isPaused = true; updateNotification(); return START_STICKY }
            ACTION_RESUME -> { isPaused = false; updateNotification(); return START_STICKY }
        }
        if (!isRecording) {
            startForeground(NOTIFICATION_ID, buildNotification())
            startRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun insertMarker() {
        synchronized(currentMarkers) {
            currentMarkers.put(JSONObject().apply {
                put("offset_s", chunkOffsetSeconds)
                put("label", "marked")
            })
        }
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT).coerceAtLeast(8192)
        audioRecord = try {
            AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        } catch (e: Exception) {
            Log.w(TAG, "UNPROCESSED unavailable, falling back")
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        }
        lockToBuiltInMic()
        audioRecord!!.startRecording()
        isRecording = true
        openNewChunk()

        val chunkLengthMs = prefs.getInt("chunk_length_s", 60) * 1000L
        val buffer = ByteArray(bufferSize)
        recordingThread = Thread({
            while (isRecording) {
                if (isPaused) { Thread.sleep(100); continue }
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    currentChunkOutputStream?.write(buffer, 0, read)
                    chunkDataSize += read
                    chunkOffsetSeconds = (System.currentTimeMillis() - chunkStartTimeMs) / 1000.0
                }
                if (System.currentTimeMillis() - chunkStartTimeMs >= chunkLengthMs) {
                    closeAndEnqueueChunk()
                    openNewChunk()
                }
            }
            closeAndEnqueueChunk()
        }, "RecordingThread").also { it.start() }
    }

    private fun lockToBuiltInMic() {
        audioRecord?.preferredDevice = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    private fun openNewChunk() {
        val dir = File(filesDir, "chunks").apply { mkdirs() }
        val file = File(dir, "chunk_${System.currentTimeMillis()}_${currentChunkSeq}.wav")
        currentChunkFile = file
        currentChunkOutputStream = FileOutputStream(file).also { writeWavHeader(it) }
        chunkStartTimeMs = System.currentTimeMillis()
        chunkDataSize = 0
        chunkOffsetSeconds = 0.0
        synchronized(currentMarkers) { while (currentMarkers.length() > 0) currentMarkers.remove(0) }
    }

    private fun closeAndEnqueueChunk() {
        val file = currentChunkFile ?: return
        currentChunkOutputStream?.let { it.flush(); it.close() }
        updateWavHeader(file, chunkDataSize)
        val recordedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(chunkStartTimeMs))
        val markersJson = synchronized(currentMarkers) { currentMarkers.toString() }
        val seq = currentChunkSeq++
        serviceScope.launch {
            db.chunkDao().insert(ChunkEntity(
                filePath = file.absolutePath, chunkSeq = seq,
                recordedAt = recordedAt, status = "PENDING", markers = markersJson
            ))
            cachedPendingCount = db.chunkDao().getPendingCount()
            updateNotification()
            uploadManager.enqueueNow()
        }
    }

    private fun writeWavHeader(os: FileOutputStream) {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()); buf.putInt(0)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()); buf.putInt(16)
        buf.putShort(1); buf.putShort(1)
        buf.putInt(SAMPLE_RATE); buf.putInt(SAMPLE_RATE * 2)
        buf.putShort(2); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(0)
        os.write(buf.array())
    }

    private fun updateWavHeader(file: File, dataSize: Int) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4); raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize + 36).array())
                raf.seek(40); raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array())
            }
        } catch (e: Exception) { Log.e(TAG, "WAV header update failed", e) }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Ambient audio capture" }
        )
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val mainPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val (label, serviceIntent) = if (isPaused)
            "Resume" to Intent(this, RecordingService::class.java).setAction(ACTION_RESUME)
        else
            "Pause" to Intent(this, RecordingService::class.java).setAction(ACTION_PAUSE)
        val togglePi = PendingIntent.getService(this, 1, serviceIntent, flags)
        val text = if (isPaused) "⏸ Paused" else "● Recording · $cachedPendingCount chunks pending"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Audio Capture").setContentText(text)
            .setContentIntent(mainPi)
            .addAction(NotificationCompat.Action(0, label, togglePi))
            .setOngoing(true).setSilent(true).build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop(); audioRecord?.release()
        recordingThread?.join(2000)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(headsetReceiver)
        unregisterReceiver(controlReceiver)
        super.onDestroy()
    }
}
