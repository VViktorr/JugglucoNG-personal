package tk.glucodata.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.R
import tk.glucodata.ui.WearSectionTitle
import tk.glucodata.update.AppUpdateController
import tk.glucodata.update.UpdateEligibility
import tk.glucodata.update.UpdateError
import tk.glucodata.update.UpdateStage
import tk.glucodata.update.updateErrorResource

@Composable
internal fun WearAppUpdatesScreen() {
    val context = LocalContext.current
    val state by AppUpdateController.state.collectAsState()
    LaunchedEffect(context) { AppUpdateController.initialize(context) }
    val transferInProgress = state.stage == UpdateStage.DOWNLOADING ||
        state.stage == UpdateStage.VERIFYING || state.stage == UpdateStage.INSTALLING

    ScreenScaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) {
            item { WearSectionTitle(stringResource(R.string.app_updates_title)) }
            item {
                Text(stringResource(
                    R.string.app_updates_installed_value, state.installedVersionName, state.installedVersionCode
                ))
            }
            if (!state.supported) {
                item {
                    Text(stringResource(if (state.blocker == UpdateEligibility.Blocker.DEBUG_BUILD) {
                        R.string.app_updates_unsupported_debug_title
                    } else {
                        R.string.app_updates_unsupported_store_title
                    }))
                }
            } else {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.checking && !transferInProgress,
                        onClick = { AppUpdateController.checkNow(context) },
                        label = { Text(stringResource(if (state.checking) R.string.app_updates_checking_title
                            else R.string.app_updates_action_check)) },
                    )
                }
                state.error?.let { error ->
                    item { Text(stringResource(updateErrorResource(error))) }
                    if (error == UpdateError.INSTALL_PERMISSION) {
                        item {
                            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    Toast.makeText(context, R.string.wear_app_update_installer_unavailable, Toast.LENGTH_LONG).show()
                                }
                            }, label = { Text(stringResource(R.string.app_updates_permission_title)) })
                        }
                    }
                }
                state.available?.let { available ->
                    item { Text(stringResource(R.string.wear_app_update_available, available.versionName)) }
                    item {
                        when (state.stage) {
                            UpdateStage.DOWNLOADING -> Text(
                                stringResource(R.string.app_updates_downloading_title) +
                                    " ${(state.downloadFraction * 100).toInt()}%"
                            )
                            UpdateStage.VERIFYING -> Text(stringResource(R.string.app_updates_verifying_title))
                            UpdateStage.INSTALLING -> Text(stringResource(R.string.wear_app_update_confirm))
                            else -> Button(modifier = Modifier.fillMaxWidth(), enabled = !state.checking, onClick = {
                                if (state.stage == UpdateStage.READY_TO_INSTALL) AppUpdateController.install(context)
                                else AppUpdateController.startDownload(context, autoInstall = true)
                            }, label = {
                                Text(stringResource(if (state.stage == UpdateStage.READY_TO_INSTALL) R.string.app_updates_action_install
                                    else R.string.app_updates_action_download_install))
                            })
                        }
                    }
                }
                if (!state.checking && state.available == null && state.error == null && state.lastCheckAtMillis > 0) {
                    item { Text(stringResource(R.string.app_updates_up_to_date_title)) }
                }
            }
        }
    }
}
