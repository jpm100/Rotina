package com.jpm.rotina.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.jpm.rotina.AppViewModel
import com.jpm.rotina.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportMsg = stringResource(R.string.export_done)
    val importMsg = stringResource(R.string.import_done)
    val importErr = stringResource(R.string.import_error)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = vm.exportJson()
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray())
                        }
                    }.isSuccess
                }
                snackbar.showSnackbar(if (ok) exportMsg else importErr)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().decodeToString()
                        } ?: return@runCatching false
                        vm.importJson(json)
                    }.getOrDefault(false)
                }
                snackbar.showSnackbar(if (ok == true) importMsg else importErr)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.notifications_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Card(Modifier.fillMaxWidth()) {
                val exactOk = com.jpm.rotina.alarm.AlarmScheduler.canScheduleExact(context)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.exact_alarm)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (exactOk) R.string.exact_ok else R.string.exact_needed
                            )
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= 31 && !exactOk) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.notif_settings)) },
                    supportingContent = { Text(stringResource(R.string.notif_settings_desc)) },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                )
            }

            Text(
                stringResource(R.string.backup_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_backup)) },
                    supportingContent = { Text(stringResource(R.string.export_desc)) },
                    leadingContent = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        exportLauncher.launch("rotina-backup-${LocalDate.now()}.json")
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.import_backup)) },
                    supportingContent = { Text(stringResource(R.string.import_desc)) },
                    leadingContent = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    }
                )
            }

            Text(
                stringResource(R.string.about),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.about_text)) },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) }
                )
            }
        }
    }
}
