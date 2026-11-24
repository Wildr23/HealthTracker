package com.gymtracker.app.ui.settings

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.cloud.CloudSyncManager
import com.gymtracker.app.data.cloud.SupabaseAuthResult
import com.gymtracker.app.data.health.HcOriginInfo
import com.gymtracker.app.data.health.HcPermissionState
import com.gymtracker.app.data.health.HealthDataSource
import com.gymtracker.app.data.prefs.AppLanguage
import com.gymtracker.app.data.prefs.AppThemeMode
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val hcPermState: HcPermissionState? = null,
    val discoveredOrigins: List<HcOriginInfo> = emptyList(),
    val statusMessage: String? = null,
    val isSyncingCloud: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val healthDataSource: HealthDataSource,
    private val backupRepository: BackupRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    private val hcState = MutableStateFlow<HcPermissionState?>(null)
    private val origins = MutableStateFlow<List<HcOriginInfo>>(emptyList())
    private val statusMsg = MutableStateFlow<String?>(null)
    private val syncingCloud = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepo.preferences,
        hcState,
        origins,
        statusMsg,
        syncingCloud
    ) { p, hc, orig, msg, syncing ->
        SettingsUiState(p, hc, orig, msg, syncing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        refreshHc()
    }

    fun refreshHc() {
        viewModelScope.launch {
            runCatching {
                val st = healthDataSource.getPermissionState()
                hcState.value = st
                if (st.coreReadGranted) {
                    origins.value = healthDataSource.discoverOrigins(30)
                }
            }
        }
    }

    fun scanOrigins() {
        viewModelScope.launch {
            origins.value = runCatching { healthDataSource.discoverOrigins(30) }.getOrDefault(emptyList())
        }
    }

    fun permissionsToRequest(): Set<String> {
        val st = hcState.value
        val core = healthDataSource.requiredCorePermissions()
        return if (st != null) core + healthDataSource.optionalHistoryAndBgPermissions(st) else core
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            prefsRepo.setLanguage(lang)
            val localeList = if (lang.tag != null) {
                LocaleListCompat.forLanguageTags(lang.tag)
            } else {
                LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun selectOrigin(pkg: String) {
        viewModelScope.launch { prefsRepo.setWatchSourcePackage(pkg) }
    }

    fun setHrMax(hrMax: Int) {
        viewModelScope.launch { prefsRepo.setHrMax(hrMax) }
    }

    fun setEstSecPerRep(sec: Int) {
        viewModelScope.launch { prefsRepo.setEstSecPerRep(sec) }
    }

    fun setDefaultRestSec(sec: Int) {
        viewModelScope.launch { prefsRepo.setDefaultRestSec(sec) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupRepository.exportJsonToUri(uri) }
                .onSuccess { cnt -> statusMsg.value = "Backup exported ($cnt workouts)" }
                .onFailure { err -> statusMsg.value = err.message ?: "Export error" }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupRepository.importJsonFromUri(uri) }
                .onSuccess { cnt -> statusMsg.value = "Restored $cnt workouts" }
                .onFailure { err -> statusMsg.value = err.message ?: "Restore error" }
        }
    }

    fun clearStatusMessage() {
        statusMsg.value = null
    }

    fun saveSupabaseConfig(url: String, key: String) {
        viewModelScope.launch {
            prefsRepo.setSupabaseConfig(url, key)
            statusMsg.value = "Supabase server config saved"
        }
    }

    fun signInSupabase(email: String, password: String) {
        viewModelScope.launch {
            syncingCloud.value = true
            runCatching { cloudSyncManager.signIn(email, password) }
                .onSuccess { session ->
                    statusMsg.value = "Signed in as ${session.email ?: session.userId}"
                }
                .onFailure { err ->
                    statusMsg.value = err.message ?: "Sign-in failed"
                }
            syncingCloud.value = false
        }
    }

    fun signUpSupabase(email: String, password: String) {
        viewModelScope.launch {
            syncingCloud.value = true
            runCatching { cloudSyncManager.signUp(email, password) }
                .onSuccess { res ->
                    statusMsg.value = when (res) {
                        is SupabaseAuthResult.Session ->
                            "Account created & signed in (${res.email ?: res.userId})"
                        is SupabaseAuthResult.ConfirmationRequired ->
                            "Account created! Check ${res.email ?: "your email"} to confirm, then Sign In"
                    }
                }
                .onFailure { err ->
                    statusMsg.value = err.message ?: "Sign-up failed"
                }
            syncingCloud.value = false
        }
    }

    fun signOutSupabase() {
        viewModelScope.launch {
            cloudSyncManager.signOut()
            statusMsg.value = "Signed out from Supabase"
        }
    }

    fun setCloudAutoSync(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSupabaseAutoSyncEnabled(enabled) }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            syncingCloud.value = true
            runCatching { cloudSyncManager.syncNow() }
                .onSuccess { sum ->
                    statusMsg.value = "Cloud synced: ${sum.workoutsUploaded} workouts, ${sum.setsUploaded} sets, ${sum.scaleReadingsUploaded} scale snapshots"
                }
                .onFailure { err ->
                    statusMsg.value = err.message ?: "Cloud sync error"
                }
            syncingCloud.value = false
        }
    }

    fun restoreCloudNow() {
        viewModelScope.launch {
            syncingCloud.value = true
            runCatching { cloudSyncManager.restoreFromCloud() }
                .onSuccess { sum ->
                    statusMsg.value = "Restored from cloud: ${sum.workoutsRestored} workouts, ${sum.bodyWeightsRestored} weight logs, ${sum.scaleReadingsRestored} scale snapshots"
                }
                .onFailure { err ->
                    statusMsg.value = err.message ?: "Cloud restore error"
                }
            syncingCloud.value = false
        }
    }
}
