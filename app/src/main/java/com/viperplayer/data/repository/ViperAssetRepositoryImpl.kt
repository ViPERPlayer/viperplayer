package com.viperplayer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.viperplayer.domain.repository.ViperAssetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filesystem + `ContentResolver` implementation of [ViperAssetRepository].
 *
 * Directory creation and the initial scans are lazy: they used to run in an `init` block, which put
 * `mkdirs()` and two `listFiles()` calls on whichever thread Hilt happened to construct on — in
 * practice the main thread, during startup. Each flow now scans on first collection, on
 * [Dispatchers.IO], and re-scans for every new collector so external changes are picked up.
 */
@Singleton
class ViperAssetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViperAssetRepository {

    private val kernelDirectory: File by lazy { File(context.filesDir, "kernels").apply { mkdirs() } }
    private val ddcDirectory: File by lazy { File(context.filesDir, "ddc").apply { mkdirs() } }

    private val _kernelFiles = MutableStateFlow<List<File>>(emptyList())
    override val kernelFiles: Flow<List<File>> =
        _kernelFiles.onStart { withContext(Dispatchers.IO) { refreshKernels() } }

    private val _ddcFiles = MutableStateFlow<List<File>>(emptyList())
    override val ddcFiles: Flow<List<File>> =
        _ddcFiles.onStart { withContext(Dispatchers.IO) { refreshDdc() } }

    private fun refreshKernels() {
        _kernelFiles.value = listFilesSorted(kernelDirectory)
    }

    private fun refreshDdc() {
        _ddcFiles.value = listFilesSorted(ddcDirectory)
    }

    private fun listFilesSorted(directory: File): List<File> =
        directory.listFiles().orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }

    override suspend fun importKernel(uri: String): String? = withContext(Dispatchers.IO) {
        importFile(uri, kernelDirectory) { refreshKernels() }?.let { fileName ->
            if (!fileName.endsWith(".irs", ignoreCase = true)) {
                val newName = "$fileName.irs"
                val source = File(kernelDirectory, fileName)
                val dest = File(kernelDirectory, newName)
                if (source.renameTo(dest)) {
                    refreshKernels()
                    return@let newName
                }
            }
            fileName
        }
    }

    override suspend fun importDdc(uri: String): ViperAssetRepository.DdcImportResult =
        withContext(Dispatchers.IO) {
            try {
                val androidUri = Uri.parse(uri)
                val fileName = getFileName(androidUri) ?: "imported_ddc.vdc"

                if (!fileName.endsWith(".vdc", ignoreCase = true)) {
                    return@withContext ViperAssetRepository.DdcImportResult.InvalidExtension
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
                        return@withContext ViperAssetRepository.DdcImportResult.Success(fileName)
                    } else {
                        return@withContext ViperAssetRepository.DdcImportResult.InvalidContent
                    }
                }
                return@withContext ViperAssetRepository.DdcImportResult.IOError
            } catch (e: Exception) {
                Timber.e(e, "Failed to import DDC profile from %s", uri)
                return@withContext ViperAssetRepository.DdcImportResult.IOError
            }
        }

    override suspend fun deleteKernel(fileName: String) {
        withContext(Dispatchers.IO) {
            val file = File(kernelDirectory, fileName)
            if (file.exists()) {
                file.delete()
                refreshKernels()
            }
        }
    }

    override suspend fun deleteDdc(fileName: String) {
        withContext(Dispatchers.IO) {
            val file = File(ddcDirectory, fileName)
            if (file.exists()) {
                file.delete()
                refreshDdc()
            }
        }
    }

    override fun getKernelFile(fileName: String): File? {
        val file = File(kernelDirectory, fileName)
        return if (file.exists()) file else null
    }

    override suspend fun parseDdcCoeffs(fileName: String): Map<Int, List<Float>>? =
        withContext(Dispatchers.IO) {
            val file = File(ddcDirectory, fileName)
            if (!file.exists()) return@withContext null
            return@withContext parseDdcContent(file.readText())
        }

    override suspend fun readTextFromUri(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val androidUri = Uri.parse(uri)
            context.contentResolver.openInputStream(androidUri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read text from %s", uri)
            null
        }
    }

    private fun importFile(uri: String, destDir: File, onRefresh: () -> Unit): String? {
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
            Timber.e(e, "Failed to import %s into %s", uri, destDir.name)
            return null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not read display name for %s", uri)
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
                        val coeffs = parseFloatList(coeffsPart)
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
            Timber.w(e, "Could not parse DDC content")
        }
        return null
    }

    private fun parseFloatList(data: String): List<Float> =
        data.split(",").mapNotNull { it.trim().toFloatOrNull() }
}
