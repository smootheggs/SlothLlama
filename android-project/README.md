# LocalMind — Offline-First Modular Android AI Assistant

LocalMind is a privacy-first, offline-capable modular AI assistant for Android built with **Kotlin**, **Jetpack Compose (Material 3)**, **Clean Architecture**, **Hilt**, **Room**, **WorkManager**, and **Google AI Edge LiteRT-LM**.

## Core Architecture & Product Guarantees
- **100% Offline-First**: Runs compatible local language models (`.litertlm`, `.task`) directly on-device with zero mandatory cloud accounts.
- **Explicit 5-Layer Separation**: Clearly distinguishes between **(a) Base Model**, **(b) Persona Instructions**, **(c) Locally Indexed Knowledge (RAG)**, **(d) Conversation Memory**, and **(e) Optional Model Adapters (Experimental)**.
- **True OLED Black (`#000000`) Interface**: Engineered for zero light bleed, high contrast legibility, and minimal visual clutter.
- **Prompt-Injection Defense**: Wraps all retrieved passages inside `<untrusted_retrieved_evidence>` tags and sanitizes adversarial override instructions before prompt assembly.

## Building & Running in Android Studio
1. Open the `android-project/` directory in **Android Studio Ladybug (2024.2.1) or newer**.
2. Ensure **JDK 17** and **Android SDK 35** are configured.
3. Run Gradle sync (`./gradlew assembleDebug`).
4. Run unit tests: `./gradlew testDebugUnitTest`.
5. Run instrumented Compose UI tests: `./gradlew connectedDebugAndroidTest`.
