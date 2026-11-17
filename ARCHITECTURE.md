# Project Structure

FlixelGDX for IntelliJ IDEA is the IDE plugin for the [FlixelGDX](https://github.com/flixelgdx/flixelgdx) game
framework. It is a thin front end for the `flxgdx` command-line tool: `flxgdx` does the real work (creating projects,
adding libraries and platforms, checking the setup, installing tools), and the plugin collects input, runs the tool in
the background, and shows the results inside the IDE.

The plugin does not depend on the framework, and the framework does not depend on the plugin. The only link between
them is `flxgdx`, plus a few framework rules the plugin knows by name (the per-frame methods, the collection classes,
and the WGSL shader contract) for its inspections and shader templates.

## How the plugin talks to flxgdx

```
 dialogs, wizard, tool window, startup check
                  |
                  v
   CommandArgs (builds the argument list)
                  |
                  v
   FlxgdxTasks (background task, progress, notifications)
                  |
                  v
   FlxgdxRunner (starts flxgdx --json --no-input, decodes events)
                  |
                  v
   model/* (typed results read from the "data" tree)
```

- **`FlxgdxLocator`** finds the executable: the path in the settings first, then `PATH`, then common install folders
  (such as `~/go/bin` and `~/.local/bin`).
- **`FlxgdxCli`** runs `flxgdx version --json` and compares the result with `CliVersion.MINIMUM`. When the tool is
  missing or too old, `FlxgdxNotifier` explains the fix once per IDE session.
- **`FlxgdxRunner`** is the only place that starts the `flxgdx` process. It adds `--json --no-input` to every call,
  reads stdout one line at a time, and turns each line into a `CliEvent` (`progress`, `log`, `warning`, `result`,
  `error`). Listeners receive the events on a background thread, and the call returns a `CliResult` with the exit
  code, the result `data`, and the error `code`.
- **`FlxgdxTasks`** wraps the runner in a cancelable background task whose progress bar follows the `progress`
  events, and reports warnings and failures as notifications. Its `onFinished` callback arrives on the EDT.

The plugin never reads human-readable output and never matches on a `message` field. It uses the event type, the
`code`, the exit code, and the `data` tree, and ignores keys it does not know so newer versions of `flxgdx` keep
working. The protocol itself is described in `flxgdx`'s
[PROTOCOL.md](https://github.com/flixelgdx/flixelgdx-cli/blob/master/PROTOCOL.md).

## Packages

Everything lives in one Gradle module, under `src/main/kotlin/org/flixelgdx/intellij/`:

- **`cli`**: The runner stack described above, plus `FlxgdxProject` (a project is a FlixelGDX project when its root
  has a `project.toml`), `CliNotes` (the notes installed packages ask to show), and `JsonRead.kt` (forgiving readers
  for `data` trees).
- **`model`**: Plain data classes for command results (`ProjectInfo`, `ChangeResult`, `DoctorResult`,
  `UpgradeResult`, `ManifestInfo`, `Handbook`) and `CommandArgs`, which builds the argument list of every command.
  `ProjectToml` reads the enabled platforms straight from `project.toml` for the one case where `flxgdx` is not
  available. Nothing in this package touches the IDE, so it is tested with plain unit tests.
- **`wizard`**: The FlixelGDX entry in File > New > Project. `FlxgdxWizardStep` collects one field per
  `flxgdx new` flag and validates them with `ProjectValidation` (the same rules as `flxgdx`), `NewProjectSpec` holds
  the choices, and `NewProjectRunner` runs `flxgdx new` and imports the result as a Gradle project. `AndroidSdk`
  finds an installed Android SDK.
- **`actions`**: The Tools > FlixelGDX menu. `FlxgdxAction` is the base class that shows an action only in FlixelGDX
  projects. `ProjectActions` holds the shared steps (read the project, run a command, report the outcome), and
  `GradleRefresh` refreshes the files and syncs Gradle after `flxgdx` changes the build. The dialogs only collect
  values and hand them to `CommandArgs`.
- **`doctor`**: The FlixelGDX tool window. `DoctorController` runs `flxgdx doctor` and keeps the latest result, and
  `DoctorPanel` draws it as a tree grouped by category.
- **`runconfig`**: `RunConfigPlanner` decides which Gradle run configurations should exist for the enabled platforms
  (a pure function, so it is unit tested), and `RunConfigSync` applies the plan. `DebuggerBeforeRunProvider` adds the
  Open FlixelGDX Debugger step to the Before launch list.
- **`debugger`**: Finding the FlixelGDX Debugger (`DebuggerDetector`, `DebuggerLocator`, and `StateToml` for the
  install `flxgdx` recorded), installing it with `flxgdx install debugger` (`DebuggerInstaller`, `InstallProgress`),
  and starting it as its own process (`DebuggerLauncher`).
- **`inspections`**: Four UAST inspections that check Java and Kotlin game code: allocation in per-frame methods,
  standard Java collections, reflection, and APIs missing on Android or the web. `FlixelInspection` is the shared
  base, and `FlixelScope` limits them to modules that have the framework on their classpath. The rule data lives in
  `CollectionMapping`, `ReflectionRules`, and `UnsupportedApis`, and the quick fixes are `MoveToFieldFix` and
  `ReplaceCollectionFix`.
- **`wgsl`**: WGSL shader editing. `WgslBundleProvider` hands the bundled TextMate grammar to the IDE,
  `NewWgslShaderAction` creates a starter shader from the file template, `WgslContext` scopes the live templates to
  `.wgsl` files, and `WgslContract` keeps the names of the framework's shader contract in one place.
- **`settings`**: The application settings (`FlxgdxSettings`, the `flxgdx` and debugger paths) and the project
  settings (`FlxgdxProjectSettings`, run configuration choices), with their pages under Settings > Tools > FlixelGDX.
- **`startup`**: `FlxgdxStartupActivity` checks the installed `flxgdx` and syncs the run configurations when a
  FlixelGDX project opens.

## Resources

Under `src/main/resources/`:

- **`META-INF/plugin.xml`**: Registers every action, extension, inspection, and setting page.
- **`inspectionDescriptions/`**: The HTML description of each inspection, shown in the settings.
- **`fileTemplates/` and `liveTemplates/`**: The WGSL file template and the FlixelGDX WGSL live templates.
- **`textmate/wgsl/`**: The bundled WGSL TextMate grammar, with its own `LICENSE` and `NOTICE.md`.

Tests live under `src/test/kotlin/`, mirroring the main packages. `src/test/resources/flxgdx/` holds recorded
`flxgdx --json` output that the model tests decode, so the tests never need `flxgdx` installed.

## Build System

The plugin is built with Gradle (Kotlin DSL) and the
[IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

### Key Files and Folders

- **`build.gradle.kts`**: Applies the Kotlin, IntelliJ Platform, and changelog plugins, picks the IDE to build
  against, reads the plugin description from [README.md](README.md) and the change notes from
  [CHANGELOG.md](CHANGELOG.md), and configures the plugin verifier (`-PverifyLocal=true` checks only the build IDE).
- **`gradle.properties`**: The plugin version, the oldest supported IDE build (`pluginSinceBuild`), the IDE the
  plugin is compiled against (`platformType` and `platformVersion`), and Gradle settings.
- **`gradle/libs.versions.toml`**: The version catalog for Kotlin, the Gradle plugins, and JUnit.
- **`docs/`**: Design notes, such as [docs/wgsl-preview-spike.md](docs/wgsl-preview-spike.md), which records why
  there is no live WGSL preview.

### Dependency Management

The plugin compiles against the IDE's bundled Java, Gradle, TextMate, and JCEF plugins, and its tests also use the
bundled Kotlin plugin. It does not bundle the Kotlin standard library, because the IDE already ships one.

## GitHub Integration

- **`.github/workflows/ci.yml`**: Runs the tests, the plugin verifier against every recommended IDE, and
  `buildPlugin` on every push and pull request.
- **`.github/dependabot.yml`**: Weekly update checks for the Gradle dependencies and the GitHub Actions.

Issue templates, the pull request template, and funding links come from the organization's `.github` repository.

See [COMPILING.md](COMPILING.md) for how to build and test the plugin, and [CONTRIBUTING.md](CONTRIBUTING.md) for how
to contribute.
