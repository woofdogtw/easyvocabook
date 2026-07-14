package tw.idv.woofdog.easyvocabook.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.idv.woofdog.easyvocabook.BuildConfig
import tw.idv.woofdog.easyvocabook.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {

            // ── App section ───────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_app))
                SettingsRow(stringResource(R.string.settings_language)) {
                    SegmentedSelect(
                        options = listOf("en" to stringResource(R.string.settings_lang_en),
                            "zh-TW" to stringResource(R.string.settings_lang_zh_tw),
                            "zh-CN" to stringResource(R.string.settings_lang_zh_cn)),
                        selected = state.uiLanguage,
                        onSelect = { activity?.let { a -> vm.setLanguage(it, a) } }
                    )
                }
                SettingsRow(stringResource(R.string.settings_theme)) {
                    SegmentedSelect(
                        options = listOf("light" to stringResource(R.string.settings_theme_light),
                            "dark" to stringResource(R.string.settings_theme_dark),
                            "auto" to stringResource(R.string.settings_theme_auto)),
                        selected = state.theme,
                        onSelect = { activity?.let { a -> vm.setTheme(it, a) } }
                    )
                }
            }

            // ── Sync section ──────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_sync))
                SegmentedSelect(
                    options = listOf(
                        SyncMethod.DISABLED to stringResource(R.string.settings_sync_disabled),
                        SyncMethod.FTP to stringResource(R.string.settings_sync_ftp),
                        SyncMethod.SFTP to stringResource(R.string.settings_sync_sftp),
                        SyncMethod.DRIVE to stringResource(R.string.settings_sync_drive),
                    ),
                    selected = state.syncMethod,
                    onSelect = { vm.setSyncMethod(it) }
                )
                Spacer(Modifier.height(8.dp))
                when (state.syncMethod) {
                    SyncMethod.FTP -> FtpFields(state, vm)
                    SyncMethod.SFTP -> SftpFields(state, vm)
                    SyncMethod.DRIVE -> DriveFields(state, vm)
                    else -> {}
                }
                Spacer(Modifier.height(8.dp))
                if (state.syncInProgress) {
                    Row { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_syncing)) }
                } else {
                    Button(
                        onClick = { vm.syncNow(context) },
                        enabled = state.syncMethod != SyncMethod.DISABLED,
                    ) { Text(stringResource(R.string.settings_sync_now)) }
                    state.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            // ── Practice section ──────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_practice))
                if (state.showClearConfirm) {
                    Text(stringResource(R.string.settings_clear_stats_confirm))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmClearStats() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.settings_clear_yes))
                        }
                        OutlinedButton(onClick = { vm.cancelClearStats() }) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    OutlinedButton(onClick = { vm.askClearStats() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.settings_clear_stats))
                    }
                }
            }

            // ── About section ─────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_about))
                SettingsRow(stringResource(R.string.settings_about_version)) { Text(BuildConfig.VERSION_NAME) }
                SettingsRow(stringResource(R.string.settings_about_author)) { Text(stringResource(R.string.settings_about_author_value)) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FtpFields(state: SettingsUiState, vm: SettingsViewModel) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.ftpHost, { vm.setFtpHost(it) }, label = { Text(stringResource(R.string.settings_ftp_host)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.ftpPort, { vm.setFtpPort(it) }, label = { Text(stringResource(R.string.settings_ftp_port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp))
        OutlinedTextField(state.ftpUser, { vm.setFtpUser(it) }, label = { Text(stringResource(R.string.settings_ftp_user)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.settings_ftp_pass)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.ftpDir, { vm.setFtpDir(it) }, label = { Text(stringResource(R.string.settings_ftp_dir)) }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(state.ftpTls, { vm.setFtpTls(it) })
            Text(stringResource(R.string.settings_ftp_tls))
        }
        Button(onClick = { vm.saveFtpPassword(password, context) }) { Text(stringResource(R.string.settings_save_creds)) }
    }
}

@Composable
private fun SftpFields(state: SettingsUiState, vm: SettingsViewModel) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.sftpHost, { vm.setSftpHost(it) }, label = { Text(stringResource(R.string.settings_sftp_host)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.sftpPort, { vm.setSftpPort(it) }, label = { Text(stringResource(R.string.settings_sftp_port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp))
        OutlinedTextField(state.sftpUser, { vm.setSftpUser(it) }, label = { Text(stringResource(R.string.settings_sftp_user)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.settings_sftp_pass)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.sftpDir, { vm.setSftpDir(it) }, label = { Text(stringResource(R.string.settings_sftp_dir)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveSftpPassword(password, context) }) { Text(stringResource(R.string.settings_save_creds)) }
    }
}

@Composable
private fun DriveFields(state: SettingsUiState, vm: SettingsViewModel) {
    val activity = LocalContext.current as? Activity
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            activity?.let { vm.driveLoginAfterConsent(it) }
        } else {
            vm.setDriveMessage("Drive consent result: code=${result.resultCode}")
        }
    }
    // Google Identity API may require multiple PendingIntent steps (e.g. account picker
    // then scope consent). Collect all of them and launch each one in turn.
    LaunchedEffect(vm) {
        vm.driveAuthResolution.collect { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.driveFolder, { vm.setDriveFolder(it) }, label = { Text(stringResource(R.string.settings_drive_folder)) }, modifier = Modifier.fillMaxWidth())
        if (state.driveLoggedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_logged_in))
                OutlinedButton(onClick = { activity?.let { vm.driveLogout(it) } }) { Text(stringResource(R.string.settings_logout)) }
            }
        } else {
            Button(onClick = { activity?.let { vm.driveLogin(it) } }) {
                Text(stringResource(R.string.settings_login_google))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun <T> SegmentedSelect(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
