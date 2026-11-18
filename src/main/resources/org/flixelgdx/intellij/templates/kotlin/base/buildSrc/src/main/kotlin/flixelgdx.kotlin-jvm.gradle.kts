// The Kotlin settings every module of the game shares. Modules apply it with id("flixelgdx.kotlin-jvm").
plugins {
  kotlin("jvm")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}
