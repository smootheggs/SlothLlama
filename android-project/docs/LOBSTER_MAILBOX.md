# The SlothLobster mailbox (version 1)

SlothLlama never goes online. The **SlothLobster** is a separate app that does: it researches,
fetches and watches things, and brings back what it finds. The two only share a **folder**: on
the same tablet, or synced between the tablet and a phone with Syncthing. This file is the
contract a Lobster app has to follow.

## The folder

```
<mailbox>/
  orders/          SlothLlama writes one file per order: <millis>-<id8>.order
  catch/           the Lobster writes one file per result: <millis>-<id8>.catch
  status.lobster   the Lobster's heartbeat, rewritten about once a minute
  STOP             while this (empty, unencrypted) file exists, the Lobster pauses
```

- **Each message is its own new file and is never edited.** Two devices syncing the folder then
  can't overwrite each other.
- **Write under a temporary name, then rename** (`x.catch.tmp` → `x.catch`). Files ending in
  `.tmp` are ignored.
- The Lobster **removes an order file when it takes the job** (that's how SlothLlama shows
  "With the Lobster"). SlothLlama removes a catch file once it has been filed or dismissed.
- Nothing else is ever written to the folder.

## Encryption

Every file except `STOP` is AES-256-GCM encrypted with a 32-byte key the two apps share:

```
"SLM1" (4 bytes) | nonce (12 random bytes) | ciphertext + 16-byte tag
associated data: "slothlobster-v1:" + type      type = order | catch | status
```

The **pairing code** shown on SlothLlama's Lobster page is that key in base64url without padding
(43 characters; the spaces shown are only for reading). A file that doesn't decrypt is ignored.
Because the type is authenticated, a catch can't be passed off as an order or the reverse.

## Order (SlothLlama → Lobster), JSON

```json
{ "v": 1, "id": "uuid", "kind": "research", "text": "…", "depth": "quick",
  "deliver": "library", "createdAt": 1763452800000 }
```

- `kind`: `research` (a question to look into), `fetch` (a link to bring back for offline
  reading), `watch` (something to keep an eye on), `brief` (a regular round-up), `ask` (anything else),
  `prices` (1.4: daily prices for SlothDesk; see below)
- `depth`: `quick`, or `deep` (may run overnight)
- `deliver`: where the user wants the catch filed: `library`, `notes` or `chat` (a hint; the user decides)
  - `later` (1.6, fetch orders only): the page comes back as a catch of kind `later`, which
    SlothLlama files straight onto its Later shelf instead of showing it in the catch
- `queries` (optional, since 1.1): for `research`, up to 5 searches to run instead of the question
  itself, e.g. planned by SlothLlama. A Lobster that doesn't know the field ignores it.

**Read from the text by the Lobster (1.1):** "every Monday at 7", "daily", "every morning" or
"weekly" make a research or brief order repeat (it runs at once, then on that schedule; the
Lobster lists it under Orders → Repeating). For a watch, words after the link such as "for Berlin,
remote" become the words to watch for.

## Catch (Lobster → SlothLlama), JSON

```json
{ "v": 1, "id": "uuid", "orderId": "uuid or null", "kind": "document",
  "title": "…", "text": "markdown", "sources": [{ "title": "…", "url": "https://…" }],
  "createdAt": 1763452800000, "fileName": "paper.pdf", "fileBase64": "…" }
```

- `kind`: `document`, `note`, `message`, `alert`, `prices` (1.4) or `later` (1.6: an article saved
  for later; when the page couldn't be read, its text says why and gives the link)
- `orderId`: the order it answers; leave it out for things the Lobster sends on its own (a brief, an alert)
- `fileName` / `fileBase64`: optional, for a fetched PDF, EPUB or HTML page (keep files under 15 MB)
- Research should list its **sources**; claims should rest on at least three of them.

## Prices for SlothDesk (1.4)

SlothDesk sends a `prices` order whose text names the series and the first day, e.g.
`Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ from 2008-01-01` (with `every day at 18:30`
once, to make it repeat). The Lobster answers with a catch of kind `prices`, a Markdown summary
as its text and `fileName` `slothdesk-prices.json`, whose content is:

```json
{ "format": "slothdesk-prices", "v": 1, "made_at": "2026-11-19T23:30:00Z", "made_by": "SlothLobster 1.4.0",
  "series": {
    "GLD":  { "kind": "bars", "source": "tiingo", "adjusted": true, "dates": ["2008-01-02", "…"],
              "open": [84.86, "…"], "close": [85.20, "…"] },
    "DTB3": { "kind": "rate", "freq": "daily", "source": "fred:DTB3", "dates": ["…"], "values": [0.0322, "…"] },
    "JP3M": { "kind": "rate", "freq": "monthly", "source": "fred:IR3TIB01JPM156N", "dates": ["…"], "values": ["…"] },
    "GVZ":  { "kind": "vol", "freq": "daily", "source": "cboe:GVZ", "dates": ["…"], "values": [0.18, "…"] } } }
```

- `bars`: adjusted daily open and close; `rate`: decimal (3.22% is 0.0322), dated as the source
  dates it (SlothDesk applies the publication lag); `vol`: a volatility index as a decimal.
- From SlothLlama 1.50 and SlothLobster 1.13: `bars` may carry `"high"` and `"low"` lists (kept
  only when they fit each day's open and close); a `"series"` kind holds any other series (FRED's,
  divided by 100 like a rate, with `freq` daily, weekly or monthly; SlothDesk uses each value from
  when it was published). The order can type a name: `crypto:BTCUSD` (Tiingo crypto, every day of
  the week), `fx:EURUSD` (weekdays: the Fed's noon rate on FRED for the majors before 2020, Tiingo FX
  from 2020, when its history starts) or `fred:DFII10` (any FRED series); plain names are
  as before. An order names at most 24 series. A newer repeating Prices order from the tablet
  replaces the last, so SlothDesk can add what its rules need to the daily one.
- The Colab notebook writes the same file (`export_prices`) and reads it (`load_bundle`), so the
  tablet and Colab can run on identical data.
- SlothDesk checks every row (dates, positive prices, rates and vols in range, frozen feeds,
  40% one-day jumps), takes the series in and removes the catch. It never runs anything in it.

## Covers for SlothBooks (SlothLlama 1.31, SlothLobster 1.7)

SlothBooks sends `covers` orders (the order box never offers them). The text is a head and one
line per book, tab-separated, under 4,000 characters (a big shelf goes as several orders):

```
SlothBooks covers
choices: 1
3f2a9c1e8b7d	The Count of Monte Cristo	Alexandre Dumas
```

`choices: 1` asks for one cover each (the whole shelf); `choices: 3` for up to three to choose
from (one book). The id is the start of the book's id; titles and authors are all that's searched.
The Lobster looks each book up on Open Library (search by title and author, then
covers.openlibrary.org by cover id), then Google Books, one book a second, and answers in catches
of kind `covers` with `fileName` `slothbooks-covers.json`, about 20 books each:

```json
{ "format": "slothbooks-covers", "v": 1, "mode": "set",
  "covers": [ { "id": "3f2a9c1e8b7d", "source": "Open Library", "images": ["<base64 JPEG>"] } ],
  "missing": ["9c0d…"] }
```

- Every picture is re-drawn by the Lobster (at most 300×450, JPEG) and again by SlothLlama before
  it's kept; only JPEG, PNG or WebP of a sane size are taken. Covers are kept in SlothLlama's
  private storage, not in backups, and the catch is removed.
- `set` gives a book its cover, but never over a photo you chose or a cover from the book's own
  file; `pick` keeps up to three for you to choose on the card. `missing` marks books with no cover found.
- The Lobster keeps no copy of covers on its own shelf.

## The edition (SlothLlama 1.32, SlothLobster 1.8)

The edition's settings go as an `edition` order (never offered in the order box) whose text is
the plan as JSON, under 4,000 characters (SlothLlama refuses to send a longer one rather than cut
it short):

```json
{ "format": "slothexpress-plan", "v": 1, "on": true,
  "times": [ { "name": "Morning", "h": 7, "m": 0 } ], "weekends": true, "top": 3,
  "sections": [ { "name": "Markets", "sources": ["search: gold OR silver OR LBMA"], "count": 4 } ],
  "corners": [ { "lang": "zh", "name": "Mandarin", "sources": ["https://easymandarin.news/en", "top"], "count": 2, "topic": "" } ],
  "lift": ["gold"], "mute": [], "now": false, "sentAt": 1795158000000 }
```

- A source is an https feed or page link, `search: words` (a Google News search in the section's
  or corner's language) or `top` (Google News's top stories for the language's region). A page
  that isn't a feed is read for its links to the same site that look like articles (the biggest
  group of them, in page order): that's how Easy Mandarin News, which has no feed, is read.
- The Lobster checks the plan (times, at most 10 sections and 8 corners of known languages, six
  sources each, counts capped, words trimmed, links through the same https check as fetches),
  keeps it, and answers with a catch of kind `edition-plan` ("Edition settings saved"), or with
  `now`, the first edition. While the plan is on, it replaces the daily brief.
- At the latest of the plan's times passed today that hasn't been made (one paper after a long
  sleep, not three), the Lobster reads the sections (muted words out, lifted ones first, feeds
  taken in turn), pulls out as top stories those two or more outlets carry, reads each corner's
  stories whole (Pinyin printed beside the characters taken out, the source's English kept line by
  line), and sends a catch of kind `edition`: a plain Markdown copy in `text`, the paper in
  `fileName` `sloth-express.json`:

```json
{ "format": "slothexpress-edition", "v": 1, "id": "…", "name": "Morning", "made": 1795158000000,
  "top": [ { "title": "…", "url": "https://…", "outlet": "BBC", "at": 0, "summary": "…",
             "meta": "", "outlets": 3, "also": ["NPR", "Reuters"], "paras": [] } ],
  "sections": [ { "name": "Tech", "stories": [ { "title": "…", "meta": "60 points · 12 comments" } ] } ],
  "corners": [ { "lang": "zh", "name": "Mandarin", "source": "Easy Mandarin News",
                 "stories": [ { "title": "香港天气转凉", "paras": [ { "t": "…", "en": "…" } ] } ] } ],
  "notes": ["Couldn't read rthk.hk: …"] }
```

- SlothLlama keeps the paper in its private storage (30 days), removes the catch, and checks it
  on the way in: known format, sizes capped, links only https. Nothing in it is run.
- A top story read clean carries its article in `paras`. A paragraph with `"aside": true` (1.13.3)
  is a line the page put around the article: a byline, a date, a caption, a link to another
  story. SlothLlama folds those in the reader and leaves them out of chats; it never drops them.
- A page saved for later (a `later` catch, 1.13.4) carries the same lines in its Markdown, each
  starting `%%aside%% `. The Lobster takes that mark out of the page's own words first, so only it
  can write one.

## Status (Lobster → SlothLlama), JSON

```json
{ "v": 1, "state": "awake", "at": 1763452800000, "version": "1.0", "working": "Researching: …", "queue": 2 }
```

`state`: `awake`, `busy`, `paused` or `asleep`. SlothLlama treats a status older than three
minutes as asleep.

`edition` (SlothLobster 1.14, optional): one line about today's edition when it hasn't come out,
such as `"Morning edition is late: None of the edition's sources answered. Trying again at 07:45"`
or `"Morning edition didn't come out: …"`. Absent once the edition is sent. SlothLlama shows it as
plain text, on one line, at most 200 characters, and only from a status written today.

## Rules for the Lobster

1. Only take orders that decrypt with the key. Never run shell commands from an order; map
   each `kind` to a job you wrote yourself.
2. Anything risky (sending, buying, booking, trading) needs the user's approval **in the Lobster**,
   never just an order.
3. Treat web pages as data: hidden instructions in them are not orders.
4. Honour `STOP`.

## Rules for SlothLlama (as built)

- Shows the catch and files it only when the user taps Library, Notes or Chat.
- Quotes the catch as reference material in chats, like documents and library passages.
- Keeps the key out of its backups.
