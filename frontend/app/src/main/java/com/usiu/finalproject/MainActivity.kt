package com.usiu.finalproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.usiu.finalproject.databinding.ActivityMainBinding
import com.usiu.finalproject.data.AlertLevel
import com.usiu.finalproject.data.ClassificationResponse
import com.usiu.finalproject.network.ApiClient
import com.usiu.finalproject.utilities.AudioRecorder
import com.usiu.finalproject.utilities.FeedbackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var feedbackManager: FeedbackManager

    private var isRecording = false
    private var isProcessing = false
    private var currentRecordingFile: File? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val RECORDING_DURATION = 5000L // 4 seconds to match training data
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            initializeApp()
        } else {
            showError(getString(R.string.permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        audioRecorder = AudioRecorder(this)
        feedbackManager = FeedbackManager(this)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.recordButton.setOnClickListener {
            if (!continuousListening) {
                startContinuousListening()
                binding.recordButton.text = getString(R.string.stop_listening)
            } else {
                stopContinuousListening()
                resetUI()
            }
        }

        binding.settingsButton.setOnClickListener {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
    }


    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeApp()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initializeApp() {
        checkServerConnection()
    }

    private fun checkServerConnection() {
        binding.statusText.text = getString(R.string.connecting)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.checkHealth()
                if (response.isSuccessful && response.body()?.model_loaded == true) {
                    binding.statusText.text = getString(R.string.connected)
                    binding.statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success_green))
                    enableUI(true)
                } else {
                    showError(getString(R.string.connection_failed))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                showError(getString(R.string.connection_failed))
            }
        }
    }

    private fun startListening() {
        if (isRecording || isProcessing) return

        try {
            currentRecordingFile = audioRecorder.startRecording()
            if (currentRecordingFile != null) {
                isRecording = true
                updateUIForRecording()

                // Auto-stop after RECORDING_DURATION
                lifecycleScope.launch {
                    delay(RECORDING_DURATION)
                    if (isRecording) {
                        stopListening()
                    }
                }
            } else {
                showError(getString(R.string.recording_error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            showError(getString(R.string.recording_error))
        }
    }


    private fun stopListening() {
        if (!isRecording) return

        try {
            val recordedFile = audioRecorder.stopRecording()
            isRecording = false

            if (recordedFile != null && recordedFile.exists()) {
                processAudioFile(recordedFile)
            } else {
                showError(getString(R.string.recording_error))
                resetUI()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            showError(getString(R.string.recording_error))
            resetUI()
        }
    }

    private fun processAudioFile(audioFile: File) {
        isProcessing = true
        updateUIForProcessing()

        lifecycleScope.launch {
            try {
                // Create multipart body for file upload
                val requestFile = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)

                // Send to server for classification
                val response = ApiClient.apiService.classifyAudio(body)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true && result.prediction != null && result.feedback != null) {
                        handleClassificationResult(result)
                    } else {
                        showError(result?.error ?: getString(R.string.classification_error))
                    }
                } else {
                    showError("Server error: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Classification failed", e)
                showError(getString(R.string.classification_error))
            } finally {
                isProcessing = false
                resetUI()
                // Clean up the audio file
                audioFile.delete()
            }
        }
    }

    private fun handleClassificationResult(result: ClassificationResponse) {
        val prediction = result.prediction!!
        val feedback = result.feedback!!

        // Update UI with results
        binding.lastDetectionText.text = prediction.`class`.replace("_", " ").uppercase()
        binding.confidenceText.text = getString(R.string.confidence_format, prediction.confidence * 100)
        binding.messageText.text = feedback.message
        binding.messageText.visibility = View.VISIBLE

        // Set message background color based on alert level
        val alertLevel = AlertLevel.fromString(feedback.alert_level)
        val backgroundColor = when (alertLevel) {
            AlertLevel.CRITICAL -> ContextCompat.getColor(this, R.color.danger_red)
            AlertLevel.HIGH -> ContextCompat.getColor(this, R.color.warning_orange)
            AlertLevel.MEDIUM -> ContextCompat.getColor(this, R.color.warning_orange)
            AlertLevel.LOW -> ContextCompat.getColor(this, R.color.success_green)
        }

        binding.messageText.setBackgroundColor(backgroundColor)

        // Provide feedback (visual + haptic)
        feedbackManager.provideFeedback(feedback, binding.mainContainer)

        Log.i(TAG, "Classification: ${prediction.`class`} (${prediction.confidence})")
    }

    private fun updateUIForRecording() {
        binding.recordButton.text = getString(R.string.stop_listening)
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.danger_red))
        binding.soundIndicator.setBackgroundResource(R.color.warning_orange)
        binding.settingsButton.isEnabled = false
    }

    private fun updateUIForProcessing() {
        binding.recordButton.text = getString(R.string.processing)
        binding.recordButton.isEnabled = false
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.soundIndicator.setBackgroundResource(R.color.light_blue)
    }

    private fun resetUI() {
        binding.recordButton.text = getString(R.string.start_listening)
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        binding.recordButton.isEnabled = true
        binding.settingsButton.isEnabled = true
        binding.loadingIndicator.visibility = View.GONE
        binding.soundIndicator.setBackgroundResource(R.drawable.circle_indicator)

        // Hide message after some time
        lifecycleScope.launch {
            delay(5000) // 5 seconds
            binding.messageText.visibility = View.GONE
        }
    }

    private fun enableUI(enabled: Boolean) {
        binding.recordButton.isEnabled = enabled
        binding.settingsButton.isEnabled = enabled
    }

    private fun showError(message: String) {
        binding.statusText.text = message
        binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        enableUI(false)
    }

    private var continuousListening = false

    private fun startContinuousListening() {
        continuousListening = true
        lifecycleScope.launch {
            while (continuousListening) {
                startListening()
                delay(RECORDING_DURATION + 1000L) // Wait for recording + a buffer for processing
            }
        }
    }

    private fun stopContinuousListening() {
        continuousListening = false
        stopListening()
    }
    override fun onPause() {
        super.onPause()
        stopContinuousListening()
        feedbackManager.cancelAllFeedback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousListening()
        audioRecorder.cleanup()
        feedbackManager.cancelAllFeedback()
    }



}