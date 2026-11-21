# CONTEXT.md: the shared language

The words this project uses, so anyone working on it (a person or an agent) means the same thing
by them. When a word here fits, use it, in code, commits and docs alike. When it doesn't, add it.
Details live elsewhere: `android-project/README.md` (what each part does), `docs/DESIGN.md`
(how it looks), `docs/LOBSTER_MAILBOX.md` (the contract between the apps).

## The two apps

- **SlothLlama** ("the Llama"): the offline app. A local language model (llama.cpp, GGUF files)
  and everything around it, on one Android tablet. It has **no internet permission**; the build
  checks that. Package `app.localmind.assistant`, folder `android-project/`.
- **SlothLobster** ("the Lobster"): the online sister app. It researches, fetches, watches and
  makes the edition, and brings results back. Package `app.slothlobster`, folder `lobster-app/`.
- **The mailbox**: the only link between them. A shared folder (same tablet, or synced with
  Syncthing) of encrypted files. Avoid: "API", "sync", "server".
  - **Order**: a file the Llama writes asking the Lobster for something. Kinds: research, fetch,
    watch, brief, ask, prices, covers, edition. Avoid: "request", "job" (except in the Lobster's
    own UI, "takes the job").
  - **Catch**: a file the Lobster writes back with what it found. Filed (to the Library, Notes,
    a chat or Later) or dismissed by the user. Avoid: "result", "response".
  - **Status** (the heartbeat): the Lobster's "I'm awake" file, about once a minute.
  - **STOP**: an empty file that pauses the Lobster.
  - **Pairing code**: the shared key (43 characters), shown on the Llama's Lobster page.

## Home

Home is one screen upright (except for the day's Chinese, which may scroll). Its parts, top down:
the date and almanac, the hello (with the **status line**: calm things such as "backup saved
Wed"), **Continue** and **New chat**, **Favourites**, then three pillars and a quiet row.

- **Learn**: the tutors (as favourites and practice dots), Core words, Flashcards, Dictionary.
- **Know**: SlothBooks, The Sloth Express, Atlas, Later.
- **Weigh** (was Do, until 1.37): SlothDesk, Forecasts, Projects; Sports joins it.
- **Quiet row**: Chats, Personas, Models (with the model's name), Library, Notes, the Lobster
  (coral with a count when there's a catch), Ourjong (a separate mahjong app, opened from here),
  Arrange.
- **Lit**: a tile that needs you shows it on the tile itself (a count on the icon, an accent
  line). There is no "Today" row any more; nothing shows twice. (In code the count is
  `HomeTile.badge`; a persona's **badge** is its mark, 普 or Au.)

## Personas

- **Persona**: a character the model plays, with its own instructions, badge and optionally its
  own model. Built-in ones can't be deleted.
- **Tutor** / **partner**: a language persona (Mandarin 普, Cantonese 粵, Japanese 日, French,
  German, Spanish, Italian). "Partner" is the conversation partner of a language.
- **The Trader** (badge Au, in gold): the precious-metals sales-trader persona; it also sits on
  the edition's desk.
- **Drill**: a five-minute practice round a tutor runs (long-press a tutor's chip).
- **The Scribe** (badge PM): writes project documents from a project's facts sheet; gaps stay
  [bracketed].
- **The Grill** (badge ?!): interviews you about a plan, one question at a time, branch by
  branch, then writes the decision record. It asks; you decide.

## Learn

- **Core words**: frequency word lists per language (Mandarin with HSK 3.0 levels).
- **Flashcards**: spaced repetition (FSRS). "Due" means due for review today.
- **Dictionary**: offline CC-CEDICT with Jyutping for Cantonese; tap a word to look it up.
- **Practice week**: the seven dots on Home, filled for a day with any practice.

## Know

- **Library**: what SlothLlama itself reads and searches: your files and offline Wikipedia.
  Set up once, like Models. Not the same as SlothBooks.
- **SlothBooks**: what *you* read: books and papers on **shelves** (All, Reading, Up next, To
  read, Read, Reference, and your own), with the **Librarian** persona. Covers come through the
  Lobster.
- **Later**: the read-it-later shelf, read in the **Reader**.
- **Notes**: plain notes.
- **The Sloth Express** ("the edition", "the paper"): a newspaper the Lobster makes at set times
  from the user's feeds. Motto: "Slowly digesting a rapidly changing world". Its parts:
  - **Plan**: the edition's settings, sent to the Lobster as an order.
  - **Masthead**, **top stories** (carried by several outlets), **sections**, the **markets
    strip** (from SlothDesk's prices), the **desk** (a persona's notes on the day, written in a
    chat and kept with "Keep on today's desk"), and the **language corners** (the day's stories in
    a tutor's language, with readings and key words).
  - **Contents bar**: the jump-to row under the top bar.
- **Atlas**: the world map on the tablet (Natural Earth, Equal Earth projection, borders **de
  facto**: as held on the ground). **Looks**: Night, White, Paper. **Relief**: the shaded relief
  layer, on or off. **Places**: the place a story or article names, found on the tablet, linking
  to the Atlas. **Your places**: pins for today's edition and the books' countries.

## Weigh

- **SlothDesk**: paper trading, never real money. **Strategies** (rule-based, run on daily
  prices), **tickets** (the paper trades they produce, to mark how they went), **Ideas** (your own
  trade ideas, to review later) and **Calculators**. Prices come from the Lobster.
- **Projects**: your own projects, one record each. **Phase** (Initiating, Planning, Executing,
  Monitoring, Closing), **brief**, **milestones** ("late" once the due day has passed), **risks**
  (score = likelihood × impact, 1 to 9: low, medium, high), **stakeholders** (the power/interest
  grid: manage closely, keep satisfied, keep informed, monitor), **decisions**, **updates** (dated,
  each with your **status**: on track, at risk, off track). The **facts sheet** is the record as
  plain text with the sums done, attached to a chat. **Write it up** makes a **document**: built
  on the tablet (risk register, stakeholder map) or written by the Scribe and kept with "Keep in
  <project>". Personal projects, not team scheduling.
- **Forecasts**: every **call** (a question, its outcomes, your **chance** for each, a **check
  date**), in **areas** (Markets, Sports, Politics, Watchlist, Other). Each change of chance keeps
  its **reason**. **Marking** says what happened (or that it doesn't count). The **Brier score**
  (0 never wrong, 0.25 a coin flip on a yes-or-no) is averaged over the days you held the call;
  **calibration** is how often your calls at each level came true. A **market's chance** is one
  you entered, from a percent or a **price** (decimal, American, fractional); the **margin** is
  what a book's prices add up to over 100%. SlothDesk's ideas appear as calls, read in place.

## House rules

- **Offline means offline.** The Llama never goes online; anything online is an order to the
  Lobster, which the user can see.
- **Nothing acts in the world.** No app sends mail, trades, buys or posts on the user's behalf.
- **The model never does the maths.** Counts, sums, dates, prices and areas are worked out in
  code; the model only words them.
- **Personal details stay out of the repo.** Tests and examples use neutral data; never the
  user's own books, people or messages.
- **Saved data uses JSON files, not new Room tables**, so updates never need a migration.
- **Only syntax-checked until CI.** Changes arrive as `git am` patches; CI's build is the first
  real compile, and its release is what Obtainium installs.

## Words to avoid

| Say | Not |
| --- | --- |
| catch | result, response, reply (from the Lobster) |
| order | request, task, job |
| the edition / the paper | newsletter, feed, digest |
| SlothBooks (your reading) vs Library (the app's reading) | using either for both |
| tutor / partner | teacher, bot |
| de facto borders | "real" borders, disputed borders |
| status (yours, from an update) | health, score (nothing sets it for you) |
| facts sheet | context, prompt data |
| call, chance | bet, prediction, confidence |
| Weigh | Do (the old name) |
