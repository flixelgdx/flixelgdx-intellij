// The Java settings every module of the game shares. Modules apply it with id("flixelgdx.java").
plugins {
  java
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}
