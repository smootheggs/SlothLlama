# Updates with Obtainium

Every signed build of the `sloth-terminal` branch is published as a GitHub release, one per
version: `slothllama-v1.15.0` for SlothLlama and `slothlobster-v1.2.0` for the SlothLobster. Each
release carries the APK and its SHA-256. Debug builds are never published, and only the newest
five releases of each app are kept.

[Obtainium](https://github.com/ImranR98/Obtainium) watches those releases and installs updates,
so there's no need to download artifacts from the Actions tab.

## Release notes

Each release's notes are its entry in `CHANGELOG.md` at the top of the repository, found by its
heading (`## SlothLlama 1.46.0 — date`, or `## SlothLlama 1.45.0 · SlothLobster 1.12.1 — date`
for a joint release), followed by the build, commit and SHA-256 lines. GitHub keeps only the
newest five releases of each app; `CHANGELOG.md` keeps every one. A version without an entry
says so instead.

## Setting it up

1. Install Obtainium from its GitHub releases page.
2. **Add app**, App source URL: `https://github.com/smootheggs/SlothLlama`
3. Open the additional options and set:
   - **Filter release titles by regular expression**: `^SlothLlama `
   - **Filter APKs by regular expression**: `SlothLlama\.apk$`
   - **Version string extraction RegEx**: `\d+\.\d+\.\d+`
4. Add it. Obtainium links it to the installed SlothLlama (release build, `app.localmind.assistant`).
5. Add the SlothLobster the same way, with `^SlothLobster ` and `SlothLobster\.apk$`.

Both apps live in one repository, so the title filter is what tells their releases apart.

## Notes

- Obtainium installs an update only if it's signed with the same key as the installed app, so
  a tampered APK can't replace yours. The Dev (debug) apps are separate and aren't updated.
- If the repository is private, Obtainium needs a GitHub token: a fine-grained token with
  read-only **Contents** access to this one repository.
- SlothLlama itself still never goes online. Obtainium is the one that checks for updates.
