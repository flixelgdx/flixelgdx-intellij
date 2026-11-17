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

/**
 * One rejected value of a project setting.
 *
 * The wording matches the `flxgdx` tool and the FlixelGDX Gradle plugin, so a person reads the same complaint in the
 * wizard, on the command line and in the build.
 *
 * @property field the dotted name of the setting, such as `app.title`.
 * @property msg the problem as a full sentence. Most start with "it", as in "it must not be blank."
 */
data class FieldError(val field: String, val msg: String) {

  /** The message in the form `flxgdx` prints: "Invalid app.title in project.toml: it must not be blank." */
  override fun toString() = "Invalid $field in project.toml: $msg"

  /**
   * Rewrites [msg] so it names the field with a friendly [label] instead of "it".
   *
   * Example: `FieldError("app.title", "it must not be blank.").sentence("Title")` gives "Title must not be blank."
   */
  fun sentence(label: String): String =
    if (msg.startsWith("it ")) "$label ${msg.removePrefix("it ")}" else "$label: $msg"
}

/**
 * The same checks that `flxgdx new` and the Gradle plugin run on the settings of a project.
 *
 * Checking in the wizard means a typo is caught while typing instead of after a failed run. Each function returns
 * `null` when the value is fine. The rules and the texts are copies of `internal/project/validate.go` in the `flxgdx`
 * repository, so keep them in step; `ProjectValidationTest` holds the shared table of examples.
 *
 * Example:
 * ```
 * ProjectValidation.title("My Game")   // null, the value is fine
 * ProjectValidation.version("v1")      // FieldError: it must start with a digit, such as '0.1.0'.
 * ```
 */
object ProjectValidation {

  private const val FORBIDDEN_NAME_CHARS = "<>:\"/\\|?*"

  private val RESERVED_NAMES = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
  )

  private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_",
  )

  /** Checks the game title. It ends up in folder names and executable names, so it is limited to safe characters. */
  fun title(value: String): FieldError? = name("app.title", value)

  /** Checks the company name. It follows the same rules as the title. */
  fun company(value: String): FieldError? = name("app.company", value)

  /** Checks the game version: letters, digits, '.', '+' and '-', starting with a digit. */
  fun version(value: String): FieldError? {
    val field = "app.version"
    if (value.isEmpty()) return problem(field, "must not be empty")
    var pos = 0
    for (cp in value.codePoints()) {
      pos++
      val ok = cp in '0'.code..'9'.code || cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code ||
        cp == '.'.code || cp == '+'.code || cp == '-'.code
      if (!ok) {
        return problem(
          field,
          "contains ${describe(cp)} at position $pos, but only letters, digits, '.', '+' and '-' are allowed",
        )
      }
    }
    if (value[0] !in '0'..'9') return problem(field, "must start with a digit, such as '0.1.0'")
    return null
  }

  /** Checks the Java package: lowercase parts separated by dots, none empty and none a Java keyword. */
  fun packageName(value: String): FieldError? {
    val field = "app.package"
    for (segment in value.split('.')) {
      if (segment.isEmpty()) {
        return problem(field, "must not have an empty part (check for a leading, trailing or doubled '.')")
      }
      var index = 0
      for (cp in segment.codePoints()) {
        val ok = cp in 'a'.code..'z'.code || cp == '_'.code || index > 0 && cp in '0'.code..'9'.code
        if (!ok) {
          return problem(
            field,
            "contains ${describe(cp)} in '$segment'; each part must start with a lowercase letter or '_' " +
              "and only contain lowercase letters, digits and '_'",
          )
        }
        index++
      }
      if (segment in JAVA_KEYWORDS) return problem(field, "has the part '$segment', which is a Java keyword")
    }
    return null
  }

  /** Checks the language: `java` or `kotlin`. */
  fun language(value: String): FieldError? =
    if (value == "java" || value == "kotlin") {
      null
    } else {
      problem("app.language", "must be \"java\" or \"kotlin\", not \"$value\"")
    }

  /** Checks the window width in pixels. */
  fun width(value: Long): FieldError? = positive("window.width", value)

  /** Checks the window height in pixels. */
  fun height(value: Long): FieldError? = positive("window.height", value)

  /** Checks one platform name: `desktop`, `html5` or `android`. */
  fun platform(name: String): FieldError? = when (name) {
    "desktop", "html5", "android" -> null
    "ios" -> FieldError("platforms", "\"ios\" is not supported yet. Use \"desktop\", \"html5\" or \"android\".")
    else -> FieldError("platforms", "\"$name\" is not a platform. Use \"desktop\", \"html5\" or \"android\".")
  }

  private fun name(field: String, value: String): FieldError? {
    if (value.trim { Character.isWhitespace(it) || Character.isSpaceChar(it) }.isEmpty()) {
      return problem(field, "must not be blank")
    }
    var pos = 0
    for (cp in value.codePoints()) {
      pos++
      if (cp < 0x20 || cp > 0x7E) {
        return problem(
          field,
          "contains ${describe(cp)} at position $pos, but only printable ASCII characters are allowed",
        )
      }
      if (FORBIDDEN_NAME_CHARS.indexOf(cp.toChar()) >= 0) {
        return problem(
          field,
          "contains ${describe(cp)} at position $pos, which is not allowed in a file name " +
            "(none of $FORBIDDEN_NAME_CHARS may be used)",
        )
      }
    }
    val first = value.first()
    if (first == ' ' || first == '.') return problem(field, "must not start with ${describe(first.code)}")
    val last = value.last()
    if (last == ' ' || last == '.') return problem(field, "must not end with ${describe(last.code)}")
    if (value.uppercase() in RESERVED_NAMES) {
      return problem(field, "must not be '$value', which is a reserved name on Windows")
    }
    return null
  }

  private fun positive(field: String, value: Long): FieldError? = when {
    value > Int.MAX_VALUE -> FieldError(field, "$value is out of range.")
    value <= 0 -> FieldError(field, "it must be greater than 0, not $value.")
    else -> null
  }

  private fun problem(field: String, text: String) = FieldError(field, "it $text.")

  private fun describe(cp: Int): String =
    if (cp < 0x20 || cp > 0x7E) "the character U+%04X".format(cp) else "the character '${cp.toChar()}'"
}

/** Derives names from what the person typed, the same way `flxgdx new` does. */
object PackageNames {

  /**
   * Makes a Java package from the [company] and the [title], in the form `com.company.title` with only lowercase
   * letters and digits, so "Test Studio" and "Java Game" give `com.teststudio.javagame`.
   *
   * A part that starts with a digit gets a leading `_`, and a part that is a Java keyword gets a trailing `_`.
   *
   * @return the package, or an empty string when either name has no letter or digit to build a part from.
   */
  fun derive(company: String, title: String): String {
    val first = part(company)
    val second = part(title)
    if (first.isEmpty() || second.isEmpty()) return ""
    return "com.$first.$second"
  }

  private fun part(name: String): String {
    val sb = StringBuilder()
    for (c in name.lowercase()) {
      if (c in 'a'..'z' || c in '0'..'9') sb.append(c)
    }
    var part = sb.toString()
    if (part.isEmpty()) return ""
    if (part[0] in '0'..'9') part = "_$part"
    // Only a Java keyword can fail here, such as "new".
    if (ProjectValidation.packageName(part) != null) part += "_"
    return part
  }
}
