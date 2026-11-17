# FlixelGDX for IntelliJ IDEA

<!-- Plugin description -->
Tools for building games with the FlixelGDX framework. The plugin is a thin front end for the
<code>flxgdx</code> command-line tool: it finds the tool on your computer, checks that its version is new enough,
and runs its commands with progress shown inside the IDE.
<!-- Plugin description end -->

## What it does

- Adds a settings page under **Settings > Tools > FlixelGDX** where you can point the IDE at the `flxgdx`
  executable and test it.
- Finds `flxgdx` for you: the path in the settings first, then your `PATH`, then common install folders such as
  `~/go/bin` and `~/.local/bin`.
- Checks that the installed `flxgdx` is new enough and shows a notification with next steps when it is missing or too
  old (only once per IDE session, and only for FlixelGDX projects, which have a `project.toml` in the project root).
- Provides the runner that later features use to run `flxgdx` in the background. It only talks to the tool through
  flags, `--json` and `--no-input`, never by reading human output.

## Requirements

- IntelliJ IDEA 2025.3 or newer.
- The [`flxgdx` command-line tool](https://github.com/flixelgdx/flixelgdx-cli), version 1.0.0 or newer. Either
  install it so it is on your `PATH`, or set its full path under **Settings > Tools > FlixelGDX**.

## Building

The build needs JDK 21. The first build downloads an IntelliJ IDEA distribution, which can take a while.

```sh
./gradlew test             # unit tests
./gradlew verifyPlugin     # plugin verifier (set -PverifyLocal=true to check only the build IDE)
./gradlew buildPlugin      # the installable zip, in build/distributions
./gradlew runIde           # a sandbox IDE with the plugin installed
```

## License

MIT. See [LICENSE](LICENSE).
