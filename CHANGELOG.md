# Changelog

Every release of SlothLlama and the SlothLobster, newest first, in the words of the change itself.
Each GitHub release carries its entry from here; this file keeps them all (GitHub keeps only the
newest five releases of each app).

## SlothLlama 1.51.0 — 2026-11-22

**Flashcards that don't give the answer away**

On the phone and the tablet, upright and sideways:

- **Review and Browse are separate.** Review shows only the card: tap it (or Show answer) to flip,
  and the four grades sit at your thumb, or down the right edge on a tablet held sideways, each
  saying when the card comes back. Browse lists the cards with their meanings hidden until you
  tap one. Before, every card's meaning was listed right under the card being reviewed.
- **Ask with the word, the meaning, or both.** The meaning→word side opens once you've recalled
  a word across a day (recognise first, then produce) and keeps its own schedule, as Anki's
  reverse cards do. In Mixed, a card shows one way per day.
- **Type the answer first**, if you like. A near miss is pointed out: one character off in
  Chinese or Japanese, missing accents, a missing article, a typo (never in words under four
  letters, where a typo is often another word). It suggests a grade; you still choose.
- **Best practice from Anki:** new cards 20 a day; a missed card comes back three cards later in
  the same session; Undo takes back the last answer; cards missed 8 times or more are flagged
  as "keeps slipping", worth rewriting or giving an example.
- **The end of a session** says how many you got right first time, lists the ones you missed,
  and lets you go over them again as practice, which changes no schedule.
- Hide or show the pinyin (or other reading) on the front; choose which languages to review.
- With a keyboard: Space or Enter flips, 1 to 4 grade, Ctrl+Z undoes.
- Deleting a card from Browse can be undone. Your review settings and the meaning-side schedules
  are in backups (study/study.json); the cards stay in the database, unchanged.

## SlothLlama 1.50.3 · SlothLobster 1.13.2 — 2026-11-22

**No more "Edition edition"**

- A paper made on demand (Send now) is the "Extra edition", in the Sloth Express's header,
  masthead and past papers, and in the Lobster's log and notification. Before, the Lobster named
  it "Edition" and both apps added "edition" again. An edition named "… edition" by you is never
  given the word twice.

## SlothLobster 1.13.1 — 2026-11-22

**FX pairs and FRED series arrive**

- USDJPY and any `fx:` pair asked from before 2020 failed with "The site answered 400": Tiingo's
  FX history starts in January 2020. The majors (USDJPY, EURUSD, GBPUSD, AUDUSD, USDCHF, USDCAD,
  USDCNY) now come from the Fed's daily noon rates on FRED, with history to the 1970s or 1999 and
  no key, but without highs and lows. Tiingo serves later starts and other pairs, from 2020 at the
  earliest, and stands in if FRED fails.
- DTB3, JP3M, DEXJPUS and other FRED series failed with "stream was reset: INTERNAL_ERROR". FRED
  is now asked under the Lobster's own name instead of a browser's, and any request whose stream a
  site resets is tried once more over HTTP/1.1.

## SlothLlama 1.50.2 — 2026-11-22

**A new rule fetches its own prices**

- The rule editor offers **Fetch** when a rule needs prices the Desk doesn't have yet (a FRED
  series such as DFII10, an FX pair, a ticker), instead of asking for a prices file. It asks the
  Lobster now and adds them to the daily order, if you have one.
- The Desk's "Your rules need … Fetch them" line now counts rules you haven't tested yet too.
  Before, a new rule needing a new series could wait forever: it couldn't be tested without the
  prices, and the Desk only fetched for tested rules.

## SlothLlama 1.50.1 — 2026-11-22

**The Trader knows the rule card by heart**

- The rule card's forms now live in the Trader's persona, so a card asked for in plain chat
  reads on the Desk too, not only one asked for from an idea. A draft asked for from the Desk
  no longer sends the forms a second time.
- The forms now show the Size line (equal, equal risk, steady risk, risk per trade with a stop)
  and "short TICKER 100%", which the Desk always read but the Trader was never shown.
- The Trader says plainly that SlothDesk tests holding, shorting and rotating prices (delta
  one), not options, and offers the nearest delta-one rule when an idea needs options.
- A test checks that every exit, sizing and action the forms offer reads on the Desk, so the
  forms and the reader can't drift apart.

## SlothLlama 1.50.0 · SlothLobster 1.13.0 — 2026-11-22

**SlothDesk reads any market: crypto, FX, FRED series, and daily highs and lows for ATR**

The fourth stage of the new SlothDesk:

- The Desk's prices order now asks for whatever your rules read, each from its source: any
  ticker (Tiingo), crypto (Tiingo, every day of the week), any FX pair (Tiingo) and any FRED
  series (no key), with the shelf's series first. "Your rules need …" on the Desk fetches what's
  missing in one tap, and "Add to it" brings the daily order up to date (the Lobster keeps only
  the newest daily prices order).
- Bars carry daily highs and lows where the source has them. SlothDesk keeps them only when they
  fit each day's open and close (a rounding hair is widened; a series with more than 1% of days
  wrong loses its highs and lows, and says so).
- New block: ATR, Wilder's average true range as a share of the price ("GLD's 14-day ATR").
- Stops can be set in ATRs: a plain one at the entry's ATR, a trailing one on today's. Risk per
  trade sizes against them, the classic way to hold the same risk on calm and wild markets.
- FRED series read as a "series", from when each value was published (daily, weekly or monthly).
- A rule card and the editor know the new blocks; a rule that needs highs and lows the prices
  don't have says so.

## SlothLlama 1.49.0 — 2026-11-22

**SlothDesk: one path for every idea, Idea → Test → Paper → Review, with Sunday's review**

The third stage of the new SlothDesk. The Desk's front is now the path:

- Your ideas, each where it is: written, its rule being tested (with its try count and
  verdict), on paper (day 23 of 60), or ready for its review. Furthest along first.
- An idea is in your own words: the idea, why you think so, what would prove it wrong, how
  sure you are, and when to review it. It's kept as you wrote it, before any test, so the
  review compares what happened with what you expected then.
- A rule to test it: ask the Trader for a draft, write it yourself, or start from the shelf.
  The Trader writes a short rule card under its reply; take it to the Desk, and the idea shows
  the draft in plain sentences (or the lines it couldn't read) before you open it in the editor.
  A card is read as data, every part checked against the rule's limits; nothing is tested or
  traded until you tap Test.
- Paper it: from the six questions, a rule goes on paper for 60 days. Only rules on paper get
  tonight's tickets now, each with why (every condition as it stood at the close, with its
  numbers). Mark each one followed, or not and what happened instead: late, skipped it, traded
  early, changed the size, didn't trust it. The paper page shows the 60 days as marks, the share
  followed, the slips and the rule's own result. Testing a new version starts its 60 again.
- Sunday's review, about five minutes, looks back on the week from Monday: the tickets and
  slips, each rule's week beside plain holding, each idea beside the week (you judge whether it
  fits), trade ideas due and price calls. Then your call for each rule (keep it, change a
  setting, take it off paper) and one line for the week ahead. The Trader can talk it through.
- The shelf's four are starting points: copy one and it becomes your idea, with its tries.
- New idea from this story, in the Sloth Express story menu: the headline comes with it.
- Strategies you'd been marking tickets for stay on paper from their first day; the rest
  stop writing tickets until you paper them. Trade ideas with levels are on their own tab.

## SlothLlama 1.48.0 — 2026-11-22

**SlothDesk: write your own rules, and every test answers six plain questions**

The second stage of the new SlothDesk:

- The rule editor. A rule reads as sentences: "When GLD closes above its 200-day average, then
  hold GLD 100%; otherwise cash." Tap any part to change it: the signal and its days, above or
  below or crossing, a number, another signal or its own percentile; add conditions (all or any
  of them, true N days in a row, or checked only before entering), the calendar, more branches,
  exits, a drawdown brake. Actions hold weights, rotate into the best of a list, or size with a
  signal.
- The trading assumptions sit apart from the rule: costs, the next-open fills, how often it's
  checked, sizing and the total allowed, the no-trade band, what it's compared with, each
  asset's market and carry.
- Start from scratch, or copy one of the four on the shelf and change it. A copy brings the
  settings already tried on the shelf, so the luck question doesn't start over.
- Each version you test is one more try, counted for good; undo goes back to the last test or
  the one before. Edits are saved as you go, once they're within the limits.
- The report asks what the six gates did, in plain words: did it beat simply holding, did it
  hold up on years it never saw, could luck explain it (too few trades always could), does it
  survive doubled costs, do nearby settings agree, have you followed it on paper. Each answer
  opens to the numbers behind it, and a rule passes all six exactly when it passed the six
  gates. Paper days still coming in read "under way", not failed.
- "Do nearby settings agree?" now works for any rule, varying its first two numbers.
- A rule on crypto alone is measured over crypto's 365-day year; everything else over 252
  trading days.
- Your rules join the Desk once tested, with tonight's tickets and the paper journal, and come
  with you in a backup (checked against the limits when they're read back).

On the big tablet the editor shows your rules and the shelf on the left and the assumptions on
the right; with the phone held sideways, the rule and the assumptions sit side by side; upright,
it's one column with the assumptions folded away.

## SlothLlama 1.47.0 — 2026-11-22

**SlothDesk's strategies become rules: the engine behind "Idea → Test → Paper → Review"**

The first stage of the new SlothDesk. Nothing on screen changes yet; underneath, a strategy is no
longer code but a rule made of blocks, the same ones the rule editor will offer (stage 2):

- Signals: price, simple and exponential averages, momentum (with a skip), the high and low of
  the days before, how far off its high, how stretched (one asset, or a ratio of two), RSI,
  realised volatility, and any series: a level, its change, or one series minus another, each as
  it could be known at the close (rates when they were published).
- Conditions: above, below, at least, at most, crossing; N days in a row; any of, not; the
  calendar; a signal's own percentile; and conditions only for entering (a filter, a crossing).
- Actions: hold weights (long or short, a pair), rotate into the strongest of a list, scale with
  a signal. Sizing: as written, equal money, equal risk, a steady risk, risk per trade; a cap on
  the total. Exits: back within a band, after N days, a stop or trailing stop, a target. A
  drawdown brake. Checked daily, weekly or monthly, with a no-trade band for small resizes.
- Markets: FX and crypto costs, carry accrued daily, and crypto's seven-day year.

The four strategies on the shelf are now rules from these blocks. They decide exactly what the
hand-written ones did: the same weights on every day of every setting in their checks, the same
reasons, and still the Colab engine's numbers. A rule can't look ahead (each is run on history
cut short and must agree with itself), and anything outside the limits (branches, conditions,
ranges, tickers, size) is refused, not run.

## SlothLlama 1.46.0 — 2026-11-21

**Your places on the Atlas, with their names and stories**

The "Your places" layer was a bare switch that put an unnamed ring on each country, and a place tapped in a story opened only its country. Built as drawn in the "Atlas, your places" mockup:

- Two chips on the map's corner, each with its mark and count ("Today's stories · 6", "Your books · 3"), switched on their own and remembered.
- Stories are round pins on the places themselves (the place finder's new spots: the cities and regions a story names, a country only where it names nothing inside it, and not a region that only says where a city is, so "Los Angeles, California" is one pin). Books are square pins at the country's label. Each carries its name ("Los Angeles · 2", "Japan · 2 books") right of the mark, else left, else under it, else none; the map's own lettering makes room for them.
- A tap docks a small card in the map's bottom corner: the headlines, each with "Read ›" (opened in the Express as a tap there would: clean or its page, by the outlet's setting), and "Country card ›". With nothing picked, the world's card lists today's stories by place and your books where they're set; a row shows its pin on the map.
- From a story or an article, a city or region (or a name more than one country has) opens on the place itself: pinned, framed, with what it is ("A city in California, United States", worked out from the subdivisions' outlines) and a "Why here" line when the name was a choice. A plain country opens on its card as before.

The place finder's hits now carry the place, its point, the other countries it could have been and why it's this one (beside, elsewhere, bigger). Only stories in a kept paper open from a pin, through the paper's own paths. Tested on the Atlas's data.

Also in this release: the quiz's map takes the new pin list.

## SlothLlama 1.45.1 — 2026-11-21

**A story's Los Angeles is California's**

The place finder kept one place per name, and when two cities shared a name the first in the Atlas's data won: Chile's Los Ángeles (spelled "Los Angeles" in Natural Earth, rank 4) came before Los Angeles, California (rank 0), so a story there pinned Chile. 20 names were affected (Vancouver, Sydney, Santiago, Portland, Hyderabad, San Jose...).

Now every place a name could be is kept, one per country, and the one meant is: a country or region named right beside it ("Los Angeles, California", "Hyderabad in Pakistan", "Chile's Los Ángeles"); else a country named elsewhere in the text, but only between places of about the same size (so "Chile's president visited Los Angeles" stays in California); else the bigger place and the higher kind. Tested on the Atlas's own data.

## SlothLlama 1.45.0 · SlothLobster 1.12.1 — 2026-11-21

**Clean text first, record in its places, signature checks**

A tap on a story opens its clean text whenever it has it: it's already on the tablet, so it opens at once and never waits on a browser. Pages kept stalling on first load in the tablet's browser, and turning off QUIC didn't help. A story without clean text says "· page" and opens its page as before; an outlet can still be set to open the page from the long press menu (the setting is now the outlets that open the page).

Your record leaves Home: it's reached from Sports, World, SlothDesk's menu and the paper's due calls. Home shows its line only when one of your own calls is due, as no tile lights up for those. Sports' "Open Forecasts" button says Your record.

Security: each app now checks the other is signed with your key before handing it anything (the hand-off permission is the Lobster's, so if the real Lobster were gone a look-alike could claim its name and the permission). The README's security model covers the hand-off and web content, with rules every new feature keeps.

## SlothLlama 1.44.0 · SlothLobster 1.12.0 — 2026-11-21

**Home rebalanced, one Library, Reload**

Home sideways: the two pages share the work. Left is you (the hello, Continue and New chat, Favourites, Learn, the day's Chinese); right is places (Know and Weigh, with taller tiles, and Your record). The quiet row runs under both, the full width, so its links fit on one line with their names; before, everything but the hello sat on the right page, which scrolled while the left ended half way, and the quiet row wrapped.

Plain names on Home, the rooms' own inside: Library opens SlothBooks, News opens The Sloth Express (its top bar now carries the name, the edition beneath), Markets opens SlothDesk.

The Library is one place: SlothBooks' Shelf, Papers and Insights, and What the llama reads (the documents and offline Wikipedia SlothLlama searches), as its last tab. A chat's Library button opens straight there; the quiet row's Library link is gone.

The Lobster's Chrome tab gets Reload as its toolbar button: the browser's own refresh isn't in every browser's menu, and a first load can stall. The Lobster keeps one connection to the browser's in-app view service (which also warms the browser up, as Chrome advises) and sends the story again through the same session, so it loads in the open tab. Save for later moves to the tab's menu, beside Read it clean. The Log now names the browser that opened each story.

## SlothLlama 1.43.1 — 2026-11-21

**Follow a market shared from Kalshi's app**

Kalshi's app shares links as kalshi.com/s/m/TICKER?r=code, which the Follow box didn't know (it took only kalshi.com/markets/...). Its last part is the same ticker the Lobster looks up, so it's read the same way; the referral code is dropped and never sent anywhere.

## SlothLlama 1.43.0 · SlothLobster 1.11.0 — 2026-11-21

**Read the Express**

A tap on a story opens its page in Chrome's in-app view, opened by the Lobster, so SlothLlama still never goes online. Chrome's own menu has Refresh, Find in page and Open in Chrome; the Lobster adds Save for later (also the toolbar button) and Read it clean in SlothLlama.

The Lobster now reads every news story clean with the paper: the text, as its reader boils it down, and the lead picture re-drawn as a small JPEG, one page at a time within eight minutes a paper. Google News links, paywalls and sites that don't answer are marked page only, with why. SlothLlama draws each picture again before keeping it beside its paper, and prunes it with the paper.

Read it clean opens in the Reader (text size, read aloud, find, Ask, places) with the picture and Open the page / Save for later. Upright it has the screen; sideways it sits beside the paper. A long press opens the story's menu, a sheet upright and a popover beside the row sideways: open the page, read it clean, save for later (at once when the text is here), ask about it, the Atlas, read or unread, and whether a tap on that outlet opens the page or the clean text.

The hand-off is guarded both ways by the Lobster's signature-level permission (only apps signed with the same key hold it). The Lobster opens only links it brought in a paper or brief; SlothLlama acts only on stories in a paper on the tablet. A side-by-side Chrome tab isn't used: it would sit over the Lobster's hand-off, not the paper.

## SlothLlama 1.42.0 · SlothLobster 1.10.0 — 2026-11-21

**World**

SlothLobster 1.10 takes a new order, World: the crowd's prices for the markets you follow on Polymarket (Gamma API) and Kalshi (public trade API). It reads each market's yes price, whether it's open or settled, its result and its rules. No key, no account, nothing traded and nothing about you sent. A watch now remembers the order that set it up, so its alerts carry that order's id. Prices take DEXJPUS, FRED's USD/JPY rate on its own.

SlothLlama 1.42 adds World under Weigh. Follow a market by pasting its link; the crowd's chance and its move this week show on each row, and the Lobster is asked for fresh prices when you open World (at most every six hours). Call it on a market and the crowd's chance at that moment is the call's reference. A Lobster news watch on a call shows its alerts on the call ("Still 55%?" with -5 / Keep / +5). When a market settles, the result is shown with its source and you confirm it. Your own questions and the briefing's watch items sit here too. Sideways, the list and the market's page sit side by side. World is in backups (what you follow, the crowd's history, the watches).

Also in this release: fix the build.

## SlothLlama 1.41.0 — 2026-11-21

**Weigh, streamlined**

Calls are made where you see them and marked where you made them. Call it is one sheet everywhere: a bottom sheet upright, a side panel when the tablet lies on its side. It opens from a game row in Sports, a watchlist line in the Express's briefing, and a level in SlothDesk.

A watch item asks "Did this happen?" at the top of the paper when its day comes. SlothDesk gains Price calls: GLD, SLV or USD/JPY above or below a level at the close on a day. The closes SlothDesk keeps propose the result, with FRED's daily rate as a second source for USD/JPY, and nothing is marked until you confirm. A close within 0.1% of the level, sources that disagree, or no prices for the day: it asks instead.

Forecasts becomes Your record, one line under Weigh: calibration, the Brier score, the record by place, how often you beat the reference (the market, the price or the crowd when you called), and reasoning against result for calls you rate as you mark them. Projects moves to the bottom row. Sports, price calls and Your record go side by side in landscape.

## SlothLobster 1.9.1 — 2026-11-21

**Fetch sports results by day or month**

ESPN's scoreboard answers 400 to a date range (20261101-20261107); it takes a single day (20261115) or a month (202611). Ask day by day for a short window and month by month for the season so far, keep only games inside the window, and skip a day or month that fails rather than dropping the whole league. Field paths checked against a live answer.

## SlothLlama 1.40.0 — 2026-11-21

**The Briefing**

A briefing written elsewhere (a scheduled Grok task, say) can't always be shared, so it's pasted into The Sloth Express as its own part. It's read into stories (region, what it says happened, why it matters in its view, what's unclear, its own source rating as dots) and its watchlist, with what it saw kept apart from its labelled speculation. Nothing is guessed: what doesn't fit is kept as written.

A story opens its place on the Atlas, goes to the Lobster to check, or to the Trader to talk over. A watchlist line becomes a watch item (a Watchlist call in Forecasts, with or without a chance) or a news watch for the Lobster. Briefings are kept 30 days, are in backups, and the desk's card sees the last day's.

Forecasts' trail no longer assumes a call has an estimate.

Also in this release: fix the Songbook's auto-scroll import.

## SlothLlama 1.39.0 · SlothLobster 1.9.0 — 2026-11-21

**Sports**

SlothLobster 1.9 takes a new order, Sports: results and fixtures for the NBA, NFL and football leagues from ESPN's public scoreboard (with the day's line where ESPN shows one), and F1 from Jolpica-F1. No key, nothing about you sent; fetched a week at a time with pauses, and sent back as a "slothsports" file.

SlothLlama 1.39 adds Sports under Weigh. Pick the leagues you follow and ask the Lobster once: this season so far, then every morning. Tables are counted from the results. A game's page has ratings from the results (Elo for points sports, Poisson attack and defence for football), your factors with their reasons, and your prices, typed or pasted in any style with the margin taken out; the game is played 20,000 times on the tablet for each result's chance, the cover and the total, fair odds and the edge against the market. F1 races give each driver's chance to win, reach the podium or score. A pick goes to Forecasts with your chance and the market's, and marks itself when the result comes in; Your picks shows how often you beat the closing line.

Paper only: nothing is ever bet. Results are kept as JSON and fetched again when needed; your prices and factors are in backups. The Lobster page opens Sports from a results catch.

## SlothLlama 1.38.0 — 2026-11-21

**Music**

Home → Learn → Music, all played and heard on the tablet:

- Tuner: guitar tunings with each string's note to play, or chromatic; saxophones' written notes (alto, tenor, soprano) and which way to move the mouthpiece; A4 can be set. Pitch is found in code (YIN) from the microphone, only while the tuner is open, and nothing is kept.
- Metronome: clicks written into the audio stream sample by sample, 2–7 beats, subdivisions, tap tempo and a speed trainer.
- Chords: notes and shapes for any chord name, worked out from the notes (playable), and "name this chord" from tapped frets.
- Scales: twelve scales and modes on the neck, by degree and position.
- Songbook: pasted chord sheets (chords over words, or ChordPro), moved to another key, with a capo, chord shapes on tap, and auto-scroll.
- Ear: intervals and chord types; the tally isn't kept.

Sounds are made in code (a plucked string, a click); no recordings ship. Songs are JSON (music/songs.json), in backups; settings in their own preferences. The microphone permission was already declared for voice input; nothing new is asked for.

## SlothLlama 1.37.0 — 2026-11-21

**Weigh, and Forecasts**

Home's third pillar is now Weigh: SlothDesk, Forecasts and Projects, with Sports to join it. The Lobster and Ourjong moved to the bottom row; a catch still shows there in coral with its count. When the row's names don't fit on one line, the Lobster and Ourjong keep only their marks first, then every link but Models. The Lobster-headline setting went with the tile.

Forecasts keeps every call with a chance and a date: yes or no, one of several outcomes, or above or below a number, in Markets, Sports, Politics, the Watchlist or Other. Each change of chance keeps its reason; a market's chance can be entered as a percent or a price (decimal, American or fractional). On the check day you mark what happened. The tablet keeps the score: the Brier score, averaged over each day the call was held, and a calibration chart of how often each level came true. SlothDesk's ideas join the ledger at the conviction they were saved with, read in place, not copied.

The odds maths (conversions, the book's margin taken out in proportion, fair odds, edge) lives in domain/odds for Sports to use next. Shared record parts and editors moved from Projects to ui/common. Calls are JSON (forecasts/calls.json), in backups; no Room change.

## SlothLlama 1.36.0 — 2026-11-20

**Projects**

Home → Do → Projects keeps one record per project: its phase (Initiating, Planning, Executing, Monitoring, Closing), the brief, milestones, risks scored likelihood × impact, stakeholders on the power/interest grid, decisions, and dated updates carrying the status you give them. Progress, late milestones, days to the deadline and risk scores are worked out in code; the app never sets a status for you.

Write it up builds a risk register or stakeholder map on the tablet, or opens a chat with the new Scribe persona, a facts sheet made from the record attached and the request waiting in the box, for a charter, one-pager, status report, retrospective or closeout. Grill the plan opens the new Grill persona, which interviews you one question at a time and writes a decision record. "Keep in <project>" under either's reply files it with the project, to read, copy or share.

Stored as JSON (projects/projects.json), no Room change; in backups, where a restore only adds projects you don't have. The Scribe and the Grill aren't offered as edition desks. The Projects tile lights when a milestone is late.

## SlothLlama 1.35.7 — 2026-11-20

**Automatic finds the engine that has the voice**

On Automatic the app only used the tablet's default engine. When that engine started but had no Mandarin voice (while Google's engine, with Mandarin installed, sat unused), Listen said "No Mandarin voice installed". Now, when the engine in use has no voice for a language, Automatic asks the other engines on the tablet, Google's first, and reads with the one that has it. Only when none has it does it say so; with an engine picked in Settings → Voice, the message names that engine. The voice installer opens Google's (when it's there) rather than the default's.

## SlothLlama 1.35.6 — 2026-11-20

**Choose the voice engine**

Settings → Voice: Automatic (the tablet's default; if it won't start, the others are tried) or a particular engine installed on the tablet, such as Google's or the tablet maker's own. "Try it in Mandarin" plays a sample; "Voices and speed" opens the tablet's text-to-speech settings. Every speaker uses the pick: tutors, Core words, the dictionary, flashcards and the Reader, switching as soon as it changes. The voice installer opens for the engine in use. Only voices kept on the tablet are ever used, as before.

## SlothLlama 1.35.5 — 2026-11-20

**Google's Mandarin voices are found**

Google's engine labels its Mandarin voices "cmn" (cmn-CN, cmn-TW) where other engines say "zh", so with Google as the tablet's engine no Mandarin voice was ever found, even once installed. Both now count as Mandarin (never a Cantonese "yue" voice). And because an engine lists its voices when it starts, a voice installed while the app is open wasn't seen: when no voice is found, the engine restarts once and tries again before saying none is installed.

Tests: Google's cmn voices pick for Mandarin, its yue one for Cantonese.

## SlothLlama 1.35.4 — 2026-11-20

**The voice starts, or says why not**

Listen in Core words, the dictionary, flashcards and tutor replies said "Text-to-speech is still starting" for ever when the tablet's text-to-speech engine failed to start: the failure was treated the same as "not yet", with no retry. Now the speaker tells the two apart. If the default engine doesn't start, it tries the other engines on the tablet (Google's first); if none start, it says so and opens the voice settings. A word tapped while the engine is still starting is said as soon as it's ready, without a second tap.

## SlothLlama 1.35.3 — 2026-11-20

**PDFs show their pages again**

The viewer's clean-up read the document's state when it ran instead of the document it was set up with, so the moment a PDF finished opening, the previous clean-up closed it: the page count and sizes were already read, so every page laid out as a blank white box. It now closes exactly the document it opened, when the viewer leaves. A page that can't be drawn says so instead of staying white (and can no longer crash the viewer).

## SlothLlama 1.35.2 — 2026-11-20

**Practice calls that wait for you**

The Trader played every part of a role-play itself (the desk, the client, the pauses and the feedback) in one reply, because its instructions never said who the colleague was or when to stop. Now: the colleague is the sales trader, the Trader plays only the client, opens in two or three sentences and waits; every turn is one to three sentences in character, never the trader's lines or a script; feedback only when the colleague ends the call.

Under the Trader's replies: End call · feedback, Hint (one line out of character, then back), and New client. Its starter says who plays whom. The tutors' Role-play chip also says to play the other person and wait.

## Unnumbered changes — 2026-11-20

### CONTEXT.md: the project's shared language (2026-11-20)

A glossary of the words SlothLlama and SlothLobster use (orders and catches, the mailbox, Home's pillars, personas, SlothBooks vs the Library, the edition's parts, the Atlas, SlothDesk), the house rules, and words to avoid, so people and agents working on the repo mean the same things. Neutral: no personal details.

## SlothLlama 1.35.1 — 2026-11-20

**The map stays inside its box**

Edge to edge, the map lost the rounded frame that used to clip it, so the land was drawn over the top bar and the chips. The map now clips itself to its own bounds, wherever it's used.

## SlothLlama 1.35.0 — 2026-11-20

**The Atlas's looks, and shaded relief**

Three looks of the map's own, whatever the app's theme: Night (muted land on an ink-blue sea), White (pale and flat), and Paper (an old school atlas: cream land, sepia lettering, grey-green seas with water lines along every coast). The page's colours follow the look; the earlier white-map choice carries over.

Shaded relief, on by default and a chip to switch it off: Natural Earth's 1:50m shaded relief (public domain), projected to Equal Earth by scripts/atlas/build_relief.py into a 5120 x 2492 lossless WebP (2.9 MB) of light and shadow that works over any look. It's held as a hardware bitmap and only the part in view is drawn.

The map itself: the sea, the globe's edge and a 30-degree grid (15 closer in); a coast halo; atlas lettering (spaced serif capitals on a soft halo, oceans and seas in italics, capitals as rings); the picked country glows and the rest dim; subdivisions dashed; a scale bar for the middle of the view and the data's credit in the corners.

Upright, the map takes the top half edge to edge and a card the rest: the picked country's (facts as tiles, neighbours as pills, talk, quiz, its page), or the world's (search, your places, the quizzes). Sideways, beside the map.

Tests: whole-number populations, latitude back from the map's y, the scale bar's distances, the relief's size and shape, and every sea named over water.

## SlothLlama 1.34.2 — 2026-11-20

**The bottom row on one line**

Arrange is an icon at the end of the row instead of a link of its own, so Chats, Personas, Models with the model's name, the Library and Notes fit on one line upright. Where even that doesn't fit (the right-hand page in landscape), Models shows just the model's name beside its chip icon.

Know's tiles size short lines ("2 to read · 2 min") to stay on one line, with a little less padding beside the icon.

## SlothLlama 1.34.1 — 2026-11-20

**The Atlas opens; no line under The Sloth Express**

The map never opened: Natural Earth gives Somalia's population as 10192317.3, and a fraction where a whole number belongs stopped the countries file from reading at all. The data now has 10192317, the build script rounds populations (and makes the other counts whole), and if the map's data ever fails to open the Atlas says why instead of waiting.

Home: The Sloth Express's tile has no line under its name; the unread count stays on its icon. The motto stays on the paper's masthead.

## SlothLlama 1.34.0 — 2026-11-20

**Home without repeats, a tidier Sloth Express**

Home: the Today row is gone. What needs you lights up its own place: unread news as a count on The Sloth Express, a catch on SlothLobster, cards due on Flashcards, tickets to mark on SlothDesk. Calm things (backup saved, nothing new from the Lobster) share one quiet line under the hello, red if the backup failed. Know's tiles put the icon beside the name, with widths that follow the names when equal ones don't fit, so Home fits upright without scrolling. The Sloth Express's line on Home is its motto.

The Sloth Express: a masthead with the motto and the dateline between rules; a contents bar that stays in view, with unread counts, the part you're in picked and a tap to jump; "Mark read" per section; hairlines between stories; read stories fold to their title; the sections come before the desk and the corners upright. The top bar names the edition rather than repeating the paper's name.

## SlothLlama 1.33.0 — 2026-11-20

**Atlas, places in what you read, a one-screen Home**

- Atlas
- A world map kept on the tablet, from Natural Earth (public domain) with flag-icons' flags (MIT), built into assets/atlas by scripts/atlas/build_atlas.py. Equal Earth projection; borders de facto.
- Pinch, drag, double-tap; tap a country for its card: flag, English name always and its names in the tutors' languages, capital (or main city), subdivisions drawn inside it, neighbours by land (parts of the same sovereign apart), local time from its zone, Natural Earth's population estimate, and an area worked out from the borders.
- Country page: its own subdivision map and list (Natural Earth's Pinyin for China's provinces), neighbours to hop to, today's stories and your books set there, and the tutor of its language to talk about it with.
- Map labels in English or a tutor's language; a white map of its own.
- Search across countries, capitals, cities and provinces in any of those languages; quizzes on flags and capitals by region and on a country's subdivisions, with the missed ones again.

- Places in what you read
- PlaceFinder finds the places a text names (countries, capitals, big cities, the provinces of large countries; Chinese and Japanese names too).
- The edition marks each story's place ("Guangdong ›"); the Reader lists an article's places; "Your places" pins today's stories and your books.

- Home
- Today shows tiles only for what needs you; calm things share one line.
- Know and Do are one row each (up to four tiles): Know is SlothBooks, The Sloth Express, Atlas, Later; Notes joins the quiet row at the bottom. Later was left off Home in 1.32 when the edition tile took its place.

Also in this release: fix the build.

## SlothLlama 1.32.0 · SlothLobster 1.8.0 — 2026-11-20

**The Sloth Express**

The morning brief becomes an edition. SlothLlama sends the Lobster a plan (an "edition" order: times, sections from feeds, news searches or a page that lists stories, top stories, words to lift or mute, language corners); the Lobster makes the paper at those times and sends it back as an "edition" catch with a plain Markdown copy.

- Lobster 1.8
- Feeds: publication times, Google News outlets (and titles without their " - Outlet"), Hacker News's link lines turned into "points · comments"; per-language Google News editions.
- Edition.kt: the plan checked and capped; the edition due at the latest time passed today; muted/lifted words; stories two or more outlets carry as top stories; pages without a feed read for their article links (Easy Mandarin News); corner stories read whole, Pinyin printed beside characters taken out, the source's English kept per paragraph. Square brackets no longer break brief links.
- While the plan is on it replaces the daily brief; Settings shows it.

- SlothLlama 1.32
- The Sloth Express page: masthead, markets strip worked out from SlothDesk's prices, top stories and sections (tap to mark read, + to have the Lobster fetch to Later), the desk, language corners; two pages side by side when wide. Listen reads it aloud offline, corners in their own language. Archive of 30 days with read marks.
- Corner stories: Pinyin/Jyutping over each word, 简/繁 word by word, tap a word to look it up, English line by line when the source gives it, key words from the dictionary and word lists by your level (HSK for Mandarin, Core words for French) to add to Flashcards, and the language's partner to talk it over with.
- The desk: any persona can be seated (the Trader to start); its notes are written in a chat from a card of headlines and prices and kept on the edition with "Keep on today's desk".
- Edition settings: times, weekends, length, sections (tap to swap), top stories, lift/mute words, corners (sources, topic, count, on/off), desk personas; sent sealed through the mailbox, refused if too long rather than cut short. Settings are in backups.
- Home: Today shows the edition's unread count and lead story; a Know tile.
- SlothBooks: a hairline and fade where the books scroll under the shelf controls (the cut-off look in cover view).

## SlothLlama 1.31.0 · SlothLobster 1.7.0 — 2026-11-20

**Real covers through the Lobster**

- New "covers" order (sent only from SlothBooks): titles and authors, tab-separated, split into orders under 4,000 characters.
- SlothLobster 1.7 looks each book up on Open Library (search, then covers by id) and Google Books, one a second, re-draws each picture as a JPEG of at most 300x450 and sends batches of 20 as "covers" catches. No copy is kept on its own shelf; old Lobsters say to update.
- SlothLlama takes covers catches straight out of the mailbox, re-draws every picture again before keeping it (JPEG/PNG/WebP, sane sizes only), and never replaces a photo you chose or a file's own cover.
- SlothBooks: Find covers for the shelf (menu), and Change cover on each card: where it came from, up to three choices from the Lobster, a photo of your copy (system photo picker, no permission), or back to the made cover. Covers stay on the tablet, not in backups.
- Mailbox format documented. Tests for both apps.

## SlothLlama 1.30.0 — 2026-11-20

**Shelves of your own, grouping, covers and grids**

- Shelves: the default ones (All, Reading, Up next, To read, Read, Reference) can't be removed; make your own with + New shelf, rename or remove them (books stay), and put a book on any from its card.
- Group by author (A-Z strip, pinned headers), genre, topic (History split by region and period; your own topic wins), series, era, language or region.
- Author stacks (4+ books) open in place; copies of one book (an illustrated edition, a translation) are one row, listed on the card.
- Reference: courses, cookbooks and guides move out of To read, on import and once for shelves brought in with 1.29.
- Views: list, small covers, large covers (remembered).
- Covers: a PDF's first page or an EPUB's own cover, re-drawn and kept as small JPEGs in private storage; otherwise a made cover (title and author in serif on a colour by genre).
- An unreadable shelf file is set aside, never written over.
- Version 1.30.0 (37). Tests with made-up books.

## SlothLlama 1.29.0 — 2026-11-20

**SlothBooks, the Librarian, and papers kept on the tablet**

- SlothBooks (Home > Know, in the Library's place): Shelf, Papers and Insights tabs. Bring in a spreadsheet once (.xlsx or .csv, read on the tablet without any library; columns found by name, ISBNs and links left out), then add books in the app. Every book starts unread.
- A card per book: the catalogue (Edit details), your reading (status with dates, progress and "up to", reading language, copy, rating, mood/pace tags, why now, verdict, quotes), a gist marked with its source and folded while you read a novel, books it goes with, recall. Series and cycles keep their order; Up next is a queue.
- The Librarian persona: book club that keeps to your progress, gist cards saved to the book from the chat, quizzes, and picks from your unread shelf worked out in code. A default favourite.
- Papers: a PDF the Lobster fetches is saved in Documents/SlothBooks/Papers (EPUBs in .../Books) and gets a card; Add a PDF and Share > Keep in SlothBooks do the same. PDFs open in a built-in viewer (Android's PdfRenderer) and are indexed in the Library.
- Insights: read counts, series progress, eras, languages, regions.
- The Library moves to Home's bottom row; saved Know orders carry over.
- Shelf in backups. Version 1.29.0 (36). Tests with made-up books.

## SlothLlama 1.28.0 — 2026-11-19

**Home as two pages, favourites, arrange, white reader page**

- Home: one page upright, two sideways (tablet landscape, an open foldable held sideways). Left page is now (hello, Continue / New chat, Today, A little Chinese); right page is places (Favourites, Learn, Know, Do, the quiet row). Each page spreads over one screen and only scrolls if it can't fit.
- Today's cards are filled (status); Know and Do tiles are outlined (places).
- Favourite personas: a star in Personas, chips on Home, a Favourites section first in Personas. Long-press a tutor chip for a drill.
- Arrange: tap a tile, then the one to swap it with, within Favourites, Know or Do. Order saved in Home prefs; new tiles keep their place.
- Reader: a white page toggle, remembered, with dark status-bar icons.
- Docs: README and DESIGN.md Home rules. Version 1.28.0 (35).

## SlothLlama 1.27.0 — 2026-11-19

**One SlothDesk, and Home by Learn / Know / Do**

- SlothDesk takes in the Trading book: three tabs, Strategies, Ideas, Calculators, as plain text tabs with a moss underline (no filled pill for navigation). A Trader card saved from a chat opens SlothDesk on the Ideas tab, in the editor. The separate page is gone.
- Calculators: results live beside the inputs on a wide screen; 1W–1Y tenor buttons; the USD rate starts from the 3-month T-bill (and USD/JPY from its close) in SlothDesk's prices until you type your own, with where it came from underneath; inputs kept between visits (trading_inputs, this tablet only); one-line fields a fixed 48 dp with a smaller hint, so side-by-side fields line up.
- Home follows the three pillars: Today, then Learn (tutor chips, then Core words, Flashcards and Dictionary as quiet links), Know (Library, Notes, Later) and Do (SlothDesk, the Lobster, Ourjong) as tiles three a row on a tablet, and a last row of Chats, Personas, Models. Two rows shorter, so portrait fits one screen on the Y700. SlothDesk's tile says when ideas are due.
- DESIGN.md: navigation and chips, and the Home layout.

## SlothLlama 1.26.0 — 2026-11-19

**The Trading book (idea book and desk calculators)**

- Idea book: trade ideas written before they play out. The thesis, levels, "wrong if" and conviction are fixed once saved; dated notes are added after; a review closes each idea (right / wrong / mixed / no call, right for the reason given or not, exit and R, what happened, the lesson). Shows open and due ideas, hit rate, average R and calibration by conviction. Saved in trading/ideas.json and in backups (BackupContents.trading).
- A Trader reply with an idea card gets a bookmark action that opens the card in the editor (IdeaCard parses "Instrument: … Wrong if: …"), to make it yours before saving.
- Calculators, all in MetalsMath (unit tested): metal forwards from rate and lease, implied swap and lease from a quoted forward or EFP; metal in another currency through the FX swap (from rates or swap points) and its swap rate there; local per gram / tael / tola against London; weights and fineness; positions, P&L and contract counts.
- "Ask the Trader" sends an idea to be challenged or reviewed, or a calculator's numbers to be explained as they are. Home "Trading book" (with ideas due), SlothDesk's menu.

## SlothLlama 1.25.1 — 2026-11-19

**A Trader persona, and Cantonese read only in Cantonese**

- Cantonese no longer falls back to Mandarin. An engine without a Cantonese voice answers "Chinese (Hong Kong)" with its Mandarin one; now a voice is picked by what it really speaks (VoiceChoice), Cantonese needs a yue / zh-HK voice or at least a country match, and without one the app says how to add a voice instead of reading in Mandarin. Mandarin is never read by a Cantonese voice either. Voices that need the network are never picked by name, so text isn't sent off the tablet to be spoken.
- Trader: a built-in persona, a senior precious-metals sales trader who coaches. It reads news under five headings (what happened, why it matters, priced in or a surprise, clients, the other side), writes trade ideas as cards, and plays clients for pitch practice. It never works out prices, forwards, swap points or P&L itself.
- "Read it like a trader" in Share to SlothLlama, "Ask the trader" in the reader, and SlothDesk's "Ask about this" open a chat with the Trader (LaunchRequest.Shared.personaId).

## SlothLlama 1.25.0 — 2026-11-19

**Core words**

The most-used words of Mandarin (2,500), Cantonese (1,500) and French (1,575), ranked by frequency and sorted by part of speech, as reference lists with a card per word. No drills.

- Lists: Mandarin from complete-hsk-vocabulary (ranks, HSK 3.0 levels, measure words) with CC-CEDICT meanings and hand-picked readings; Cantonese from the Hong Kong Cantonese Corpus with its counts and Jyutping, glosses written for about 600 words; French ranked by wordfreq over a curated lemma list. Built by scripts/core_words/ (sources pinned in SOURCES.md, read as data only) and committed as assets/core/*.json.
- Page: Home -> Core words. Language tabs, part-of-speech and top-100/500/1,000/2,000 filters, new/learning/known marks (settings file core_words, in backups), search by any inflected form, Pinyin, Jyutping or English, and a coverage line for your known words. Two panes on wide screens.
- Card: reading, Listen and Practise, gloss, mark, Add to flashcards, Ask the teacher (opens the language's tutor with the question in the box, unsent). French verbs in every tense, noun gender and plural, adjective forms; Mandarin measure words and other readings; notes with examples for about 110 little words.
- FrenchConjugator.kt ports the Python engine; a unit test checks all 422 listed verbs against its output.

## SlothLlama 1.24.0 · SlothLobster 1.6.0 — 2026-11-19

**Later, a read-it-later shelf**

Save for later: in any app, share a link and pick "Save for later" (a SlothLobster share target with no screen). The Lobster fetches a clean copy and sends it as a catch of kind "later"; SlothLlama moves it straight onto the Later shelf instead of the catch. Only https links to public sites, at most 30 an hour, each logged with the app it came from. A page that can't be read still arrives, with why and the link. No notification per article. SlothLlama's own Lobster page and the Reader's link menu can send to Later too.

The shelf lives in the Reader: a Later tab (the default when there's something to read) with minutes to read, a progress bar, To read and Archive, favourites, Done at the end of an article, a copy to the Library (searchable by meaning), Delete. Home gets a Later tile ("3 to read · 18 min"); Dictionary moves to the row below.

Read aloud: any text in the Reader, with the tablet's own offline voices (Chinese and Japanese paragraphs get theirs), pause, speed and the page following along. The engine starts only when asked.

Stored as files on the tablet (no database change) and included in backups. Mailbox doc updated: deliver "later", catch kind "later".

## SlothLlama 1.23.0 — 2026-11-19

**A calmer Home**

Colour now means "needs you": cards due, a new catch, tickets to mark, a problem. A saved backup or marked tickets are grey with a small green dot, and when nothing needs you Today folds into one line ("All caught up · backup saved yesterday"). Today's tiles are slim: the value and its name on one line, then a live detail.

Tutors show the languages practised in the last two weeks (the first two until there are some); the rest fold into a "+N" chip. Their marks are outlined, filled only for a language practised today. The week's dots sit beside the label with "n of 7".

No chevrons on tiles, and second lines only when they carry live information. The idiom shows its literal sense without a tap. The design kit says so too.

## SlothLlama 1.22.1 — 2026-11-19

**The Ourjong tile finds Ourjong Dev**

Home looked only for app.ourjong; the dev build installs as app.ourjong.debug. It now opens whichever is there, release first. The manifest's package query lists both (still no permissions).

## SlothLlama 1.22.0 — 2026-11-19

**Personas, the Lobster page and SlothDesk, redesigned**

Built from the approved mockups, under one design kit (docs/DESIGN.md):

Personas: a grid of tiles grouped by language with the language's own name (普通话, 廣東話 …), partner first, then the assistants and a dashed "your own persona" tile. Each tile has a script mark (普, 粤, 师), its level and when you last chatted, Voice on, and its model only when it isn't the usual one. A tap chats; ⋯ customizes, picks a model, deletes. A strip at the top names the model they all use, with Change.

Lobster page: a slim coral status line; pausing and pairing move into ⋯. A calmer order box with a coral Send and "How to ask · Draft it in a chat" beneath. The newest catch is a hero card (serif headline, its first headlines, Read); the rest are rows with a kind mark and ⋯ to file or dismiss.

SlothDesk: one line for the prices (Update now; schedule and import in ⋯). Tonight's tickets as a table with gate dots and a ring to mark each one followed or not, plus "Mark all followed". One strategy in full with a taller chart (held-out years shaded) and four figures in fixed-width digits; the others as rows that open in its place.

## Unnumbered changes — 2026-11-19

### Home: room to breathe (2026-11-19)

The page had grown past one screen, so "spread over the height" squeezed every gap to nothing. Sections now keep at least 32 dp between them and still spread out when they fit; labels sit 14 dp above their content.

Today fits four across when there's room (two by two otherwise), with tiles sharing a height and the Lobster's headline allowed two lines. The hello no longer repeats what Today shows (the catch, cards due); it keeps birthdays, festivals and the turning season. A little more air in the date lines, the hello and the daily card.

## SlothLlama 1.21.0 — 2026-11-19

**Home's small touches, each one yours to switch**

Under the date, a faint almanac line: today's festival or solar term (the 24 jieqi from the Sun's position, in China's time) or the next one, the New Year countdown in its last two months, and the Sun's sign. The double hour follows the lunar date; a tap on the date opens the almanac.

A hello with your name that notices the day (birthday, festival, the season turning, the Lobster's catch, cards due), a hello in Mandarin or Cantonese that speaks when tapped, and sloth moods (coffee, sleepy z's, a lantern, coral for a catch).

A little Chinese: a word of the day (Mandarin and Cantonese in turn) to hear, keep as a flashcard or ask the teacher about, and an idiom with its story. Practice dots for the week and "last practised" on each tutor; long-press a tutor (or its menu) for a five-minute drill.

SlothDesk's fresh tickets in Today and a SlothDesk tile, the Lobster's top headline on its tile, and an Ourjong tile (package query only, no permission). Settings -> Home turns each touch on or off; the "home" settings file is in backups. No schema change: two read-only queries.

## SlothLlama 1.20.0 — 2026-11-19

**How to ask the Lobster, a tidier catch**

- How to ask (under the Lobster page's order box, hidden until opened): how the Lobster works without a model, a rule and tappable examples for each kind, and Draft it in a chat, which has SlothLlama shape a rough idea into a well-formed order.
- Ask is no longer offered (the Lobster runs it as Research).
- Messages and alerts show only Read and Dismiss; previews show plain text without Markdown marks or a repeated title.
- Orders brought back more than a day ago fold into "Done earlier".
- Tests for previews and tips. SlothLlama 1.20.0.

## SlothLobster 1.5.0 — 2026-11-19

**Ask runs as Research, better topic briefs**

- No model, by design: an Ask order is researched and SlothLlama answers from the sources, instead of a "no brain" message.
- An order kind this version doesn't know gets an "update the SlothLobster" message.
- Topic briefs search on key words: "updates", "top news", "prices", "etc." and the like are dropped (quoted phrases and OR are kept), and only the last two days' stories come back.
- Orders screen hints for Ask and Brief. Tests for the brief search.
- SlothLobster 1.5.0.

## Unnumbered changes — 2026-11-19

### Keep clear of the taskbar, and a Full screen option (2026-11-19)

- SlothLlama: every page now sits above the navigation bar and a tablet's taskbar, so snackbars and bottom buttons (SlothDesk, the Lobster page, the Reader) are no longer hidden behind it.
- Both apps: Settings -> Full screen hides the status bar and the taskbar, as Ourjong does; a swipe in from the edge shows them for a moment. SlothLlama: Settings -> Appearance; SlothLobster: Settings -> Look.
- SlothLlama 1.19.1, SlothLobster 1.4.1.

## SlothLobster 1.4.0 — 2026-11-19

**Prices for SlothDesk**

- A Prices order: SlothLlama's SlothDesk names the series it needs (GLD SLV USDJPY DTB3 JP3M GVZ) and the Lobster brings back a slothdesk-prices file as a sealed catch of kind "prices", once or every day ("every day at 18:30").
- ETFs and USD/JPY from Tiingo (adjusted daily open and close) with your own free key; the T-bill and Japan's 3-month rate from FRED and gold's volatility index from Cboe, no key needed. Without a key, USD/JPY falls back to FRED's daily noon rate.
- Settings -> Market data: the Tiingo key is sealed by its own Android Keystore key, sent only in a header (never in a link or the Log) and wiped from the clipboard once saved.
- Web.get can send headers; KeyVault can hold more than one secret.
- The prices file format is in docs/LOBSTER_MAILBOX.md.
- Tests for reading Prices orders. SlothLobster 1.4.0.

Also in this release: fix the build (Prices in the Orders screen).

## SlothLlama 1.19.0 — 2026-11-19

**SlothDesk**

- SlothDesk (Home -> SlothDesk): the four starter strategies (gold trend with a GVZ filter, gold/silver ratio, USD/JPY carry, modelled GLD covered call) run on the tablet with a Kotlin port of the Colab engine (slothdesk.py 0.6). Unit tests pin it to Colab's numbers on shared synthetic data.
- Per strategy: held-out years against buy-and-hold and against buy-and-hold at the same risk, costs doubled, the deflated Sharpe ratio (every setting tried is logged), the track record needed, a reshuffled-history range, why positions closed, and six gates.
- Tonight's tickets with a drawdown guardrail and a stale-data warning, and a paper journal (followed or not, and which slip) that feeds the sixth gate. Settings can be nudged within bounds; a nearby- settings check runs on the in-sample years only.
- Prices from the Lobster (once, or every evening or morning) or a prices file from Colab; every row is checked before use. A Prices catch opens SlothDesk instead of being filed.
- Ask about this attaches the report to a chat, which explains it; the numbers are always worked out in code. Nothing reaches a broker.
- The Desk's journal, trials and settings go into backups.
- SlothLlama 1.19.0.

## SlothLlama 1.18.0 — 2026-11-19

**The Reader**

- A Reader for the Lobster's catch (briefs, fetched pages, research), Library documents and notes: headings, lists, quotes and links laid out for reading, text size A-/A+, find in text, and it reopens where you stopped.
- Links never open: tap one to send the Lobster a Fetch order, or pick several headlines with + and send them together.
- Press and hold a paragraph to save it to Notes, or ask a new chat to explain, simplify, translate or sum it up. The passage goes along as an attachment, so web text stays reference material.
- Share as Markdown. Catch items can be filed or dismissed from the Reader.
- On a wide screen the catch, Library and Notes sit beside the text.
- Read buttons on catch cards and note cards; tap a Library document to read it. LibraryStore.fullText rebuilds a document from its passages; no schema change.
- Unit tests for the reader's text handling. SlothLlama 1.18.0.

## SlothLobster 1.3.0 — 2026-11-18

**White and OLED themes, a new Deck, better research pages**

- Themes: OLED black and white (or match the tablet), Settings → Look. All colours come from one theme (SeaColors); text is at least 4.5:1 in both, with a deeper coral for white. System bar icons follow the theme.
- Coral is kept for Send, new things and progress; "awake" is sea teal and problems a soft red. Chips use a coral tint instead of a solid fill.
- Rail: a gear for Settings (the old rays read as brightness), labelled, and a thin coral mark for the chosen tab instead of a filled pill.
- Deck, on one page: a status line with Pause; a New order box with a preview of the searches research will run; Today tiles (brought back, watches changed, the next brief, search engines answering); the job in hand with progress and Send now; Coming up (brief, watch checks, repeating orders); Brought back today. "$0 spent" is gone.
- Research reads the best pages across all its searches (Picks): engine order first, then title match, original and reference sources over answer farms, paywalled document sites and index pages; one page per site; tracking parameters don't make a page new; Wikipedia isn't read twice.
- Repeats.next works out when a repeat or the brief next runs.
- Tests for both. SlothLobster 1.3.0.

## Unnumbered changes — 2026-11-18

### Home fits on one screen (2026-11-18)

The portrait home spreads its sections over the screen's height instead of running past it, and only scrolls when it truly can't fit (very large text, split screen). Cards, chips and tiles are a little more compact, and the recent chats list is gone from landscape (Chats has them). SlothLlama 1.17.1.

### Home redesign and moss-green accent (2026-11-18)

- Accent is moss green on OLED black (deep moss in the light theme), with slightly green neutrals; faint text now passes 4.5:1. Coral is kept for SlothLobster things.
- Home: search and settings at the top; Continue (with when) and New chat, the page's one filled action; a Today strip (flashcards due, the Lobster's catch and status, the weekly backup); tutors one chip per language with their roles, marked by script or code instead of flags; your things with live status; Chats, Personas and Models in a quiet row.
- Touch targets at least 48 dp; headings marked for TalkBack.
- SlothLlama 1.17.0.

### Release 2: memory of you, search by meaning (2026-11-18)

- Memory: "remember that …", "forget that …" and "what do you remember about me?" in any chat; Settings → Memory to add, edit or delete. Only what the user adds is kept (the existing user_memories table, so no database change). Memories go with messages as background: all of them while few, otherwise the closest to the question. Included in backups.
- Search by meaning: import EmbeddingGemma (GGUF) once in the Library. A separate CPU-only embedding model in the llama.cpp bridge gives each passage a vector in the background, pausing while a reply is written. Library search merges word search (BM25) with meaning (reciprocal rank fusion); passages found only by meaning must pass a similarity floor.
- Embedding models no longer show up as chat models.
- SlothLlama 1.16.0.

### Fix the build: name clash in SettingsViewModel (2026-11-18)

Inside the view model, `app` is the Application, so full names starting with app.localmind… didn't resolve. Use imports instead.

### Release 1: FSRS flashcards, weekly backups, Diagnostics, Obtainium releases (2026-11-18)

- Flashcards now use FSRS-5 (default parameters, 90% retention) instead of SM-2. The memory state lives in the card's unused level column, so there's no database change; SM-2 cards carry over from their interval and ease.
- Back up every week: an encrypted backup into a chosen folder, newest four kept, password wrapped by the Android Keystore, written under a temporary name so a failure never costs the last good one. A failed run notifies.
- Backups list model and Library file names; a restore shows what to import again.
- Diagnostics in both apps: crash notes kept in private storage and a report (version, device, memory, battery setting, recent problems) to copy.
- Both workflows publish signed sloth-terminal builds as GitHub releases for Obtainium, from a separate job that alone can write to the repository.
- SlothLlama 1.15.0, SlothLobster 1.2.0.

## SlothLobster 1.1.0 — 2026-11-18

**Narrower watches, repeating orders, multi-search research, better briefs, reader**

- Renamed to SlothLobster (one word) in both apps; SlothLlama also takes "slothlobster: …"
- Watch only one part of a page (pick a heading) and/or only lines with chosen words; orders like "watch <link> for Berlin, remote" set the words
- Repeating research and briefs: every day or every week at a time, from the Orders tab or from SlothLlama ("every Monday at 7 …"); listed under Orders → Repeating
- Research takes several searches (extra lines, or the order's new optional "queries"), never reading the same site twice
- Briefs arranged by feed group ("# F1" lines), feeds importable from a news reader's OPML file, and no headline sent twice
- Search: Brave and Mojeek join DuckDuckGo and Bing; failing engines go last and the Log and Settings say so
- Everything brought back is kept to read in full in the app, with tappable sources
- Unit tests for the new parsing (watch parts and words, repeat rules, searches, feeds, results)

## Unnumbered changes — 2026-11-18

### Security: pinned build steps, read-only builds, safer pairing code, no replayed orders (2026-11-18)

- Both workflows: every third-party action pinned to an exact commit (a moved or hijacked tag can't swap in code that would see the signing key), and the builds' GitHub token is read-only
- SlothLlama's build no longer falls back to downloading unverified SDK tools
- SlothLlama marks the copied pairing code sensitive (hidden from the clipboard preview and keyboard suggestions); the Lobster wipes it from the clipboard once pasted
- The Lobster ignores orders it has taken before and orders older than 14 days

### Sloth Lobster: fix the FlowRow import so the app compiles (2026-11-18)

## SlothLobster 1.0.0 — 2026-11-18

**SlothLlama's sister app, the one that goes online**

A separate app in lobster-app/ (its own Gradle build and workflow, so SlothLlama's APK never carries network code). A sloth riding a lobster, OLED black with a coral accent, for phone and tablet (bottom tabs on a phone; a rail and two panes on the tablet).

- Mailbox: pairs with SlothLlama's code (kept wrapped by the Android Keystore), takes orders from the shared folder, sends back sealed catch, writes the heartbeat every minute, honours STOP
- Free jobs, no model: research (Wikipedia plus DuckDuckGo or Bing, 5 to 9 sources gathered for SlothLlama to answer from), fetch (page text, PDF or EPUB), watch (pages or news topics, alerts on change), brief (daily headlines from feeds, or a topic)
- "Ask" needs a model, which is off: the Lobster tells SlothLlama and suggests Research
- Deck, Orders, Watch, Log and Settings screens; share a link to it from any app
- HTTPS only, no addresses on your own network, pages read as text only, no backups
- Stays awake with a quiet notification, or checks every 15 minutes
- Unit tests for the mailbox format, link safety, search parsing, page reading, feeds and diffs

## Unnumbered changes — 2026-11-17 to 2026-11-18

### Tighten: tests, offline check, previews, one memory, safer key (2026-11-18)

- Tests: unit tests for the calculator, units, time zones and dates, the tool router (timers, alarms, notes, Lobster orders, and sentences that must not trigger anything), the library/tool message blocks, search words, passages and the mailbox encryption. CI runs them in their own job beside the build; a failure is flagged without holding back the APK.
- Offline promise checked on every build: CI fails if the finished APK asks for any network permission.
- Dead code and libraries out: the unused cloud-model hook, the old vector store and source validator (and their tests), OkHttp and security-crypto. The chat database is untouched.
- Tool preview: while you type, a faint line above the box shows what the tools would do ("Will set a 10 min timer", "12 × 7.5 = 90", "Will send this to the Lobster"). Nothing runs until you send.
- One memory: the library search (and so the library switch in chats) also finds your notes, which come first when they clearly match.
- "What's the date in 100 days" is a date sum, not today's date.
- Lobster: the mailbox key is wrapped by the Android Keystore (an older plain key is moved over); a check every 15 minutes (a folder read, no network) sends a quiet notification when the Lobster brings something back, and opens its page; notifications are asked for when it's set up.
- README: the vision, three pillars, design rules (emoji) and guarantees.

Version 1.14.0 (17).

### Share to SlothLlama, and the Sloth Lobster mailbox (2026-11-18)

Share to SlothLlama: text, links, photos, PDFs and books shared from any app open a small sheet with what fits: explain, summarize, translate, read or translate a photo's text, save to Notes, add to the Library, or send a link to the Lobster. Chats open with the content attached and the request in the box (a tutor chat switches to the general assistant); nothing is sent until you tap send. Files are read while the share still grants access.

Sloth Lobster mailbox: SlothLlama stays offline and shares only a folder with the Lobster, a separate app that goes online. orders/, catch/, status.lobster and STOP; every file is AES-256-GCM encrypted with a key only the two apps hold (pairing code on the Lobster page; not in backups). The Lobster page (Settings → Sloth Lobster; on Home and in the chat list once switched on) sets up the folder, sends research/fetch/watch/brief orders, shows whether the Lobster is awake, pauses it, and lists the catch to file in the Library, Notes or a chat. "Ask the lobster to …" in a chat sends an order too. docs/LOBSTER_MAILBOX.md is the contract for the Lobster app.

Version 1.13.0 (16).

### Flags, a little whimsy, better chat chips, faster CI builds (2026-11-18)

- Home: tutor chips show the language's flag (Mandarin follows the Chinese setting: 🇹🇼 for traditional, 🇨🇳 otherwise) instead of a badge that rendered blank, and tell partners and teachers apart.
- The hello on Home and the start page ends with one small emoji for the time of day; "Saved to Notes 📝" and "Added to your library 📚".
- Chips above the message box have icons and fit the persona: tutors get Correct me, Say it naturally, Translate, Simpler, then the language's own (Pinyin/Jyutping and Characters, Furigana and polite/casual, German cases, verbs and tu/vous, du/Sie, tú/usted, tu/Lei), then practice (Quiz me, Your turn, Grammar, New words, Role-play). Assistants get Simpler, Shorter, Examples, Step by step, Pros & cons, Go deeper, Table, As an email, Ask me, In Chinese, and Explain the code after code. "Save to Notes" keeps the last reply without sending anything.
- CI: llama.cpp is compiled through ccache and the cache kept between builds; Gradle's cache is saved from this branch too; the dictionary is reused; a newer push cancels a build still running.

Version 1.12.1 (15).

### Offline tools: sums, units, clock, dates, timers, notes, calendar (2026-11-17)

When a message asks for it, the app works the answer out on the tablet before the model replies, and sends the exact result with the question in a <tools> block (shown as chips on the message; later turns keep only the one-line result, so nothing is re-read):

- calculator: + − × ÷ ^ % n! roots, logs, trig in degrees, "18% of 2,450", "12 times 7.5" (ranges like 9-5 and 24/7 are left alone)
- units: length, weight, US kitchen volumes, temperature, speed, area, data, time; "how many cups in a litre"
- clock: the time in ~150 cities and countries, converting a time between them, time differences
- dates: days until / between dates, weekday of a date, N days from today
- timers and alarms, set in the tablet's clock app
- notes: "take a note: …", "show my notes", "what did I note about …", plus a Notes page (kept in preferences, so they go into backups)
- calendar: read only, switched on in Settings → Assistant
- the Chinese dictionary, and "look up … in my library / Wikipedia"

Settings → Assistant → Offline tools (on); tutors use them only when "Tools in tutor chats too" is on. Version 1.12.0 (14).

### Private library: books, documents and offline Wikipedia (2026-11-17)

Library (Home tile and chat list) indexes PDF, EPUB, HTML and text files on the tablet: they are cut into passages under their headings and put in an FTS4 index of search words (accent-folded Latin words; Chinese and Japanese as characters and character pairs, Chinese folded to simplified). Kiwix .zim files (offline Wikipedia) are read in place with libkiwix 2.7.0: title suggestions first, then full-text search, and the article's opening plus its best-matching paragraphs.

In a chat, the book button next to + sends each question with the best passages (BM25 ranking, at most three per document, about 1,500 tokens), in a <library> block the reply cites as [1], [2]. The user's message shows a "From your library" chip that opens to the passages. Earlier lookups are only named in the history the model reads, so the chat doesn't fill up and llama.cpp doesn't have to re-read it. Items can be switched off or removed, and the Library screen has a test search (side by side in landscape).

Version 1.11.0 (13).

### Japanese, French, German, Spanish and Italian tutors (2026-11-17)

- Tutors:
- Five conversation partners (日 Fr De Es It) built from one template: natural speech in the language, ✏️ corrections, ⭐ new words in the flashcard format, lessons on request.
- Settings → Tutors: the learner's level per language (Beginner, A2, B1–B2, C1, Fluent), for all seven languages including Mandarin and Cantonese (the levels used to be constants in the code). TutorRules puts the level and a matching reply format into the prompt: EN and Romaji lines up to B2, natural replies only from C1. Levels are part of backups; nothing personal is in the repository.

- Listening (sherpa-onnx, offline):
- Whisper Small joins SenseVoice: Models → "Voice input: European languages" imports the encoder, decoder and tokens (validated: ONNX header, sizes, 40k+ tokens).
- The transcriber picks per language: SenseVoice for zh/yue/ja/en/ko (faster, better at them), Whisper for fr/de/es/it and as a fallback; messages name the model that's missing.

- Everything that assumed Chinese now handles the others:
- Read-aloud and hands-free speak the right lines: Chinese as before; Japanese lines without romaji or English; for European languages the lines above each "EN:" translation.
- Repeat-after-me scores characters (Chinese; Japanese with kana folded) or words (others, ignoring capitals and accents), with words spaced in the result.
- Flashcards: five more languages; ⭐ and word-list words are picked up in letters too; the pronunciation field is optional where no romanization is used.
- Save-words and practise buttons show for every tutor (not only when a reply has Chinese) and no longer for the general assistant.
- The Chinese dictionary (tap to look up, reading checks) stays with the Chinese tutors and the general assistant.

versionName 1.10.0 (versionCode 12).

### Home page and landscape layouts (2026-11-17)

Home page (the new default on a fresh start; Settings → Appearance → Open on the Home page): the time and today in Chinese, a sloth hello, Continue and New chat, a chip for every tutor (so new tutors appear by themselves), shortcuts (Flashcards with what's due, Dictionary, Chats, Personas, Models with the loaded model, Settings) and recent chats. Two columns when the screen is wide, one otherwise.

Navigation now keeps a small back stack (saved as one string so it survives rotation): screens open on top of each other, Back returns to where you came from, from the chat Back goes Home, and from Home it leaves. "Home" is the first row of the menu.

- Landscape (840 dp and wider; a Y700 sideways is about 1,280 dp):
- Chat: the chat list stays open on the left (320 dp) with the chat beside it; the top-left button becomes Home. The slide-over drawer is portrait-only, and its open/close sync is skipped in landscape so it never animates a drawer that isn't there.
- Settings: sections in two columns.
- Models and Personas: cards in an adaptive grid (two side by side), status cards and headings full width.

versionName 1.9.0 (versionCode 11).

### Tutor replies: keep line breaks, correct readings from the dictionary, stop loops (2026-11-17)

- What the Cantonese tutor screenshot showed, and the fixes:
- Lines were run together: the Markdown parser joined a paragraph's lines with spaces, so 繁 / 简 / Jyutping / EN collapsed into one run. Single line breaks are kept now.
- Wrong Jyutping ("ngo5 lou5" for 你好, "geoi1 hau3" in 你今日好唔好): small models guess romanization. ReplyPolisher rewrites each Pinyin / Jyutping line from the Chinese line above it with the dictionary (longest-word matching), plus word-list readings and "嘅 (ge3)"-style glosses. Only the persona's language, and only when every character is in the dictionary; otherwise the model's text is kept.
- Identical 繁 and 简 lines (你好！ twice) become one "繁/简:" line.
- A reply stuck repeating a block ("繁：你好（nei5 hou5）" over and over) is stopped as soon as the block appears three times in a row, and the copies are trimmed. Such a reply doesn't offer Continue.
- Tutors were told they only see typed text, so they stop claiming to hear the learner's tones.

The engine records the polished reply in its chat mirror, so the next turn doesn't replay.

versionName 1.8.1 (versionCode 10).

### Settings: put the snackbar and backup dialogs back in SettingsScreen (2026-11-17)

Patch 1 inserted them into SettingRow by mistake, where snackbar, vm, saveBackup and backupDialog don't exist (compile error). The screen's root is now a Box so the snackbar can sit at the bottom.

### Photos and documents in chats (2026-11-17)

- Photos (vision):
- llama.cpp's libmtmd is built on its own (LLAMA_BUILD_MTMD, no video/ffmpeg) and linked into the JNI. loadProjector() loads a model's vision file (mmproj) the first time a photo is sent; processUserPromptWithImage() tokenizes the photo with the chat template and evaluates the chunks (M-RoPE positions handled by mtmd), then generation carries on as usual. Size is checked against the context before anything is decoded.
- Models: each GGUF card gets "Photos: Add / Remove". The mmproj is validated (GGUF header and general.architecture = "clip", read by a small metadata parser) and stored next to its model as mmproj-for-<model>.gguf, hidden from the model list. Importing an mmproj as a chat model is refused with an explanation.
- The photo picker (no permission) feeds a copy re-encoded without EXIF location, at most 1600 px, in private storage; the model gets 896 px RGB. Photos the chats no longer mention are cleaned up at start.
- Photo encoding uses the GPU when the model is on it, behind the same crash guard ("<model>#photos" falls back to the CPU on its own).
- The engine records a sent photo in its chat mirror as "[The learner shared a photo here.]", matching how history describes it, so follow-up questions reuse the cache (the model still sees the photo) instead of replaying without it.

- Documents:
- PDF (pdfbox-android, text of up to 40 pages; Bouncy Castle left out) or any text file (UTF-8/16, with GB18030 / Big5 fallback for older Chinese files), cut to ~2,600 tokens at a sentence boundary.
- Sent as reference material: the engine introduces it as data not to take instructions from and runs the existing prompt-injection sanitizer over it.

Both travel inside the message text (a Markdown image line and a <document> block), so there is no schema change and edits, search and backups keep working. Bubbles show a thumbnail and a document chip; titles use the typed text; editing keeps the attachments.

versionName 1.8.0 (versionCode 9).

### Offline dictionary, repeat-after-me, and hands-free conversation (2026-11-17)

- Dictionary (offline):
- CI builds an SQLite dictionary from CC-CEDICT (122k entries, CC BY-SA 4.0) with Jyutping for every entry from ToJyutping's data (BSD 2-Clause), plus 11k Cantonese words CC-CEDICT lacks. Both come from PyPI wheels pinned by SHA-256; scripts/build_dictionary.py reads them as data (no third-party code runs). The asset is copied to no-backup storage once per version.
- Tap any Chinese character in a reply: the longest word starting there opens in a sheet with characters in the chosen script, Pinyin (tone marks) and Jyutping, meanings, Listen, Practise, and save to flashcards. Contextual readings come out right (銀行 ngan4 hong4, 行人 hang4 jan4).
- Drawer → Dictionary: search by characters, by pinyin with or without tones, or by English, ranked so everyday words come first (eat → 食, 吃 before 餔).
- Settings → About → Dictionary sources shows the licence notices.

- Repeat after me:
- 🗣 under tutor replies (pick a sentence) or Practise in the dictionary. Listen reads it slowly; Say it records until you pause (energy-based endpointing against the room's noise), transcribes with SenseVoice, and colours each character heard / missed with a score.
- Alignment is a longest common subsequence after converting both sides to simplified, and a homophone the recogniser picked counts as the right sound (same pinyin/Jyutping).

- Hands-free conversation:
- Headset button in the composer: listen until a pause, send, read the reply aloud (waiting for TTS to finish via UtteranceProgressListener), listen again. Pauses after two silent turns; ■ stops it, letting a reply in progress finish as a normal stopped reply. Leaving the chat ends it. English personas are read in the tablet's own language.
- send() now goes through sendAndAwait(), so hands-free can wait for the finished reply.

versionName 1.7.0 (versionCode 8).

### GPU: run GGUF models on the Adreno GPU (llama.cpp OpenCL), with a crash guard (2026-11-17)

Build: CI prepares the Khronos OpenCL headers and ICD loader at pinned commits (scripts/fetch-opencl.sh checks each commit hash and the loader's SONAME) and builds llama.cpp with GGML_OPENCL and the Adreno-tuned kernels as a loadable backend (libggml-opencl.so). The loader is only linked against; packaging excludes it, so the tablet's own driver (/vendor/lib64/libOpenCL.so, via <uses-native-library>) is used. CI fails if the backend is missing from the APK or the loader slipped in. Local builds without the script stay CPU-only.

- Runtime:
- llama.cpp lists the GPU at start-up; Models shows it with a "Use the GPU" switch (on by default). Changing it reloads the model in memory.
- GPU loads offload every layer and warm up once, so the GPU sets itself up while "Loading" shows instead of during the first reply. CPU loads pass an empty device list, so nothing goes to the GPU, not even large batches.
- Compiled kernels are cached in the code cache (Android has no TMPDIR, so the backend would otherwise recompile every launch). The first GPU load says it takes about a minute.
- A model that won't fit on the GPU falls back to the CPU by itself.
- Crash guard: GPU drivers abort the process rather than throw. A marker is committed before the GPU load and again for the first reply, and removed when either ends in a way the app sees. If the app starts and finds it, that model goes on a CPU-only list; Models explains and offers "Try the GPU again". The setting lives in its own prefs file (it describes this tablet), so it isn't part of backups.
- Models → loaded card says GPU (Adreno 7xx) or CPU; the chat header shows the setup note.

versionName 1.6.0 (versionCode 7).

### Start page, backups, and a round of easy wins (2026-11-17)

Start page (shown on open and after New chat), in the spirit of Sloth Lobster's: the time, today in Chinese (weekday, lunar date and zodiac year from ICU's ChineseCalendar, in the chosen script), a sloth hello, tiles for Continue / Mandarin / Cantonese / Flashcards (n due), and the persona's suggestions. Offline by design, so the Chinese calendar takes the weather's place.

Backups (Settings → Backup): chats, messages, custom personas, flashcards and settings in one file; gzipped JSON, optionally sealed with AES-256-GCM (PBKDF2-HMAC-SHA256, 310k rounds, header as AAD so tampering is detected). Restore merges by id inside one transaction.

- Easy wins:
- Continue: replies that stop at the length limit are marked (errorCode note, no schema change) and get a Continue button.
- Speed and context under each reply ("18.2 tokens/s · 2,340 / 6,144 context"), and a note when older messages no longer fit. Speed now measures generation only.
- Drawer search across titles and message text (LIKE with escaping, debounced).
- Quick actions above the composer: Correct me / Quiz me / Simpler / Grammar / Role-play / New words for tutors; Simpler / Shorter / Examples / In Chinese otherwise.
- Home-screen shortcuts (Mandarin tutor, Cantonese tutor, Flashcards), published at runtime so they work for both app ids; launch requests generalised from Ask SlothLlama.
- Chinese character size (Settings → Chinese, 100–160%) with taller lines so nothing overlaps.
- Daily flashcard reminder (Settings → Flashcards, opt-in, POST_NOTIFICATIONS asked then): @HiltWorker on WorkManager, which now gets its configuration from the Application (the default initializer was removed but nothing replaced it).

versionName 1.5.0 (versionCode 6).

### Drawer: long model status no longer squeezes "Models" into one letter per line (2026-11-17)

- NavRow: the label never wraps; the trailing status takes the remaining width and is cut with "…".
- Short model names drop the quantization tag and an uploader prefix ("Qwen_Qwen3.5-4B-Q4_K_M.gguf" → "Qwen3.5-4B"), also while loading or on failure.

### Flashcards with spaced repetition, and "Ask SlothLlama" from any app (2026-11-17)

- Flashcards live in the existing vocabulary_items table (no schema change, no migration risk). SM-2 scheduling (Again 10 min / Hard / Good / Easy), the interval carrying progress.
- ☆ under any reply with Chinese opens "Save to flashcards": the reply's ⭐ words and word-list lines ("时间 / 時間 – shíjiān – time") are offered as chips that prefill editable fields, with an example sentence from the reply; save several in a row.
- Flashcards screen: review the most overdue card, reveal, say it aloud, grade with the next interval shown on each button; list of all cards with delete. Drawer shows "n due".
- "Ask SlothLlama" in other apps' text-selection menu (PROCESS_TEXT): a trampoline activity forwards the text; the chat opens a new conversation with the question waiting in the box. Nothing is sent automatically; text is capped at 4,000 characters.
- README: study features. versionName 1.4.0 (versionCode 5).

### Offline voice input with SenseVoice (Mandarin, Cantonese, English, Japanese, Korean) (2026-11-17)

- sherpa-onnx 1.13.8 Android AAR (Kotlin API + native libs, no libc++_shared, Kotlin 1.7 metadata) fetched by scripts/fetch-sherpa-onnx.sh and checked against a pinned SHA-256; CI runs it before building. The AAR itself isn't committed.
- VoiceTranscriber: imports model.int8.onnx + tokens.txt (picked together), sanity-checks them, runs SenseVoice on one dedicated thread with a language hint per persona (Mandarin tutors "zh", Cantonese "yue", others auto-detect).
- VoiceRecorder: 16 kHz mono microphone capture up to 60 s with a loudness level.
- Composer: mic button; "Listening…" bar with a voice-reactive dot, ✓ to finish, ✕ to discard; the transcript lands in the input box to check before sending.
- Models screen: Voice input card (import / progress / ready / remove).
- RECORD_AUDIO permission, requested on first tap. Audio never leaves the device.

### Per-persona models: each persona can run on its own model (2026-11-17)

- PersonaModelStore maps persona id → model file name (works for built-in personas too); personas without one use the default model from the Models screen.
- RoutingInferenceEngine.loadModel(modelId) loads that file if it exists, else the default.
- Sending a message uses the persona's model; switching persona preloads it (never mid-reply).
- Personas screen: a "Model" picker on every persona card.
- Models screen: "Default model" wording, and the loaded model is marked even when it's a persona's own model rather than the default.

### Fix chat memory: follow-ups were losing the whole conversation (2026-11-17)

The second message in a chat called upsertConversation(), an @Insert(REPLACE). SQLite's REPLACE deletes the old conversation row before inserting the new one, and the messages table's ON DELETE CASCADE then deleted every earlier message. The model only ever saw the latest question.

- Daos: conversations, messages, knowledge collections and sources use @Upsert (update in place) instead of REPLACE.
- Regenerate removed the old answer but kept the old question and then re-sent it, so the model saw the question twice; it now removes both before asking again.
- Token estimates count Chinese characters as ~1 token each (was 4 characters per token), so long Chinese chats are trimmed before they overflow the context.

### Thinking on/off for reasoning models (Qwen3.5 etc.); safer context handling (2026-11-17)

- llama_jni: detect reasoning models (chat template uses <think>). With thinking off, the reply is pre-filled with an empty think block, as the official templates do for enable_thinking=false; with thinking on, it opens "<think>" so reasoning stays hidden. New supportsThinking() / setThinking().
- shift_context() reports failure instead of corrupting memory on hybrid / recurrent models (Qwen3.5 can't drop a middle range of its memory); generation stops cleanly and the app replays a trimmed history next turn.
- Settings → Assistant → Thinking (default off). Thinking adds 1536 tokens of reply budget.
- Models screen marks reasoning models. ReplyFilter tolerates a reply that starts inside a think block and a closing tag with no opener.
- Context 4096 → 6144 tokens for longer tutoring sessions and room to think.
- versionName 1.3.0 (versionCode 4).

### Sloth-on-a-llama launcher icon; Settings → Chinese for 简/繁 and Pinyin/Jyutping (2026-11-17)

- Adaptive launcher icon (mipmap-anydpi): the sloth_llama mark on slate #1B2433, sized for the 66dp safe zone so circle, squircle and square masks all fit. Replaces the old placeholder ring icon.
- Settings → Chinese: Simplified / Traditional / Both, and a Pinyin & Jyutping toggle (default: Both + romanization). Stored in ChineseStyleStore.
- ChineseRules builds labelled "简:" / "繁:" / "Pinyin:" / "Jyutping:" rules with a worked example; the four tutor prompts now take them via {{CHINESE_RULES}} (custom personas with a Chinese voice get them appended). Cantonese shows 繁 first, Mandarin 简 first.
- Tutor reply limits raised (partners 768, teachers 1024 tokens) for the longer format.
- Read-aloud speaks only the first line of each 简/繁 pair and the first form in word lists.
- Persona editor explains the placeholder.

### Security hardening: air-gapped, private release key, model integrity, privacy screen (2026-11-17)

- Manifest: strip INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE (tools:node="remove"), so Android blocks all network access; drop unused POST_NOTIFICATIONS.
- llama_jni: stop writing the full text of every chat message to logcat.
- Release build signed with a private key from GitHub Secrets (SLOTH_KEYSTORE_B64 / SLOTH_KEYSTORE_PASSWORD), not debuggable; CI falls back to the debug build without them. Debug build is labelled "SlothLlama Dev". Artifact renamed to slothllama-apk/SlothLlama.apk.
- GGUF header validation (magic, version, plausible counts) before any file reaches the native parser, including sideloaded files; SHA-256 computed during import and on demand (Models → fingerprint) to compare with Hugging Face.
- Settings → Privacy: live network status, backup exclusion, and a privacy-screen toggle (FLAG_SECURE: no screenshots / recordings, blank in Recents).
- README: security model.
