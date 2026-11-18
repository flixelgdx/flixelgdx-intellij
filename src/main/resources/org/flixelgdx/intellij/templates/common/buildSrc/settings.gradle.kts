dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
  versionCatalogs {
    create("libs") {
      // Reads the same version catalog the rest of the project uses.
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

rootProject.name = "buildSrc"
