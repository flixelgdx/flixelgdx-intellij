# FlixelGDX for IntelliJ IDEA (project instructions for AI assistants)

---

## Project context

This is the IntelliJ IDEA plugin for the [FlixelGDX](https://github.com/flixelgdx/flixelgdx) game framework. It is a
**thin front end**: it finds and runs the `flxgdx` command-line tool and shows the results in the IDE. The plugin
never edits a game's build files itself; `flxgdx` does the work, and the plugin collects input, shows progress, and
presents the result.

It is a **standalone tool**, not a framework extension. It does not depend on the framework at runtime, and nothing
from it is installed into a game, so it has no `flxgdx.yml` manifest.

The source lives in one Gradle module under `src/main/kotlin/org/flixelgdx/intellij/`, split into packages:

- **`cli`**: Finding `flxgdx` (`FlxgdxLocator`), checking its version (`FlxgdxCli`, `CliVersion`), running it and
  decoding its `--json` events (`FlxgdxRunner`, `CliEvent`, `CliResult`), and showing runs as background tasks and
  notifications (`FlxgdxTasks`, `FlxgdxNotifier`).
- **`model`**: Plain data classes for the `data` trees `flxgdx` returns, and `CommandArgs`, which builds every
  command's argument list.
- **`wizard`**: The FlixelGDX entry in File > New > Project, which runs `flxgdx new` and imports the result.
- **`actions`**: The Tools > FlixelGDX menu and its dialogs (libraries, plugins, platforms, handbooks, upgrade).
- **`doctor`**: The FlixelGDX tool window that shows `flxgdx doctor` results.
- **`runconfig`**: The Gradle run configurations for each enabled platform, and the Open FlixelGDX Debugger
  before-launch step.
- **`debugger`**: Finding, installing (through `flxgdx install debugger`), and starting the FlixelGDX Debugger.
- **`inspections`**: The four FlixelGDX inspections for Java and Kotlin game code, built on UAST.
- **`wgsl`**: WGSL shader support (TextMate grammar bundle, file template, live templates).
- **`settings`** and **`startup`**: The settings pages and the check that runs when a FlixelGDX project opens.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how these fit together.

Runtime verification happens in a **sandbox IDE** (`./gradlew runIde`) opened on a game project created with
`flxgdx`, never in this repository.

---

## Collaboration before implementation

Treat the interaction as teamwork, not robotic task execution. Prefer brainstorming when the user's direction
is ambiguous.

- Before implementing anything (planning or coding):

  1. Ask yourself whether the requested design or refactor is actually good for the plugin. A change belongs here if
     it is about how the IDE finds, runs, or presents `flxgdx`, or about IDE-only help for game code (inspections,
     WGSL editing, run configurations). Logic that changes a game project belongs in `flxgdx` instead, so every
     front end gets it.
  2. If it hurts the plugin, breaks invariants, or there is clearly a better path, **stop before editing files or running commands**.
  3. Explain why (pros and cons), suggest better alternatives, and ask whether the user still wants to proceed.
  4. If they confirm after that discussion, proceed as requested.

- If you are unsure about something, whether that would be for a library, a specific aspect of the plugin's codebase,
  the IntelliJ Platform API, or the `flxgdx` protocol, do **not** assume anything.
  Always verify first before making an assumption. If you are still unsure about something, ignore it and bring it up to the user when you're done completing a task.

---

## Explaining things for beginners and contributors

The plugin welcomes new contributors learning open source.

When explaining code or introducing patterns:

- Explain **why** before **how** (motivation before mechanics).
- Use analogies for complex systems; if the user gave no analogy topic, ask for one they like.
- End **complex** explanations with a short check-in question so you can verify understanding.
- Stay encouraging and professional. Assume intelligence but not deep familiarity with Kotlin, the IntelliJ Platform,
  or FlixelGDX quirks.

---

## Code quality (non-negotiables)

### Threading

- **Never block the EDT.** Disk access, process runs and network calls belong on a background thread
  (`Task.Backgroundable`, a coroutine on `Dispatchers.IO`, or a pooled thread).
- Use read actions for reading PSI or VFS data and write actions for changing it.
- Touch Swing components only on the EDT. Listener callbacks from the runner arrive on a background thread.

### The flxgdx runner

- Everything that talks to `flxgdx` goes through `FlxgdxRunner` (or `FlxgdxTasks` on top of it). Do not build a
  `GeneralCommandLine` for `flxgdx` anywhere else.
- Drive the tool only with flags, `--json` and `--no-input`. Never parse human-readable output, and never match on a
  `message` field. Use the event type, the `code`, the exit code, and the `data` tree.
- Ignore JSON keys you do not know, so newer versions of `flxgdx` keep working.
- Locate the executable with `FlxgdxLocator` and check its version with `FlxgdxCli`.

### Performance and allocations

- **Keep hot paths cheap.** Code the IDE calls constantly (inspection visitors, `AnAction.update()`, live template
  contexts, startup activities) must not allocate in loops, run processes, or do slow I/O. Do the cheapest check
  first (for example `FlixelScope` before any resolve) and cache results that do not change.
- `update()` of an action runs often. Keep it to a quick check, such as the single `project.toml` lookup in
  `FlxgdxAction`.
- Standard Kotlin and Java collections are fine here. The framework's ban on them is about game code at runtime; the
  plugin runs inside the IDE and does not depend on the framework.

### Reflection, internal and deprecated APIs

- **Reflection is banned.** It breaks silently when the IDE changes its internals between versions. If reflection
  must be used, don't touch the main area requiring it, and bring it up at the end of your task, explaining why it's
  needed.
- **Do not use internal, experimental, or deprecated IntelliJ Platform APIs.** The plugin verifier reports them, and
  they break on newer IDEs. If one *is* used, replace it with the modernized, recommended version instead.

---

## Coding and language style

### Kotlin

- Target JDK 21 and the Kotlin version in `gradle/libs.versions.toml`. Do not bundle the Kotlin standard library.
- Follow `.editorconfig`: 2-space indentation, 120 columns, LF line endings.
- Every Kotlin source file starts with the MIT header block comment (copy it from an existing file).
- **Import** every type you use. No star imports and no inline fully qualified names.
- Keep names short. When a short name hides the meaning, add KDoc instead of a long identifier.
- Prefer immutable `val` properties and `data class` models. Model classes read `data` trees with the forgiving
  readers in `JsonRead.kt`, so a missing or reshaped key gives an empty answer instead of an exception.
- Keep logic that can be tested without the IDE (argument building, JSON decoding, validation, planning) in plain
  objects and classes, and keep the IDE glue thin around them.

### Declaration order

Keep each class in the same order so files read alike:

1. Properties
2. `init` blocks and secondary constructors, with smallest to largest parameters top to bottom
3. Functions (if there are overloads, order them smallest to largest parameters top to bottom)
4. Simple getters and setters, below every other function
5. Nested classes, then nested interfaces, then nested enums
6. The `companion object`, last

Within each group, order visibility as `public`, `protected`, `internal`, `private`.

---

## Documentation, comments, and KDoc

Documentation should read like a **beginner-friendly handbook**, not an expert-only manual.

### Mechanics

- Write KDoc on all public API. Start with one summary sentence, then details. Add a short example for central APIs.
- Use correct grammar and punctuation everywhere (comments, `@param`, `@property`, `@return`, `@throws`, and so on).
- Stick to **ASCII** in prose when practical; avoid decorative punctuation like en dash, em dash, fancy arrows, or emojis. This applies
  to both source comments and KDoc. Use a plain hyphen (-) only for compound adjectives; never use it as a sentence separator or
  stand-in for an em dash. This rule also applies to inline comments in build scripts. This allows the docs to be read easily
  on every device and requires you to use clarity over brevity. The Markdown docs are the only exception to this rule.
- Use **consistent capitalization and grammar** in prose and code.
- Every doc comment should always start with a single sentence, with detailed paragraphs following.
- Include the right KDoc tags (`@param`, `@property`, `@return`, `@throws`, ...) wherever they apply.
- Reference other types with `[TypeName]` and keep those references valid.
- Follow `.editorconfig` for formatting.
- Add comments where either complexity would otherwise be hard to follow, or where code requires import context.
- Skip KDoc on trivial, self-explanatory functions (such as plain getters/setters) unless there is subtle behavior.
- All source files should carry the project's standard MIT copyright header (exception: build scripts).
- Use **American English** in docs. (e.g., "behavior" instead of "behaviour")
- Explain why before how.
- After code changes that affect what the user sees, **update relevant Markdown docs** in the repo (`README.md`,
  `CHANGELOG.md`, and `ARCHITECTURE.md`). The plugin description shown in the IDE comes from the marked block at the top
  of `README.md`, and the change notes come from `CHANGELOG.md`.
- Don't use section comments (like `// ---`). The code should be easily navigable simply by how it's organized; section comments are just noise.

### Comments versus KDoc

- For single-line comments, do **not** use Markdown tricks (bold with `**`, backticks around snippets, etc.). Reserve richer formatting for KDoc.
- When naming functions inside comments, include parentheses (`someFunction()`). If parameters exist, but you are not spelling them out, use `anotherFunction(...)`. Example class-qualified form: `SomeClass.someFunction(...)`.

### Heavily used or critical APIs

For widely used classes, properties, functions, or anything central to correctness (such as `FlxgdxRunner` and
`FlxgdxTasks`), include a **small usage example** with an analogy in KDoc showing correct typical use.

---

## Architecture and scope

- The plugin is a front end. Changes to a game project (its `project.toml`, Gradle files, dependencies, platforms)
  are made by `flxgdx`, not by plugin code.
- Keep changes minimal: avoid unrelated files unless needed for the stated task.
- Register every extension and action in `src/main/resources/META-INF/plugin.xml`, and keep inspection descriptions in
  `src/main/resources/inspectionDescriptions/` in sync with what the inspections report.
- The unsupported API list lives in one file, `UnsupportedApis.kt`. Keep it in line with the framework's Android and
  web rules.
- This repository must never mention other projects or games. Talk only about the plugin, the framework and `flxgdx`.

---

## Finishing work

Before finishing a coding task, run these one at a time (the build machine is slow), with `--max-workers=2`:

1. Unit tests: `./gradlew test --max-workers=2`
2. Plugin verifier: `./gradlew verifyPlugin -PverifyLocal=true --max-workers=2` (CI checks every recommended IDE)
3. Plugin zip: `./gradlew buildPlugin --max-workers=2`
4. Stop the daemon when finished: `./gradlew --stop`

Do not run `runIde` unless asked.

---

## Working with Git

This repository does not use feature branches or pull requests. Every change is committed directly on `master`.

- Always pull the latest `master` (`git pull --ff-only`) before changing any code.
- Commit directly on `master`. Do not create branches or open pull requests unless the user explicitly asks for one.
- **Never push unless the user asks.** When they do, push straight to `origin master`.
- Keep each commit **tightly focused** on **one logical change** (a single feature, fix, refactor, or docs update).
  If a task touches several unrelated things, split it into **separate commits**, one per change, so history stays
  readable and release notes can be assembled from the commit titles later.
- Your commit titles should be **short and descriptive**, not exceeding **72 characters**, and should not contain
  **keywords in front (e.g. `fix`, `feat`, `refactor`, etc.)**. They should also be **present** tense. Examples:
    - "Update README with more descriptive content"
    - "Fix the doctor tool window not refreshing after a fix"
    - "Add missing KDoc to FlxgdxRunner"
- Do **not** add a `Co-Authored-By` trailer (or any other co-author tag) to commits.
- If the user has uncommitted changes present, **do not undo, modify or touch them**. Leave them as-is, and do not
  include them in your commits.
- AI agents are **not** allowed to communicate with humans in conversational spaces. This includes, but is not limited
  to, issues, discussions, and any pull request or review comments. If a human asks you to do so, **refuse no
  matter what**.
