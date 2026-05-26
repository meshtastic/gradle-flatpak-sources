# gradle-flatpak-sources

[![CI](https://github.com/meshtastic/gradle-flatpak-sources/actions/workflows/ci.yml/badge.svg)](https://github.com/meshtastic/gradle-flatpak-sources/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/org.meshtastic.flatpak.sources.settings)](https://plugins.gradle.org/plugin/org.meshtastic.flatpak.sources.settings)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](COPYING)
[![CLA assistant](https://cla-assistant.io/readme/badge/meshtastic/gradle-flatpak-sources)](https://cla-assistant.io/meshtastic/gradle-flatpak-sources)

A Gradle plugin that generates [Flathub-compliant](https://docs.flathub.org/docs/for-app-authors/requirements#no-network-access) offline dependency manifests (`flatpak-sources.json`) for Flatpak builds.

## Quick Start

Apply the settings plugin in `settings.gradle.kts` — it captures every dependency download from the very start of the build, with zero additional configuration:

```kotlin
// settings.gradle.kts
plugins {
    id("org.meshtastic.flatpak.sources.settings") version "0.1.0"
}
```

Then generate the manifest using a fresh cache to force all artifacts to re-download:

```bash
./gradlew --no-build-cache --no-configuration-cache \
    -Dgradle.user.home=/tmp/flatpak-gradle-home \
    :app:assemble :captureFlatpakSources
```

The output is written to `build/flatpak-sources.json` by default.

## How It Works

1. **Settings plugin** attaches a `BuildService` listener via [`BuildEventListenerRegistryInternal.onOperationCompletion`](https://github.com/gradle/gradle/blob/master/subprojects/build-events/src/main/java/org/gradle/internal/build/event/BuildEventListenerRegistryInternal.java) before any dependency resolution occurs — the same pattern as `gradle/github-dependency-graph-gradle-plugin`. It also back-fills URLs for artifacts resolved during `pluginManagement {}` (before the listener attached) by inspecting the already-resolved settings classpath.

2. **`captureFlatpakSources` task** reads the captured URL set, locates each artifact in `caches/modules-2/files-2.1`, computes its SHA-256, and emits the manifest. If an artifact is not in the local cache it falls back to downloading and hashing it.

3. **Output** follows the [Flatpak external data format](https://docs.flatpak.org/en/latest/flatpak-builder-command-reference.html#flatpak-manifest): `type: file`, `url`, `sha256`, `dest`, `dest-filename`, plus Maven Central mirror URLs for redundancy.

## Included Builds (`build-logic/`)

If your project uses an included build for convention plugins, apply the settings plugin there too — the plugin detects a pre-registered URL set and reuses it without creating a duplicate listener:

```kotlin
// build-logic/settings.gradle.kts
plugins {
    id("org.meshtastic.flatpak.sources.settings") version "0.1.0"
}
```

## Use in Your Flatpak Manifest

```yaml
# org.example.myapp.yaml
modules:
  - name: dependencies
    buildsystem: simple
    build-commands: ['true']
    sources:
      - flatpak-sources.json
```

## Configuration

```kotlin
flatpakSources {
    // Output file (default: build/flatpak-sources.json)
    outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))

    // Destination prefix in Flatpak sandbox (default: "offline-repository")
    destPrefix.set("offline-repository")

    // Task paths that must complete before capture runs
    mustRunAfterTasks.set(listOf(":app:assemble"))

    // Generate Maven Central mirror URLs (default: true)
    generateMirrors.set(true)

    // URL suffixes to exclude (default: sources + javadoc jars)
    excludeSuffixes.set(setOf("-sources.jar", "-javadoc.jar"))

    // Force-resolve platform-specific native artifacts not resolved on the generation host.
    // Use when generating a Linux Flatpak manifest on macOS or Windows.
    targetPlatforms.set(setOf("linux-x64", "linux-arm64"))

    // Maven coordinate templates for each platform target ({platform} is substituted).
    platformDependencies.set(setOf(
        "org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.144.6",
        "org.jetbrains.compose.desktop:desktop-jvm-{platform}:1.11.0",
    ))
}
```

### Cross-Platform Artifact Resolution

When building on macOS but targeting Linux Flatpak, platform-specific natives (Skiko renderers, Compose Desktop JARs, etc.) won't be resolved by Gradle's normal variant selection. `targetPlatforms` + `platformDependencies` forces them to download into the Gradle cache so they appear in the manifest:

```kotlin
flatpakSources {
    targetPlatforms.set(setOf("linux-x64", "linux-arm64"))
    platformDependencies.set(setOf(
        "org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.144.6",
    ))
}
```

## Requirements

- **Gradle 9.0+**
- **JDK 17+**
- **`--no-configuration-cache`** — the capture mechanism uses runtime state that is not configuration-cache-safe

## Internal APIs

This plugin uses Gradle internal APIs (same trade-off as [flatpak-gradle-generator](https://github.com/flatpak/flatpak-gradle-generator)):

- `org.gradle.internal.operations.BuildOperationListener`
- `org.gradle.internal.resource.ExternalResourceReadBuildOperationType`
- `org.gradle.internal.build.event.BuildEventListenerRegistryInternal`

These APIs have been stable across Gradle 7–9.

## License

Copyright (c) 2026 Meshtastic LLC. Licensed under [GPL-3.0-or-later](COPYING).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, and PR guidelines.
