# SlothLlama 🦥

A private room on the tablet that teaches, keeps and works things out, without going online.
Open models run on the device (llama.cpp, Adreno GPU); nothing you say, read or keep leaves it.
The only door to the internet is the **SlothLobster**, a separate app that shares an encrypted
mailbox folder with SlothLlama and never anything more.

**Three pillars.** Everything in the app belongs to one of them.
- **Learn:** tutors for Mandarin, Cantonese, Japanese, French, German, Spanish and Italian;
  flashcards, speaking practice, the dictionary.
- **Know:** the Library (your books and documents, offline Wikipedia), Notes.
- **Do:** offline tools (sums, units, time zones, dates, timers, calendar), the Lobster.

**Design rules.** OLED black and quiet. 🦥 is you and the app, 🦞 the Lobster, flags are for
languages, and at most one emoji per line. Icons, not emoji, on buttons and chips.

**Guarantees, checked on every build.** The APK has no network permissions (CI fails if one
appears). Unit tests cover the tools' pattern matching, the message format and the mailbox
encryption (`gradle :app:testDebugUnitTest`). Anything from outside (shared text, documents,
library passages, the Lobster's catch) is reference material for the model, never instructions,
and nothing is sent until you tap send.

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
  them under **Flashcards** (spaced repetition with FSRS, the scheduler Anki uses: Again / Hard /
  Good / Easy; cards from older versions keep their progress).
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
- **Japanese, French, German, Spanish and Italian tutors.** One conversation partner per language
  (they teach on request too). Settings → Tutors sets your level per language (Beginner → Fluent);
  replies follow it, and from C1 up they drop the English / romaji lines. Read-aloud, hands-free,
  repeat-after-me (scored word by word, accents optional) and flashcards work in every language.
  Listening: SenseVoice covers Japanese; French, German, Spanish and Italian need Whisper Small
  (Models → Voice input: European languages; three files, about 375 MB).
- **Home page.** Opens by default: the time, today in Chinese, a hello, Continue and New chat,
  **Today** (flashcards due, what the Lobster brought back, the weekly backup), the tutors one
  chip per language, your things with live status, and recent chats (Settings → Appearance →
  Open on the Home page). OLED black with a moss-green accent, kept for the main action and live
  numbers; coral is the Lobster's.
  Back returns to where you came from; Home is in the menu.
- **Landscape.** On a tablet held sideways the chat list stays open beside the chat, the Home
  page and Settings use two columns, and Models and Personas show cards side by side.
- **Start page.** The time, today in Chinese (weekday, lunar date, zodiac year — computed on the
  tablet, no weather because the app stays offline), a hello, and quick ways in.
- **Private library.** Library (Home or the chat list): add books and documents (PDF, EPUB,
  HTML, text) and they are cut into passages and indexed on the tablet; add Kiwix `.zim` files
  (offline Wikipedia from library.kiwix.org; the "mini" and "nopic" editions are small) and they
  are read where they are. In a chat, the book button next to **+** sends each question with the
  passages that best answer it, and the reply cites them as [1], [2]. Search is by key words
  (BM25), and handles Chinese and Japanese without spaces. **Search by meaning** (Library → Import
  model, once: EmbeddingGemma, `embeddinggemma-300M-Q8_0.gguf`, 334 MB from
  huggingface.co/ggml-org/embeddinggemma-300M-GGUF) also finds passages that say the same thing
  in other words or another language; it reads the library in the background (pausing while a
  reply is written) and runs on the CPU beside the chat model. Each item can be switched off or
  removed; the library has its own database and isn't in backups. Kiwix files are read with
  [libkiwix](https://github.com/kiwix/java-libkiwix) (GPLv3).
- **Memory of you.** Say "remember that I'm vegetarian" in any chat (or add it in Settings →
  Memory) and it goes with later messages as background. Only what you add is kept, in your
  words; "forget that …" or Settings → Memory removes it, and "what do you remember about me?"
  lists it. Memories are in backups. With search by meaning, the ones closest to the question go
  first when there are many.
- **Offline tools.** When a message asks for it, the app works it out on the tablet and hands
  the exact result to the assistant, so it doesn't guess: sums ("18% of 2,450", "12 times
  7.5"), unit conversions ("350 F to C", "how many cups in a litre"), the time in other cities
  and converting times between them, date sums ("days until July 25", "what day is Christmas"),
  timers and alarms (set in the clock app), quick notes ("take a note: …", "what did I note
  about wifi?", and a Notes page), your calendar (read only, switched on in Settings), the
  Chinese dictionary, and "look up … in my library / Wikipedia". Each result shows as a chip on
  your message. Settings → Assistant → Offline tools; tutors don't use them unless you allow it.
- **Share to SlothLlama.** Share text, a link, a photo, a PDF or a book from any app: a small
  sheet offers what fits (explain, summarize, translate, read a photo's text, save to Notes,
  add to the Library, or send a link to the Lobster). Chats open with it attached and the
  request in the box; nothing is sent until you tap send.
- **SlothLobster mailbox.** SlothLlama stays offline; the SlothLobster, a separate app, goes
  online for you. They share only an encrypted mailbox folder (same tablet, or synced to a phone
  with Syncthing). The Lobster page (Settings → SlothLobster) sets it up, sends orders
  (research, fetch, watch, brief), shows whether the Lobster is awake, and lists what it brought
  back for you to file in the Library, Notes or a chat. In a chat, "ask the lobster to …" sends
  an order too. The format is in [docs/LOBSTER_MAILBOX.md](docs/LOBSTER_MAILBOX.md).
- **Backups.** Settings → Backup: one file with chats, custom personas, flashcards, notes and settings,
  optionally AES-256 encrypted with a password. Restore adds it back. Models aren't included, but
  a backup lists their names (and the Library's), so a restore shows what to import again.
  **Back up every week** saves an encrypted backup into a folder you choose (an SD card or USB
  stick is safest) and keeps the newest four; its password is locked by the Android Keystore.
- **Diagnostics.** Settings → About → Diagnostics: version, device, memory and any recent crash,
  noted on the tablet only, to copy into a bug report.
- **Updates with Obtainium.** Each signed build is published as a GitHub release
  (`slothllama-v…`), so Obtainium can install updates. See [docs/UPDATES.md](docs/UPDATES.md).
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
- **Doors in, and what they can do.** "Ask SlothLlama", "Share to SlothLlama" and the Lobster's
  mailbox can only fill the message box, attach something or offer a catch to file: none of
  them can send a message or act. The mailbox is AES-256-GCM encrypted; its key is wrapped by the
  Android Keystore and kept out of backups.
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
