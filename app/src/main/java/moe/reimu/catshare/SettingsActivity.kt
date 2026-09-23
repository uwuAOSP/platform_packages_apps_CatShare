package moe.reimu.catshare

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import moe.reimu.catshare.ui.theme.CatShareTheme
import org.uwuaosp.compose.settingslib.PreferenceRow
import org.uwuaosp.compose.settingslib.SettingsHomepageIcon
import org.uwuaosp.compose.settingslib.SettingsScaffold
import org.uwuaosp.compose.settingslib.SettingsSection
import org.uwuaosp.compose.settingslib.SwitchPreferenceRow
import org.uwuaosp.compose.settingslib.TextInputPreferenceDialog
import java.io.File

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                SettingsActivityContent()
            }
        }
    }
}

@Composable
fun SettingsActivityContent() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }

    var deviceNameValue by remember {
        mutableStateOf(settings.deviceName)
    }

    var showDeviceNameDialog by remember {
        mutableStateOf(false)
    }

    var verboseValue by remember {
        mutableStateOf(settings.verbose)
    }

    var autoAcceptValue by remember {
        mutableStateOf(settings.autoAccept)
    }

    fun saveAndFinish() {
        settings.verbose = verboseValue
        settings.autoAccept = autoAcceptValue
        activity?.finish()
    }

    BackHandler {
        saveAndFinish()
    }

    SettingsScaffold(
        title = stringResource(R.string.title_activity_settings),
        showBackButton = true,
        onNavigateUp = { saveAndFinish() },
    ) {
        SettingsSection(title = stringResource(R.string.catshare_category_device)) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.device_name),
                    summary = deviceNameValue,
                    iconContent = {
                        SettingsHomepageIcon(iconRes = R.drawable.ic_mobile_info)
                    },
                    onClick = { showDeviceNameDialog = true },
                )
            }
        }
        SettingsSection(title = stringResource(R.string.catshare_category_transfer)) {
            item {
                SwitchPreferenceRow(
                    title = stringResource(R.string.auto_accept_name),
                    summary = stringResource(R.string.auto_accept_desc),
                    checked = autoAcceptValue,
                    onCheckedChange = { autoAcceptValue = it },
                    iconContent = {
                        SettingsHomepageIcon(iconRes = R.drawable.ic_folder_check)
                    },
                )
            }
            item {
                SwitchPreferenceRow(
                    title = stringResource(R.string.verbose_name),
                    summary = stringResource(R.string.verbose_desc),
                    checked = verboseValue,
                    onCheckedChange = { verboseValue = it },
                    iconContent = {
                        SettingsHomepageIcon(iconRes = R.drawable.ic_expansion_panel)
                    },
                )
            }
        }
        SettingsSection(title = stringResource(R.string.catshare_category_debug)) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.capture_logs),
                    summary = stringResource(R.string.capture_logs_desc),
                    iconContent = {
                        SettingsHomepageIcon(iconRes = R.drawable.ic_bug_report)
                    },
                    onClick = { activity?.let { captureLogs(it) } },
                )
            }
        }
    }

    if (showDeviceNameDialog) {
        TextInputPreferenceDialog(
            title = stringResource(R.string.edit_device_name),
            value = deviceNameValue,
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            errorText = stringResource(R.string.device_name_empty_error),
            validator = { it.isNotBlank() },
            onConfirm = { value ->
                val name = value.trim()
                settings.deviceName = name
                deviceNameValue = name
                showDeviceNameDialog = false
            },
            onDismissRequest = { showDeviceNameDialog = false },
        )
    }
}

private fun captureLogs(context: Context) {
    Thread {
        try {
            val logDir = File(context.cacheDir, "logs")
            logDir.mkdirs()
            val logFile = File(logDir, "logcat.txt")

            logFile.outputStream().use {
                val proc = Runtime.getRuntime().exec("logcat -d")
                try {
                    proc.inputStream.copyTo(it)
                } finally {
                    proc.destroy()
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileProvider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, logFile.name, uri)
                type = "text/plain"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.capture_logs))
            val targets = context.packageManager.queryIntentActivities(intent, 0)
            targets.forEach { target ->
                context.grantUriPermission(
                    target.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("LogcatCapture", "Failed to save logs", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(R.string.log_capture_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }.start()
}
