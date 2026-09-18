plugins {
    // The changelog is the one thing that belongs to the repository rather than to
    // :plugin, so it is also the one plugin the root applies. It manages
    // CHANGELOG.md, which stays hand-written: the plugin parses and renders the
    // file and never generates an entry from a commit.
    alias(libs.plugins.changelog)
}

changelog {
    // `version=` in gradle.properties — the same value Gradle puts on
    // `project.version` and the plugin publishes under, so the heading
    // `patchChangelog` cuts cannot disagree with the artifact. Note :plugin appends
    // `-SNAPSHOT` under `-PsnapshotBuild`; the raw property is what a release uses.
    version = providers.gradleProperty("version")
    repositoryUrl = "https://github.com/meshtastic/gradle-flatpak-sources"
    // An empty Unreleased fails the bump here, with the plugin's own message.
    // The default skips the task green and leaves no heading, which the release
    // gate would only catch one tag later.
    patchEmpty = false
    // Breaking leads. A Gradle plugin's contract is its DSL, its task names and its
    // behaviour inside someone else's build, so "do I have to change my build
    // script" is the first question a consumer has.
    groups = listOf("Breaking", "Added", "Changed", "Deprecated", "Removed", "Fixed", "Security")
    // `patchChangelog` rewrites everything between the title and the first section
    // from this value, so anything that must survive a release lives here.
    introduction =
        """
        All notable changes to this project will be documented in this file.

        The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
        """.trimIndent()
}
