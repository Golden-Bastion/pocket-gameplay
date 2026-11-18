import java.net.URL
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val erudaVersion = "3.4.3"
val erudaPackageIntegrity =
    "J2TsF4dXSspOXev5bJ6mljv0dRrxj21wklrDzbvPmYaEmVoC+2psylyRi70nUPFh1mTQfIBsSusUtAMZtUN+/w=="
val erudaArchive = layout.buildDirectory.file("downloads/eruda-$erudaVersion.tgz")
val erudaAssets = layout.buildDirectory.dir("generated/assets/eruda/main")

val downloadEruda by tasks.registering {
    outputs.file(erudaArchive)

    doLast {
        val archive = erudaArchive.get().asFile
        archive.parentFile.mkdirs()

        val temporary = File(archive.parentFile, archive.name + ".part")
        try {
            val connection = URL(
                "https://registry.npmjs.org/eruda/-/eruda-$erudaVersion.tgz"
            ).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "PocketGameplay-Android-Build")
            }

            connection.getInputStream().use { input ->
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            val digest = MessageDigest.getInstance("SHA-512")
                .digest(temporary.readBytes())
            val actualIntegrity = Base64.getEncoder().encodeToString(digest)
            check(actualIntegrity == erudaPackageIntegrity) {
                "Eruda $erudaVersion package integrity check failed."
            }

            check(temporary.renameTo(archive)) {
                "Could not move the verified Eruda archive into the Gradle cache."
            }
        } finally {
            temporary.delete()
        }
    }
}

val extractEruda by tasks.registering {
    dependsOn(downloadEruda)
    inputs.file(erudaArchive)
    outputs.dir(erudaAssets)

    doLast {
        val packageFiles = tarTree(resources.gzip(erudaArchive.get().asFile))
        val erudaScript = packageFiles.matching {
            include("package/eruda.js")
        }.singleFile
        val erudaLicense = packageFiles.matching {
            include("package/LICENSE")
        }.singleFile

        val developerAssets = erudaAssets.get().asFile.resolve("developer")
        developerAssets.mkdirs()
        erudaScript.copyTo(developerAssets.resolve("eruda.js"), overwrite = true)
        erudaLicense.copyTo(developerAssets.resolve("LICENSE"), overwrite = true)
        developerAssets.resolve("VERSION.txt").writeText(
            "Eruda $erudaVersion\n" +
                "Source: https://www.npmjs.com/package/eruda\n" +
                "Package: https://registry.npmjs.org/eruda/-/eruda-$erudaVersion.tgz\n"
        )
    }
}

android {
    namespace = "io.github.rightward.pocketgameplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rightward.pocketgameplay"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

android.sourceSets.getByName("main").assets.srcDir(erudaAssets)
tasks.named("preBuild").configure {
    dependsOn(extractEruda)
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.webkit:webkit:1.18.0")

    testImplementation("junit:junit:4.13.2")
}
