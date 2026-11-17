/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.flixelgdx.intellij.model

/**
 * Builds the argument lists of the project commands, one function per command.
 *
 * Keeping this in one plain object means the dialogs only collect values, and tests can check the exact flags without
 * any UI. The lists never contain `--json` or `--no-input`; the runner adds them.
 *
 * Example:
 * ```
 * CommandArgs.addFromRepo("library", "owner/lib", "", "") // ["add", "library", "--repo", "owner/lib"]
 * ```
 */
object CommandArgs {

  /** The scopes a manually added library can have. */
  val SCOPES = listOf("default", "processor", "desugar", "buildsrc")

  /**
   * `add library|plugin --repo <repo> [--package <pkg>] [--version <tag>]`. Blank [pkg] and [version] are left out,
   * which means "the only package" and "the latest release".
   */
  fun addFromRepo(kind: String, repo: String, pkg: String, version: String): List<String> {
    val args = arrayListOf("add", kind, "--repo", repo.trim())
    if (pkg.isNotBlank()) args += listOf("--package", pkg.trim())
    if (version.isNotBlank()) args += listOf("--version", version.trim())
    return args
  }

  /**
   * `add library|plugin` by hand. A plugin takes its [pluginId] as the argument and has no scope. A library omits
   * `--scope` for the default scope and `--alias` when [alias] is blank.
   */
  fun addManual(
    kind: String,
    group: String,
    artifact: String,
    version: String,
    modules: List<String>,
    scope: String = "default",
    alias: String = "",
    pluginId: String = "",
  ): List<String> {
    val args = arrayListOf("add", kind)
    if (kind == InstalledPackage.KIND_PLUGIN) args += pluginId.trim()
    args += listOf("--group", group.trim(), "--artifact", artifact.trim(), "--version", version.trim())
    args += listOf("--to", modules.joinToString(","))
    if (kind == InstalledPackage.KIND_LIBRARY && scope != "default") args += listOf("--scope", scope)
    if (alias.isNotBlank()) args += listOf("--alias", alias.trim())
    return args
  }

  /**
   * `remove library|plugin` for an installed package: by `--repo` and `--package`, or by `--alias` for an entry that
   * was added by hand.
   */
  fun remove(pkg: InstalledPackage): List<String> {
    val kind = if (pkg.isLibrary) InstalledPackage.KIND_LIBRARY else InstalledPackage.KIND_PLUGIN
    return if (pkg.manual) {
      listOf("remove", kind, "--alias", pkg.alias)
    } else {
      listOf("remove", kind, "--repo", pkg.repo, "--package", pkg.pkg)
    }
  }

  /** `add platform <name>`. */
  fun addPlatform(name: String): List<String> = listOf("add", "platform", name)

  /** `remove platform <name>`, with `--delete-files` when the module folder should go too. */
  fun removePlatform(name: String, deleteFiles: Boolean): List<String> =
    if (deleteFiles) listOf("remove", "platform", name, "--delete-files") else listOf("remove", "platform", name)

  /** `install handbook --repo <repo> [--version <tag>]`. A blank [version] is left out. */
  fun installHandbook(repo: String, version: String): List<String> {
    val args = arrayListOf("install", "handbook", "--repo", repo.trim())
    if (version.isNotBlank()) args += listOf("--version", version.trim())
    return args
  }

  /** `install debugger`, with `--force` to download and unpack it again even when it is already installed. */
  fun installDebugger(force: Boolean): List<String> =
    if (force) listOf("install", "debugger", "--force") else listOf("install", "debugger")

  /** `uninstall handbook --repo <repo>`. */
  fun uninstallHandbook(repo: String): List<String> = listOf("uninstall", "handbook", "--repo", repo)

  /** `upgrade --dry-run`: works out the changes and writes nothing. */
  fun upgradePlan(): List<String> = listOf("upgrade", "--dry-run")

  /** `upgrade --yes`: applies every available upgrade. */
  fun upgradeApply(): List<String> = listOf("upgrade", "--yes")

  /** `doctor`, with `--offline` and `--fix` when asked. */
  fun doctor(offline: Boolean, fix: Boolean): List<String> {
    val args = arrayListOf("doctor")
    if (offline) args += "--offline"
    if (fix) args += "--fix"
    return args
  }

  /** `manifest check <path>`: reads a local `flxgdx.yml` (or the folder that holds it). */
  fun manifestCheck(path: String): List<String> = listOf("manifest", "check", path)

  /** `info`. */
  fun info(): List<String> = listOf("info")
}
