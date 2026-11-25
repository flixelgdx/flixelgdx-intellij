# FlixelGDX for IntelliJ IDEA

<!-- Plugin description -->
Tools for building games with the FlixelGDX framework: a New Project wizard, menu actions that add libraries and
platforms by editing the project files, Gradle run configurations, the FlixelGDX Debugger, inspections for game code
and WGSL shader editing. It needs no extra command-line tools.
<!-- Plugin description end -->

## What it does

- Adds **FlixelGDX** to File > New > Project. The wizard asks for the game title, company, version, package, window
  size, language (Java or Kotlin), platforms (Desktop, Web, Android), the template, the framework version, and whether
  to create a Git repository. Under Advanced, people who work on the framework itself can take it from their Maven
  local repository or build it from a local clone as a composite build, for quick test games. Fields are checked while
  you type with the same rules as the FlixelGDX build, and the package follows the company and title. Creating the project finds the latest framework
  release on Maven Central (unless you typed a version), renders every file from the templates bundled with the
  plugin, writes them at once, and imports the result as a Gradle project. Kotlin games also get the Kotlin extension
  of the framework.
- Adds a **FlixelGDX** menu under Tools (and in the project view menu when you select the project root) in FlixelGDX
  projects, which have a `project.toml` in the project root. Every action edits the project files directly, keeps
  your comments and layout, can be undone with Edit > Undo, and syncs Gradle afterwards:
  - **Add Library** takes Maven coordinates (`group:artifact:version`), an alias and a bundle, adds a `[libraries]`
    entry to `gradle/libs.versions.toml` and appends the alias to the bundle (`core` for game code, a platform name,
    a name ending in `Processors` for annotation processors, or `buildsrc` for Gradle plugins). A Gradle plugin also
    needs its `id("...")` line in the `plugins {}` block of the module that uses it; add that line by hand.
  - **Remove Library** lists the catalog entries (the framework's own are greyed out) and removes the entry, its name
    in every bundle, and its version when nothing else uses it.
  - **Add Platform** creates the module folder from the platform's templates (existing files are kept), copies the
    catalog entries the platform needs, and adds it to `platforms` in `project.toml`. **Remove Platform** takes it
    out of `platforms` and can delete the module folder.
  - **Upgrade** looks up the latest framework release (and Kotlin extension release) on Maven Central, shows it next
    to the version the game uses, and after you confirm changes `[versions]` in the catalog and the settings plugin
    version in `settings.gradle.kts`.
- Creates Gradle run configurations in a **FlixelGDX** folder for each enabled platform, so the green Run and Debug
  buttons work right away: **Desktop** (`:desktop:run`), **Desktop (Debug Tools)** (`:desktop:debug`, the framework's
  debug mode with its debugging tools), **Web** (`:html5:run`), **Web (Debug Tools)** (`:html5:debug`) and
  **Android** (`:android:installDebug`). The IDE's own Debug button on a configuration attaches the Java debugger as
  usual. They are created when a FlixelGDX project opens, after the wizard finishes, and after Add Platform or Remove
  Platform. Only missing ones are created, Desktop is selected when nothing else is, and when a platform is removed
  only the configurations the plugin created for it (and that you did not edit) are removed. Turn it off with
  **Create run configurations automatically** under **Settings > Tools > FlixelGDX > Project**.
- Adds four inspections under **Settings > Editor > Inspections > FlixelGDX** for Java and Kotlin. They only run in
  modules that have the framework on their classpath, and each one has a description that explains why it matters:
  - **Allocation in per-frame code** (`FlixelGdxAllocation`, weak warning): `new` objects, arrays, string
    concatenation, boxing into collections, and capturing lambdas or anonymous classes inside methods that override
    the framework's `update(float)` or `draw(...)`. Java files get the quick fix **Move to a field** for a local
    variable whose initializer does not depend on the method.
  - **Standard Java collection** (`FlixelGdxCollection`, weak warning): `java.util` collections in fields, locals,
    parameters and `new` expressions, plus Kotlin's `mutableListOf` and friends. For a Java or Kotlin field or local
    created with a no-argument or capacity constructor (in Kotlin also an empty `arrayListOf()`, `mutableListOf()`,
    `hashSetOf()` or `hashMapOf()`) the quick fix **Replace with FlixelArray** (or `FlixelSet`, `FlixelMap`,
    `FlixelIntMap`, `FlixelIntArray` and so on) rewrites the declaration and adds the import. The mapping is
    `List`/`ArrayList` to `FlixelArray` (or a primitive array for boxed numbers), `Set`/`HashSet` to `FlixelSet`
    (`FlixelIntSet` and `FlixelLongSet` for `Integer` and `Long`), `Map`/`HashMap` to `FlixelMap` (`FlixelIntMap` and
    `FlixelLongMap` for `Integer` and `Long` keys, `FlixelObjectIntMap` and `FlixelObjectFloatMap` for number values)
    and `IdentityHashMap` to `FlixelIdentityMap`. Ordered and sorted collections are only reported. The calls that
    use the variable are not rewritten, because the method names differ.
  - **Reflection** (`FlixelGdxReflection`, warning): `java.lang.reflect`, `Class.forName`, `getDeclaredMethod` and
    similar, proxies, `MethodHandles.Lookup.find*` and `kotlin.reflect.full`, which break on the web and on Android
    with R8.
  - **API missing on Android or the web** (`FlixelGdxUnsupportedApi`, warning): `java.lang.foreign`,
    `ScopedValue`, `StructuredTaskScope`, virtual threads, `java.lang.classfile`, the Java 21 sequenced collection
    methods, `Math.clamp`, `StringBuilder.repeat`, `HexFormat`, `Thread.threadId` and `Locale.of`. The list lives in
    one file, `UnsupportedApis.kt`.

  Suppress any of them with `@SuppressWarnings("<id>")` in Java or `@Suppress("<id>")` in Kotlin.
- Edits WGSL shaders (`.wgsl`): syntax highlighting from a bundled TextMate grammar, **File > New > WGSL Shader**
  (a starter fragment shader that follows the framework's shader contract, offered in FlixelGDX projects), and live
  templates in the **FlixelGDX WGSL** group: `flxfrag` (the `fs_main` entry point), `flxsample` (sample the sprite
  texture) and `flxuniform` (a uniform struct in group 1). Put game shaders in `src/main/shaders` of the core module.
  The grammar comes from the MIT-licensed vscode-wgsl extension (see
  `src/main/resources/textmate/wgsl/NOTICE.md`). A language plugin that provides its own WGSL file type takes
  priority over the bundle.
- Works with the [FlixelGDX Debugger](https://github.com/flixelgdx/flixelgdx-debugger), the desktop app that inspects
  a running game. See [The debugger](#the-debugger) below.

## The debugger

The FlixelGDX Debugger is a separate desktop app. A game started in debug mode (`-Dflixel.mode=debug`, which is what
the **Desktop (Debug Tools)** and **Web (Debug Tools)** run configurations do) connects to it over a WebSocket on
`127.0.0.1:6510`, and retries every two seconds until the debugger is listening.

- **Settings > Tools > FlixelGDX > FlixelGDX Debugger** has a **Debugger executable** field and an **Install
  Debugger** button (with a **Reinstall** box). Install downloads the file for your computer from the latest release
  (`https://github.com/flixelgdx/flixelgdx-debugger/releases/latest/download/<file>`) with a progress bar that follows
  the download, checks its SHA-256 when the release lists one in `checksums.txt`, unpacks it next to the install
  folder and swaps it in, so a failed install leaves the old one working. On Linux and macOS it also puts a
  `flixelgdx-debugger` link in `~/.local/bin`, and on Linux a menu entry. Below the field the page shows what was
  found.
- The install folder is `$XDG_DATA_HOME/flixelgdx/debugger` (or `~/.local/share/flixelgdx/debugger`) on Linux,
  `~/Library/Application Support/FlixelGDX/Debugger` on macOS and `%LOCALAPPDATA%\FlixelGDX\Debugger` on Windows.
  Builds exist for Linux and Windows on x64 and ARM64, and for macOS on ARM64.
- The debugger is found in this order: the path in the settings (the executable, or the folder that holds it), then
  the install folder above, then `flixelgdx-debugger` on `PATH`.
- **Tools > FlixelGDX > Install Debugger...** does the same install from the menu, and **Tools > FlixelGDX > Open
  Debugger** starts the app as its own program (nothing waits for it). When it is not installed, a notification offers
  to install it. Starting it twice is harmless: if a `FlixelDebugger.App` process is already running, nothing new is
  started.
- Run configurations: a Gradle configuration that runs `:desktop:debug` or `:html5:debug` can have a step called
  **Open FlixelGDX Debugger** in its **Before launch** list (the **+** button offers it for those configurations
  only). The step starts the debugger unless it is already running and then lets the run go on immediately. It never
  fails the run: a missing debugger only shows a notification. The automatic run configurations get this step when
  they are created and the debugger is installed at that moment. Configurations that already exist are never changed,
  so add the step by hand there if you want it.

### Jumping from the debugger to your code

The debugger does not open source files yet. Any program can ask the IDE to do it through the IDE's built-in web
server, which listens on `127.0.0.1` (port 63342 unless that port is taken, then the next ones):

```
GET http://localhost:63342/api/file/<path>:<line>:<column>
```

`<path>` is a path relative to the project folder, or an absolute path of a file inside an open project (use `/` as
the separator and escape it as a URL path). `:<line>` and `:<column>` are optional and start at 1. For example,
`http://localhost:63342/api/file/core/src/main/java/com/example/Player.java:42` opens line 42 of that file in the
window of the project that owns it. The IDE may ask the first time before it lets another program open files. The
plugin has no code for this on purpose: it only needs the debugger to send the request.

## Requirements

- IntelliJ IDEA 2025.3 or newer, with the bundled Gradle and TOML plugins enabled.
- An internet connection when creating a project (to find the latest framework release, unless you type a version)
  and when installing the debugger.

## Building

The build needs JDK 21. The first build downloads an IntelliJ IDEA distribution, which can take a while.

```sh
./gradlew test             # unit tests
./gradlew verifyPlugin     # plugin verifier (set -PverifyLocal=true to check only the build IDE)
./gradlew buildPlugin      # the installable zip, in build/distributions
./gradlew runIde           # a sandbox IDE with the plugin installed
```

A live WGSL preview inside the IDE is not possible today: the IDE's embedded browser does not offer WebGPU.

## Contributing and building

- **[COMPILING.md](COMPILING.md)** - how to build the plugin, run the tests and the plugin verifier, and try it in a
  sandbox IDE.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - contribution guidelines (shared with the main framework).
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - the package layout and how the plugin creates and edits game projects.

## License

MIT. See [LICENSE](LICENSE).
