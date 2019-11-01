package tc.planeta.audiotracktest

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.util.SparseArray
import android.widget.TextView


class MainActivity : Activity() {

    private val handler = Handler()

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var isInitializing = false

    @Volatile
    private var bytesPerSecond = 0

    private var counter = 0

    private val data = SparseArray<ByteArray>()

    private var thread: Thread? = null

    private lateinit var textView: TextView

    private val lock = Object()

    private val reinitRunnable = Runnable {
        reinitAudioTrack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.text)
    }

    override fun onResume() {
        super.onResume()

        reinitAudioTrack()

        if (audioTrack != null) {
            startDataWriteThread()
        }
    }

    private fun startDataWriteThread() {
        thread = Thread {
            try {
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        break
                    }
                    synchronized(lock) {
                        if (isInitializing) {
                            lock.wait()
                        }

                        val bytes = getBytes()

                        audioTrack?.write(bytes, 0, bytes.size)
                    }
                }
            } catch (e: Throwable) {
            }
        }.also {
            it.start()
        }
    }

    override fun onPause() {
        super.onPause()

        thread?.interrupt()
        counter = 0
        handler.removeCallbacks(reinitRunnable)
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        audioTrack?.let {
            println("Releasing AudioTrack")
            it.flush()
            it.release()
        }
    }

    private fun getBytes(): ByteArray {
        var bytes = data.get(bytesPerSecond)

        if (bytes == null) {
            bytes = ByteArray(bytesPerSecond) {
                (Math.random() * Byte.MAX_VALUE).toByte()
            }
            data.put(bytesPerSecond, bytes)
        }

        return bytes
    }

    private fun initAudioTrack(outputChannelConfig: Int) {
        counter++

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setChannelMask(outputChannelConfig)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .build()

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            outputChannelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also {
            val state = it.state

            if (state == AudioTrack.STATE_INITIALIZED) {
                it.play()
                textView.text = "AudioTrack create counter: $counter"

                handler.postDelayed(reinitRunnable, INTERVAL)
            } else {
                textView.text = "AudioTrack create failed: $counter (state: $state)"
                thread?.interrupt()
                it.release()
            }
        }
    }

    private fun reinitAudioTrack() {
        isInitializing = true

        synchronized(lock) {
            releaseAudioTrack()

            // bytesPerSecond = (sample rate) * (bytes per sample) * (number of channels)
            if (counter % 2 == 0) {
                println("Initializing AudioTrack with 2 channels")
                bytesPerSecond = SAMPLE_RATE * 2 * 2
                initAudioTrack(
                    AudioFormat.CHANNEL_OUT_FRONT_LEFT or
                            AudioFormat.CHANNEL_OUT_FRONT_RIGHT
                )
            } else {
                println("Initializing AudioTrack with 6 channels")
                bytesPerSecond = SAMPLE_RATE * 2 * 6
                initAudioTrack(
                    AudioFormat.CHANNEL_OUT_FRONT_LEFT or
                            AudioFormat.CHANNEL_OUT_FRONT_RIGHT or
                            AudioFormat.CHANNEL_OUT_FRONT_CENTER or
                            AudioFormat.CHANNEL_OUT_LOW_FREQUENCY or
                            AudioFormat.CHANNEL_OUT_BACK_LEFT or
                            AudioFormat.CHANNEL_OUT_BACK_RIGHT
                )
            }
            isInitializing = false
            lock.notifyAll()
        }
    }

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val INTERVAL = 3000L
    }
}