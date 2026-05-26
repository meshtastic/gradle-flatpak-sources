# Contributing to gradle-flatpak-sources

Thank you for your interest in contributing! We welcome contributions from everyone.

## Getting Started

1. **Fork the repository** and clone your fork.
2. Ensure you have **JDK 17+** installed.
3. Build and test locally:

```bash
./gradlew :plugin:build :plugin:functionalTest --stacktrace
```

## Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- We use [detekt](https://detekt.dev/) for static analysis. Run it before submitting:

```bash
./gradlew :plugin:detekt
```

- Fix any warnings or errors reported. Suppress sparingly and only with justification.

## Making Changes

1. Create a branch from `main` with a descriptive prefix:
   - `bugfix/` — bug fixes
   - `enhancement/` — new features or improvements
   - `docs/` — documentation-only changes
2. Make your changes in logical, atomic commits.
3. Test thoroughly — both unit tests and functional tests should pass.
4. Submit a pull request with a clear description.

## Pull Requests

- Ensure your branch is up to date with `main`.
- Reference related issues (e.g., `Fixes #123`).
- Enable **"Allow edits by maintainers"**.
- Be responsive to review feedback.

## Versioning & Releases

The source carries a `-SNAPSHOT` version. Releases are triggered by pushing a `v*` tag (e.g., `v0.1.0`). The CI workflow builds, tests, and publishes to the Gradle Plugin Portal and Maven Central.

## CLA

Before contributing, you must sign the Meshtastic Contributor License Agreement via [CLA Assistant](https://cla-assistant.io/meshtastic/gradle-flatpak-sources).

## Community

- **Discord:** [discord.gg/meshtastic](https://discord.gg/meshtastic)
- **Discussions:** [GitHub Discussions](https://github.com/meshtastic/gradle-flatpak-sources/discussions)
- **Code of Conduct:** [meshtastic.org/docs/legal/conduct](https://meshtastic.org/docs/legal/conduct/)

## Reporting Issues

- Search existing issues before opening a new one.
- Provide a clear title, steps to reproduce, expected vs. actual behavior.
- Include Gradle version, JDK version, and OS.

Thank you for helping improve gradle-flatpak-sources!
