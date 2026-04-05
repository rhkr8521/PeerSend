package com.rhkr8521.p2ptransfer.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class P2pStorage(
    private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "p2p_storage"
        private const val KEY_SAVE_TREE_URI = "save_tree_uri"
    }

    sealed interface OutputTarget {
        val displayName: String
    }

    data class LocalOutput(val file: File) : OutputTarget {
        override val displayName: String = file.name
    }

    data class TreeOutput(val document: DocumentFile) : OutputTarget {
        override val displayName: String = document.name ?: "downloaded_file.dat"
    }

    data class MediaStoreOutput(
        val uri: Uri,
        override val displayName: String,
        val relativePath: String,
    ) : OutputTarget

    sealed interface DirectoryTarget {
        val displayName: String
    }

    data class LocalDirectory(val dir: File) : DirectoryTarget {
        override val displayName: String = dir.name
    }

    data class TreeDirectory(val document: DocumentFile) : DirectoryTarget {
        override val displayName: String = document.name ?: "folder"
    }

    data class MediaStoreDirectory(
        val relativePath: String,
        override val displayName: String,
    ) : DirectoryTarget

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun persistSaveTreeUri(uri: Uri?) {
        prefs.edit().putString(KEY_SAVE_TREE_URI, uri?.toString()).apply()
    }

    fun currentSaveTreeUri(): Uri? = prefs.getString(KEY_SAVE_TREE_URI, null)?.let(Uri::parse)

    fun saveLocationLabel(): String = when (val root = currentRoot()) {
        is LocalDirectory -> root.dir.absolutePath
        is TreeDirectory -> root.document.name ?: root.document.uri.toString()
        is MediaStoreDirectory -> actualMediaStorePath(root.relativePath)
    }

    fun currentRoot(): DirectoryTarget {
        val treeRoot = currentSaveTreeUri()
            ?.let { uri -> DocumentFile.fromTreeUri(context, uri) }
            ?.takeIf { it.canWrite() }
        if (treeRoot != null) {
            return TreeDirectory(treeRoot)
        }

        return MediaStoreDirectory(
            relativePath = defaultRelativePath(),
            displayName = "Download",
        )
    }

    fun createUniqueOutputTarget(displayName: String, parent: DirectoryTarget = currentRoot()): OutputTarget {
        val cleanName = safeName(displayName)
        return when (parent) {
            is LocalDirectory -> {
                parent.dir.mkdirs()
                LocalOutput(uniqueLocalFile(parent.dir, cleanName))
            }

            is TreeDirectory -> {
                runCatching {
                    TreeOutput(uniqueTreeFile(parent.document, cleanName))
                }.getOrElse {
                    createMediaStoreOutput(defaultRelativePath(), cleanName)
                }
            }

            is MediaStoreDirectory -> {
                createMediaStoreOutput(parent.relativePath, cleanName)
            }
        }
    }

    fun createUniqueDirectory(displayName: String, parent: DirectoryTarget = currentRoot()): DirectoryTarget {
        val cleanName = safeName(displayName).substringBeforeLast('.').ifBlank { "files" }
        return when (parent) {
            is LocalDirectory -> {
                parent.dir.mkdirs()
                LocalDirectory(uniqueLocalDirectory(parent.dir, cleanName))
            }

            is TreeDirectory -> {
                runCatching {
                    TreeDirectory(uniqueTreeDirectory(parent.document, cleanName))
                }.getOrElse {
                    uniqueMediaStoreDirectory(defaultRelativePath(), cleanName)
                }
            }

            is MediaStoreDirectory -> {
                uniqueMediaStoreDirectory(parent.relativePath, cleanName)
            }
        }
    }

    fun createOutputInDirectory(parent: DirectoryTarget, displayName: String): OutputTarget {
        return createUniqueOutputTarget(displayName, parent)
    }

    fun openOutputStream(target: OutputTarget): OutputStream = when (target) {
        is LocalOutput -> BufferedOutputStream(FileOutputStream(target.file))
        is TreeOutput -> {
            context.contentResolver.openOutputStream(target.document.uri, "w")
                ?: error("Unable to open output stream for ${target.document.uri}")
        }

        is MediaStoreOutput -> {
            context.contentResolver.openOutputStream(target.uri, "w")
                ?: error("Unable to open output stream for ${target.uri}")
        }
    }

    fun openInputStream(target: OutputTarget): InputStream = when (target) {
        is LocalOutput -> FileInputStream(target.file)
        is TreeOutput -> {
            context.contentResolver.openInputStream(target.document.uri)
                ?: error("Unable to open input stream for ${target.document.uri}")
        }

        is MediaStoreOutput -> {
            context.contentResolver.openInputStream(target.uri)
                ?: error("Unable to open input stream for ${target.uri}")
        }
    }

    fun deleteTarget(target: OutputTarget) {
        when (target) {
            is LocalOutput -> if (target.file.exists()) {
                target.file.delete()
            }

            is TreeOutput -> target.document.delete()
            is MediaStoreOutput -> context.contentResolver.delete(target.uri, null, null)
        }
    }

    fun describeDirectory(directory: DirectoryTarget): String = when (directory) {
        is LocalDirectory -> directory.dir.absolutePath
        is TreeDirectory -> directory.document.name ?: directory.document.uri.toString()
        is MediaStoreDirectory -> actualMediaStorePath(directory.relativePath)
    }

    private fun uniqueLocalFile(parent: File, displayName: String): File {
        val baseName = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidate = File(parent, displayName)
        var index = 1
        while (candidate.exists()) {
            val name = if (extension.isNotEmpty()) "${baseName}_$index.$extension" else "${baseName}_$index"
            candidate = File(parent, name)
            index += 1
        }
        return candidate
    }

    private fun uniqueLocalDirectory(parent: File, displayName: String): File {
        var candidate = File(parent, displayName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "${displayName}_$index")
            index += 1
        }
        candidate.mkdirs()
        return candidate
    }

    private fun uniqueTreeFile(parent: DocumentFile, displayName: String): DocumentFile {
        val baseName = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidateName = displayName
        var index = 1
        while (parent.findFile(candidateName) != null) {
            candidateName = if (extension.isNotEmpty()) "${baseName}_$index.$extension" else "${baseName}_$index"
            index += 1
        }
        val mimeCandidates = buildList {
            add(mimeTypeFromName(candidateName))
            add("application/octet-stream")
            add("*/*")
        }.distinct()

        mimeCandidates.forEach { mimeType ->
            parent.createFile(mimeType, candidateName)?.let { return it }
        }

        error("Unable to create document for $candidateName")
    }

    private fun uniqueTreeDirectory(parent: DocumentFile, displayName: String): DocumentFile {
        var candidateName = displayName
        var index = 1
        while (parent.findFile(candidateName) != null) {
            candidateName = "${displayName}_$index"
            index += 1
        }
        return parent.createDirectory(candidateName)
            ?: error("Unable to create directory for $candidateName")
    }

    private fun createMediaStoreOutput(relativePath: String, displayName: String): MediaStoreOutput {
        val uniqueName = uniqueMediaStoreFileName(relativePath, displayName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFromName(uniqueName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create MediaStore download for $uniqueName")
        return MediaStoreOutput(
            uri = uri,
            displayName = uniqueName,
            relativePath = relativePath,
        )
    }

    private fun uniqueMediaStoreDirectory(parentRelativePath: String, displayName: String): MediaStoreDirectory {
        var candidateName = displayName
        var index = 1
        while (mediaStoreRelativePathExists("$parentRelativePath$candidateName/")) {
            candidateName = "${displayName}_$index"
            index += 1
        }
        return MediaStoreDirectory(
            relativePath = "$parentRelativePath$candidateName/",
            displayName = candidateName,
        )
    }

    private fun uniqueMediaStoreFileName(relativePath: String, displayName: String): String {
        val baseName = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidateName = displayName
        var index = 1
        while (mediaStoreFileExists(relativePath, candidateName)) {
            candidateName = if (extension.isNotEmpty()) {
                "${baseName}_$index.$extension"
            } else {
                "${baseName}_$index"
            }
            index += 1
        }
        return candidateName
    }

    private fun mediaStoreFileExists(relativePath: String, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(relativePath, displayName)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun mediaStoreRelativePathExists(relativePath: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(relativePath)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun defaultRelativePath(): String = "${Environment.DIRECTORY_DOWNLOADS}/"

    @Suppress("DEPRECATION")
    private fun actualMediaStorePath(relativePath: String): String {
        val normalized = relativePath.removeSuffix("/")
        return File(Environment.getExternalStorageDirectory(), normalized).absolutePath
    }
}
