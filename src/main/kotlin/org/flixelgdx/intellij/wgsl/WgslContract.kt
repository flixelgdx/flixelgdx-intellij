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
package org.flixelgdx.intellij.wgsl

/**
 * The names of the WGSL shader contract of the framework, kept in one place for the file template, the live
 * templates and the tests.
 *
 * The framework puts a preamble in front of every shader file. It declares the camera data, the texture and the
 * sampler, so a shader file only supplies the entry points. See the shader chapter of the FlixelGDX handbook.
 */
object WgslContract {

  /** The name of the file template, which is also its file name in `fileTemplates/internal`. */
  const val TEMPLATE = "WGSL Shader"

  /** The fragment entry point every shader file must define. */
  const val FRAGMENT_ENTRY = "fs_main"

  /** The optional vertex entry point. Without it the framework uses its own. */
  const val VERTEX_ENTRY = "vs_main"

  /** The struct that reaches the fragment stage, with `uv` and `color` members. */
  const val VERTEX_OUTPUT = "FlixelVertexOutput"

  /** The texture of the sprite being drawn. Declared by the framework. */
  const val TEXTURE = "flixelTexture"

  /** The sampler for [TEXTURE]. Declared by the framework. */
  const val SAMPLER = "flixelSampler"

  /** The bind group that belongs to the framework. A shader file never declares anything in it. */
  const val FRAMEWORK_GROUP = 0

  /** The bind group of the shader's own uniform struct, at binding 0. */
  const val SHADER_GROUP = 1
}
