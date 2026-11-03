plugins { id("com.android.application") }

android {
    namespace = "dev.ryuya.galaxydiag"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "dev.ryuya.galaxydiag"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.register("writeRuntimeDependencies") {
    doLast {
        val entries =
            configurations
                .getByName("releaseRuntimeClasspath")
                .resolvedConfiguration
                .resolvedArtifacts
                .map { it.moduleVersion.id.toString() }
                .toSortedSet()
        val out = layout.buildDirectory.file("runtime-dependencies.txt").get().asFile
        out.parentFile.mkdirs()
        out.writeText(entries.joinToString("\n", postfix = "\n"))
    }
}
