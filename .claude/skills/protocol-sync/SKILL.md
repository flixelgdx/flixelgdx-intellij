---
name: protocol-sync
description: Check that the plugin still matches the flxgdx JSON protocol and command output after flxgdx changes - re-record the test fixtures, compare the result models and CommandArgs with PROTOCOL.md and the real output, and report or fix what drifted. Use after a flxgdx release, when PROTOCOL.md changes, or when a plugin feature reads a new field.
argument-hint: "[flxgdx version, tag, or path to a local flixelgdx-cli checkout] (defaults to the flxgdx on PATH)"
---

# flxgdx protocol sync

The plugin only talks to `flxgdx` through `--json --no-input`, so its correctness depends on the event shapes, error
codes, flags, and `data` trees that `flxgdx` produces. This skill keeps three things in line with the version of
`flxgdx` given in `$ARGUMENTS` (or the one on `PATH`):

- the recorded output in `src/test/resources/flxgdx/`,
- the result models in `src/main/kotlin/org/flixelgdx/intellij/model/` and the decoders in `cli/`,
- the argument lists in `model/CommandArgs.kt`.

## Steps

1. **Find the reference.** Run `flxgdx version --json` and note the version. Read `PROTOCOL.md` from the matching
   `flixelgdx-cli` checkout, or with `gh api repos/flixelgdx/flixelgdx-cli/contents/PROTOCOL.md --jq .content | base64 -d`.
   Never edit the `flixelgdx-cli` repository from here.
2. **Compare the event layer.** Check `CliEvent.kt`, `CliResult.kt`, and `FlxgdxRunner.kt` against the event shape,
   event types, exit codes, and error codes in `PROTOCOL.md`. Unknown keys must still be ignored.
3. **Re-record the fixtures.** For each file in `src/test/resources/flxgdx/`, work out which command produced it (the
   test that loads it through `Fixtures` shows the scenario), run that command with `--json --no-input` in a
   throwaway project created with `flxgdx new` in the scratchpad, and replace machine paths with the same
   placeholders the existing fixture uses. Only replace a fixture when the new output really differs.
4. **Compare the models.** For every model class, check that each key it reads still exists with the same type and
   meaning, and list keys that are new and might be useful. Readers must stay forgiving (see `JsonRead.kt`).
5. **Compare the flags.** Check every function in `CommandArgs.kt` against `flxgdx <command> --help`. Flags that were
   renamed or removed are bugs; new flags are suggestions.
6. **Check the minimum version.** If the plugin now relies on something newer than `CliVersion.MINIMUM`, raise it and
   update the version in `README.md` and `COMPILING.md`.
7. **Run the tests** with `./gradlew test --max-workers=2`, then `./gradlew --stop`.

## Report

List each drift as the file, what the plugin expects, and what `flxgdx` does now, with the most breaking ones first
(a removed key or flag before a new optional one). Say which fixtures were re-recorded and which models changed. Do
not commit unless you were asked to.
