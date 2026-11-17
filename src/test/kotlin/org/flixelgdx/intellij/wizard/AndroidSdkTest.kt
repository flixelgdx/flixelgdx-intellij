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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidSdkTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun env(
    vars: Map<String, String>,
    home: File,
    windows: Boolean = false,
    mac: Boolean = false,
  ) = SdkEnvironment({ vars[it] }, home.toPath(), windows, mac)

  @Test
  fun androidHomeWins() {
    val home = tmp.newFolder("home")
    File(home, "Android/Sdk").mkdirs()
    val sdk = tmp.newFolder("sdk")
    assertEquals(sdk.toPath(), AndroidSdk.find(env(mapOf("ANDROID_HOME" to sdk.toString()), home)))
  }

  @Test
  fun sdkRootIsUsedWhenHomeIsMissingOrWrong() {
    val home = tmp.newFolder("home")
    val sdk = tmp.newFolder("sdk")
    val vars = mapOf("ANDROID_HOME" to File(tmp.root, "gone").toString(), "ANDROID_SDK_ROOT" to sdk.toString())
    assertEquals(sdk.toPath(), AndroidSdk.find(env(vars, home)))
  }

  @Test
  fun defaultFolderOnLinux() {
    val home = tmp.newFolder("home")
    val sdk = File(home, "Android/Sdk").also { it.mkdirs() }
    assertEquals(sdk.toPath(), AndroidSdk.find(env(emptyMap(), home)))
  }

  @Test
  fun defaultFolderOnMac() {
    val home = tmp.newFolder("home")
    val sdk = File(home, "Library/Android/sdk").also { it.mkdirs() }
    assertEquals(sdk.toPath(), AndroidSdk.find(env(emptyMap(), home, mac = true)))
  }

  @Test
  fun defaultFolderOnWindowsUsesLocalAppData() {
    val home = tmp.newFolder("home")
    val local = tmp.newFolder("local")
    val sdk = File(local, "Android/Sdk").also { it.mkdirs() }
    assertEquals(sdk.toPath(), AndroidSdk.find(env(mapOf("LOCALAPPDATA" to local.toString()), home, windows = true)))
  }

  @Test
  fun nothingFoundGivesNull() {
    val home = tmp.newFolder("home")
    val vars = mapOf("ANDROID_HOME" to "  ", "ANDROID_SDK_ROOT" to File(tmp.root, "gone").toString())
    assertNull(AndroidSdk.find(env(vars, home)))
    assertNull(AndroidSdk.find(env(emptyMap(), home, windows = true)))
  }
}
