import java.net.URI
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.1.0"
  id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()

version = providers.gradleProperty("pluginVersion").get()

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    intellijIdeaUltimate(providers.gradleProperty("platformVersion").get())
    instrumentationTools()
    pluginVerifier()
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = "21"
  targetCompatibility = "21"
}

val lspVersion: String? =
    providers.gradleProperty("lspVersion").orNull
        ?: providers.environmentVariable("LSP_VERSION").orNull

val downloadLsp by tasks.registering {
  description = "Downloads infracost-ls binaries into bin/"
  val binDir = project.layout.projectDirectory.dir("bin")

  outputs.dir(binDir)
  onlyIf {
    val files = binDir.asFile.listFiles() ?: emptyArray()
    files.none { it.name.startsWith("infracost-ls-") }
  }

  doLast {
    val platforms =
        listOf(
            "linux-amd64",
            "linux-arm64",
            "darwin-amd64",
            "darwin-arm64",
            "windows-amd64",
            "windows-arm64",
        )

    val version =
        lspVersion
            ?: run {
              logger.lifecycle("No lspVersion set, fetching latest from GitHub...")
              val url = URI("https://api.github.com/repos/infracost/lsp/releases/latest").toURL()
              val json = url.readText()
              val match =
                  Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").find(json)
                      ?: error("Could not determine latest LSP version")
              match.groupValues[1]
            }
    logger.lifecycle("Downloading infracost-ls v$version for all platforms")

    val binDirFile = binDir.asFile
    binDirFile.mkdirs()

    val pluginBinaries =
        listOf(
            "infracost-parser-plugin",
            "infracost-provider-plugin-aws",
            "infracost-provider-plugin-azurerm",
            "infracost-provider-plugin-google",
        )

    for (platform in platforms) {
      val os = platform.substringBefore("-")
      val arch = platform.substringAfter("-")

      val isWindows = os == "windows"
      val ext = if (isWindows) "zip" else "tar.gz"
      val archive = file("${project.layout.buildDirectory.get()}/tmp/lsp_${os}_${arch}.$ext")
      archive.parentFile.mkdirs()

      val downloadUrl =
          "https://github.com/infracost/lsp/releases/download/v$version/lsp_${os}_${arch}.$ext"
      logger.lifecycle("  Downloading $platform...")
      URI(downloadUrl).toURL().openStream().use { input ->
        archive.outputStream().use { output -> input.copyTo(output) }
      }

      val extractDir = file("${project.layout.buildDirectory.get()}/tmp/lsp-extract-$platform")
      extractDir.deleteRecursively()
      extractDir.mkdirs()

      if (isWindows) {
        exec { commandLine("unzip", "-o", archive.absolutePath, "-d", extractDir.absolutePath) }
      } else {
        exec { commandLine("tar", "xzf", archive.absolutePath, "-C", extractDir.absolutePath) }
      }

      val suffix = if (isWindows) ".exe" else ""
      extractDir
          .walk()
          .filter { it.name == "infracost-ls$suffix" && it.isFile }
          .forEach { binary ->
            val target = file("${binDirFile.absolutePath}/infracost-ls-$platform$suffix")
            binary.copyTo(target, overwrite = true)
            target.setExecutable(true)
          }

      for (name in pluginBinaries) {
        extractDir
            .walk()
            .filter { it.name == "$name$suffix" && it.isFile }
            .forEach { binary ->
              val target = file("${binDirFile.absolutePath}/$name-$platform$suffix")
              binary.copyTo(target, overwrite = true)
              target.setExecutable(true)
            }
      }

      extractDir.deleteRecursively()
      archive.delete()
    }
  }
}

tasks {
  prepareSandbox {
    dependsOn(downloadLsp)
    from("bin") { into("${intellijPlatform.projectName.get()}/bin") }
  }
}

intellijPlatform {
  pluginConfiguration {
    name = providers.gradleProperty("pluginName")
    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = providers.gradleProperty("pluginUntilBuild")
    }
  }

  pluginVerification { ides { ide(IntelliJPlatformType.IntellijIdeaUltimate, "2025.1") } }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing { token = providers.environmentVariable("PUBLISH_TOKEN") }
}

providers.gradleProperty("localIdePath").orNull?.let { idePath ->
  intellijPlatformTesting {
    runIde {
      register("runLocalIde") {
        localPath = file(idePath)
        task {
          val nioFsJar = file("$idePath/Contents/lib/nio-fs.jar")
          if (nioFsJar.exists()) {
            jvmArgs("-Xbootclasspath/a:${nioFsJar.absolutePath}")
          }
        }
      }
    }
  }
}
