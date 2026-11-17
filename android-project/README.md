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

## Running a real model on the tablet
1. Download a model from Hugging Face (`litert-community`):
   - **For Mandarin/Cantonese tutoring:** `Qwen2.5-1.5B-Instruct` →
     `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task` (~1.6 GB, CPU/GPU, 4096-token context).
   - Smaller, English-first alternative: `Gemma3-1B-IT` →
     `Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task` (~660 MB; accept the Gemma license first).
2. Build and run the app once so it creates its model folder.
3. Plug the tablet in over USB and copy the `.task` file into
   `Internal storage/Android/data/app.localmind.assistant.debug/files/models/`
   (`app.localmind.assistant` without `.debug` for release builds).
   Or with adb: `adb push <file>.task /data/local/tmp/llm/`.
4. Reopen the app. The chat header badge turns green and shows the file name once the model has loaded.

The engine (`data/inference/LiteRtLmInferenceEngine.kt`) picks up any `.task` / `.litertlm` file in
those folders (see `ModelFileLocator`). If none is found, replies say where to copy one.

### Tutors
Tap the 🎓 icon in the chat header to pick a tutor (Mandarin/Cantonese partner or teacher,
any-language partner, general assistant). Each tutor has its own prompt and starter prompts;
switching starts a new chat. The 🔊 button reads the Chinese parts of a reply aloud using
Android's text-to-speech — install the Chinese (Mandarin) and Chinese (Hong Kong / Cantonese)
voices under Settings → Text-to-speech → Google → Install voice data.
