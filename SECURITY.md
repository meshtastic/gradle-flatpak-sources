# Security Policy

## Reporting a vulnerability

Please report security vulnerabilities **privately** — do not open a public issue.

File a private [GitHub Security Advisory](https://github.com/meshtastic/gradle-flatpak-sources/security/advisories/new) on this repository. We aim to acknowledge reports within a few days and will coordinate a fix and disclosure with you. For broader Meshtastic security matters, see <https://meshtastic.org>.

## Supported versions

This Gradle plugin is pre-1.0; only the latest published release (Gradle Plugin Portal / Maven Central) receives security fixes. There is no LTS branch.

## Scope

In scope: the plugin's own code — dependency-download capture and the generated `flatpak-sources.json` manifest. Out of scope: vulnerabilities in Gradle itself or in third-party dependencies (report those upstream).
