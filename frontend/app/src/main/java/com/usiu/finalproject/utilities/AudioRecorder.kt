package com.usiu.finalproject.utilities

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun startRecording(): File? {
        if (isRecording) {
            stopRecording()
        }

        return try {
            // Create output file
            outputFile = File(context.cacheDir, "recorded_audio_${System.currentTimeMillis()}.wav")

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            outputFile

        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun stopRecording(): File? {
        return if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                outputFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    fun isRecording(): Boolean = isRecording

    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
        outputFile?.delete()
    }
}

// Alternative WAV recorder for better compatibility with your model
class WAVAudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun startRecording(): File? {
        if (isRecording) {
            stopRecording()
        }

        return try {
            outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                setAudioSamplingRate(22050) // Match your training data
                setOutputFile(outputFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            outputFile

        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun stopRecording(): File? {
        return if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                outputFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    fun isRecording(): Boolean = isRecording

    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
        outputFile?.delete()
    }
}