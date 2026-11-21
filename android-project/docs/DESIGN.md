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
- Status as marks, not sentences: gate dots, practice dots, a coloured status dot, ✓ / ✕ rings.
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

- **Left page, now**: the hello with its status line, Continue and New chat, and **A little
  Chinese** (the word and the idiom as compact lines, ⋯ for flashcards or the teacher).
- **Right page, places**: **Favourites** (a chip per favourite persona, + to choose them in
  Personas), **Learn** (the week's practice dots and the word tools as quiet links), **Know**
  (SlothBooks, Notes, Later) and **Do** (SlothDesk, the Lobster, Ourjong) three a row, then the
  quiet row: Chats, Personas, Models, Library, Arrange. SlothBooks is what you read; the Library
  is what SlothLlama reads, set up once like Models.
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
| Green / red (`Success` / `Danger`) | Gates passed / failed, followed / not |

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
  names; only then does it go above. Know: SlothBooks, The Sloth Express (no line under it: its
  unread count rides on the icon), Atlas, Later. Notes joined Chats, Personas,
  Models and the Library in the quiet row at the bottom. The day's Chinese may still scroll;
  that's allowed.

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
- Places in the edition and the Reader are quiet accent links ("Guangdong ›"); they open the
  Atlas on that country.

## The Sloth Express (the edition)

- A paper, not a feed: the masthead's serif name over a small-capitals line (edition, day,
  stories, minutes to listen) and a double rule. Sideways, two pages: the news on the left (the
  markets strip, top stories, sections), the desk and the language corners on the right.
  Upright, one column.
- Stories are serif titles with one line of summary and faint small print (outlet, "3 outlets",
  "2 h"; Hacker News's points and comments). A tap marks a story read (it goes faint); the bookmark has
  the Lobster fetch it to Later. No links shown.
- The markets strip is worked out on the tablet from SlothDesk's prices: last close and the move,
  muted, with ▲ ▼ rather than red and green.
- The desk is one card per desk persona (the Trader to start; any persona can be seated). Its notes
  are written in a chat from a card of the edition's headlines and prices, and kept on the edition
  with "Keep on today's desk"; they're marked model-written, not advice.
- Language corners are quiet: a badge (普 粵 日 Fr De Es It), the source, the story. Chinese has its
  reading over each word (Pinyin or Jyutping), 简 / 繁 word by word, faint underlines for words
  above your level, and a tap looks a word up. Key words come from the dictionary and the word lists,
  never a model. "Talk it over" opens the language's partner with the story.

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
