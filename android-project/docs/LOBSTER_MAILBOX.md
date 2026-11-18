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
  reading), `watch` (something to keep an eye on), `brief` (a regular round-up), `ask` (anything else)
- `depth`: `quick`, or `deep` (may run overnight)
- `deliver`: where the user wants the catch filed: `library`, `notes` or `chat` (a hint; the user decides)
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

- `kind`: `document`, `note`, `message` or `alert`
- `orderId`: the order it answers; leave it out for things the Lobster sends on its own (a brief, an alert)
- `fileName` / `fileBase64`: optional, for a fetched PDF, EPUB or HTML page (keep files under 15 MB)
- Research should list its **sources**; claims should rest on at least three of them.

## Status (Lobster → SlothLlama), JSON

```json
{ "v": 1, "state": "awake", "at": 1763452800000, "version": "1.0", "working": "Researching: …", "queue": 2 }
```

`state`: `awake`, `busy`, `paused` or `asleep`. SlothLlama treats a status older than three
minutes as asleep.

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
