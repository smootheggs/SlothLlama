# SlothLlama 🦥

A private room on the tablet that teaches, keeps and works things out, without going online.
Open models run on the device (llama.cpp, Adreno GPU); nothing you say, read or keep leaves it.
The only door to the internet is the **SlothLobster**, a separate app that shares an encrypted
mailbox folder with SlothLlama and never anything more.

**Three pillars.** Everything in the app belongs to one of them.
- **Learn:** tutors for Mandarin, Cantonese, Japanese, French, German, Spanish and Italian;
  flashcards, speaking practice, the dictionary, Core words.
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
- **Trader.** A built-in persona: a senior precious-metals sales trader (gold, silver, PGMs;
  spot, forwards, leases and the FX swaps between currencies) who coaches. Send it news with
  **Read it like a trader** (Share to SlothLlama, or **Ask the trader** in the reader): what
  happened, why it matters, priced in or a surprise, which clients care and what you'd tell
  them, and the other side. It writes trade ideas as cards (entry, stop, target, horizon,
  thesis, wrong if) and plays clients for pitch practice, with feedback when the call ends.
  SlothDesk's **Ask about this** goes to it too. It never works out prices or P&L itself.
- **SlothBooks and the Librarian.** Home → SlothBooks keeps your books and papers, with three
  tabs: Shelf, Papers, Insights. **Bring in a spreadsheet** once (⋯ → Bring in a spreadsheet:
  .xlsx or .csv, read on the tablet; a Title column is all it needs, and Author, Year, Form,
  Genre, Written in, Reading in, Series and the rest are read by name; ISBNs and links are left
  out), then **Add a book** from then on. Every book starts unread. Each book has a card: the
  catalogue (fixable in Edit details), then **you** (status, dates, progress and where you're up
  to, the language you're reading it in, your copy, rating, mood and pace tags, why now, your
  verdict, quotes), a **gist** marked with where it came from (folded while you read a novel, so
  nothing is spoiled), the books on your shelf it goes with, and recall questions. Series and
  cycles keep their order. **Talk it through** opens the **Librarian**, a built-in persona: a
  book-club partner that is told how far you are and keeps to it, drafts gists as cards you save
  to the book with one tap, quizzes you, and recommends from your own unread books first (the
  picks and every count are worked out in code). **Papers:** a PDF the Lobster fetches is kept
  by itself in Documents/SlothBooks/Papers on the tablet (an EPUB in Documents/SlothBooks/Books)
  and gets a card; **Add a PDF** or Share → **Keep in SlothBooks** does the same for a file you
  have. PDFs open in SlothBooks' own viewer, and their text goes into the Library so the book
  button finds it. **Insights:** what you've read, series progress, when your books were
  written, in what languages and where from, all counted on the tablet.
- **The Sloth Express** (1.32, needs SlothLobster 1.8). Home → The Sloth Express: the morning
  paper the Lobster makes from the settings you send it. Mostly English news in sections (feeds,
  news searches, or a page that lists stories), the stories several outlets carry as top stories,
  a markets strip worked out from SlothDesk's prices, and **the desk**: the Trader's (or any
  persona's) notes on the day, written in a chat and kept on the edition. **Language corners** bring
  each language's biggest stories (Easy Mandarin News for Mandarin, then Cantonese, Japanese,
  French, German, Spanish, Italian), with Pinyin or Jyutping over the words, 简 / 繁, a tap to look a
  word up, the source's English line by line, key words by your level (HSK for Mandarin, the Core
  words list for French) to add to Flashcards, and the language's partner to talk it over with.
  Listen reads the edition aloud offline; past editions are kept 30 days with what you've read.
  Settings: times (morning, evening…), weekends, length, sections (tap to swap), top stories,
  words to lift or mute, corners with their sources, topics and story counts, and who sits on the desk.
- **Atlas** (1.33). Home → Atlas: a world map kept on the tablet (Natural Earth, public domain;
  flags from flag-icons, MIT). Pinch and drag; tap a country for its card: flag, its English name
  always and its names in the tutors' languages, capital, subdivisions (provinces, states,
  prefectures) drawn on its own map, neighbours, local time, Natural Earth's population estimate and
  an area worked out from the borders. Borders are de facto, as held on the ground. Map labels can
  be in English or a tutor's language; a white map is a tap away. Search finds countries, capitals,
  cities and provinces in any of those languages; quizzes ask flags and capitals by region and the
  subdivisions of a country. **Places in what you read:** the edition marks the place each story
  names (tap it to see it on the map), the Reader lists the places an article names, and "Your
  places" pins today's stories and the books on your shelf by country. Places are found on the
  tablet, from the Atlas's own names.
- **Home without repeats** (1.34). The Today row is gone: what needs you lights up its own place
  (unread news as a count on The Sloth Express, a catch on SlothLobster, cards due on Flashcards,
  tickets to mark on SlothDesk), and calm things share one quiet line under the hello. Know's tiles
  put the icon beside the name, so the page fits upright without scrolling. **The Sloth Express**
  reads like a paper: a masthead with its motto, a contents bar that stays in view with unread
  counts and jumps to each part, "Mark read" per section, and read stories folded to their title.
- **The Atlas's looks** (1.35). Night, White or Paper (an old atlas's cream and sepia), each with
  the sea, the globe's grid and atlas lettering; shaded relief (Natural Earth's, public domain,
  about 3 MB) switches on and off. Upright, the map takes the top half and the country's card,
  or the world's, the rest.
- **Weigh and Forecasts** (1.37). Home's third pillar is now **Weigh**: SlothDesk, Forecasts and
  Projects (Sports joins it next). The Lobster and Ourjong moved to the bottom row; a catch still
  shows there in coral, with its count. **Forecasts** keeps every call with a chance and a date: a
  yes-or-no, one of several outcomes, or above or below a number. Change your chance whenever you
  learn something, with a line on why; add a market's chance or price if you like (33%, 1.87, −115,
  20/23). On the check day, mark what happened. The tablet keeps the score: the Brier score
  (averaged over each day you held the call, so being right early counts) and how often your 60%,
  70%, 80% calls came true. SlothDesk's ideas join in at the conviction you saved them with, read
  in place. Tabs for Markets, Sports, Politics, the Watchlist and Other. In backups.
- **Projects** (1.36). Home → Do → Projects: one page per project with the record a project
  manager keeps: the phase (Initiating, Planning, Executing, Monitoring, Closing), the brief (goal,
  sponsor, scope in and out, constraints, success measures, deadline), milestones, risks
  (likelihood × impact, scored on the tablet), stakeholders on the power/interest grid, decisions,
  and dated updates with the status you give them. Counts, late milestones, days to the deadline
  and risk scores are worked out in code. **Write it up** builds a risk register or stakeholder
  map on the tablet, or hands the **Scribe** persona a facts sheet made from the record for a
  charter, one-pager, status report, retrospective or closeout: it only words what's there, and
  anything you haven't written stays a [bracket]. **Grill the plan** opens the **Grill** persona,
  which interviews you one question at a time and writes a decision record. Keep either reply with
  the button under it and it's filed with the project, to read, copy or share. In backups.
- **Organising SlothBooks.** The default shelves (All, Reading, Up next, To read, Read,
  Reference) are always there; make your own with **+ New shelf**, rename or remove them from
  their ⋯ (the books stay), and put a book on any of them from its card. **Group by** author,
  genre, topic (History splits into China and East Asia, the World Wars, the Middle East…), series,
  era, language or region, with a header per group and an A–Z strip by author. An author with four
  or more books folds into one stack you open in place; copies of one book (an illustrated
  edition, a translation) are one row with "2 copies". Language courses, cookbooks and guides go on
  Reference, out of To read. Show the shelf as a **list**, **small covers** or **large covers**.
  Every book has a cover: a PDF's first page or an EPUB's own cover when there's a file, else one
  made on the tablet from the title and genre.
- **Real covers, through the Lobster.** SlothBooks ⋯ → **Find covers for the shelf** sends the
  titles and authors of books with made covers to the SlothLobster (1.7 or newer), which looks them
  up on Open Library and Google Books and sends small covers back in batches of 20; the shelf fills
  in as they arrive. A book's **Change cover** shows where its cover came from and lets you ask the
  Lobster for up to three to choose from, use a photo of your own copy, or go back to the made
  cover. Photos you chose and covers from a book's own file are never replaced. Every picture is
  re-drawn on the tablet before it's kept; covers stay on the tablet and aren't in backups.
- **SlothDesk: Ideas and Calculators.** SlothDesk has three tabs: Strategies, Ideas, Calculators.
  **Ideas:** write an idea before it
  plays out (instrument, direction, entry, stop, target, horizon, thesis, what would prove it
  wrong, how sure you are) or save the Trader's card with the bookmark under its reply. Once
  saved, the thesis, levels and conviction are fixed; you add dated notes, and at the review
  date you close it: right, wrong or mixed, right for the reason you gave or not, the exit
  (its result in R), what happened and the lesson. The page shows your hit rate, average R, and
  how often each level of conviction came true. **Calculators:** metal forwards from rates and
  lease rates (and the rate a quoted forward or EFP implies); metal in EUR, CHF, JPY, CNH, HKD,
  GBP or AUD through the FX swap, with the metal's swap rate in that currency; local prices per
  gram, tael or tola against London; weights and fineness; positions, P&L and contract counts.
  Tenor buttons (1W–1Y) fill the days, the USD rate starts from the 3-month T-bill (and USD/JPY
  from its close) in SlothDesk's prices until you type your own, and inputs are kept between
  visits. On a wide screen the results sit beside the inputs.
  **Ask the Trader** sends an idea (to be challenged or reviewed) or a calculator's numbers (to be
  explained, never recalculated). In backups. Paper only.
- **Voices.** Each language is read by a voice installed on the tablet (never one that sends the
  text online). Cantonese is only read by a real Cantonese voice: without one, it says so instead
  of reading in Mandarin. To add one: Speech Services by Google → Install voice data →
  Chinese (Hong Kong).
- **Which engine reads** (1.35.6). Settings → Voice: Automatic (the tablet's default, trying the
  others if it won't start) or a particular engine, such as Google's or the tablet maker's, with a
  Mandarin sample to try it. Google's Mandarin voices (labelled "cmn") are recognised.
- **Core words.** Home → Core words: the most-used words of Mandarin (2,500, with HSK 3.0
  levels), Cantonese (1,500, from spoken Hong Kong Cantonese) and French (1,575), ranked by
  frequency and sorted by part of speech (nouns, verbs, adjectives, measure words, particles…).
  Cut the list to the top 100, 500, 1,000 or 2,000; search by any form (`allons`, `yeux`), by
  Pinyin or Jyutping, or by English. Each word's card has its reading, Listen and Practise, a
  gloss, your mark (new, learning, known), Add to flashcards and Ask the teacher (the tutor's
  chat opens with the question in the box). French verbs show every tense (présent through
  passé simple, with the passé composé's auxiliary and agreement), nouns their gender and
  plural, adjectives their four forms; Mandarin words their measure words and other readings;
  the little words (了, 嘅, 咗, que, en…) a short note with examples. Marks are in backups, and a
  line shows how much of everyday text your known words cover. The lists are built by
  `scripts/core_words/` (sources and licences in its `SOURCES.md`) and ship inside the app.
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
- **Home page.** Opens by default (Settings → Appearance → Open on the Home page). Upright it's
  one page; sideways (the tablet in landscape, an open foldable) it's two, each one screen
  tall. The left page is now: the time, today in Chinese, a hello, Continue and New chat,
  **Today** (flashcards due, what the Lobster brought back, SlothDesk's tickets, the weekly
  backup) and the day's Chinese. The right page is places: **Favourites**, Learn, Know (SlothBooks,
  Notes, Later) and Do (SlothDesk, the Lobster, Ourjong). OLED black with a moss-green accent,
  kept for the main action and live numbers; coral is the Lobster's. The Library (what SlothLlama
  searches: files and Wikipedia) sits in the bottom row with Models.
- **Favourites.** Star a persona in Personas (☆ beside ⋯) and it gets a chip on Home and leads
  the Personas page. The Trader, the Librarian, the Mandarin and Cantonese partners and the
  General Assistant are starred to begin with. Long-press a tutor's chip for a five-minute drill.
- **Arrange.** Home → Arrange, then tap a tile and the one to swap it with (Favourites, Know or
  Do). Done saves the order.
  Back returns to where you came from; Home is in the menu.
- **Small touches on Home** (each one on or off in Settings → Home). Under the date, a faint
  almanac line: today's festival or solar term (the 24 节气, worked out from the Sun's position
  in China's time) or the next one, the New Year countdown in its last two months, and the
  Sun's sign; the double hour (时辰) follows the lunar date, and a tap on the date opens the
  almanac. A hello with your name that notices the day (a birthday, a festival, the season
  turning, something from the Lobster, cards waiting), a hello in Mandarin or Cantonese (tap
  to hear it), and a sloth with a coffee in the morning or a lantern on festival days.
  **A little Chinese**: a word of the day (Mandarin and Cantonese in turn) to hear, keep as a
  flashcard or ask the teacher about, and an idiom with its story. Seven dots for the week's
  practice, and "last practised" on each favourite tutor.
  SlothDesk's fresh tickets in Today, the Lobster's top headline on its tile, and an Ourjong
  tile that opens the mahjong app if it's installed. All offline, from short lists in the app.
- **Later, a read-it-later shelf.** In any app, share a link and pick **Save for later**: the
  SlothLobster fetches a clean copy and SlothLlama shelves it under Later in the Reader, with
  minutes to read, a progress bar, Done to archive, favourites and a copy to the Library. Any
  text in the Reader can be **read aloud** with the tablet's own voices (Chinese and Japanese
  paragraphs get their own), with speed and pause; the page follows along. Nothing leaves the
  tablet except the Lobster's fetch, and the shelf is in your backups.
- **One design kit.** Personas is a grid of tiles grouped by language (tap to chat, ⋯ to
  customize or give one its own model); the Lobster page leads with a slim status line, a calm
  order box and the newest catch as a card, the rest as rows; SlothDesk shows tonight's tickets
  as a table you mark in place, one strategy in full and the others as rows. The rules are in
  [docs/DESIGN.md](docs/DESIGN.md).
- **Landscape.** On a tablet held sideways the chat list stays open beside the chat, Home is
  two pages, Settings uses two columns, and Models and Personas show cards side by side.
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
  add to the Library, keep a PDF or EPUB in SlothBooks, or send a link to the Lobster). Chats
  open with it attached and the
  request in the box; nothing is sent until you tap send.
- **SlothLobster mailbox.** SlothLlama stays offline; the SlothLobster, a separate app, goes
  online for you. They share only an encrypted mailbox folder (same tablet, or synced to a phone
  with Syncthing). The Lobster page (Settings → SlothLobster) sets it up, sends orders
  (research, fetch, watch, brief), shows whether the Lobster is awake, and lists what it brought
  back for you to file in the Library, Notes or a chat (a fetched PDF or EPUB goes straight to
  SlothBooks and Documents/SlothBooks instead). In a chat, "ask the lobster to …" sends
  an order too. The format is in [docs/LOBSTER_MAILBOX.md](docs/LOBSTER_MAILBOX.md). **How to ask** (under the order box)
  explains how the Lobster works, with examples for each kind, and **Draft it in a chat** has
  SlothLlama shape a rough idea into a well-formed order. Old orders fold away after a day;
  messages show only Read and Dismiss; previews show plain text.
- **SlothDesk (paper trading).** Home → SlothDesk runs four starter strategies (gold trend with a
  GVZ filter, the gold/silver ratio, USD/JPY carry, a modelled GLD covered call) on daily prices,
  on the tablet, with the same engine and numbers as the Colab notebook: fills at the next open,
  slippage, commission, borrow, financing and FX swap costs. Each strategy shows its held-out
  years against buy-and-hold and against buy-and-hold at the same risk, a rerun with costs
  doubled, the **deflated Sharpe ratio** (luck from trying many settings, counted on the tablet),
  a reshuffled-history range, and six **gates** (enough trades, holds up held out, survives
  doubled costs, not luck, nearby settings agree, paper-traded by the rules). Tonight's ticket
  for each comes with a drawdown guardrail and a **paper journal** (followed or not, and which
  slip). Prices come from the Lobster (a Prices order, once or every day; ETFs need a free Tiingo
  key in the Lobster) or a prices file from Colab. "Ask about this" attaches a report to a chat,
  which explains it; the numbers are always worked out in code. Nothing connects to a broker.
- **Reader.** Tap **Read** on a catch (the morning brief, a fetched page, research), a Library
  document or a note to read it in full: headings, lists and links laid out for reading, text
  size (A− / A+), a white page for daylight (the sun / moon button; it stays as you left it),
  find in text, and it reopens where you stopped. Tap a link to have the Lobster
  fetch it, or pick several headlines with **+** and send them together; the reader itself never
  opens a link. Press and hold a paragraph to save it to Notes or ask a new chat to explain,
  simplify, translate or sum it up (the passage goes along as an attachment). Share sends the
  text as Markdown. On a tablet held sideways the catch, the Library and Notes sit beside it.
- **Backups.** Settings → Backup: one file with chats, custom personas, flashcards, notes, the SlothBooks shelf and settings,
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
