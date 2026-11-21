# SlothLobster 🦞

SlothLlama's sister app: **the sloth goes to sea**. SlothLlama never goes online; the Lobster is
the one that does. It takes orders from SlothLlama through a shared, encrypted mailbox folder,
goes out on the web, and brings back what it finds for SlothLlama to read offline.

## What it does (free jobs, no AI model, no account)

- **Research**: searches Wikipedia and the web (DuckDuckGo, Bing as a fallback), reads 5 to 9
  pages and brings back their text with numbered sources. SlothLlama writes the answer offline.
- **Fetch**: brings back a page as clean text, or a PDF or EPUB as a file.
- **Watch**: checks a page (or a news topic) every hour, 6 hours or day, and sends an alert when it changes.
- **Brief**: headlines from your feeds every morning, or a round-up on a topic.
- **Covers** (sent from SlothBooks): looks each book up by title and author on Open Library, then
  Google Books, and sends back small covers (re-drawn as 300×450 JPEGs), a second between books.
  Only the titles and authors are searched.
- **The edition** (1.8, set in SlothLlama): the morning paper. Sections from feeds, news
  searches or a page that lists stories (sites without a feed, such as Easy Mandarin News); the
  stories several outlets carry pulled out as top stories; muted words left out and lifted ones
  first; and a corner per language with its biggest stories read whole (Pinyin printed beside the
  characters is taken out; SlothLlama adds its own). Hacker News's "Article URL / Comments URL"
  lines become "113 points · 19 comments". While the plan is on, it replaces the daily brief.
- **Sports** (1.9, set in SlothLlama's Sports page): results, fixtures and the day's line for the
  NBA, NFL and football leagues from ESPN's public scoreboard, and F1 from Jolpica-F1 (both free,
  no key). Fetched a day or a month at a time (the scoreboard takes no ranges), gently; nothing
  about you is sent. SlothLlama works out the tables, ratings and simulations itself.
- **World** (1.10, set in SlothLlama's World page): the crowd's prices for the markets you follow
  on Polymarket and Kalshi, read from their public market data (no key, no account; nothing is
  traded and nothing about you is sent): each market's yes price, whether it's open or settled,
  its result, and its rules as written. Prices also take **DEXJPUS**, FRED's USD/JPY rate on its
  own, which SlothLlama uses as a second source for price calls.
- **Ask** needs a model, which is switched off for now: the Lobster says so and suggests Research.

Orders come from SlothLlama's Lobster page, or can be given in the Lobster itself (or shared to it
from any app). Everything it opens is listed in the **Log**.

New in 1.1:

- **Watch part of a page, or only chosen words**: pick a heading on the Watch tab, or list words.
- **Repeating orders**: research or briefs every day or every week, from the Lobster or by
  writing "every Monday at 7" in an order from SlothLlama.
- **Several searches per research order**: put extra searches on new lines.
- **Better briefs**: feeds in groups ("# F1"), import from a news reader's OPML file, and no
  headline is sent twice.
- **Sturdier search**: Brave and Mojeek join DuckDuckGo and Bing; Settings shows which answer.
- **Read it all here**: everything brought back opens in full in the Lobster.

New in 1.5:

- **Ask runs as Research**: there's still no model here, by design (the Lobster reads the web, so
  it has no brain to be talked into anything); a question is researched and SlothLlama answers
  from the sources, offline. An order kind this version doesn't know gets an "update the
  SlothLobster" message instead of a guess.
- **Better topic briefs**: filler such as "updates", "top news" or "etc." is left out of the
  news search, and only the last two days' stories come back.

New in 1.4:

- **Prices for SlothDesk**: SlothLlama's SlothDesk page sends a Prices order (once, or every day
  after the close); the Lobster fetches daily prices and sends them back as a sealed prices
  file. ETFs (GLD, SLV …) and USD/JPY come from **Tiingo** with your own free key
  (**Settings → Market data**); rates come from FRED and gold's volatility index from Cboe,
  with no key. Without a key, USD/JPY falls back to FRED's daily noon rate.

New in 1.3:

- **White or OLED black**: Settings → Look (or match the tablet). Same coral, readable in both.
- **A Deck that does more on one page**: give orders right there (research shows the searches it
  will run as you type), Today in numbers (brought back, watches, the next brief, search
  engines answering), the job in hand with Send now, and what's coming up.
- **Better pages for research**: every search's results are weighed together; original sources
  (public bodies, universities, journals, documentation, established news) come first, answer
  farms, paywalled document sites and index pages last, one page per site.
- **A proper settings gear**, labelled, in the rail.

New in 1.2:

- **Diagnostics**: Settings → Diagnostics shows the version, device, battery setting, recent
  problems from the Log and any crash, to copy into a bug report. Nothing is sent.
- **Updates with Obtainium**: each signed build is a GitHub release (`slothlobster-v…`).

## Setting it up

1. In SlothLlama, open the **Lobster** page, switch it on, choose a mailbox folder (for example
   `Documents/SlothMailbox`) and copy the **pairing code**.
2. In the SlothLobster: **Settings → Paste the code**, then **Mailbox folder → Choose** the same folder.
3. Leave **Stay awake** on so SlothLlama sees it as awake (a quiet notification shows while it is).

On a phone, keep the same folder in sync with the tablet using Syncthing.

## Safety

- Every mailbox file is sealed with AES-256-GCM using the pairing key, which is kept wrapped by
  the Android Keystore. The format is in `android-project/docs/LOBSTER_MAILBOX.md`.
- HTTPS only. It refuses addresses on your own network (router, other apps on the device).
- Pages are read as text only. Nothing in a page or an order is ever run as a command.
- Covers are decoded and re-drawn as plain JPEGs before they're sent on; anything that isn't a
  picture of a sane size is dropped.
- An edition plan is plain data: feed and page links (HTTPS, public sites only), news searches and
  numbers, checked and capped before it's kept. A listing page is read for its links to the same
  site; nothing on it is run.
- No backups of its data, no tracking, no account. CI checks it asks for no other permissions.
- Orders older than 14 days, or taken before, are ignored, so old order files can't be replayed.
- The pairing code is wiped from the clipboard once it's pasted (SlothLlama marks it sensitive).
- The Tiingo key is sealed by its own Android Keystore key, sent only to api.tiingo.com in a
  header (never in a link, so never in the Log), and wiped from the clipboard once saved.
- Builds only read the repo, and every third-party build step is pinned to an exact commit.
- Install the **release** build (signed with your own key from GitHub Secrets). The "Dev" build
  is signed with the public debug key in this repo, so anyone could make an APK that installs
  over it as an update and reads its data.

## Building

GitHub Actions builds it (`.github/workflows/lobster.yml`) whenever `lobster-app/` changes, signed
with the same key as SlothLlama. It is a separate Gradle build, so SlothLlama's APK never contains
any of its network code. The mark is `art/sloth-lobster.svg`.
