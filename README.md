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

A developer-only experiment checks whether the IDE's embedded browser offers WebGPU, as a first step toward a shader
preview. It is hidden unless you start the IDE with `-PwgslSpike=true`. See
[docs/wgsl-preview-spike.md](docs/wgsl-preview-spike.md).

## License

MIT. See [LICENSE](LICENSE).
