// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: CC0-1.0

import java.io.ByteArrayOutputStream

plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
    jacoco
}

qupathExtension {
    name = "qupath-extension-braian"
    group = "fr.cnrs.ipmc.silvalab"
    version = "1.1.1-SNAPSHOT"
    description = "QuPath extension for whole-brain data extraction"
    automaticModule = "qupath.extension.braian"
}

dependencies {
    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    shadow(libs.snakeyaml)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    testImplementation(libs.snakeyaml)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.+")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        csv.required = true
    }
    doLast {
        print("INSTRUCTIONS: " + printJacocoCoverage("3"))
        println("  BRANCHES: " + printJacocoCoverage("5"))
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.8".toBigDecimal()
            }
        }
    }
}

fun printJacocoCoverage(xpath: String): String {
    val jacocoCoverage = providers.exec {
        commandLine("xmllint","-html","-xpath", "//tfoot//td[$xpath]/text()", "build/reports/jacoco/test/html/index.html")
    }.standardOutput.asText.get()
    return  jacocoCoverage
}
