package com.waigi.dock.util

import android.os.Environment
import com.waigi.dock.util.PreferenceUtil.getString
import java.io.File

/**
 * Helpers for resolving download directories and file paths.
 * Keeps everything under the public Downloads folder by default,
 * matching Android scoped storage expectations.
 */
object FileUtil {

    /** Default video download directory: /sdcard/Download/Dock/Video/ */
    val defaultVideoDir: File
        get() {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(root, "Dock/Video").also { it.mkdirs() }
        }

    /** Default audio download directory: /sdcard/Download/Dock/Audio/ */
    val defaultAudioDir: File
        get() {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(root, "Dock/Audio").also { it.mkdirs() }
        }

    /** Resolved video directory — reads saved preference or returns the default. */
    val videoDownloadDir: String
        get() = VIDEO_DIRECTORY.getString().ifEmpty { defaultVideoDir.absolutePath }

    /** Resolved audio directory — reads saved preference or returns the default. */
    val audioDownloadDir: String
        get() = AUDIO_DIRECTORY.getString().ifEmpty { defaultAudioDir.absolutePath }

    /** Temp directory for intermediate files during download/conversion. */
    fun getTempDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Dock/.tmp"
        ).also { it.mkdirs() }

    /** Path for the yt-dlp download archive file (prevents re-downloading). */
    fun getArchiveFile(): File =
        File(getTempDir(), "dock_archive.txt").also { if (!it.exists()) it.createNewFile() }

    /** Path for optional yt-dlp config file. */
    fun getConfigFile(): File =
        File(getTempDir(), "dock_config.txt")

    /** Path for cookies Netscape file. */
    fun getCookiesFile(): File =
        File(getTempDir(), "dock_cookies.txt")

    /** Sanitize a filename so it works on all file systems. */
    fun sanitizeFilename(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    fun getFolderSize(path: String): Long {
        val file = File(path)
        if (!file.exists()) return 0L
        return getFolderSizeRecursive(file)
    }

    private fun getFolderSizeRecursive(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            val children = file.listFiles() ?: return 0L
            for (child in children) {
                size += getFolderSizeRecursive(child)
            }
        } else {
            size = file.length()
        }
        return size
    }

    fun cleanFolder(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return deleteFolderContents(file)
    }

    private fun deleteFolderContents(file: File): Boolean {
        var success = true
        if (file.isDirectory) {
            val children = file.listFiles() ?: return true
            for (child in children) {
                if (child.isDirectory) {
                    success = deleteFolderContents(child) && child.delete() && success
                } else {
                    success = child.delete() && success
                }
            }
        }
        return success
    }

    val tempDirSize: Long
        get() = getFolderSizeRecursive(getTempDir())

    fun cleanTempDir(): Boolean =
        deleteFolderContents(getTempDir())

    fun cleanUpSubtitleFiles(videoPath: String) {
        try {
            val videoFile = File(videoPath)
            val parentDir = videoFile.parentFile ?: return
            val baseName = videoFile.nameWithoutExtension
            val extensions = listOf("srt", "vtt", "ass", "lrc")
            val files = parentDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.name.startsWith("$baseName.") && file.absolutePath != videoFile.absolutePath) {
                    val ext = file.extension.lowercase()
                    if (extensions.contains(ext)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun shiftSubtitleTimestamps(inputFile: File, outputFile: File, offsetMs: Int) {
        if (offsetMs == 0) {
            try {
                inputFile.copyTo(outputFile, overwrite = true)
            } catch (e: Exception) {}
            return
        }
        try {
            val lines = inputFile.readLines()
            val writer = outputFile.bufferedWriter()
            
            val timeRegexLong = Regex("""(\d{2}):(\d{2}):(\d{2})([,\.])(\d{3})""")
            val timeRegexShort = Regex("""(\d{2}):(\d{2})([,\.])(\d{3})""")
            
            fun parseToMs(timeStr: String): Long {
                val matchLong = timeRegexLong.matchEntire(timeStr.trim())
                if (matchLong != null) {
                    val h = matchLong.groupValues[1].toLong()
                    val m = matchLong.groupValues[2].toLong()
                    val s = matchLong.groupValues[3].toLong()
                    val ms = matchLong.groupValues[5].toLong()
                    return h * 3600000 + m * 60000 + s * 1000 + ms
                }
                val matchShort = timeRegexShort.matchEntire(timeStr.trim())
                if (matchShort != null) {
                    val m = matchShort.groupValues[1].toLong()
                    val s = matchShort.groupValues[2].toLong()
                    val ms = matchShort.groupValues[4].toLong()
                    return m * 60000 + s * 1000 + ms
                }
                return -1L
            }
            
            fun formatMs(msVal: Long, separator: String, isLong: Boolean): String {
                val clampedMs = msVal.coerceAtLeast(0L)
                val h = clampedMs / 3600000
                val m = (clampedMs % 3600000) / 60000
                val s = (clampedMs % 60000) / 1000
                val ms = clampedMs % 1000
                return if (isLong) {
                    String.format("%02d:%02d:%02d%s%03d", h, m, s, separator, ms)
                } else {
                    String.format("%02d:%02d%s%03d", m, s, separator, ms)
                }
            }
            
            for (line in lines) {
                if (line.contains("-->")) {
                    val parts = line.split("-->")
                    if (parts.size == 2) {
                        val startStr = parts[0].trim()
                        val endStr = parts[1].trim()
                        
                        val isStartLong = timeRegexLong.matchEntire(startStr) != null
                        val separatorStart = if (startStr.contains(",")) "," else "."
                        val startMs = parseToMs(startStr)
                        
                        val isEndLong = timeRegexLong.matchEntire(endStr) != null
                        val separatorEnd = if (endStr.contains(",")) "," else "."
                        val endMs = parseToMs(endStr)
                        
                        if (startMs >= 0 && endMs >= 0) {
                            val newStart = formatMs(startMs + offsetMs, separatorStart, isStartLong)
                            val newEnd = formatMs(endMs + offsetMs, separatorEnd, isEndLong)
                            writer.write("$newStart --> $newEnd")
                            writer.newLine()
                            continue
                        }
                    }
                }
                writer.write(line)
                writer.newLine()
            }
            writer.close()
        } catch (e: Exception) {
            try {
                inputFile.copyTo(outputFile, overwrite = true)
            } catch (ex: Exception) {}
        }
    }
}
