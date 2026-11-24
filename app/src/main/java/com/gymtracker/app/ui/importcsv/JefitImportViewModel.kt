package com.gymtracker.app.ui.importcsv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.importer.JefitImportReport
import com.gymtracker.app.data.importer.JefitParsedBundle
import com.gymtracker.app.data.repo.JefitImportRepository
import com.gymtracker.app.work.BackfillWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Parsing : ImportUiState
    data class Preview(val fileName: String, val bundle: JefitParsedBundle) : ImportUiState
    data class Importing(val progress: Float) : ImportUiState
    data class Completed(val report: JefitImportReport, val backfillEnqueued: Boolean) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class JefitImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importRepository: JefitImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            runCatching {
                val name = resolveFileName(uri)
                val bundle = importRepository.previewFromUri(uri)
                ImportUiState.Preview(name, bundle)
            }.onSuccess {
                _uiState.value = it
            }.onFailure { err ->
                _uiState.value = ImportUiState.Error(err.message ?: "Failed to parse CSV")
            }
        }
    }

    fun commitImport() {
        val current = _uiState.value as? ImportUiState.Preview ?: return
        viewModelScope.launch {
            _uiState.value = ImportUiState.Importing(0f)
            runCatching {
                importRepository.commitImport(current.bundle, current.fileName) { p ->
                    _uiState.value = ImportUiState.Importing(p)
                }
            }.onSuccess { report ->
                _uiState.value = ImportUiState.Completed(report, backfillEnqueued = false)
            }.onFailure { err ->
                _uiState.value = ImportUiState.Error(err.message ?: "Import failed")
            }
        }
    }

    fun startRetroactiveBackfill() {
        val current = _uiState.value as? ImportUiState.Completed ?: return
        val fromInstant = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        BackfillWorker.enqueue(context, fromInstant)
        _uiState.value = current.copy(backfillEnqueued = true)
    }

    private fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "jefit_export.csv"
    }
}
