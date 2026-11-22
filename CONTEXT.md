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
- **The mailbox**: the link between them for everything they exchange. A shared folder (same
  tablet, or synced with Syncthing) of encrypted files. Avoid: "API", "sync", "server".
- **The hand-off** (Llama 1.43, Lobster 1.11): the one other link, on the same tablet only. The
  Llama asks the Lobster to open a story's page in Chrome's in-app view (a "Chrome tab"); the
  tab's "Read it clean" and "Save for later" come back. Guarded by the Lobster's signature-level
  permission, so only apps signed with your key can use it. Carries a link, never data.
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

- **Learn**: the tutors (as favourites and practice dots), Core words, Flashcards, Dictionary, Music.
- **Know**: Library, News, Atlas, Later.
- **Weigh** (was Do, until 1.37): Markets, Sports and World. **Your record** is reached from
  each of them (and the News's due calls); since 1.45 its line shows on Home only when one of
  your own calls is due, as no tile lights up for those.
- **Quiet row**: Chats, Personas, Models (with the model's name), Notes, Projects, the Lobster
  (coral with a count when there's a catch), Ourjong (a separate mahjong app, opened from here),
  Arrange.
- **Plain names on Home, own names inside** (1.44): the tiles say what a place is (Library, News,
  Markets); the place keeps its name on its top bar (SlothBooks, The Sloth Express, SlothDesk).
  In talk and docs either works; the room's own name is preferred.
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
- **Flashcards**: spaced repetition (FSRS). "Due" means ready to review today: reviews due plus new cards up to 20 a day. Review and Browse are separate tabs so nothing gives an answer away; the meaning→word side has its own schedule in study/study.json (in backups).
- **Dictionary**: offline CC-CEDICT with Jyutping for Cantonese; tap a word to look it up.
- **Practice week**: the seven dots on Home, filled for a day with any practice.
- **Music**: the **tuner** (a **tuning**, or chromatic; a **horn** setting shows saxophones'
  written notes), the **metronome** (beats, subdivision, **speed trainer**), **chords** (a
  **shape** is one way to finger a chord, worked out from its notes), **scales** (a **position**
  is five frets of one), the **songbook** (your pasted **sheets**; **shift** moves the key, the
  **capo** shows the shapes you play), and **ear** training.

## Know

- **Library** (1.44): one place for reading. Its tabs: SlothBooks' **Shelf**, **Papers** and
  **Insights**, and **What the llama reads**: the files and offline Wikipedia SlothLlama itself
  searches when a chat asks (until 1.44 this alone was "the Library"; it's still that from a
  chat's Library button).
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
  - **Clean text** ("read it clean"): a story's own words without the page around it, read by
    the Lobster with the paper and kept on the tablet with its lead picture. **Page only**: a
    story without it (a Google News link, a paywall, a site that didn't answer), which opens its
    page. Avoid: "reader mode", "article view".
- **Atlas**: the world map on the tablet (Natural Earth, Equal Earth projection, borders **de
  facto**: as held on the ground). **Looks**: Night, White, Paper. **Relief**: the shaded relief
  layer, on or off. **Places**: the place a story or article names, found on the tablet, linking
  to the Atlas. A name several places share (Los Angeles, Vancouver, Georgia) goes to the one a
  region named right beside it says ("Los Angeles, California"), else to a country named
  elsewhere in the text if the places are of about the same size, else to the bigger place
  (1.45.1). A place opens on the map as itself (a city, a region), with what it is and, for a
  shared name, **why here** (1.46). **Your places**: two layers, each switched on its own chip on
  the map: **today's stories**, a round pin on each place a story names (its name and count
  beside it; a region that only says where a city is isn't pinned), and **your books**, a square
  pin on the country each is set in. A tap shows the headlines, each with **Read**, opened as a
  tap in the paper would; the card beside the map lists them by place.

## Weigh

- **SlothDesk**: paper trading, never real money. **Strategies** (rule-based, run on daily
  prices), **tickets** (the paper trades they produce, to mark how they went), **Ideas** (your own
  trade ideas, to review later) and **Calculators**. Prices come from the Lobster. A strategy is a
  **rule** (1.47): **branches** ("when … then"), each **condition** a **signal** compared with a
  value, another signal or its own **percentile**; an **action** (hold, rotate, scale); **sizing**;
  **exits** (back within, after N days, stop, trailing stop, target); a **brake**; an options
  **overlay**. A branch is held **while** its conditions hold, or **until** an exit; a condition
  only **to enter** (a filter, a crossing) doesn't keep it. Rules are data within set limits,
  never code; the four on the **shelf** are rules too. **Your rules** (1.48) are written in the
  **rule editor** as sentences of chips, or **copied** from the shelf; the **trading assumptions**
  (costs, check days, sizing, markets, carry) sit apart from the rule. Each version you **test**
  is a **try** (a copy brings the shelf's tries with it). A test's **report** answers **the six
  questions**: did it beat simply holding, did it hold up on years it never saw, could luck
  explain it, does it survive doubled costs, do nearby settings agree, have you followed it on
  paper. A rule on crypto alone counts a 365-day year. **The path** (1.49): every idea goes
  **Idea → Test → Paper → Review** on the Desk's front (the **Desk** tab; trade ideas with levels
  are on **Trade ideas**). An **idea** is in your words (why, what would prove it wrong, how sure,
  when to review it), kept as written; a **rule** tests it: the **Trader's draft** (a **rule card**
  the Desk reads and checks), one you write, or a **shelf** copy (a copy becomes an idea). **Paper
  it** puts a rule on **paper** for 60 days of **tonight's tickets** (only rules on paper get
  them), each marked **followed** or not with a **slip** (late, skipped it, traded early, changed
  the size, didn't trust it); a new tested version starts its 60 again. **Sunday's review**
  looks back on the week (Monday to Sunday): the tickets, each rule's week against holding, each
  idea against the week, price calls; then your **call** per rule (keep, change, take off paper)
  and **one line** for next week. A story's menu has **New idea from this story**. **Prices**
  (1.50): the Desk's order asks the Lobster for the shelf's series and whatever your rules read:
  any ticker, **crypto** (every day), **FX** pairs and any **FRED** series; bars carry **highs and
  lows**, for the **ATR** (a day's range as a share of the price) and **ATR stops** (risk per trade
  sizes against them). "Your rules need …" fetches what's missing; the daily order can be updated.
- **Projects**: your own projects, one record each. **Phase** (Initiating, Planning, Executing,
  Monitoring, Closing), **brief**, **milestones** ("late" once the due day has passed), **risks**
  (score = likelihood × impact, 1 to 9: low, medium, high), **stakeholders** (the power/interest
  grid: manage closely, keep satisfied, keep informed, monitor), **decisions**, **updates** (dated,
  each with your **status**: on track, at risk, off track). The **facts sheet** is the record as
  plain text with the sums done, attached to a chat. **Write it up** makes a **document**: built
  on the tablet (risk register, stakeholder map) or written by the Scribe and kept with "Keep in
  <project>". Personal projects, not team scheduling.
- **Your record** (Forecasts until 1.41; still `forecasts` in code): every **call** (a question, its
  outcomes, your **chance** for each, a **check date**), in **areas** (Markets, Sports, Politics,
  Watchlist, Other) that fall into **places** (Markets, Sports, World, your own). **Call it** is the
  one sheet calls are made on. The **reference** is the market's (or the crowd's) chance when you
  called; you **beat the reference** when your Brier score is lower than its. **Reasoning**
  (sound, partly, shaky) is rated when marking, kept apart from the result. A **price call** is a
  level by a date on GLD, SLV or USD/JPY, **checked** against SlothDesk's closes (a **second
  source** where there is one) and **confirmed** by you; its **evidence** stays on the call. Each change of chance keeps
  its **reason**. **Marking** says what happened (or that it doesn't count). The **Brier score**
  (0 never wrong, 0.25 a coin flip on a yes-or-no) is averaged over the days you held the call;
  **calibration** is how often your calls at each level came true. A **market's chance** is one
  you entered, from a percent or a **price** (decimal, American, fractional); the **margin** is
  what a book's prices add up to over 100%. SlothDesk's ideas appear as calls, read in place.
- **Briefing**: a paper pasted from elsewhere into the edition, read into **stories** (what it
  says happened, why it matters, what's unclear, its **source rating**) and its **watchlist**
  (what it **saw**, kept apart from its **speculation**). Its words, not checked on the tablet; **Check
  it** sends a story to the Lobster. A **watch item** is a Watchlist call made from one, asked
  about ("Did this happen?") at the top of the paper when its day comes.
- **World** (1.42): **markets** you **follow** on Polymarket or Kalshi (a **ref**, from a pasted
  link), the **crowd**'s chance of yes (their price, read by the Lobster; nothing traded), your
  calls against it, **news on a call** (alerts from the Lobster watch tied to it) and
  **settlements** proposed for you to confirm. Also your own questions and the briefing's watch items.
- **Sports**: the **leagues** you follow; **games** and **races** with their **results**, brought by
  the Lobster; **tables** counted from them. A game has **ratings** (Elo, or attack and defence),
  your **factors** (points, goals or places toward one side, each with a reason) and a **book**
  (the prices: money lines, the home side's **spread**, the **total**). It's **played out** many
  times for its chances; **fair odds** are 1 ÷ chance; the **edge** is the model's chance less the
  market's. A **pick** is a call in Forecasts that marks itself from the score; the **closing
  line** is the market's last price before the start.

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
| pick (paper) | bet, wager, stake |
| Your record | Forecasts (the old name), stats, scoreboard |
| Call it (make a call) | predict, bet |
| the crowd (a market's price) | the odds, the consensus, the truth |
| briefing (pasted) | report, intel, digest |
