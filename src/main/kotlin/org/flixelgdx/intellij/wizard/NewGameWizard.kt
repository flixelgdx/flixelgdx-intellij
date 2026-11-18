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
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import javax.swing.Icon

/**
 * Adds "FlixelGDX" to the list on the left of File > New > Project.
 *
 * The wizard is a chain of steps: the standard step with the project name and location, the standard "Create Git
 * repository" checkbox, then [NewGameStep] with the game settings. Choosing Create writes the game files from the
 * templates and imports the result as a Gradle project.
 */
class NewGameWizard : GeneratorNewProjectWizard {

  override val id: String = "org.flixelgdx.ide.newProject"

  override val name: String = "FlixelGDX"

  // The plain Java icon, the same one the built-in Java project wizard shows.
  override val icon: Icon = AllIcons.FileTypes.Java

  override val description: String = "Create a game with the FlixelGDX framework."

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::NewProjectWizardBaseStep)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep(::NewGameStep)
}
