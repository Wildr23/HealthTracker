package com.gymtracker.app.ui.importcsv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.importer.JefitImportReport
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JefitImportScreen(
    onBack: () -> Unit,
    viewModel: JefitImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.import_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = { picker.launch(arrayOf("text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_pick_file))
                }
            }

            when (val s = state) {
                ImportUiState.Idle -> Unit
                ImportUiState.Parsing -> {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                is ImportUiState.Preview -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${stringResource(R.string.import_preview_title)}: ${s.fileName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                ReportSummaryBlock(s.bundle.report)
                                Button(
                                    onClick = { viewModel.commitImport() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.import_commit_btn))
                                }
                            }
                        }
                    }
                }
                is ImportUiState.Importing -> {
                    item {
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ImportUiState.Completed -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent)
                                Text(
                                    text = stringResource(R.string.import_success_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                ReportSummaryBlock(s.report)
                                if (!s.backfillEnqueued) {
                                    Button(
                                        onClick = { viewModel.startRetroactiveBackfill() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Watch, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.import_backfill_cta))
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.backfill_notif_title),
                                        color = GoldPr,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                is ImportUiState.Error -> {
                    item {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryBlock(r: JefitImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.import_stat_sessions, r.sessionsRead, r.sessionsImported, r.sessionsSkippedEmpty))
        Text(stringResource(R.string.import_stat_logs, r.exerciseLogsCount, r.setLogRowsCount))
        Text(
            text = stringResource(R.string.import_stat_timestamped, r.timestampedStrengthWorkoutsCount, r.timestampedStrengthSetsCount),
            color = GoldPr,
            fontWeight = FontWeight.SemiBold
        )
        Text(stringResource(R.string.import_stat_external, r.externalHcDuplicatesCount))
        Text(stringResource(R.string.import_stat_eids, r.distinctEidsMapped, r.routinesCount, r.routineDaysCount))
        Text(stringResource(R.string.import_stat_bw, r.bodyWeightsCount, r.bodyWeightOutliers.size))
        Text(stringResource(R.string.import_stat_disagreements, r.setCountDisagreements.size))
        if (r.firstWorkoutDate != null && r.lastWorkoutDate != null) {
            Text(
                text = "${r.firstWorkoutDate} → ${r.lastWorkoutDate}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
