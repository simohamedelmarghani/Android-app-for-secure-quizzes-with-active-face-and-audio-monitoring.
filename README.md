# ProctorQuiz 🛡️

A secure, proctored Android quiz application designed to prevent cheating during online exams. By utilizing device sensors and on-device AI, the app ensures a fair and verified testing environment.

---

### 📂 Project Resources
> [!IMPORTANT]
> The **Project Report** and **Slideshow Presentation** are located directly in the root directory of this repository:
> * 📄 **[Project Report](./)** (Detailed documentation of the project)
> * 📊 **[Project Presentation](./)** (Overview slides of the architecture and features)

---

## ✨ Key Features

*   **🔑 Secure Authentication:** Built-in login and registration system powered by **Firebase Authentication** to ensure user identity.
*   **🤖 AI Face Detection:** Integrates **Google ML Kit Vision** and **CameraX** to detect if the user's face is present in front of the camera. If no face is detected, a fraud warning is triggered.
*   **🎙️ Smart Audio Monitoring:** Continuously measures ambient noise levels using the device's microphone to detect talking or suspicious background noise.
*   **📍 Location Verification:** Uses the Google Play Services **Fused Location Provider** to log and display the device's coordinates, verifying the physical exam location.
*   **⚙️ Automated Fraud Response:** Instantly resets the quiz and alerts the user if any anti-cheating rule is violated.

---

## 🛠️ Tech Stack & Dependencies

*   **Language:** Java / Kotlin
*   **UI Framework:** XML (Android Material Design)
*   **Real-time AI:** Google ML Kit (Face Detection API)
*   **Camera API:** Jetpack CameraX (ImageAnalysis & Preview)
*   **Backend & Auth:** Firebase Auth
*   **Location Services:** Google Play Services Location (`FusedLocationProviderClient`)

---

## 🚀 Getting Started

### Prerequisites
*   Android Studio (Ladybug or newer recommended)
*   Android Device or Emulator with camera and microphone permissions enabled
*   A Firebase project linked to your app (configured via `google-services.json`)

### Installation & Run
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/proctor-quiz.git
    ```
2.  **Open the project** in Android Studio.
3.  Add your own **`google-services.json`** inside the `app/` directory.
4.  **Sync** Gradle and **Run** the app on a physical device (recommended for camera/microphone features) or emulator.

---

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
