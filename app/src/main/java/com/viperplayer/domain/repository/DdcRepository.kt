package com.viperplayer.domain.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DdcRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ddcDirectory = File(context.filesDir, "ddc")
    
    private val _ddcFiles = MutableStateFlow<List<File>>(emptyList())
    val ddcFiles: Flow<List<File>> = _ddcFiles.asStateFlow()

    init {
        if (!ddcDirectory.exists()) {
            ddcDirectory.mkdirs()
        }
        _ddcFiles.value = ddcDirectory.listFiles()?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase() } ?: emptyList() 
    }

    suspend fun refreshFiles() = withContext(Dispatchers.IO) {
        val files = ddcDirectory.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
        _ddcFiles.value = files.sortedBy { it.name.lowercase() }
    }

    suspend fun addDdcFile(sourceFile: File) = withContext(Dispatchers.IO) {
        if (sourceFile.exists()) {
            val destFile = File(ddcDirectory, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            refreshFiles()
        }
    }
    
    sealed interface DdcImportResult {
        data class Success(val fileName: String) : DdcImportResult
        data object InvalidExtension : DdcImportResult
        data object InvalidContent : DdcImportResult
        data object IOError : DdcImportResult
    }

    suspend fun importDdcFile(uri: String): DdcImportResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val androidUri = android.net.Uri.parse(uri)
            val fileName = getFileName(androidUri, contentResolver) ?: "imported_ddc.vdc"
            
            // Validation 1: Check extension
            if (!fileName.endsWith(".vdc", ignoreCase = true)) {
                return@withContext DdcImportResult.InvalidExtension
            }

            // Validation 2: Try to parse content
            contentResolver.openInputStream(androidUri)?.use { input ->
                val content = input.bufferedReader().readText()
                if (parseFileContent(content) != null) {
                    // Valid content, proceed to save
                    val destFile = File(ddcDirectory, fileName)
                    destFile.outputStream().use { output ->
                        output.write(content.toByteArray())
                    }
                    refreshFiles()
                    return@withContext DdcImportResult.Success(fileName)
                } else {
                    return@withContext DdcImportResult.InvalidContent
                }
            }
            return@withContext DdcImportResult.IOError
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext DdcImportResult.IOError
        }
    }

    suspend fun deleteDdcFile(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(ddcDirectory, fileName)
        if (file.exists()) {
            file.delete()
            refreshFiles()
        }
    }

    suspend fun parseDdcCoeffs(fileName: String): Map<Int, List<Float>>? = withContext(Dispatchers.IO) {
        val file = File(ddcDirectory, fileName)
        if (!file.exists()) return@withContext null
        return@withContext parseFileContent(file.readText())
    }

    private fun parseFileContent(content: String): Map<Int, List<Float>>? {
        try {
            val resultMap = mutableMapOf<Int, List<Float>>()
            content.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("SR_")) {
                    val ratePart = trimmed.substringBefore(":")
                    val coeffsPart = trimmed.substringAfter(":")
                    val rate = ratePart.removePrefix("SR_").toIntOrNull()
                    
                    if (rate != null) {
                        val coeffs = parseLine(coeffsPart).toList()
                        if (coeffs.isNotEmpty()) {
                            resultMap[rate] = coeffs
                        }
                    }
                }
            }

            if (resultMap.isNotEmpty()) {
                return resultMap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseLine(data: String): FloatArray {
        return data.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .toFloatArray()
    }
    
    private fun getFileName(uri: android.net.Uri, contentResolver: android.content.ContentResolver): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result!!.substring(cut + 1)
            }
        }
        return result
    }
}
