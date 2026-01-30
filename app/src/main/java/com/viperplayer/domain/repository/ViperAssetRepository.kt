package com.viperplayer.domain.repository

import android.content.Context
import android.net.Uri
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
class ViperAssetRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val kernelDirectory = File(context.filesDir, "kernels")
    private val ddcDirectory = File(context.filesDir, "ddc")
    
    // Cache of available kernel files
    private val _kernelFiles = MutableStateFlow<List<File>>(emptyList())
    val kernelFiles: Flow<List<File>> = _kernelFiles.asStateFlow()

    // Cache of available DDC files
    private val _ddcFiles = MutableStateFlow<List<File>>(emptyList())
    val ddcFiles: Flow<List<File>> = _ddcFiles.asStateFlow()

    init {
        if (!kernelDirectory.exists()) kernelDirectory.mkdirs()
        if (!ddcDirectory.exists()) ddcDirectory.mkdirs()
        refreshGenerics()
        refreshDdc()
    }

    private fun refreshGenerics() {
        val files = kernelDirectory.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
        _kernelFiles.value = files.sortedBy { it.name.lowercase() }
    }

    private fun refreshDdc() {
        val files = ddcDirectory.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
        _ddcFiles.value = files.sortedBy { it.name.lowercase() }
    }

    suspend fun importKernel(uri: String): String? = withContext(Dispatchers.IO) {
        importFile(uri, kernelDirectory) { refreshGenerics() }?.let { fileName ->
            if (!fileName.endsWith(".irs", ignoreCase = true)) {
                val newName = "$fileName.irs"
                val source = File(kernelDirectory, fileName)
                val dest = File(kernelDirectory, newName)
                if (source.renameTo(dest)) {
                    refreshGenerics()
                    return@let newName
                }
            }
            fileName
        }
    }

    suspend fun importDdc(uri: String): DdcImportResult = withContext(Dispatchers.IO) {
        try {
            val androidUri = Uri.parse(uri)
            val fileName = getFileName(androidUri) ?: "imported_ddc.vdc"
            
            if (!fileName.endsWith(".vdc", ignoreCase = true)) {
                return@withContext DdcImportResult.InvalidExtension
            }

            // Parse check
            context.contentResolver.openInputStream(androidUri)?.use { input ->
                val content = input.bufferedReader().readText()
                if (parseDdcContent(content) != null) {
                    val destFile = File(ddcDirectory, fileName)
                    destFile.outputStream().use { output ->
                        output.write(content.toByteArray())
                    }
                    refreshDdc()
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
    
    suspend fun deleteKernel(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(kernelDirectory, fileName)
        if (file.exists()) {
            file.delete()
            refreshGenerics()
        }
    }

    suspend fun deleteDdc(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(ddcDirectory, fileName)
        if (file.exists()) {
            file.delete()
            refreshDdc()
        }
    }

    fun getKernelFile(fileName: String): File? {
        val file = File(kernelDirectory, fileName)
        return if (file.exists()) file else null
    }

    suspend fun parseDdcCoeffs(fileName: String): Map<Int, List<Float>>? = withContext(Dispatchers.IO) {
        val file = File(ddcDirectory, fileName)
        if (!file.exists()) return@withContext null
        return@withContext parseDdcContent(file.readText())
    }

    private suspend fun importFile(uri: String, destDir: File, onRefresh: () -> Unit): String? {
         try {
            val androidUri = Uri.parse(uri)
            val fileName = getFileName(androidUri) ?: "imported_file"
            
            val destFile = File(destDir, fileName)
            
            context.contentResolver.openInputStream(androidUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            onRefresh()
            return fileName
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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

    private fun parseDdcContent(content: String): Map<Int, List<Float>>? {
        try {
            val resultMap = mutableMapOf<Int, List<Float>>()
            content.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("SR_")) {
                    val ratePart = trimmed.substringBefore(":")
                    val coeffsPart = trimmed.substringAfter(":")
                    val rate = ratePart.removePrefix("SR_").toIntOrNull()
                    
                    if (rate != null) {
                        val coeffs = utilParseFloatArray(coeffsPart).toList()
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

    private fun utilParseFloatArray(data: String): FloatArray {
        return data.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .toFloatArray()
    }

    sealed interface DdcImportResult {
        data class Success(val fileName: String) : DdcImportResult
        data object InvalidExtension : DdcImportResult
        data object InvalidContent : DdcImportResult
        data object IOError : DdcImportResult
    }
}
