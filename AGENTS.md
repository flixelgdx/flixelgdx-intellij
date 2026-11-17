# Project instructions for AI assistants

This is the IntelliJ IDEA plugin for the FlixelGDX game framework. It is a thin front end: it finds and runs the
`flxgdx` command-line tool and shows the results in the IDE.

## Kotlin conventions

- Target JDK 21 and the Kotlin version in `gradle/libs.versions.toml`. Do not bundle the Kotlin standard library.
- Follow `.editorconfig`: 2-space indentation, 120 columns, LF line endings.
- Every Kotlin source file starts with the MIT header block comment (copy it from an existing file).
- Import every type you use. No star imports and no inline fully qualified names.
- Write KDoc on all public API. Start with one summary sentence, then details. Add a short example for central APIs.
- Comments and docs use beginner-friendly, ASCII-only American English. No em dashes, no emojis, and no
  section-divider comments. Explain why before how.
- Keep names short. When a short name hides the meaning, add KDoc instead of a long identifier.

## Threading rules

- Never block the EDT. Disk access, process runs and network calls belong on a background thread
  (`Task.Backgroundable`, a coroutine on `Dispatchers.IO`, or a pooled thread).
- Use read actions for reading PSI or VFS data and write actions for changing it.
- Touch Swing components only on the EDT. Listener callbacks from the runner arrive on a background thread.

## The flxgdx runner

- Everything that talks to `flxgdx` goes through `FlxgdxRunner` (or `FlxgdxTasks` on top of it). Do not build a
  `GeneralCommandLine` for `flxgdx` anywhere else.
- Drive the tool only with flags, `--json` and `--no-input`. Never parse human-readable output, and never match on a
  `message` field. Use the event type, the `code`, the exit code, and the `data` tree.
- Ignore JSON keys you do not know, so newer versions of `flxgdx` keep working.
- Locate the executable with `FlxgdxLocator` and check its version with `FlxgdxCli`.

## Finishing checks

Run these one at a time (the build machine is slow), with `--max-workers=2`:

1. `./gradlew test`
2. `./gradlew verifyPlugin -PverifyLocal=true` (CI checks every recommended IDE)
3. `./gradlew buildPlugin`
4. `./gradlew --stop` when finished.

Do not run `runIde` unless asked.

## Git

- Make small, focused commits. Titles are present tense, at most 72 characters, with no `feat:` or `fix:` prefix.
- Never add a `Co-Authored-By` trailer.
- Never push unless the user asks.

## Scope

This repository must never mention other projects or games. Talk only about the plugin, the framework and `flxgdx`.
