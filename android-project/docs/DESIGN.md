# SlothLlama design kit

The rules the Home, Personas, Lobster and SlothDesk pages follow. Keep new screens to them.

## Three kinds of card

- **Hero**: one per screen, for the thing that matters now (the newest catch, the strategy
  shown in full, New chat). It may have one filled button.
- **Row**: for lists. A mark or icon, one line of title, one line of detail, and ⋯ for
  everything else. A tap does the main thing (open, read, show).
- **Tile**: for grids (personas, your things, Today). A tap is the main action; edits live in ⋯.

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

- **Left page, now**: the hello, Continue and New chat, **Today** (two by two) and **A little
  Chinese** (the word and the idiom as compact lines, ⋯ for flashcards or the teacher).
- **Right page, places**: **Favourites** (a chip per favourite persona, + to choose them in
  Personas), **Learn** (the week's practice dots and the word tools as quiet links), **Know**
  (Library, Notes, Later) and **Do** (SlothDesk, the Lobster, Ourjong) three a row, then the quiet
  row: Chats, Personas, Models, Arrange.
- Upright, the same sections in that order on one page; Today goes four in a row and the word
  sits beside the idiom when there's room.
- **Filled cards are status, outlined tiles are places.** Today's cards are filled with no
  border; Know and Do tiles are outlined. Don't mix the two in one section.
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
