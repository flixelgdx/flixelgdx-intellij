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
package org.flixelgdx.intellij.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import org.flixelgdx.intellij.cli.CliStatus
import org.flixelgdx.intellij.cli.FlxgdxCli
import org.flixelgdx.intellij.settings.FlxgdxConfigurable
import java.nio.file.Files
import java.nio.file.Path

/**
 * The second step of the New Project wizard: the game settings, one field per `flxgdx new` flag.
 *
 * Fields check themselves while the person types, using the same rules as `flxgdx` ([ProjectValidation]). The
 * package follows the company and the title until the person edits it. When Create is chosen, [setupProject] hands a
 * [NewProjectSpec] to [NewProjectRunner].
 *
 * Anything that touches the disk or runs `flxgdx` (the Android SDK search, the version check and the folder check)
 * runs on a pooled thread and reports back on the EDT.
 *
 * @param parent the step that owns the project name and location.
 */
class FlxgdxWizardStep(private val parent: NewProjectWizardBaseStep) : AbstractNewProjectWizardStep(parent) {

  private val titleProp = propertyGraph.property(parent.name)
  private val companyProp = propertyGraph.property("")
  private val versionProp = propertyGraph.property(NewProjectSpec.DEFAULT_VERSION)
  private val packageProp = propertyGraph.property("")
  private val widthProp = propertyGraph.property(NewProjectSpec.DEFAULT_WIDTH)
  private val heightProp = propertyGraph.property(NewProjectSpec.DEFAULT_HEIGHT)
  private val kotlinProp = propertyGraph.property(false)
  private val desktopProp = propertyGraph.property(true)
  private val html5Prop = propertyGraph.property(true)
  private val androidProp = propertyGraph.property(false)
  private val librariesProp = propertyGraph.property("")
  private val pluginsProp = propertyGraph.property("")
  private val suggestedProp = propertyGraph.property(true)
  private val handbookProp = propertyGraph.property(true)
  private val gitProp = propertyGraph.property(false)
  private val tagProp = propertyGraph.property("")
  private val templateProp = propertyGraph.property(NewProjectSpec.DEFAULT_TEMPLATE)
  private val androidWarnProp = propertyGraph.property(false)
  private val cliShownProp = propertyGraph.property(false)
  private val folderShownProp = propertyGraph.property(false)

  private val cliLabel = JBLabel(AllIcons.General.Error)
  private val folderLabel = JBLabel(AllIcons.General.Error)

  @Volatile private var sdkMissing = false

  @Volatile private var cliProblem: String? = null

  @Volatile private var folderProblem: String? = null

  private var titleEdited = false
  private var packageEdited = false
  private var syncingTitle = false
  private var deriving = false

  init {
    parent.nameProperty.afterChange { name ->
      if (!titleEdited) {
        syncingTitle = true
        titleProp.set(name)
        syncingTitle = false
      }
      checkFolder()
    }
    parent.pathProperty.afterChange { checkFolder() }
    titleProp.afterChange {
      if (!syncingTitle) titleEdited = true
      derivePackage()
    }
    companyProp.afterChange { derivePackage() }
    packageProp.afterChange { if (!deriving) packageEdited = true }
    androidProp.afterChange { updateAndroidWarning() }
  }

  override fun setupUI(builder: Panel) {
    builder.apply {
      row {
        cell(cliLabel)
        link("Open Settings") {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, FlxgdxConfigurable::class.java)
          checkCli()
        }
        link("Check again") { checkCli() }
      }.visibleIf(cliShownProp)
      row {
        cell(folderLabel)
      }.visibleIf(folderShownProp)

      row("Game title:") {
        textField().bindText(titleProp).columns(COLUMNS).align(AlignX.FILL)
          .checked { ProjectValidation.title(it)?.sentence("Title") }
      }
      row("Company:") {
        textField().bindText(companyProp).columns(COLUMNS).align(AlignX.FILL)
          .checked { ProjectValidation.company(it)?.sentence("Company") }
      }
      row("Version:") {
        textField().bindText(versionProp).columns(COLUMNS)
          .checked { ProjectValidation.version(it)?.sentence("Version") }
      }
      row("Package:") {
        textField().bindText(packageProp).columns(COLUMNS).align(AlignX.FILL)
          .comment("Follows the company and the title until you change it.")
          .checked { packageProblem(it) }
      }
      row("Window size:") {
        textField().bindIntText(widthProp).columns(SIZE_COLUMNS)
          .checked { sizeProblem(it, "Width") { v -> ProjectValidation.width(v) } }
        label("x")
        textField().bindIntText(heightProp).columns(SIZE_COLUMNS)
          .checked { sizeProblem(it, "Height") { v -> ProjectValidation.height(v) } }
        label("pixels")
      }
      buttonsGroup {
        row("Language:") {
          radioButton("Java").bindSelected({ !kotlinProp.get() }, { kotlinProp.set(!it) })
          radioButton("Kotlin").bindSelected(kotlinProp)
        }
      }
      row("Platforms:") {
        checkBox("Desktop").bindSelected(desktopProp)
          .validationOnApply { if (anyPlatform()) null else error("Pick at least one platform.") }
        checkBox("Web").bindSelected(html5Prop)
        checkBox("Android").bindSelected(androidProp)
      }
      row {
        icon(AllIcons.General.Warning)
        label(ANDROID_WARNING)
      }.visibleIf(androidWarnProp)

      row("Libraries:") {
        textField().bindText(librariesProp).align(AlignX.FILL)
          .contextHelp(REPO_HELP, "Libraries")
          .checked { RepoSpecs.problem(it) }
      }
      row("Plugins:") {
        textField().bindText(pluginsProp).align(AlignX.FILL)
          .contextHelp(REPO_HELP, "Plugins")
          .checked { RepoSpecs.problem(it) }
      }
      row {
        checkBox("Install suggested packages").bindSelected(suggestedProp)
      }
      row {
        checkBox("Include the FlixelGDX handbook").bindSelected(handbookProp)
      }
      row {
        checkBox("Create Git repository").bindSelected(gitProp)
          .validationOnApply { cliProblem?.let { error(it) } ?: folderProblem?.let { error(it) } }
      }

      collapsibleGroup("Advanced") {
        row("Framework version:") {
          textField().bindText(tagProp).columns(COLUMNS)
            .comment("A release tag such as v0.8.0. Leave empty for the latest release.")
        }
        row("Template:") {
          comboBox(NewProjectSpec.TEMPLATES).bindItem(templateProp)
        }
      }
    }
    detectSdk()
    checkCli()
    checkFolder()
  }

  override fun setupProject(project: Project) {
    val spec = NewProjectSpec(
      dir = Path.of(parent.path).resolve(parent.name),
      title = titleProp.get().trim(),
      company = companyProp.get().trim(),
      version = versionProp.get().trim(),
      pkg = packageProp.get().trim(),
      width = widthProp.get(),
      height = heightProp.get(),
      language = if (kotlinProp.get()) GameLanguage.KOTLIN else GameLanguage.JAVA,
      desktop = desktopProp.get(),
      html5 = html5Prop.get(),
      android = androidProp.get(),
      libraries = librariesProp.get(),
      plugins = pluginsProp.get(),
      suggested = suggestedProp.get(),
      handbook = handbookProp.get(),
      git = gitProp.get(),
      flixelVersion = tagProp.get(),
      template = templateProp.get(),
    )
    project.service<NewProjectRunner>().createIn(spec)
  }

  private fun Cell<JBTextField>.checked(rule: (String) -> String?): Cell<JBTextField> =
    validationOnInput { field -> rule(field.text.trim())?.let { error(it) } }
      .validationOnApply { field -> rule(field.text.trim())?.let { error(it) } }

  private fun packageProblem(value: String): String? =
    if (value.isEmpty()) "Package must not be empty." else ProjectValidation.packageName(value)?.sentence("Package")

  private fun sizeProblem(text: String, label: String, rule: (Long) -> FieldError?): String? {
    val value = text.toLongOrNull() ?: return "$label must be a whole number."
    return rule(value)?.sentence(label)
  }

  private fun anyPlatform() = desktopProp.get() || html5Prop.get() || androidProp.get()

  private fun derivePackage() {
    if (packageEdited) return
    deriving = true
    packageProp.set(PackageNames.derive(companyProp.get().trim(), titleProp.get().trim()))
    deriving = false
  }

  private fun updateAndroidWarning() {
    androidWarnProp.set(androidProp.get() && sdkMissing)
  }

  private fun detectSdk() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val missing = AndroidSdk.find(SdkEnvironment.system()) == null
      onEdt {
        sdkMissing = missing
        updateAndroidWarning()
      }
    }
  }

  private fun checkCli() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val status = service<FlxgdxCli>().status()
      val text = when (status) {
        is CliStatus.Ok -> null
        else -> status.describe() + " Set its location in the settings, then check again."
      }
      onEdt {
        cliProblem = text
        cliLabel.text = text.orEmpty()
        cliShownProp.set(text != null)
      }
    }
  }

  private fun checkFolder() {
    val base = parent.path
    val name = parent.name
    ApplicationManager.getApplication().executeOnPooledThread {
      val dir = runCatching { Path.of(base).resolve(name) }.getOrNull()
      val entries = if (dir != null && Files.isDirectory(dir)) {
        runCatching { Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() } }
          .getOrDefault(emptyList())
      } else {
        emptyList()
      }
      val text = if (TargetFolder.hasForeignFiles(entries)) {
        "The folder $dir is not empty. Pick an empty folder or another name."
      } else {
        null
      }
      onEdt {
        folderProblem = text
        folderLabel.text = text.orEmpty()
        folderShownProp.set(text != null)
      }
    }
  }

  private fun onEdt(action: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
  }

  companion object {
    private const val COLUMNS = 24
    private const val SIZE_COLUMNS = 6
    private const val ANDROID_WARNING =
      "No Android SDK was found, so Android is skipped until one is set up (ANDROID_HOME or Android Studio)."
    private const val REPO_HELP =
      "Comma-separated repositories to install. Write owner/repo to install a repository, or owner/repo:package " +
        "to pick one package of it. Example: flixelgdx/flixelgdx-ui, someone/tools:tools-core"
  }
}
