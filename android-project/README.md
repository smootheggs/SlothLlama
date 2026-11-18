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

## What SlothLlama is
A private, offline AI terminal for the tablet: load **any open-source model**, then chat with it
through **personas** (built-in or your own). Language tutors are built-in personas.

| Tab | What it does |
|---|---|
| Chat | Conversations; the 👤 icon applies a persona to a new chat; 🔊 reads Chinese aloud |
| Personas | Built-in personas + your own: system prompt, temperature, reply length, read-aloud, starter prompts |
| Models | Import, choose, and delete models; shows what's loaded in memory |

### Runtimes
| File | Runtime | Where models come from |
|---|---|---|
| `.gguf` | **llama.cpp** (Adreno GPU via OpenCL, or CPU with Arm KleidiAI) | Almost every open model on Hugging Face (Qwen, Gemma, Llama, Mistral, Phi, DeepSeek…) |
| `.task` / `.litertlm` | Google AI Edge MediaPipe | `litert-community` on Hugging Face |

**GPU.** On Snapdragon tablets, GGUF models run on the Adreno GPU (Models → Use the GPU, on by
default). The first GPU load compiles the GPU kernels (about a minute) and caches them. Q4_0
files are the fastest on Adreno; Q4_K_M works too. If the GPU driver ever takes the app down,
that model is moved to the CPU automatically and the Models screen offers to try again. A
model too big for the GPU falls back to the CPU on its own.

## Building
1. **Get the llama.cpp sources** (pinned submodule, once):
   ```
   git submodule update --init --recursive
   ```
   If you copied files in by hand instead of using `git am`, add it yourself:
   ```
   git submodule add https://github.com/ggml-org/llama.cpp android-project/third_party/llama.cpp
   cd android-project/third_party/llama.cpp && git checkout 9048bad8d6e3a0f9f1726e238e451181c7e02613
   ```
2. In Android Studio → **SDK Manager → SDK Tools** (tick *Show Package Details*), install
   **NDK 29.0.13113456** and **CMake 3.31.6**.
3. Optional, for the GPU backend: run `scripts/fetch-opencl.sh` (with `ANDROID_HOME` set). It
   fetches the Khronos OpenCL headers and loader at pinned commits; CI always runs it. Without
   it the app builds CPU-only.
4. Open `android-project/`, sync Gradle, press ▶ with the tablet selected.
   The first build compiles llama.cpp and takes several minutes; later builds are quick.
   The app needs Android 13+ (minSdk 33).

## Study features
- **Voice input (offline).** Download the SenseVoice package once
  (`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2` from the sherpa-onnx
  `asr-models` release), extract it, then **Models → Voice input → Import** and select
  `model.int8.onnx` and `tokens.txt` together. Tap the mic in any chat; Mandarin tutors listen
  for Mandarin, Cantonese tutors for Cantonese. The transcript lands in the box to check first.
- **Flashcards.** Tap ☆ under a tutor reply to save its ⭐ words and word-list entries; review
  them under **Flashcards** (spaced repetition: Again / Hard / Good / Easy).
- **Ask SlothLlama.** Select text in any app → **Ask SlothLlama** opens a new chat with the
  question waiting in the box.
- **A model per persona.** Personas → Model, e.g. a Cantonese model for the Cantonese tutors.
- **Dictionary (offline).** Tap any Chinese character in a reply to look up the word that starts
  there: characters in your script, Pinyin and Jyutping, meanings, Listen, Practise, and ☆ to
  save it as a flashcard. Drawer → Dictionary searches by characters, pinyin (`nihao`,
  `ni3 hao3`) or English. Built from CC-CEDICT (CC BY-SA 4.0) with Jyutping from ToJyutping;
  CI fetches both at pinned checksums (`scripts/fetch-dictionary.sh`).
- **Repeat after me.** The 🗣 button under a tutor reply (or Practise in the dictionary): pick a
  sentence, tap Listen, then Say it. It stops listening when you pause, then colours each
  character green (heard) or red (missed) with a score. Homophones the recogniser picks still
  count, and simplified / traditional don't matter. Needs the voice model.
- **Hands-free conversation.** The headset button in the composer: talk, pause, and the reply is
  read aloud, then it listens again. Stop with ■, or it pauses after two silent turns.
- **Photos.** Models that can see (Qwen3.5, Gemma 3/4, Qwen2.5-VL, MiniCPM-V…) ship a separate
  vision file with "mmproj" in its name on the same Hugging Face page. Download it, then
  **Models → the model's card → Photos → Add**. In a chat, **+ → Photo** (the system photo
  picker; no gallery permission). Photos are re-saved without location data in the app's
  private storage; the model sees one photo per message, and remembers it while the chat stays
  open. Backups don't include photos.
- **Documents.** **+ → Document**: a PDF or any text file (.txt, .md, .csv, subtitles). Its text
  goes into the message as reference material (instructions inside it are not followed).
  About 2,600 tokens fit, roughly 10,000 English characters or 2,600 Chinese characters;
  longer documents are cut and the chip says "first part only". Scanned PDFs have no text.
- **Home page.** Opens by default: the time, today in Chinese, a hello, Continue, New chat,
  every tutor, shortcuts and recent chats (Settings → Appearance → Open on the Home page).
  Back returns to where you came from; Home is in the menu.
- **Landscape.** On a tablet held sideways the chat list stays open beside the chat, the Home
  page and Settings use two columns, and Models and Personas show cards side by side.
- **Start page.** The time, today in Chinese (weekday, lunar date, zodiac year — computed on the
  tablet, no weather because the app stays offline), a hello, and quick ways in.
- **Backups.** Settings → Backup: one file with chats, custom personas, flashcards and settings,
  optionally AES-256 encrypted with a password. Restore adds it back. Models aren't included.
- **Small things.** Continue for cut-off replies, chat search, one-tap follow-ups (Correct me,
  Quiz me…), home-screen shortcuts, a character-size setting, speed / context under replies,
  and an optional daily flashcard reminder.

## Security model
- **No network.** The app has no internet permission, so Android refuses any connection it
  tries to make. Models come in through **Models → Import**. Settings → Privacy shows the status.
- **Private signing key.** Release builds are signed with your own key, kept only in GitHub
  Secrets (`SLOTH_KEYSTORE_B64`, `SLOTH_KEYSTORE_PASSWORD`) and in your Termux home. Without
  those secrets, CI builds the debug app ("SlothLlama Dev"), which is debuggable and signed
  with the public debug key, so use it only for development.
- **Model files are the main untrusted input.** GGUF headers are sanity-checked before they
  reach llama.cpp, and Models → fingerprint shows each file's SHA-256 to compare with
  Hugging Face. Only use models from publishers you trust, and keep llama.cpp up to date.
- **Nothing leaves in logs or backups.** Chat text isn't logged, and app data is excluded from
  cloud backup and device transfer. Settings → Privacy screen blocks screenshots and Recents.

## Getting models
In the **Models** tab tap **Import Model File** and pick a file from Downloads, an SSD or any folder,
or copy files over USB into `Android/data/app.localmind.assistant.debug/files/models/`
(`app.localmind.assistant` without `.debug` for release builds).

On Hugging Face, search for a model name plus "GGUF" and take a **Q4_K_M** file. On a 12 GB tablet,
files under ~5 GB (up to ~7–8B parameters) run comfortably. Good starting points:
- **Qwen2.5-3B-Instruct** or **Qwen3-4B** — fast, and the strongest small models for Mandarin/Cantonese
- **Gemma 3 4B it** — strong all-rounder
- **Llama 3.2 3B Instruct** — quick everyday chat
- **Qwen2.5-7B-Instruct** — deeper answers, slower

Reasoning models (Qwen3, DeepSeek-R1 distills) work too; their `<think>` sections are hidden.

### Tutors
The built-in tutors (Mandarin/Cantonese partner and teacher, any-language partner) are personas like any other —
pick one from the 👤 menu in Chat or from the Personas tab. Tap **Customize** to make your own copy.
For read-aloud, install the Chinese (Mandarin) and Chinese (Hong Kong / Cantonese) voices under
Settings → Text-to-speech → Google → Install voice data.
