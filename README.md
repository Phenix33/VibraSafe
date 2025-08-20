# VibraSafe

VibraSafe is an assistive application designed to help people with hearing impairments by classifying real-world sounds using machine learning.  
It leverages the **UrbanSound8K dataset** to train a model that can recognize various environmental sounds, and provides real-time feedback to the user.

The system consists of:
- **Backend (Python)**: Trains and serves the ML model for sound classification.
- **Frontend (Android, Kotlin)**: Records 5-second audio clips, sends them to the backend, and displays sound classifications.

---

## Features
- Real-time audio classification in 5-second intervals.
- Model trained on **UrbanSound8K** dataset.
- Assistive feedback for hearing-impaired individuals.
- Modular design: ML backend + mobile frontend.

---

## Project Structure
```

vibrasafe/
│
├── backend/
│   ├── train\_model.py        # Script to train the ML model
│   ├── server.py             # Backend server for handling audio classification requests
│   ├── model/                # Folder where the trained model (.h5) will be saved
│   └── requirements.txt      # Python dependencies
│
└── android-app/
├── app/
│   ├── src/main/java/.../network/ApiService.kt
│   └── src/main/res/xml/network\_security\_config.xml
└── build.gradle

````

## Setup Instructions

### 1. Train the Model (Backend)
1. Install dependencies:
   ```bash
   cd backend
   pip install -r requirements.txt
````

2. Download and place the **UrbanSound8K** dataset in the appropriate location (see `train_model.py` for details).
3. Train the model:

   ```bash
   python train_model.py
   ```

   This will output a trained model file (e.g., `sound_classifier.h5`) in the `model/` folder.

---

### 2. Run the Backend Server

1. Start the backend server:

   ```bash
   python server.py
   ```
2. Copy the server **IP address** shown in the terminal — you’ll need this for the Android app configuration.

---

### 3. Configure the Android App

1. Open the Android project (`android-app/`) in **Android Studio**.
2. Update the backend server IP in:

   * `res/xml/network_security_config.xml`
   * `network/ApiService.kt`
3. Build and run the app on a device or emulator.

---

### 4. Usage

* The Android app will continuously record **5-second audio clips**.
* Each clip is sent to the backend for classification.
* The classification result is returned and displayed in the app, helping users identify sounds in their environment.

---

## Requirements

* **Backend**

  * Python 3.8+
  * TensorFlow / Keras
  * Flask (or FastAPI, depending on implementation)
* **Frontend**

  * Android Studio
  * Kotlin

---

## Notes

* Ensure the backend server is running **before** starting the Android app.
* Always update the server IP in the Android app whenever the backend server address changes.
* The trained model (`.h5` file) must be present in the backend `model/` directory before running the server.

---

## Future Improvements

* Support for on-device inference (no server required).
* Expanded dataset for more sound classes.
* Notifications and haptic feedback for critical sounds (alarms, sirens, etc.).

---

## License

MIT License. See [LICENSE](LICENSE) for details.


