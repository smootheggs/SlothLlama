# SlothLlama design kit

The rules the Home, Personas, Lobster, SlothDesk and SlothBooks pages follow. Keep new screens to them.

## Three kinds of card

- **Hero**: one per screen, for the thing that matters now (the newest catch, the strategy
  shown in full, New chat). It may have one filled button.
- **Row**: for lists. A mark or icon, one line of title, one line of detail, and ⋯ for
  everything else. A tap does the main thing (open, read, show).
- **Tile**: for grids (personas, your things). A tap is the main action; edits live in ⋯.

## Rules

- One filled button per card. Everything else is a quiet text action, ⋯, or a long press that
  also has a menu line (long presses aren't discoverable on their own).
- Status as marks, not sentences: the six questions as dots, practice dots, a coloured status dot, ✓ / ✕ rings.
  Say it in words for TalkBack (`contentDescription`), not on screen.
- Two columns from about 600 dp wide (grids), and two panes from 840 dp (`WIDE`).
- Sections are at least 32 dp apart (Home's pages: 20–48 dp, spread to the screen); a section
  label sits 14 dp above its content. When a page is taller than the screen it scrolls; gaps
  never shrink below their minimum to fit.
- Touch targets are at least 44–48 dp, even when the mark inside is small.

## Navigation and chips

- Tabs (SlothDesk's Strategies / Ideas / Calculators) are plain text with a short moss underline:
  navigation is quieter than any button.
- Two chip styles only: quiet chips (outlined, soft moss when picked) for filters and choices,
  and the one filled moss button per card for the main action. A filled pill is never a tab.
- Text fields on one line have a fixed height (48 dp) and a hint smaller than typed text, so
  fields side by side line up.

## Home

Home is one page upright and two pages sideways (the tablet in landscape, an open foldable held
sideways: from 720 dp wide when wider than tall). Each page is one screen tall; its sections
spread over the height, 20–48 dp apart, and the page scrolls only if they can't fit.

- **Left page, you** (1.44): the hello with its status line, Continue and New chat,
  **Favourites** (a chip per favourite persona, + to choose them in Personas), **Learn** (the
  week's practice dots and the word tools as quiet links) and **A little Chinese**.
- **Right page, places**: **Know** (Library, News, Atlas, Later) and **Weigh** (Markets, Sports,
  World, then Your record), with taller tiles. Neither page scrolls.
- **The quiet row** runs under both pages, the full width, so its links fit on one line with
  their names: Chats, Personas, Models, Notes, Projects, the Lobster, Ourjong, Arrange. (Until
  1.44 it shared the right page and wrapped onto two lines.)
- **Names**: plain on Home, the rooms' own inside. Library opens SlothBooks (and What the llama
  reads, its last tab); News opens The Sloth Express; Markets opens SlothDesk.
- Upright, the same sections in that order on one page; the word sits beside the idiom when
  there's room.
- **A place lights up when it needs you; nothing shows twice.** Unread news puts a moss count on
  The Sloth Express's icon and a moss outline round it; a catch does the same in coral on
  SlothLobster; cards due turn Flashcards moss; tickets to mark or ideas to review turn SlothDesk's
  line moss. Calm things (backup saved, nothing new from the Lobster) share one quiet line under
  the hello, with a red dot when the backup failed.
- **Arrange** is tap to swap: tap a tile, then the one to trade places with, in the same
  section (Favourites, Know or Do). Tiles go dashed while arranging and the picked one turns
  soft moss. The order is saved; a new tile keeps its place after the saved ones.

## Colour

| Role | Where |
| --- | --- |
| Canvas, Card, Elevated | OLED black, then two steps up |
| Moss (`Accent`) | SlothLlama: the one main action, live numbers, "done" |
| Coral (`Lobster`) | Anything the Lobster brought or is doing; its Send button |
| Amber (`Warning`) | Prices, and stale data |
| Green / red (`Success` / `Danger`) | Questions answered yes / no, followed / not |

Colour means "needs you". Something healthy (a saved backup, marked tickets) is grey with at most
a small green dot; a page where everything is fine should be mostly grey. Icons stay neutral
(`Muted`) unless their colour carries meaning, and tiles don't carry chevrons: the whole tile is
the button.

A second line on a tile is for live information (2 items, Awake, Prices to 19 Nov), never a
description of what the tile is.

## Type

- Titles: the system sans, Medium, 18–22 sp. Section labels: 12 sp capitals, 1.4 sp tracking.
- Reading and the hero catch's headline: serif.
- Figures on the Desk: fixed-width digits (`fontFeatureSettings = "tnum"`) so columns line up.
- Chinese gets its own size (Settings → Chinese → Character size) and its romanization in a
  quieter colour beside or beneath it.

## Marks

The sloth on its llama for SlothLlama; coral for the Lobster. Small touches (a coffee, a lantern,
sleepy z's) are decoration: hidden from TalkBack and switchable in Settings → Home.

## Home, kept to one screen

- No Today row (1.34): what needs you lights up its own tile instead (see Home above), and the
  calm things are one line under the hello.
- Know and Do are one row each: up to four tiles when each still gets 120 dp. The icon sits beside
  the name whenever every name fits, at equal widths or, failing that, widths that follow the
  names; only then does it go above. Know: Library, News (no line under it: its unread count
  rides on the icon), Atlas, Later. Notes joined Chats, Personas and Models in the quiet row at
  the bottom. The day's Chinese may still scroll; that's allowed.

## The Sloth Express

- A masthead (the name, the motto in serif italics, a double rule, the dateline between rules),
  the markets strip, then the paper: top stories, the sections, the desk, the language corners.
  Wide: the news on the left, the desk and the corners on the right.
- A contents bar stays under the top bar: each part with its unread count, the part you're in
  picked, a tap to jump. Each section's head says what's unread and has "Mark read".
- Stories are separated by hairlines; a read story folds to its faint title. The bookmark sends it
  to Later through the Lobster.

## Atlas

- A map, not a street map: Equal Earth (equal-area, so areas measured on it are true), Natural
  Earth's seven map colours so neighbours never match. The sea is drawn (with the globe's edge
  and a 30° grid, 15° closer in), so countries never float on black.
- Three looks of its own, whatever the app's theme (1.35): **Night** (muted land on an ink-blue
  sea, moss for the picked country), **White** (pale land, flat and quick to read) and **Paper**
  (an old school atlas: cream land, sepia lettering, grey-green seas with two water lines along
  every coast, sepia-red for the picked country). The page's own colours follow the look.
- **Shaded relief** is a switch (on by default): Natural Earth's shaded relief, projected to
  Equal Earth, as light and shadow over the land, so mountains read in any look. It's an image
  of about 3 MB in the app, drawn only for the part in view.
- Lettering as an atlas sets it: countries in spaced serif capitals on a soft halo, oceans in
  spaced italics, seas in italics from a little closer, capitals as small rings. The picked
  country lifts (a soft glow and a firm line) and the rest dim a little; its subdivisions are
  dashed inside it. A scale bar for the middle of the view and the data's credit sit in the
  bottom corners.
- Labels thin out as you zoom out (Natural Earth's label rank decides) and never overlap; capitals
  appear from a little closer. English is always there: the card's title, the list's first column.
  The map's labels can switch to a tutor's language.
- Upright, the map takes the top half edge to edge and the card fills the rest: the picked
  country's (facts as small tiles, neighbours as pills to hop to, talk, quiz, open its page), or
  with nothing picked the world's (search, your places, the quizzes). Sideways, the card sits
  beside the map. "Open" gives the country its page:
  its own map of subdivisions, facts each with where they come from, neighbours to hop to, today's
  stories and your books set there, and the tutor of its language to talk about it with.
- Places in the edition and the Reader are quiet accent links ("Guangdong ›"). A country opens
  the Atlas on its card; a city or region (or a name more than one country has) opens on the
  place itself, pinned, with its own card: its name, what it is ("A city in California, United
  States"), a quiet "Why here" box when the name was a choice, and the way to the country's card.
- Your places (1.46) are never a bare switch. Two chips sit on the map's top corner, each with
  its mark and count ("Today's stories · 6", "Your books · 3"). Stories are round pins on the
  places themselves, books square ones at the country's label; each carries its name ("Los
  Angeles · 2", "Japan · 2 books") right of the mark, else left, else under it, else none, and
  the map's own lettering makes room for them. A tap docks a small card in the map's bottom
  corner (never over the pin): the headlines with "Read ›", and "Country card ›". With nothing
  picked, the world's card lists them: today's stories by place, your books where they're set.

## The Sloth Express (the edition)

- A paper, not a feed: the masthead's serif name over a small-capitals line (edition, day,
  stories, minutes to listen) and a double rule. Sideways, two pages: the news on the left (the
  markets strip, top stories, sections), the desk and the language corners on the right.
  Upright, one column.
- Stories are serif titles with one line of summary and faint small print (outlet, "3 outlets",
  "2 h"; Hacker News's points and comments). No links shown.
- Reading (1.45). A tap opens the clean text whenever the story has it, and marks it read: it's
  already on the tablet, so it opens at once and never waits on a browser. A story without it
  (Google News links, paywalls) says "· page" in its small print, and a tap opens its page in the
  browser's in-app tab, opened by the Lobster (SlothLlama stays offline); Back returns to the
  paper. The tab's toolbar button is Reload; its ⋮ adds Read it clean in SlothLlama and Save for
  later. The tab is full screen: a side-by-side tab would sit over the Lobster's hand-off, not the
  paper, so the paper beside it wouldn't answer touch (Android's split screen still puts them side
  by side). (1.43 and 1.44 opened the page first; pages stalled on first load in the tablet's
  browser, and turning off QUIC didn't help.)
- The clean text is the Reader's own page (text size, white page, read aloud, find, Ask, places),
  titled "Read clean", with the story's lead picture and "Open the page" / "Save for later" at the
  top and the end. Upright it has the screen; sideways it sits beside the paper, which goes to one
  column. An outlet can be set to open the page instead (the long-press menu, "A tap on … opens");
  the choice is per outlet, kept on the tablet, and starts as clean text.
- A long press opens the story's menu: Open the page, Read it clean (or "Page only:" and why),
  Save for later, Ask about it in a chat, Show it on the Atlas, Mark as read or unread, and what a
  tap on this outlet opens. Upright a sheet from the bottom; sideways a popover beside the row.
- The bookmark saves for later: at once when the clean text is here (it goes gold), otherwise the
  Lobster fetches the article.
- The markets strip is worked out on the tablet from SlothDesk's prices: last close and the move,
  muted, with ▲ ▼ rather than red and green.
- The desk is one card per desk persona (the Trader to start; any persona can be seated). Its notes
  are written in a chat from a card of the edition's headlines and prices, and kept on the edition
  with "Keep on today's desk"; they're marked model-written, not advice.
- Language corners are quiet: a badge (普 粵 日 Fr De Es It), the source, the story. Chinese has its
  reading over each word (Pinyin or Jyutping), 简 / 繁 word by word, faint underlines for words
  above your level, and a tap looks a word up. Key words come from the dictionary and the word lists,
  never a model. "Talk it over" opens the language's partner with the story.

## The Briefing

- A part of the edition, after the sections and before the desk, with the pasted paper's source
  and time in small print. Each story is a row: the rank in faint serif, the headline in serif,
  its source rating as three small dots at the right. Opened: the place (a link to the Atlas)
  and region, what happened, Why it matters in muted type, and What's unclear folded away.
- The source rating is the briefing's own, so it's dots, never a word, with its note in small
  print ("Its sources: …").
- The watchlist keeps What it saw (a list) apart from Its guess, as it labelled it, set in
  italics, so the two never run together. The preface and anything that didn't fit are folded at
  the top and foot.
- Actions are quiet text buttons. The Lobster's coral is only on the ones that send it an order:
  Check with the Lobster (a story) and Lobster: watch the news (a watchlist line).

## SlothBooks

- Shelf, Papers and Insights are text tabs, like SlothDesk's. Sideways, the list sits beside the
  picked book's card; upright, a tap opens the card.
- **Reading now** is the shelf's hero (filled); books are rows with a small spine mark (no
  covers: the app is offline) and a status mark: ○ to read, ◐ reading, ● read.
- A card goes catalogue, **You**, **Gist**, **On your shelf too**, **Recall**, with one filled
  button, Talk it through. Your words lead; anything the model wrote carries a faint mark saying
  where it came from ("general knowledge, not the text"). A novel's gist stays folded until you
  mark it Read.
- Counts on Insights are worked out in code and say so.
- The shelves row comes first: default shelves (can't be removed), a hairline, then yours and
  "+ New shelf" (dashed). A picked shelf of yours shows its ⋯ (rename, remove).
- Group by and the view switch (list, small covers, large covers) sit on one line under it;
  once books scroll under them, a hairline and a short fade mark the edge.
  Group headers stay pinned while you scroll. Stacks and copies keep a long shelf short.
- Made covers: the title in serif and the author on a dark colour by genre, so the grid never
  has holes; a real cover replaces it when there is one.

## Projects

- The list is cards: the name in serif, the phase as a small label, the status you gave (a dot:
  green, amber, red; hollow before the first update), a thin progress bar from the milestones,
  the next milestone, and amber only for what's late or a high risk.
- A project's page: the five phases as one segmented line (tap to move), facts the code counted
  as small tiles, then one filled button (Write it up) and two outlined ones (Grill the plan,
  Write an update). The record follows in cards; sideways, Brief, Milestones and Stakeholders on
  the left, Updates, Risks, Decisions and Documents on the right.
- The app never sets a status: the latest update's is the project's. Risk scores show as a number
  in a soft circle with its band beside it.
- Documents read like a page: a serif title, small-caps headings, the chat's markdown for the
  rest. A note at the foot says whether it was built on the tablet or kept from a chat.

## Weigh and Your record

- Home's pillars are Learn, Know and Weigh. Weigh is for things with a chance attached: SlothDesk,
  Sports and World, with **Your record** as one bordered line under the tiles
  (calls, Brier, beat the reference; "N to mark" in moss). Projects, the Lobster and Ourjong are
  places you go, so they sit in the bottom row; when the names don't fit on one line, the Lobster,
  Ourjong and Projects keep only their marks first, then every link but Models.
- **Call it** is one sheet: upright it rises from the bottom (at most 720 dp wide); on a screen
  840 dp or wider lying on its side it opens at the right, 460 dp, so what you were looking at
  stays in view. The chance is large in moss with a slider and one-tap presets (the model's, the
  market's, the crowd's, round numbers); the check is either a line saying how it settles by
  itself or a few date chips.
- Marking happens where the call was made: a pick by its score; a watch item by "Did this
  happen?" at the top of the paper (a soft moss card, one at a time); a price call by a "Check this
  result" card that shows the close, its source and a second source with a tick, and needs a
  confirm. The reasoning question is optional, quiet pills: Sound, Partly, Shaky.
- Your record: the calibration card (what you said across, how often it happened up; dots grow
  with the number of calls), the Brier score with a thin gauge toward 0.25, beat the reference in
  moss, then By place, Reasoning and result (four cells), To check and Checked. Sideways: the
  card and reasoning on the left, places and lists on the right.
- A call's page: the question in serif, your chance large in moss, a market's in grey, the days
  to the check date, whether it beat its reference, what settled it, the trail of your chance
  (solid) and the market's (dashed), and your reasons newest first.
- Sideways (840 dp and wider, lying on its side), lists keep their place on the left and what you
  open shows beside them: Sports' games, SlothDesk's price calls (chart and calls left, checks and
  the new call right).

## World

- Rows like Sports': the question in serif, the platform small, a small grey line of the crowd's
  chance, the crowd's chance and its move this week, then yours in moss with its check date (or
  Call it). The crowd is always grey, you are always moss, as market and model are in Sports.
- News on a call and settlements to confirm are soft moss cards at the top, one per call, each
  with its source; nothing settles without your tap.
- A market's page: the question, the crowd, you, the gap, the check date; the trail (you solid in
  steps, the crowd dashed); How it settles in the market's own words; your reasons. The Lobster's
  coral only on "Ask the Lobster" and "Lobster: watch the news".

## Music

- One page under Learn with six tabs as pills: Tuner, Metronome, Chords, Scales, Songbook, Ear.
  Each tab is a few cards; the big number (the note, the tempo) is serif, moss when it's right.
- The tuner's needle: ticks every 5 cents, the centre in moss; the needle's bar is amber when off
  and moss within 5 cents. The words say which way to go ("tighten a little", "push the mouthpiece
  in"), never just a number.
- Chord boxes are drawn from the shape (strings up, frets across, × and ○ above the nut, the fret
  number beside a shape up the neck), so every chord has them. Tapping one plays it.
- The songbook shows chords over their words as pairs that wrap together, so a narrow screen
  never splits a chord from its word. Section names are small moss capitals.

## Sports

- Pills for Games, Tables and Your picks, then the leagues you follow, greyed. Games are rows:
  the time, "Lakers at Warriors" (the away side first for American leagues; "Arsenal v Chelsea"
  for football), the score, or the model's favourite and its chance.
- A game's page reads top down: the two sides with their records and form, what goes in
  (ratings, your factors, the expected margin), the games played out (a histogram of margins, the
  home side's wins in moss, the spread dashed), then model and market as paired bars, the model's
  in moss over the market's in grey. Football has the score grid instead, darker for likelier.
- The Lobster's coral appears only where the Lobster acts: "Ask the Lobster". Results credit
  their sources at the foot of the page.
