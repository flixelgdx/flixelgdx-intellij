# Changelog

## [Unreleased]

### Added

- A New Project wizard under File > New > Project that creates a FlixelGDX game from templates bundled with the plugin and imports it as a Gradle project. It asks for the title, company, version, package, window size, language (Java or Kotlin), platforms and a Git repository, and finds the latest framework release on Maven Central unless a version is typed under Advanced. Kotlin games also get the Kotlin extension of the framework.
- A FlixelGDX menu under Tools and in the project view with Add Library, Remove Library, Add Platform, Remove Platform, Install Debugger, Open Debugger and Upgrade.
- Add Library and Remove Library, which edit `gradle/libs.versions.toml` and its bundles while keeping comments and layout. Gradle plugins that need an `id("...")` line in a module are still added to that module by hand.
- Add Platform, which creates the module folder from the platform's templates, adds the catalog entries the platform needs and enables it in `project.toml`, and Remove Platform, which disables it and can delete the folder.
- Upgrade, which shows the latest framework release from Maven Central and updates the catalog and the settings plugin version after you confirm.
- A Gradle sync after the project actions change the build. Every edit can be undone with Edit > Undo.
- Gradle run configurations for each enabled platform (Desktop, Desktop (Debug Tools), Web, Web (Debug Tools) and Android) in a FlixelGDX folder, created when a project opens and after the platforms change.
- A project setting, Create run configurations automatically, under Tools > FlixelGDX > Project.
- Four inspections for Java and Kotlin under Editor > Inspections > FlixelGDX: allocation in per-frame code, standard Java collections, reflection, and APIs missing on Android or the web. They only run in modules that use the framework.
- Quick fixes to move a per-frame local variable to a field and to replace a standard collection with the FlixelGDX one.
- WGSL editing: syntax highlighting for `.wgsl` files, File > New > WGSL Shader (a starter fragment shader that follows the framework's shader contract), and a FlixelGDX WGSL group of live templates (`flxfrag`, `flxsample`, `flxuniform`).
- A Debugger executable setting and an Install Debugger button under Tools > FlixelGDX. The debugger is found from the settings, its install folder, or PATH.
- Install Debugger, which downloads the latest FlixelGDX Debugger release from GitHub with download progress, checks it, swaps it into its install folder and creates the Linux and macOS launchers.
- An Open FlixelGDX Debugger step for the Before launch list of Gradle run configurations that run the debug-tools tasks, added automatically to new Debug Tools configurations when the debugger is installed.
