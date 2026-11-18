import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  alias(libs.plugins.kotlin)
  alias(libs.plugins.intelliJPlatform)
  alias(libs.plugins.changelog)
}

val pluginGroup: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val platformType: String by project
val platformVersion: String by project

group = pluginGroup
version = pluginVersion

kotlin {
  jvmToolchain(21)
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  testImplementation(libs.junit)

  intellijPlatform {
    create(platformType, platformVersion)
    bundledPlugins(
      "com.intellij.java",
      "org.jetbrains.plugins.gradle",
      "org.jetbrains.plugins.textmate",
      "org.toml.lang",
      "com.intellij.modules.jcef",
    )
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.Java)
    testBundledPlugin("org.jetbrains.kotlin")
  }
}

changelog {
  groups.empty()
  repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

intellijPlatform {
  pluginConfiguration {
    version = pluginVersion

    description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"
      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("README.md is missing the plugin description markers.")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    }

    val changelog = project.changelog
    changeNotes = providers.gradleProperty("pluginVersion").map { v ->
      with(changelog) {
        renderItem(
          (getOrNull(v) ?: getUnreleased()).withHeader(false).withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }

    ideaVersion {
      sinceBuild = pluginSinceBuild
      // No untilBuild: the plugin stays installable on newer IDEs until the verifier says otherwise.
      untilBuild = provider { null }
    }
  }

  pluginVerification {
    ides {
      // Set -PverifyLocal=true to check only the IDE the plugin is built against.
      if (providers.gradleProperty("verifyLocal").orNull == "true") {
        create(platformType, platformVersion)
      } else {
        recommended()
      }
    }
  }
}

tasks {
  wrapper {
    gradleVersion = "9.5.1"
  }

  test {
    // Pass -Pgolden.update=true to rewrite the golden files of the game templates after a deliberate change.
    providers.gradleProperty("golden.update").orNull?.let { systemProperty("golden.update", it) }
  }
}
