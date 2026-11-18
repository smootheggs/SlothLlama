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
- No backups of its data, no tracking, no account. CI checks it asks for no other permissions.
- Orders older than 14 days, or taken before, are ignored, so old order files can't be replayed.
- The pairing code is wiped from the clipboard once it's pasted (SlothLlama marks it sensitive).
- Builds only read the repo, and every third-party build step is pinned to an exact commit.
- Install the **release** build (signed with your own key from GitHub Secrets). The "Dev" build
  is signed with the public debug key in this repo, so anyone could make an APK that installs
  over it as an update and reads its data.

## Building

GitHub Actions builds it (`.github/workflows/lobster.yml`) whenever `lobster-app/` changes, signed
with the same key as SlothLlama. It is a separate Gradle build, so SlothLlama's APK never contains
any of its network code. The mark is `art/sloth-lobster.svg`.
