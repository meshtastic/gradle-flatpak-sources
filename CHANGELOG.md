# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.6]

No change to plugin behaviour — the generated `flatpak-sources.json` is identical to
0.1.5. Dependency and CI maintenance only.

### Changed

- Update Gradle to v9.7.0 (#32).
- Update `org.junit.platform:junit-platform-launcher` to v6.1.3 (#33).
- Update `actions/setup-java` to v5.7.0 (#27); update `gradle/actions` to v6.3.0 (#29).
- Update `github/codeql-action` to v4.37.4 through v4.37.7 (#26, #30, #31, #34).

### Added

- Onboarded to the OSS Community Develocity instance for Build Scans and remote
  caching (#28).

## [0.1.5] - 2026-07-26

No change to plugin behaviour — the generated `flatpak-sources.json` is identical to
0.1.4. This release exists to ship the packaging and documentation fixes below.

### Fixed

- The published `-javadoc.jar` now contains the real Dokka HTML API reference. Every
  release up to and including 0.1.4 shipped a 261-byte empty stub, because the plugin
  is written in Kotlin and the stock Java `javadoc` task produced nothing (#14).
- `COPYING` now carries the complete FSF GPL-3.0 text. It was previously abbreviated,
  which left GitHub reporting the licence as "Other" rather than GPL-3.0 (#11).

### Added

- Browsable API documentation published to GitHub Pages at
  <https://meshtastic.github.io/gradle-flatpak-sources/>, built by Dokka on every push
  to `main` (#21).
- Unit tests for the `MirrorGenerator` and `CacheFileLocator` internals, alongside the
  existing TestKit functional suite (#19).
- Community-health files (`CONTRIBUTING`, `CODE_OF_CONDUCT`, `SECURITY`, `CODEOWNERS`,
  issue and PR templates) (#17, #18).

### Security

- Every third-party GitHub Action is pinned to a full commit SHA, workflows run with
  least-privilege `contents: read`, the Gradle wrapper is pinned by SHA-256 checksum,
  and CodeQL, OpenSSF Scorecard, and dependency-review analysis now run on the
  repository (#13, #17, #20, #21). CI-only — the published plugin is unaffected.

## [0.1.4] - 2026-07-17

### Changed

- Update Gradle toolchain to 9.6.1
- Update `com.gradleup.nmcp` to 1.6.1
- Update `actions/checkout` to v7
- Update `org.junit.platform:junit-platform-launcher` to 6.1.2
- Add Renovate configuration with automerge

## [0.1.3] - 2026-07-16 [YANKED]

Burned tag: the publish failed and no artifacts were released to the Gradle
Plugin Portal or Maven Central. Do not use — upgrade to 0.1.4 or later.

## [0.1.2] - 2026-05-28

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
