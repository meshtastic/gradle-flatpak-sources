/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.detekt)
    alias(libs.plugins.nmcp)
    signing
}

group = "org.meshtastic.flatpak"
if (project.hasProperty("snapshotBuild")) version = "$version-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(files("src/main/kotlin"))
}

dependencies {
    detektPlugins(libs.detekt.formatting)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    website = "https://github.com/meshtastic/gradle-flatpak-sources"
    vcsUrl = "https://github.com/meshtastic/gradle-flatpak-sources.git"

    plugins {
        create("flatpakSources") {
            id = "org.meshtastic.flatpak.sources"
            displayName = "Flatpak Sources Generator (Project)"
            description = "Captures Gradle dependency downloads via BuildOperationListener and emits " +
                "a Flathub-compliant flatpak-sources.json for offline Flatpak builds."
            implementationClass = "org.meshtastic.flatpak.sources.FlatpakSourcesPlugin"
            tags = listOf("flatpak", "flathub", "offline", "maven", "dependencies", "linux", "packaging")
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
        create("flatpakSourcesSettings") {
            id = "org.meshtastic.flatpak.sources.settings"
            displayName = "Flatpak Sources Generator (Settings)"
            description = "Settings plugin that captures ALL Gradle downloads from build start — " +
                "no init script or -I flag needed. Recommended approach."
            implementationClass = "org.meshtastic.flatpak.sources.FlatpakSourcesSettingsPlugin"
            tags = listOf("flatpak", "flathub", "offline", "maven", "dependencies", "linux", "packaging")
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name = "Gradle Flatpak Sources"
                description = "Generate Flathub-compliant offline dependency manifests for Flatpak builds."
                url = "https://github.com/meshtastic/gradle-flatpak-sources"
                licenses {
                    license {
                        name = "GPL-3.0-or-later"
                        url = "https://www.gnu.org/licenses/gpl-3.0.html"
                    }
                }
                developers {
                    developer {
                        id = "meshtastic"
                        name = "Meshtastic LLC"
                        url = "https://meshtastic.org"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/meshtastic/gradle-flatpak-sources.git"
                    developerConnection = "scm:git:ssh://git@github.com:meshtastic/gradle-flatpak-sources.git"
                    url = "https://github.com/meshtastic/gradle-flatpak-sources"
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
    isRequired = !version.toString().endsWith("SNAPSHOT")
}

nmcp {
    publishAllPublicationsToCentralPortal {
        username.set(findProperty("centralPortalUsername") as? String ?: System.getenv("CENTRAL_PORTAL_USERNAME") ?: "")
        password.set(findProperty("centralPortalPassword") as? String ?: System.getenv("CENTRAL_PORTAL_PASSWORD") ?: "")
    }
}

// Functional tests source set
val functionalTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    group = "verification"
    description = "Run functional tests against the plugin."
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check { dependsOn(functionalTestTask) }
