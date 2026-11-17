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
package org.flixelgdx.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * The small icons the plugin shows inside the IDE.
 *
 * The big `META-INF/pluginIcon.svg` is only for the plugin's listing, so every place inside the IDE (such as the
 * New Project list or a tool window button) uses these 16 and 13 pixel icons instead. Each icon has a `_dark`
 * twin that the IDE picks on its own when a dark theme is active.
 */
object FlixelIcons {

  /** The 16 pixel FlixelGDX icon, used in lists such as File > New > Project. */
  @JvmField
  val FLIXELGDX: Icon = IconLoader.getIcon("/icons/flixelgdx.svg", FlixelIcons::class.java)

  /** The 13 pixel single-color icon for the FlixelGDX tool window button. */
  @JvmField
  val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/flixelgdxToolWindow.svg", FlixelIcons::class.java)
}
