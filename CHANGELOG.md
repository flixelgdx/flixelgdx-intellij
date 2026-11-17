# Changelog

## [Unreleased]

### Added

- A FlixelGDX menu under Tools and in the project view with Add Library, Add Plugin, Remove Library or Plugin, Add Platform, Remove Platform, Install Handbook, Uninstall Handbook, Upgrade and Run Doctor.
- A FlixelGDX tool window that shows the results of flxgdx doctor, with Run Again, Fix Automatically and Offline buttons.
- A Gradle sync after the project actions change the build.
- Gradle run configurations for each enabled platform (Desktop, Desktop (Debug Tools), Web, Web (Debug Tools) and Android) in a FlixelGDX folder, created when a project opens and after the platforms change.
- A project setting, Create run configurations automatically, under Tools > FlixelGDX > Project.
- A list of recently used repositories in the add and handbook dialogs.
- Four inspections for Java and Kotlin under Editor > Inspections > FlixelGDX: allocation in per-frame code, standard Java collections, reflection, and APIs missing on Android or the web. They only run in modules that use the framework.
- Quick fixes to move a per-frame local variable to a field and to replace a standard collection with the FlixelGDX one.
- WGSL editing: syntax highlighting for `.wgsl` files, File > New > WGSL Shader (a starter fragment shader that follows the framework's shader contract), and a FlixelGDX WGSL group of live templates (`flxfrag`, `flxsample`, `flxuniform`).

## [1.0.0]

### Added

- A New Project wizard under File > New > Project that creates a FlixelGDX game with flxgdx and imports it as a Gradle project.
- A FlixelGDX settings page under Tools with the path of the flxgdx command-line tool and a Test button.
- Automatic detection of flxgdx in the settings, on PATH, and in common install folders.
- A check that the installed flxgdx is new enough, with a notification that explains how to fix it.
- A runner that drives flxgdx through its JSON protocol with progress, cancellation and notifications.
