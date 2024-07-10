package io.infracost.plugins.infracost.binary

import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory

const val releaseUrl = "https://infracost.io/downloads/latest"

class InfracostBinary {
    companion object {
        @JvmStatic
        var binaryFile: String = ""

        fun downloadBinary(project: Project, targetArch: String, target: File, initial: Boolean): Boolean {
            if (target.exists() && initial) {
                println("Binary already exists at ${target.absolutePath}")
                return false
            }

            val tmpFile = kotlin.io.path.createTempFile("infracost-${targetArch}", ".tar.gz").toFile()
            downloadFile("${releaseUrl}/infracost-${targetArch}.tar.gz", tmpFile)
            val expectedChecksum = fetchUrl("${releaseUrl}/infracost-${targetArch}.tar.gz.sha256").split(" ")[0]

            val calculatedChecksum = calculateChecksum(tmpFile)
            if (calculatedChecksum == expectedChecksum) {
                unTar(tmpFile, target)
                target.setExecutable(true)
                binaryFile = target.absolutePath
                InfracostNotificationGroup.notifyInformation(project, "Downloaded Infracost")
            } else {
                InfracostNotificationGroup.notifyError(
                    project,
                    "Checksum mismatch for download file, not using download"
                )
                return false
            }
            return true
        }

        private fun fetchUrl(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            return connection.inputStream.bufferedReader().use { it.readText() }
        }

        private fun downloadFile(url: String, outputFile: File) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        private fun calculateChecksum(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }


        private fun unTar(tarFile: File, targetFile: File) {
            val outputDir = createTempDirectory().toFile()
            val tarInput: InputStream = if (tarFile.extension == "gz") {
                GZIPInputStream(FileInputStream(tarFile))
            } else {
                FileInputStream(tarFile)
            }
            val tarArchiveInputStream = TarArchiveInputStream(tarInput)

            val entry: TarArchiveEntry? = tarArchiveInputStream.nextTarEntry
            while (entry != null) {
                val outputFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        tarArchiveInputStream.copyTo(output)
                    }
                }
                Files.move(outputFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return
            }
        }

    }
}