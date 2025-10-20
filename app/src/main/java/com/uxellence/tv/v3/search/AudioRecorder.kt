package com.uxellence.tv.v3.search

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio Recorder for capturing microphone input and saving to WAV file
 *
 * Specs:
 * - Sample Rate: 16kHz (optimal for speech recognition)
 * - Channels: Mono
 * - Encoding: 16-bit PCM
 * - Format: WAV (RIFF header + PCM data)
 *
 * Usage:
 * ```kotlin
 * val recorder = AudioRecorder(File(context.cacheDir, "audio.wav"))
 * recorder.start()
 * // ... record for some time ...
 * recorder.stop()
 * ```
 */
class AudioRecorder(private val outputFile: File) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false

    /**
     * Callback for real-time amplitude updates (0-32767)
     * Called approximately every 100ms during recording
     */
    var onAmplitudeUpdate: ((Int) -> Unit)? = null

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
        private const val AMPLITUDE_UPDATE_INTERVAL_MS = 100 // Update UI every 100ms
    }

    /**
     * Start recording audio
     *
     * @throws SecurityException if RECORD_AUDIO permission not granted
     * @throws IllegalStateException if already recording
     */
    fun start() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                writeAudioDataToFile(bufferSize)
            }.apply {
                name = "AudioRecorderThread"
                start()
            }

            Log.d(TAG, "Recording started to: ${outputFile.absolutePath}")

        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
        }
    }

    /**
     * Stop recording and finalize WAV file
     */
    fun stop() {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return
        }

        isRecording = false

        try {
            recordingThread?.join(1000) // Wait max 1 second
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            val fileSizeBytes = outputFile.length()
            val fileSizeKb = fileSizeBytes / 1024
            val durationSeconds = fileSizeBytes / (SAMPLE_RATE * BYTES_PER_SAMPLE).toFloat()

            Log.d(TAG, "Recording stopped successfully")
            Log.d(TAG, "  File: ${outputFile.name}")
            Log.d(TAG, "  Size: $fileSizeBytes bytes ($fileSizeKb KB)")
            Log.d(TAG, "  Duration: ~${String.format("%.1f", durationSeconds)}s")
            Log.d(TAG, "  Sample Rate: ${SAMPLE_RATE}Hz")
            Log.d(TAG, "  Channels: Mono")
            Log.d(TAG, "  Encoding: 16-bit PCM")

            if (fileSizeBytes < 5000) {
                Log.w(TAG, "WARNING: Very short recording (<5KB, ~0.3s)")
            }

            if (fileSizeBytes == 0L) {
                Log.e(TAG, "ERROR: Empty audio file created!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * Write audio data to file with WAV header
     */
    private fun writeAudioDataToFile(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var tempFile: File? = null
        var lastAmplitudeUpdate = System.currentTimeMillis()

        try {
            // Write raw PCM data to temp file first
            tempFile = File.createTempFile("audio_raw_", ".pcm", outputFile.parentFile)
            FileOutputStream(tempFile).use { fos ->
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        fos.write(buffer, 0, read)

                        // Calculate and report amplitude every 100ms
                        val now = System.currentTimeMillis()
                        if (now - lastAmplitudeUpdate >= AMPLITUDE_UPDATE_INTERVAL_MS) {
                            val amplitude = calculateRMS(buffer, read)
                            Log.d(TAG, "Amplitude: $amplitude (callback: ${onAmplitudeUpdate != null})")
                            onAmplitudeUpdate?.invoke(amplitude)
                            lastAmplitudeUpdate = now
                        }
                    }
                }
            }

            // Now convert PCM to WAV (add RIFF header)
            convertPcmToWav(tempFile, outputFile)

        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Calculate RMS (Root Mean Square) amplitude from audio buffer
     *
     * @param buffer PCM audio data (16-bit samples)
     * @param size Number of bytes to analyze
     * @return Amplitude value (0-32767)
     */
    private fun calculateRMS(buffer: ByteArray, size: Int): Int {
        var sum = 0.0
        var sampleCount = 0

        // Process 16-bit samples (2 bytes per sample)
        var i = 0
        while (i < size - 1) {
            // Convert two bytes to 16-bit signed integer (little-endian)
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
            sampleCount++
            i += 2
        }

        if (sampleCount == 0) return 0

        // Calculate RMS
        val rms = kotlin.math.sqrt(sum / sampleCount)
        return rms.toInt().coerceIn(0, 32767)
    }

    /**
     * Convert raw PCM data to WAV file with proper RIFF header
     *
     * WAV format:
     * - RIFF header (12 bytes)
     * - fmt chunk (24 bytes)
     * - data chunk (8 bytes + PCM data)
     */
    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        try {
            val pcmData = pcmFile.readBytes()
            val pcmSizeKb = pcmData.size / 1024
            Log.d(TAG, "Converting PCM to WAV: ${pcmSizeKb}KB raw PCM data")

            val wavHeader = createWavHeader(pcmData.size)

            FileOutputStream(wavFile).use { fos ->
                fos.write(wavHeader)
                fos.write(pcmData)
            }

            val wavSizeKb = wavFile.length() / 1024
            Log.d(TAG, "WAV file created successfully")
            Log.d(TAG, "  Path: ${wavFile.absolutePath}")
            Log.d(TAG, "  Size: ${wavFile.length()} bytes (${wavSizeKb}KB)")
            Log.d(TAG, "  PCM data: ${pcmData.size} bytes (${pcmSizeKb}KB)")
            Log.d(TAG, "  WAV header: 44 bytes")

            // Verify WAV file structure
            if (wavFile.length() == (pcmData.size + 44).toLong()) {
                Log.d(TAG, "✅ WAV file structure verified")
            } else {
                Log.w(TAG, "⚠️ WAV file size mismatch: expected ${pcmData.size + 44}, got ${wavFile.length()}")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Error converting PCM to WAV", e)
        }
    }

    /**
     * Create WAV file header (44 bytes)
     */
    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        val header = ByteArray(44)
        val byteRate = SAMPLE_RATE * 1 * BYTES_PER_SAMPLE // sample rate * channels * bytes per sample
        val totalDataLen = pcmDataSize + 36
        val blockAlign = 1 * BYTES_PER_SAMPLE

        header[0] = 'R'.code.toByte()  // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()  // File size - 8
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()  // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()  // fmt
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16  // fmt chunk size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1  // PCM format
        header[21] = 0
        header[22] = 1  // Mono = 1 channel
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()  // Sample rate
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()  // Byte rate
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = blockAlign.toByte()  // Block align
        header[33] = 0
        header[34] = 16  // Bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()  // data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte()  // Data size
        header[41] = ((pcmDataSize shr 8) and 0xff).toByte()
        header[42] = ((pcmDataSize shr 16) and 0xff).toByte()
        header[43] = ((pcmDataSize shr 24) and 0xff).toByte()

        return header
    }

    /**
     * Get the output file
     */
    fun getFile(): File = outputFile

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
}
