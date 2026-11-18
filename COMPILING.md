# Compiling & Testing

FlixelGDX for IntelliJ IDEA is an IDE plugin, so it is built and tested with the IntelliJ Platform Gradle Plugin
instead of being run on its own. It does not depend on the FlixelGDX framework at all, and it needs no other tools:
the game templates are bundled with it.

This guide focuses on what is specific to this repository. For general setup (installing Git, Eclipse Temurin, and
IntelliJ IDEA), follow the framework's guide:
**[flixelgdx/flixelgdx -> COMPILING.md](https://github.com/flixelgdx/flixelgdx/blob/master/COMPILING.md)**.
The one difference is the JDK version: this repository needs **JDK 21**, not JDK 25 (see
[Prerequisites](#prerequisites)).

---

## Table of contents

1. [Prerequisites](#prerequisites)
2. [Getting the source](#getting-the-source)
3. [Building the plugin](#building-the-plugin)
4. [Running the tests](#running-the-tests)
5. [Running the plugin verifier](#running-the-plugin-verifier)
6. [Trying the plugin locally](#trying-the-plugin-locally)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- **Java (JDK 21, Eclipse Temurin recommended).** The IntelliJ Platform runs on Java 21, so the build compiles the
  plugin with a Java 21 toolchain (`kotlin { jvmToolchain(21) }` in `build.gradle.kts`). The build does not download a
  JDK for you, so a JDK 21 must be installed where Gradle can find it (on `PATH`, in `JAVA_HOME`, or in a standard
  install folder). Install it the same way the framework's
  [COMPILING.md](https://github.com/flixelgdx/flixelgdx/blob/master/COMPILING.md) describes for JDK 25, choosing
  version 21 instead.
- **Git**, to clone this repository.
- **Disk space and patience for the first build.** The IntelliJ Platform Gradle Plugin downloads the IntelliJ IDEA
  distribution the plugin is compiled against (`platformType` and `platformVersion` in `gradle.properties`), which is
  over a gigabyte. Later builds reuse it.
- **(Trying the plugin only)** An internet connection, so the wizard can find the latest framework release on Maven
  Central and the debugger can be downloaded. It is not needed to build or test the plugin.

You do not need to install Gradle or Kotlin. The Gradle wrapper (`gradlew`) downloads the right Gradle version, and
the Kotlin compiler comes with the Kotlin Gradle plugin (its version is in `gradle/libs.versions.toml`).

Verify Java after installing:

```bash
java -version   # should report 21
```

---

## Getting the source

```bash
git clone https://github.com/flixelgdx/flixelgdx-intellij.git
cd flixelgdx-intellij
```

If you are contributing, fork the repository first, clone your fork, and add the upstream remote, exactly as described
in the framework's [CONTRIBUTING.md](https://github.com/flixelgdx/flixelgdx/blob/master/CONTRIBUTING.md).

To work on the plugin in IntelliJ IDEA, open the folder as a Gradle project. Installing the **Plugin DevKit**
plugin in your IDE adds code insight for `plugin.xml` and the IntelliJ Platform APIs.

---

## Building the plugin

```bash
./gradlew buildPlugin
```

This compiles the plugin and packs it into an installable zip at `build/distributions/flixelgdx-intellij-<version>.zip`.
The version comes from `pluginVersion` in `gradle.properties`.

Two parts of the zip come from other files, so a broken one fails the build:

- The plugin description shown in the IDE is the block between `<!-- Plugin description -->` and
  `<!-- Plugin description end -->` at the top of [README.md](README.md).
- The change notes are the matching version (or `[Unreleased]`) section of [CHANGELOG.md](CHANGELOG.md).

On Windows, use `gradlew.bat` instead of `./gradlew` in every command of this guide.

---

## Running the tests

```bash
./gradlew test
```

The tests use JUnit 4 and the IntelliJ Platform test framework. Plain tests (template rendering, validation, version
parsing) run without an IDE. The inspection and TOML editing tests start a light, headless IDE fixture, so the first
run takes longer.

The template tests compare every generated project with the golden files in `src/test/resources/golden/`. After a
deliberate change to the templates, rewrite them with `./gradlew test -Pgolden.update=true` and review the diff.

To run one test class:

```bash
./gradlew test --tests "org.flixelgdx.intellij.template.GameTemplatesTest"
```

---

## Running the plugin verifier

The IntelliJ Plugin Verifier checks that the plugin only uses APIs that exist in the IDE versions it claims to
support, and reports deprecated, internal, and experimental API use.

```bash
./gradlew verifyPlugin -PverifyLocal=true
```

With `-PverifyLocal=true`, it only checks the IDE the plugin is built against, which is already downloaded. Without
the flag it checks every recommended IDE version, which downloads several more IDEs. CI runs the full check, so
use the local one on your machine.

---

## Trying the plugin locally

### In a sandbox IDE

```bash
./gradlew runIde
```

This starts a separate IntelliJ IDEA with the plugin installed, using its own settings folder inside `build/`, so it
never touches your normal IDE settings. Open a FlixelGDX game in it (one with a `project.toml` in its root), or create
one with File > New > Project > FlixelGDX.

### In your own IDE

Build the zip with `./gradlew buildPlugin`, then in IntelliJ IDEA open **Settings > Plugins**, click the gear icon,
choose **Install Plugin from Disk...**, and pick the zip from `build/distributions/`.

---

## Troubleshooting

### Cannot find a Java installation matching languageVersion=21

Gradle could not find a JDK 21 for the toolchain. Install JDK 21 and make sure it is on `PATH` or in `JAVA_HOME`, then
run the build again. Gradle itself can run on a newer JDK; only the toolchain must be 21.

### The first build is very slow or runs out of disk space

The first build downloads the IntelliJ IDEA distribution into the Gradle cache (`~/.gradle/caches`). Make sure you
have a few gigabytes free, and let it finish once; later builds reuse it.

### The build runs out of memory

`gradle.properties` gives the Gradle daemon 1 GB (`org.gradle.jvmargs = -Xmx1g`). On a slow or small machine, run one
task at a time and limit the workers, for example `./gradlew test --max-workers=2`. Stop the daemon with
`./gradlew --stop` when you are done.

### README.md is missing the plugin description markers

The build reads the plugin description from [README.md](README.md). Keep the `<!-- Plugin description -->` and
`<!-- Plugin description end -->` lines when you edit it.

### Creating a project fails to find the latest FlixelGDX version

The wizard reads the latest release from Maven Central. Without a connection, type a version (such as `0.7.2`) under
**Advanced > Framework version** in the wizard.

### Gradle daemon or configuration cache issues

The build uses the configuration cache and the build cache (`gradle.properties`). If a build behaves strangely after
you change build files, stop the daemon with `./gradlew --stop` and run the task again, or add
`--no-configuration-cache` once to rule the cache out.
