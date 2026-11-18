plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
  google()
  mavenCentral()
}

dependencies {
  // The Gradle plugins your modules use, such as the web and Android plugins. Add more to the buildsrc bundle.
  implementation(libs.bundles.buildsrc)
}
