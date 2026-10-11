plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "lager"
version = providers.environmentVariable("VERSION").getOrElse("1.0.0")

labyMod {
    defaultPackageName = "lager"

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    // devLogin = true
                }
            }
        }
    }

    addonInfo {
        namespace = "lagermod"
        displayName = "LagerMod"
        author = "Amando"
        description = "Mod für LabyMod und Fabric zur Lagerverwaltung auf OpSucht"
        minecraftVersion = "*"
        version = rootProject.version.toString()
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version

    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

val iconInJar = "assets/lagermod/textures/icon.png"

tasks.register<Copy>("copyReleaseJar") {
    dependsOn(":core:build")
    from(file("core/build/libs"))
    include("*.jar")
    into(file("build/libs"))
    rename { "lagermod-release.jar" }
}

// Icon in addon.json setzen + lagermod.jar erzeugen (für den Launcher)
tasks.register("finalizeReleaseJar") {
    dependsOn("copyReleaseJar")
    doLast {
        val releaseJar = file("build/libs/lagermod-release.jar")
        require(releaseJar.exists()) {
            "Release-JAR fehlt: " + releaseJar.absolutePath
        }

        val work = layout.buildDirectory.dir("tmp-finalize-jar").get().asFile
        work.deleteRecursively()
        work.mkdirs()

        project.copy {
            from(project.zipTree(releaseJar))
            into(work)
        }

        val q = 34.toChar()
        val brace = 125.toChar()
        val iconField = q + "icon" + q
        val iconValue = iconField + ":" + q + iconInJar + q

        val addonJson = File(work, "addon.json")
        var json = addonJson.readText(Charsets.UTF_8).trimEnd()
        val iconMarker = iconField + ":"
        if (json.contains(iconMarker)) {
            val start = json.indexOf(iconMarker)
            var end = start + iconMarker.length
            if (end < json.length && json[end] == q) {
                end++
                while (end < json.length && json[end] != q) {
                    end++
                }
                if (end < json.length) {
                    end++
                }
            }
            json = json.substring(0, start) + iconValue + json.substring(end)
        } else if (json.endsWith(brace)) {
            json = json.dropLast(1) + "," + iconValue + brace
        }
        addonJson.writeText(json, Charsets.UTF_8)

        val jarExe = File(System.getProperty("java.home"), "bin/jar.exe")
        val jarBin = if (jarExe.exists()) jarExe.absolutePath else "jar"

        releaseJar.delete()
        val exitCode = ProcessBuilder(jarBin, "cf", releaseJar.absolutePath, ".")
            .directory(work)
            .inheritIO()
            .start()
            .waitFor()
        require(exitCode == 0) {
            "jar cf fehlgeschlagen (exit=" + exitCode + ")"
        }

        releaseJar.copyTo(file("build/libs/lagermod.jar"), overwrite = true)
        logger.lifecycle("Release fertig mit icon=" + iconInJar)
    }
}

tasks.named("build") {
    finalizedBy("finalizeReleaseJar")
}

tasks.named("copyReleaseJar") {
    finalizedBy("finalizeReleaseJar")
}
