import os
import librosa
import numpy as np
import pandas as pd
import warnings
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import (Conv1D, MaxPooling1D, LSTM, 
                                   Dense, Dropout, BatchNormalization)
from tensorflow.keras.utils import to_categorical
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau
import matplotlib.pyplot as plt

# Suppress Librosa warnings
warnings.filterwarnings("ignore", category=UserWarning)

# Constants
DATASET_PATH = 'UrbanSound8K/audio'
METADATA_PATH = 'UrbanSound8K/metadata/UrbanSound8K.csv'
SAMPLE_RATE = 22050
TRACK_DURATION = 4  # seconds
N_MFCC = 40
MIN_SAMPLES = 500  # Minimum audio samples to process

def extract_features(file_path):
    try:
        # Load with minimum length check
        audio, _ = librosa.load(file_path, 
                              sr=SAMPLE_RATE,
                              duration=TRACK_DURATION,
                              res_type='kaiser_fast')
        
        if len(audio) < MIN_SAMPLES:
            return None
            
        # Extract MFCCs with fixed n_fft
        n_fft = min(2048, len(audio))
        mfccs = librosa.feature.mfcc(y=audio, sr=SAMPLE_RATE, n_mfcc=N_MFCC, n_fft=n_fft)
        mfccs_scaled = (mfccs - np.mean(mfccs)) / np.std(mfccs)
        
    except Exception as e:
        print(f"Error processing {file_path}: {str(e)[:100]}...")
        return None
    return mfccs_scaled.T  # Transpose to (time_steps, n_mfcc)

# Load and process data
metadata = pd.read_csv(METADATA_PATH)
features = []
labels = []

for _, row in metadata.iterrows():
    file_path = os.path.join(DATASET_PATH, f'fold{row["fold"]}', row['slice_file_name'])
    if not os.path.exists(file_path):
        continue
        
    data = extract_features(file_path)
    if data is not None:
        features.append(data)
        labels.append(row['class'])

# Pad sequences to uniform length
max_len = max([x.shape[0] for x in features])
X = np.array([np.pad(x, ((0, max_len - x.shape[0]), (0, 0)), mode='constant') for x in features])

# Reshape for Conv1D: (samples, time_steps, features)
X = X.reshape(X.shape[0], X.shape[1], X.shape[2])

# Encode labels
label_encoder = LabelEncoder()
y = label_encoder.fit_transform(labels)
y_categorical = to_categorical(y)

# Train-test split
X_train, X_test, y_train, y_test = train_test_split(X, y_categorical, test_size=0.2, random_state=42)

# CNN+LSTM Model
model = Sequential([
    # Input shape: (time_steps, n_mfcc)
    Conv1D(64, 3, activation='relu', padding='same', input_shape=(max_len, N_MFCC)),
    BatchNormalization(),
    MaxPooling1D(2),
    Dropout(0.3),
    
    Conv1D(128, 3, activation='relu', padding='same'),
    BatchNormalization(),
    MaxPooling1D(2),
    Dropout(0.3),
    
    Conv1D(256, 3, activation='relu', padding='same'),
    BatchNormalization(),
    MaxPooling1D(2),
    Dropout(0.3),
    
    LSTM(128, return_sequences=True),
    LSTM(128),
    Dropout(0.5),
    
    Dense(256, activation='relu'),
    Dropout(0.5),
    Dense(10, activation='softmax')
])

model.compile(optimizer='adam',
              loss='categorical_crossentropy',
              metrics=['accuracy'])

callbacks = [
    EarlyStopping(patience=10, restore_best_weights=True),
    ReduceLROnPlateau(factor=0.2, patience=5, min_lr=1e-6)
]

print(f"Training on {len(X_train)} samples")
history = model.fit(X_train, y_train,
                   batch_size=32,
                   epochs=100,
                   validation_data=(X_test, y_test),
                   callbacks=callbacks,
                   verbose=1)

# Evaluation
test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
print(f"\nTest Accuracy: {test_acc*100:.2f}%")
print(f"Test Loss: {test_loss:.4f}")

# Save model
model.save('urban_sound_cnn_lstm_final.h5')
print("Model saved successfully")