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
- Adds **FlixelGDX** to File > New > Project. The wizard asks for the game title, company, version, package, window
  size, language (Java or Kotlin), platforms (Desktop, Web, Android), extra libraries and plugins, and a few options
  (suggested packages, the handbook, a Git repository, and under Advanced the framework version and the template).
  Fields are checked while you type with the same rules as `flxgdx`, and the package follows the company and title.
  Creating the project runs `flxgdx new` with progress and a Cancel button, then imports it as a Gradle project and
  shows the notes of the installed packages. The wizard cannot finish while `flxgdx` is missing or too old.
- Adds a **FlixelGDX** menu under Tools (and in the project view menu when you select the project root) in FlixelGDX
  projects. It has Add Library, Add Plugin, Remove Library or Plugin, Add Platform, Remove Platform, Install Handbook,
  Uninstall Handbook, Upgrade and Run Doctor. Adding a library or plugin works from a repository (`owner/repo`, a
  GitHub URL or a local folder) or by hand with Maven coordinates. Upgrade shows the planned changes before it applies
  them. After a change the project files are refreshed and Gradle is synced, and the notes of the packages are shown.
- Adds a **FlixelGDX** tool window (bottom) with the results of `flxgdx doctor` grouped by category. Its toolbar can
  run the checks again, fix what is safe to fix automatically, and switch the offline mode.
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
    parameters and `new` expressions, plus Kotlin's `mutableListOf` and friends. For a Java field or local
    created with a no-argument or capacity constructor the quick fix **Replace with FlixelArray** (or `FlixelSet`,
    `FlixelMap`, `FlixelIntMap`, `FlixelIntArray` and so on) rewrites the declaration. The mapping is
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
- Provides the runner that later features use to run `flxgdx` in the background. It only talks to the tool through
  flags, `--json` and `--no-input`, never by reading human output.

## The debugger

The FlixelGDX Debugger is a separate desktop app. A game started in debug mode (`-Dflixel.mode=debug`, which is what
the **Desktop (Debug Tools)** and **Web (Debug Tools)** run configurations do) connects to it over a WebSocket on
`127.0.0.1:6510`, and retries every two seconds until the debugger is listening.

- **Settings > Tools > FlixelGDX > FlixelGDX Debugger** has a **Debugger executable** field and an **Install
  Debugger** button (with a **Reinstall** box for `--force`). Install runs `flxgdx install debugger` with a progress
  bar that follows the download. Below the field the page shows what was found.
- The debugger is found in this order: the path in the settings (the executable, or the folder that holds it), then
  the install that `flxgdx` recorded in `state.toml` in its config folder (`$XDG_CONFIG_HOME/flxgdx` or
  `~/.config/flxgdx` on Linux, `~/Library/Application Support/flxgdx` on macOS, `%AppData%\flxgdx` on Windows), then
  `flixelgdx-debugger` on `PATH`.
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

A live WGSL preview inside the IDE is not possible today: the IDE's embedded browser does not offer WebGPU. See
[docs/wgsl-preview-spike.md](docs/wgsl-preview-spike.md) for what was tried.

## License

MIT. See [LICENSE](LICENSE).
