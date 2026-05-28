# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.2] - Unreleased

### Added

- Settings plugin (`org.meshtastic.flatpak.sources.settings`) that captures all dependency downloads from build start via `BuildOperationListener` + `BuildService`
- Project plugin (`org.meshtastic.flatpak.sources`) with fallback listener for use without the settings plugin
- `captureFlatpakSources` task that emits a Flathub-compliant `flatpak-sources.json` manifest
- SHA-256 computation from local Gradle cache with remote download fallback
- Maven Central mirror URL generation for redundancy
- Cross-platform artifact resolution via `targetPlatforms` + `platformDependencies` configuration
- Cache scan for artifacts resolved before listener attachment (included build plugins, settings bootstrap)
- URL suffix exclusion (sources/javadoc jars excluded by default)
- Configurable output file, destination prefix, and task ordering
- Functional test suite using Gradle TestKit
- CI workflow (GitHub Actions) with Gradle 9.x matrix
- Publish workflow for Gradle Plugin Portal and Maven Central
