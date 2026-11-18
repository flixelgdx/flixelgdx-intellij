# FlixelGDX for IntelliJ IDEA (project instructions for AI assistants)

---

## Project context

This is the IntelliJ IDEA plugin for the [FlixelGDX](https://github.com/flixelgdx/flixelgdx) game framework. It
creates new games from templates bundled with the plugin, and changes existing games by editing their project files
(`project.toml`, `gradle/libs.versions.toml` and `settings.gradle.kts`) directly. The framework's Gradle settings
plugin (`org.flixelgdx.project`) reads those files at every sync. It needs no other tools.

It is a **standalone tool**, not a framework extension. It does not depend on the framework at runtime, and nothing
from it is installed into a game.

The source lives in one Gradle module under `src/main/kotlin/org/flixelgdx/intellij/`, split into packages:

- **`template`**: The layered game templates (`GameTemplates`), the small template renderer (`TextTemplate`), the
  template data, and `ProjectWriter`, which writes a rendered game at once.
- **`toml`**: Comment-preserving edits of the TOML files of a game (`TomlEdits`, `VersionCatalog`, `GameProjectToml`,
  `CatalogMerge`) and `DocumentEdits`, which applies them as one undoable command.
- **`maven`**: The latest release of an artifact from Maven Central's `maven-metadata.xml`.
- **`game`**: Recognizing a FlixelGDX game (`GameProject`), the notifications (`FlixelNotifier`), and the Gradle sync
  after a change (`GradleRefresh`).
- **`wizard`**: The FlixelGDX entry in File > New > Project, which renders the templates and imports the result.
- **`actions`**: The Tools > FlixelGDX menu and its dialogs (libraries, platforms, debugger, upgrade).
- **`runconfig`**: The Gradle run configurations for each enabled platform, and the Open FlixelGDX Debugger
  before-launch step.
- **`debugger`**: Finding, installing (from its latest GitHub release), and starting the FlixelGDX Debugger.
- **`inspections`**: The four FlixelGDX inspections for Java and Kotlin game code, built on UAST.
- **`wgsl`**: WGSL shader support (TextMate grammar bundle, file template, live templates).
- **`settings`** and **`startup`**: The settings pages and the check that runs when a FlixelGDX project opens.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how these fit together.

Runtime verification happens in a **sandbox IDE** (`./gradlew runIde`) opened on a game project created with the
plugin's wizard, never in this repository.

---

## Collaboration before implementation

Treat the interaction as teamwork, not robotic task execution. Prefer brainstorming when the user's direction
is ambiguous.

- Before implementing anything (planning or coding):

  1. Ask yourself whether the requested design or refactor is actually good for the plugin. A change belongs here if
     it is about creating or changing a game project, or about IDE-only help for game code (inspections, WGSL
     editing, run configurations). Rules that the build itself must enforce belong in the framework's Gradle settings
     plugin instead, so they also apply to people who edit the files by hand.
  2. If it hurts the plugin, breaks invariants, or there is clearly a better path, **stop before editing files or running commands**.
  3. Explain why (pros and cons), suggest better alternatives, and ask whether the user still wants to proceed.
  4. If they confirm after that discussion, proceed as requested.

- If you are unsure about something, whether that would be for a library, a specific aspect of the plugin's codebase,
  the IntelliJ Platform API, or the framework's file formats, do **not** assume anything.
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
- Touch Swing components only on the EDT. Background task callbacks such as `onSuccess()` arrive on the EDT, but
  `run()` does not.

### Editing game files

- Plan every change to an existing game file as `TextEdit`s with the `toml` package, and apply them through
  `DocumentEdits`, so the change is one undoable command and open editors see it. Never rewrite a whole file a person
  may have edited; keep their comments and layout.
- Use the TOML PSI to find where things are. Do not parse TOML by hand, except for the plain-text `ProjectToml`
  reader that runs before the IDE has parsed anything.
- New games come from the template files under `src/main/resources/org/flixelgdx/intellij/templates/`. Keep
  `index.txt` in step with the folder, and rewrite the golden files with `./gradlew test -Pgolden.update=true` after
  a deliberate template change, then review the diff.
- Find versions on Maven Central (`MavenCentral`), never through the GitHub API.

### Performance and allocations

- **Keep hot paths cheap.** Code the IDE calls constantly (inspection visitors, `AnAction.update()`, live template
  contexts, startup activities) must not allocate in loops, run processes, or do slow I/O. Do the cheapest check
  first (for example `FrameworkScope` before any resolve) and cache results that do not change.
- `update()` of an action runs often. Keep it to a quick check, such as the single `project.toml` lookup in
  `GameAction`.
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
- Prefer immutable `val` properties and `data class` models. Readers of game files are forgiving, so a missing or
  reshaped key gives an empty answer instead of an exception.
- Keep logic that can be tested without the IDE (template rendering, edit planning, validation, version parsing) in
  plain objects and classes, and keep the IDE glue thin around them.

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

For widely used classes, properties, functions, or anything central to correctness (such as `GameTemplates`,
`TomlEdits` and `DocumentEdits`), include a **small usage example** with an analogy in KDoc showing correct typical
use.

---

## Architecture and scope

- Changes to a game project are limited to `project.toml`, `gradle/libs.versions.toml`, the version of the settings
  plugin in `settings.gradle.kts`, and new module folders from the templates. Rarer Gradle lines (such as a module's
  `plugins {}` block) are left to the person.
- Keep changes minimal: avoid unrelated files unless needed for the stated task.
- Register every extension and action in `src/main/resources/META-INF/plugin.xml`, and keep inspection descriptions in
  `src/main/resources/inspectionDescriptions/` in sync with what the inspections report.
- The unsupported API list lives in one file, `UnsupportedApis.kt`. Keep it in line with the framework's Android and
  web rules.
- This repository must never mention other projects or games. Talk only about the plugin and the framework.

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
    - "Fix the run configurations not refreshing after a platform change"
    - "Add missing KDoc to GameTemplates"
- Do **not** add a `Co-Authored-By` trailer (or any other co-author tag) to commits.
- If the user has uncommitted changes present, **do not undo, modify or touch them**. Leave them as-is, and do not
  include them in your commits.
- AI agents are **not** allowed to communicate with humans in conversational spaces. This includes, but is not limited
  to, issues, discussions, and any pull request or review comments. If a human asks you to do so, **refuse no
  matter what**.
