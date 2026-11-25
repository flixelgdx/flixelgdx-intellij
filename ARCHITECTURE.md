# Project Structure

FlixelGDX for IntelliJ IDEA is the IDE plugin for the [FlixelGDX](https://github.com/flixelgdx/flixelgdx) game
framework. It creates new games from templates bundled with the plugin, changes existing games by editing their
project files (`project.toml`, `gradle/libs.versions.toml` and `settings.gradle.kts`), installs and starts the
FlixelGDX Debugger, and helps with game code through inspections and WGSL shader support. It needs no other tools.

The plugin does not depend on the framework at runtime, and the framework does not depend on the plugin. The links
between them are the file formats the framework's Gradle settings plugin (`org.flixelgdx.project`) reads, the Maven
coordinates of the framework, and a few framework rules the plugin knows by name (the per-frame methods, the
collection classes, and the WGSL shader contract) for its inspections and shader templates.

## How the plugin creates a game

```
 wizard (NewGameStep -> NewProjectSpec)
                  |
                  v
   MavenCentral (latest framework version, unless one was typed)
                  |
                  v
   GameTemplates (layers the template files and renders them with TextTemplate, in memory)
                  |
                  v
   ProjectWriter (writes every file at once, and removes them again if one fails)
                  |
                  v
   Gradle import, then RunConfigSync
```

- **The template files** live under `src/main/resources/org/flixelgdx/intellij/templates/` in three layers:
  `common/`, then `<language>/base/`, then `<language>/<template>/`. A later layer replaces a file at the same path.
  A name ending in `.tmpl` is rendered and loses the suffix, `dot_` becomes a leading dot (so Git and Gradle leave those files alone here), a `__package__` folder
  becomes the package folders, `__Class__` becomes the game class name, and the `desktop`, `html5` and `android`
  folders are only created for the chosen platforms. `index.txt` lists every file, because resources inside a jar
  cannot be listed.
- **`TextTemplate`** renders the plugin's small template syntax: `{{name}}` prints a value, `{{name | toml}}` (or
  `kotlin`, `xml`) escapes it, `{{if name}}`/`{{if not name}}`/`{{else}}`/`{{end}}` leave out blocks, and the
  `{{-`/`-}}` markers trim white space. It fails loudly on anything else.
- **`project.toml` and the version catalog** are templates too. The catalog gets the framework entries for the
  chosen platforms and language, in the bundles the build files use (`core`, `desktop`, `html5`, `android`, the
  `*Processors` bundles, `androidDesugar` and `buildsrc`).
- **Golden files** in `src/test/resources/golden/` hold every generated project as text, one per language and
  platform set, and `GameTemplatesTest` compares the output with them.

## How the plugin edits a game

```
 menu actions (AddLibraryAction, AddPlatformAction, UpgradeAction, ...)
                  |
                  v
   VersionCatalog / GameProjectToml / CatalogMerge (plan TextEdits from the TOML PSI)
                  |
                  v
   DocumentEdits (applies them as one undoable command and saves the files)
                  |
                  v
   GradleRefresh (refreshes the files and syncs Gradle)
```

The TOML PSI of the bundled TOML plugin is only used to find where things are. Each change touches the fewest
characters it can (one entry line, one array item, one value), so people keep their comments and layout. Adding a
platform renders the templates for the new platform set and copies over only what is missing: the module folder and
the catalog entries (`CatalogMerge`).

## Packages

Everything lives in one Gradle module, under `src/main/kotlin/org/flixelgdx/intellij/`:

- **`template`**: `GameTemplates`, `TextTemplate`, `Escapes`, `GameTemplateData`, `GameNames` (class name and file name slug)
  and `ProjectWriter`, described above. Nothing here touches the IDE, so it is tested with plain unit tests.
- **`toml`**: `TomlEdits` (small planned edits of TOML files), `VersionCatalog`, `GameProjectToml`, `CatalogMerge`,
  `Coordinates`, `SettingsPluginVersion`, `DocumentEdits` (applies edits through the IDE's documents), and
  `ProjectToml` (a plain-text reader of the platforms for the startup check).
- **`maven`**: `MavenCentral` reads `maven-metadata.xml` to find the latest release of an artifact.
- **`game`**: `GameProject` (a project is a FlixelGDX game when its root has a `project.toml`), `Notifier`
  (the plugin's notifications) and `GradleRefresh`.
- **`wizard`**: The FlixelGDX entry in File > New > Project. `NewGameStep` collects the settings and validates
  them with `ProjectValidation` (the same rules as the framework's settings plugin), `NewProjectSpec` holds the
  choices, and `NewProjectRunner` creates the files and imports the result. `FrameworkSource` (in `template`) says
  where the game gets the framework from; the Maven local and local clone choices only change the rendered
  `settings.gradle.kts`. Git uses the platform's own wizard step.
  `AndroidSdk` finds an installed Android SDK.
- **`actions`**: The Tools > FlixelGDX menu. `GameAction` is the base class that shows an action only in FlixelGDX
  projects. The dialogs only collect values; the actions plan the edits with the `toml` package.
- **`runconfig`**: `RunConfigPlanner` decides which Gradle run configurations should exist for the enabled platforms
  (a pure function, so it is unit tested), and `RunConfigSync` applies the plan. `DebuggerBeforeRunProvider` adds the
  Open FlixelGDX Debugger step to the Before launch list.
- **`debugger`**: Finding the FlixelGDX Debugger (`DebuggerDetector`, `DebuggerLocator`, `DebuggerPaths`),
  installing it from its latest GitHub release (`DebuggerInstaller`, `DebuggerRelease`, `DebuggerUnpacker`,
  `DebuggerLaunchers`, `InstallProgress`), and starting it as its own process (`DebuggerLauncher`).
- **`inspections`**: Four UAST inspections that check Java and Kotlin game code: allocation in per-frame methods,
  standard Java collections, reflection, and APIs missing on Android or the web. `GameCodeInspection` is the shared
  base, and `FrameworkScope` limits them to modules that have the framework on their classpath. The rule data lives in
  `CollectionMapping`, `ReflectionRules`, and `UnsupportedApis`, and the quick fixes are `MoveToFieldFix`,
  `ReplaceCollectionFix` (Java) and `KotlinReplaceCollectionFix` (Kotlin, which writes planned text through UAST so
  the plugin needs no Kotlin plugin dependency).
- **`wgsl`**: WGSL shader editing. `WgslBundleProvider` hands the bundled TextMate grammar to the IDE,
  `NewWgslShaderAction` creates a starter shader from the file template, `WgslContext` scopes the live templates to
  `.wgsl` files, and `WgslContract` keeps the names of the framework's shader contract in one place.
- **`settings`**: The application settings (`PluginSettings`, the debugger path) and the project settings
  (`PluginProjectSettings`, run configuration choices), with their pages under Settings > Tools > FlixelGDX.
- **`startup`**: `GameStartupActivity` syncs the run configurations when a FlixelGDX project opens.

## Resources

Under `src/main/resources/`:

- **`META-INF/plugin.xml`**: Registers every action, extension, inspection, and setting page.
- **`inspectionDescriptions/`**: The HTML description of each inspection, shown in the settings.
- **`fileTemplates/` and `liveTemplates/`**: The WGSL file template and the FlixelGDX WGSL live templates.
- **`textmate/wgsl/`**: The bundled WGSL TextMate grammar, with its own `LICENSE` and `NOTICE.md`.
- **`org/flixelgdx/intellij/templates/`**: The files of a new game, in layers, with `index.txt` listing them.

Tests live under `src/test/kotlin/`, mirroring the main packages. `src/test/resources/golden/` holds the expected
output of the templates. Rewrite it after a deliberate template change with `./gradlew test -Pgolden.update=true`, then
review the diff.

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

### Dependency Management

The plugin compiles against the IDE's bundled Java, Gradle, TextMate, and TOML plugins, and its tests also use
the bundled Kotlin plugin. It does not bundle the Kotlin standard library, because the IDE already ships one.

## GitHub Integration

- **`.github/workflows/ci.yml`**: Runs the tests, the plugin verifier against every recommended IDE, and
  `buildPlugin` on every push and pull request.
- **`.github/dependabot.yml`**: Weekly update checks for the Gradle dependencies and the GitHub Actions.

Issue templates, the pull request template, and funding links come from the organization's `.github` repository.

See [COMPILING.md](COMPILING.md) for how to build and test the plugin, and [CONTRIBUTING.md](CONTRIBUTING.md) for how
to contribute.
