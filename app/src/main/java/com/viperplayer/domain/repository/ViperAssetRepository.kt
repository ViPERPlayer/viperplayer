package com.viperplayer.domain.repository

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Storage for the two user-supplied ViPER asset kinds: convolution kernels (`.irs` impulse responses)
 * and ViPER DDC profiles (`.vdc`). Imports copy the document into app-private storage, and the two
 * flows publish what is currently on disk so the DSP screen can list them.
 *
 * Implemented by `data.repository.ViperAssetRepositoryImpl`, which owns the `ContentResolver` and
 * filesystem work. Kept as an interface here so the DSP ViewModel and the audio processor depend on
 * the capability rather than on Android storage APIs.
 */
interface ViperAssetRepository {

    /** Convolution kernels currently in app storage, sorted case-insensitively by file name. */
    val kernelFiles: Flow<List<File>>

    /** DDC profiles currently in app storage, sorted case-insensitively by file name. */
    val ddcFiles: Flow<List<File>>

    /**
     * Copy the kernel document at [uri] into app storage, giving it an `.irs` extension when it has
     * none. Returns the stored file name, or null when the import failed.
     */
    suspend fun importKernel(uri: String): String?

    /**
     * Copy the DDC profile at [uri] into app storage after checking it parses. The extension and the
     * content are validated separately so the UI can explain which one failed.
     */
    suspend fun importDdc(uri: String): DdcImportResult

    /** Delete a stored kernel by file name; a name that is not present is a no-op. */
    suspend fun deleteKernel(fileName: String)

    /** Delete a stored DDC profile by file name; a name that is not present is a no-op. */
    suspend fun deleteDdc(fileName: String)

    /** The stored kernel [fileName] as a [File], or null when it does not exist. */
    fun getKernelFile(fileName: String): File?

    /**
     * Parse a stored DDC profile into per-sample-rate coefficients, or null when the file is missing
     * or holds no `SR_<rate>:` line that parses.
     */
    suspend fun parseDdcCoeffs(fileName: String): Map<Int, List<Float>>?

    /**
     * Read the full text of a document [uri] (e.g. an AutoEq profile). Returns null on any IO
     * failure. Parsing of the returned text is done by pure, testable classes outside this repository.
     */
    suspend fun readTextFromUri(uri: String): String?

    /** Outcome of [importDdc]: success carries the stored name, the failures say what was wrong. */
    sealed interface DdcImportResult {
        data class Success(val fileName: String) : DdcImportResult
        data object InvalidExtension : DdcImportResult
        data object InvalidContent : DdcImportResult
        data object IOError : DdcImportResult
    }
}
