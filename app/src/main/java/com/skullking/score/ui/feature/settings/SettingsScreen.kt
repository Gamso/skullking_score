package com.skullking.score.ui.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.data.backup.AutoBackupManager
import com.skullking.score.domain.repository.AppSettings
import com.skullking.score.domain.repository.SettingsRepository
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val autoBackup: AutoBackupManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    fun setKeepScreenOn(enabled: Boolean) = repository.update { it.copy(keepScreenOn = enabled) }

    fun setRememberSetup(enabled: Boolean) = repository.update { it.copy(rememberSetup = enabled) }

    fun setAutoBackup(enabled: Boolean) {
        repository.update { it.copy(autoBackup = enabled, lastBackupFailed = false) }
        if (enabled) backupNow()
    }

    fun setFolder(uri: Uri) {
        repository.update { it.copy(backupFolder = uri.toString(), lastBackupFailed = false) }
        backupNow()
    }

    fun backupNow() {
        viewModelScope.launch { autoBackup.run() }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onRestore: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setFolder(uri)
        }
    }

    SkullScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_game))
            SettingSwitch(
                title = stringResource(R.string.settings_keep_screen_on),
                description = stringResource(R.string.settings_keep_screen_on_desc),
                checked = settings.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            SettingSwitch(
                title = stringResource(R.string.settings_remember_setup),
                description = stringResource(R.string.settings_remember_setup_desc),
                checked = settings.rememberSetup,
                onChange = viewModel::setRememberSetup,
            )

            SectionTitle(stringResource(R.string.settings_backup))
            SettingSwitch(
                title = stringResource(R.string.settings_auto_backup),
                description = stringResource(R.string.settings_auto_backup_desc),
                checked = settings.autoBackup,
                onChange = viewModel::setAutoBackup,
            )
            if (settings.autoBackup) {
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (settings.backupFolder == null) {
                                R.string.settings_choose_folder
                            } else {
                                R.string.settings_change_folder
                            },
                        ),
                    )
                }
                val last = settings.lastBackupAt
                Text(
                    text = when {
                        settings.lastBackupFailed -> stringResource(R.string.settings_backup_failed)

                        settings.backupFolder == null -> stringResource(R.string.settings_backup_no_folder)

                        last == null -> stringResource(R.string.settings_backup_never)

                        else -> stringResource(
                            R.string.settings_backup_last,
                            DateUtils.getRelativeTimeSpanString(
                                last,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            )
                                .toString(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.lastBackupFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (settings.backupFolder != null) {
                    OutlinedButton(onClick = viewModel::backupNow, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_backup_now))
                    }
                }
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_restore))
            }

            SectionTitle(stringResource(R.string.settings_about))
            val version = remember {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }
            Text(stringResource(R.string.app_name) + " " + version)
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
