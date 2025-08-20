from flask import Flask, request, jsonify
from flask_cors import CORS
import os
import librosa
import numpy as np
import tensorflow as tf
from tensorflow.keras.models import load_model
import tempfile
import logging
from datetime import datetime
import json
import subprocess
import wave
from pydub import AudioSegment
import soundfile as sf

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Constants
SAMPLE_RATE = 22050
TRACK_DURATION = 4  # seconds
N_MFCC = 40
MIN_SAMPLES = SAMPLE_RATE * 1  # At least 1 second of audio
MAX_FILE_SIZE = 10 * 1024 * 1024  # 10MB

# UrbanSound8K class labels
CLASS_LABELS = [
    'air_conditioner', 'car_horn', 'children_playing', 'dog_bark',
    'drilling', 'engine_idling', 'gun_shot', 'jackhammer',
    'siren', 'street_music'
]

# Feedback configuration
SOUND_FEEDBACK = {
    # ... (keep your existing SOUND_FEEDBACK dictionary) ...
}

class SoundClassifier:
    def __init__(self, model_path):
        self.model = None
        self.model_path = model_path
        self.load_model()

    def load_model(self):
        """Load the trained model"""
        try:
            self.model = load_model(self.model_path)
            logger.info(f"Model loaded successfully from {self.model_path}")
            # Print model summary for debugging
            self.model.summary(print_fn=logger.info)
        except Exception as e:
            logger.error(f"Error loading model: {str(e)}", exc_info=True)
            raise

    def _process_audio(self, audio):
        """Common audio processing steps"""
        if len(audio) < MIN_SAMPLES:
            logger.error(f"Audio too short: {len(audio)} samples")
            return None

        n_fft = min(2048, len(audio))
        mfccs = librosa.feature.mfcc(
            y=audio,
            sr=SAMPLE_RATE,
            n_mfcc=N_MFCC,
            n_fft=n_fft
        )
        return ((mfccs - np.mean(mfccs)) / np.std(mfccs)).T

    def extract_features(self, file_path):
        """Extract MFCC features with multiple fallback methods"""
        try:
            logger.info(f"Processing file: {file_path}")
            if not os.path.exists(file_path):
                logger.error("File does not exist!")
                return None

            # Method 1: Try soundfile directly
            try:
                audio, sr = sf.read(file_path)
                if sr != SAMPLE_RATE:
                    audio = librosa.resample(audio, orig_sr=sr, target_sr=SAMPLE_RATE)
                return self._process_audio(audio)
            except Exception as e:
                logger.warning(f"SoundFile failed: {str(e)}")

            # Method 2: Try librosa with conversion
            try:
                audio, _ = librosa.load(
                    file_path,
                    sr=SAMPLE_RATE,
                    duration=TRACK_DURATION,
                    res_type='kaiser_fast'
                )
                return self._process_audio(audio)
            except Exception as e:
                logger.warning(f"Librosa load failed: {str(e)}")

            # Method 3: Use pydub for format conversion
            try:
                audio = AudioSegment.from_file(file_path)
                audio = audio.set_frame_rate(SAMPLE_RATE).set_channels(1)
                with tempfile.NamedTemporaryFile(suffix='.wav') as tmp:
                    audio.export(tmp.name, format='wav')
                    audio, _ = librosa.load(
                        tmp.name,
                        sr=SAMPLE_RATE,
                        duration=TRACK_DURATION
                    )
                return self._process_audio(audio)
            except Exception as e:
                logger.warning(f"Pydub conversion failed: {str(e)}")

            # Method 4: Use ffmpeg directly
            try:
                with tempfile.NamedTemporaryFile(suffix='.wav') as tmp:
                    subprocess.run([
                        'ffmpeg', '-y', '-i', file_path,
                        '-ac', '1', '-ar', str(SAMPLE_RATE),
                        '-t', str(TRACK_DURATION), tmp.name
                    ], check=True, capture_output=True)
                    audio, _ = librosa.load(
                        tmp.name,
                        sr=SAMPLE_RATE,
                        duration=TRACK_DURATION
                    )
                return self._process_audio(audio)
            except Exception as e:
                logger.error(f"FFmpeg conversion failed: {str(e)}")

            logger.error("All audio loading methods failed")
            return None

        except Exception as e:
            logger.error(f"Feature extraction failed: {str(e)}", exc_info=True)
            return None

    def predict(self, file_path):
        """Predict sound class from audio file"""
        try:
            features = self.extract_features(file_path)
            if features is None:
                return None, None

            expected_timesteps = self.model.input_shape[1]
            
            # Pad/truncate features
            if features.shape[0] < expected_timesteps:
                padded_features = np.pad(
                    features,
                    ((0, expected_timesteps - features.shape[0]), (0, 0)),
                    mode='constant'
                )
            else:
                padded_features = features[:expected_timesteps]

            X = padded_features.reshape(1, expected_timesteps, N_MFCC)
            predictions = self.model.predict(X, verbose=0)
            predicted_class_idx = np.argmax(predictions[0])
            confidence = float(predictions[0][predicted_class_idx])
            
            return CLASS_LABELS[predicted_class_idx], confidence

        except Exception as e:
            logger.error(f"Prediction failed: {str(e)}", exc_info=True)
            return None, None

# Initialize classifier
try:
    classifier = SoundClassifier('urban_sound_cnn_lstm_final.h5')
except Exception as e:
    logger.error(f"Classifier initialization failed: {str(e)}", exc_info=True)
    classifier = None

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy' if classifier and classifier.model else 'unhealthy',
        'timestamp': datetime.now().isoformat(),
        'model_loaded': classifier is not None and classifier.model is not None
    })

@app.route('/classify', methods=['POST'])
def classify_sound():
    """Main classification endpoint with robust file handling"""
    try:
        if not classifier or not classifier.model:
            return jsonify({'error': 'Model not loaded', 'success': False}), 500

        if 'audio' not in request.files:
            return jsonify({'error': 'No audio file', 'success': False}), 400

        file = request.files['audio']
        if file.filename == '':
            return jsonify({'error': 'Empty filename', 'success': False}), 400

        # Validate file content
        file.seek(0, os.SEEK_END)
        file_size = file.tell()
        file.seek(0)
        
        if file_size == 0:
            return jsonify({'error': 'Empty file', 'success': False}), 400
        if file_size > MAX_FILE_SIZE:
            return jsonify({'error': 'File too large', 'success': False}), 400

        # Save to temp file
        with tempfile.NamedTemporaryFile(delete=False, suffix='.wav') as tmp:
            file.save(tmp.name)
            temp_path = tmp.name

        try:
            predicted_class, confidence = classifier.predict(temp_path)
            
            if predicted_class is None:
                return jsonify({'error': 'Unprocessable audio', 'success': False}), 422

            feedback = SOUND_FEEDBACK.get(predicted_class, {
                'alert_level': 'low',
                'color': '#757575',
                'vibration_pattern': [100],
                'message': f'Unknown sound: {predicted_class}',
                'priority': 1
            })

            return jsonify({
                'success': True,
                'prediction': {
                    'class': predicted_class,
                    'confidence': confidence,
                    'timestamp': datetime.now().isoformat()
                },
                'feedback': feedback
            })

        finally:
            if os.path.exists(temp_path):
                os.unlink(temp_path)

    except Exception as e:
        logger.error(f"Classification error: {str(e)}", exc_info=True)
        return jsonify({'error': 'Internal server error', 'success': False}), 500

# ... (keep your existing /feedback-config, /supported-classes, and /batch-classify endpoints) ...

if __name__ == '__main__':
    # Verify dependencies
    try:
        subprocess.run(['ffmpeg', '-version'], check=True, capture_output=True)
    except Exception as e:
        logger.error("FFmpeg not found! Please install it first.")
        exit(1)

    if not os.path.exists('urban_sound_cnn_lstm_final.h5'):
        logger.error("Model file not found!")
        exit(1)

    logger.info("Starting server with configuration:")
    logger.info(f"Sample rate: {SAMPLE_RATE}Hz")
    logger.info(f"Track duration: {TRACK_DURATION}s")
    logger.info(f"MFCC features: {N_MFCC}")
    
    app.run(host='0.0.0.0', port=5000, debug=False)