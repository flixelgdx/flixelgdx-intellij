# Contributing to FlixelGDX for IntelliJ IDEA

FlixelGDX for IntelliJ IDEA follows the exact same contribution guidelines as the main framework. Rather than
duplicate them here (and risk the two drifting out of sync), please read the canonical version:

> [!IMPORTANT]
> **All contribution guidelines live in the main framework repository:**
> **[flixelgdx/flixelgdx -> CONTRIBUTING.md](https://github.com/flixelgdx/flixelgdx/blob/master/CONTRIBUTING.md)**
>
> That document covers coding style, our stance on AI, the branch and pull request workflow, commit
> conventions, and everything else expected of a contribution. It all applies here unchanged.

## What is different in this repository

- **Language and toolchain.** The plugin is written in **Kotlin** and built with **JDK 21**, the version the
  IntelliJ Platform runs on, instead of Java 25. The Kotlin conventions (file header, KDoc, threading rules, and the
  declaration order) are in [AGENTS.md](AGENTS.md), which applies to people as much as to AI assistants.
- **Edit game files gently.** The plugin creates games from its bundled templates and changes them by editing
  `project.toml` and the version catalog. Plan changes with the `toml` package so people keep their comments, and
  leave rules that the build must enforce to the framework's Gradle settings plugin.
- **Checks.** Before you open a pull request, run `./gradlew test`, `./gradlew verifyPlugin -PverifyLocal=true`, and
  `./gradlew buildPlugin`. CI runs the verifier against every recommended IDE version.
- **Changelog.** Add a line for every user-visible change under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md). The
  IDE shows that section as the plugin's change notes.

How to set up the build, run the tests, and try the plugin in a sandbox IDE is documented in
**[COMPILING.md](COMPILING.md)**.

For the package layout and how the plugin creates and edits game projects, see **[ARCHITECTURE.md](ARCHITECTURE.md)**.
