package moe.reimu.catshare

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import moe.reimu.catshare.models.DiscoveredDevice
import moe.reimu.catshare.models.FileInfo
import moe.reimu.catshare.models.TaskInfo
import moe.reimu.catshare.services.GattServerService
import moe.reimu.catshare.services.P2pReceiverService
import moe.reimu.catshare.services.P2pSenderService
import moe.reimu.catshare.ui.theme.CatShareTheme
import moe.reimu.catshare.utils.INTERNAL_BROADCAST_PERMISSION
import moe.reimu.catshare.utils.ServiceState
import moe.reimu.catshare.utils.registerInternalBroadcastReceiver
import org.uwuaosp.compose.settingslib.PreferenceRow
import org.uwuaosp.compose.settingslib.SettingsHomepageIcon
import org.uwuaosp.compose.settingslib.SettingsScaffold
import org.uwuaosp.compose.settingslib.SettingsToolbarActionButton
import org.uwuaosp.compose.settingslib.SettingsSection
import org.uwuaosp.compose.settingslib.SwitchPreferenceRow
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        enableEdgeToEdge()
        setContent {
            CatShareTheme {
                MainActivityContent()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= 32 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= 31) {
            for (perm in listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )) {
                if (ContextCompat.checkSelfPermission(
                        this, perm
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(perm)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray, deviceId: Int
    ) {
        for ((name, status) in permissions.zip(grantResults.toList())) {
            if (status == PackageManager.PERMISSION_GRANTED) {
                continue
            }

            Toast.makeText(this, "$name not granted", Toast.LENGTH_LONG).show()
            finish()

            return
        }
    }

    fun extractFileInfo(uri: Uri): FileInfo? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        )
        return contentResolver.query(uri, projection, null, null)?.use {
            if (it.moveToFirst()) {
                val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                FileInfo(
                    uri,
                    it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                    if (mimeIndex < 0) {
                        "application/octet-stream"
                    } else {
                        it.getString(mimeIndex)
                    },
                    it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    null
                )
            } else {
                null
            }
        }
    }
}

@Composable
fun MainActivityContent() {
    var checked by remember { mutableStateOf(false) }
    var receiveCardStates by remember { mutableStateOf<List<ReceiveCardState>>(emptyList()) }
    var sendCardStates by remember { mutableStateOf<List<SendCardState>>(emptyList()) }
    var pendingSendFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }

    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ServiceState.ACTION_UPDATE_RECEIVER_STATE) {
                    checked = intent.getBooleanExtra("isRunning", false)
                }
            }
        }

        context.registerInternalBroadcastReceiver(
            receiver,
            IntentFilter(ServiceState.ACTION_UPDATE_RECEIVER_STATE),
        )
        context.sendBroadcast(ServiceState.getQueryIntent())

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != P2pReceiverService.ACTION_RECEIVE_CARD_UPDATE) return
                receiveCardStates = ReceiveCardState.updateList(intent, receiveCardStates)
            }
        }

        context.registerInternalBroadcastReceiver(
            receiver,
            IntentFilter(P2pReceiverService.ACTION_RECEIVE_CARD_UPDATE),
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != P2pSenderService.ACTION_SEND_CARD_UPDATE) return
                sendCardStates = SendCardState.updateList(intent, sendCardStates)
            }
        }

        context.registerInternalBroadcastReceiver(
            receiver,
            IntentFilter(P2pSenderService.ACTION_SEND_CARD_UPDATE),
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { pickedUris ->
        if (pickedUris.isNotEmpty()) {
            val activity = context as? MainActivity
            val fileInfos = activity?.let { mainActivity ->
                try {
                    pickedUris.mapNotNull { mainActivity.extractFileInfo(it) }
                } catch (_: Throwable) {
                    emptyList()
                }
            }.orEmpty()
            if (fileInfos.isEmpty()) {
                Toast.makeText(context, R.string.no_file_shared, Toast.LENGTH_SHORT).show()
            } else {
                pendingSendFiles = fileInfos
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScaffold(
            title = stringResource(R.string.app_name),
            showBackButton = false,
            actions = {
                SettingsToolbarActionButton(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.title_activity_settings),
                    onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                )
            },
        ) {
            AnimatedVisibility(
                visible = sendCardStates.isNotEmpty() || receiveCardStates.isNotEmpty(),
                enter = fadeIn(tween(220)) + expandVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.animateContentSize(tween(280, easing = FastOutSlowInEasing)),
                ) {
                    sendCardStates.forEach { state ->
                        SendTransferCard(
                            state = state,
                            onCancel = {
                                context.sendBroadcast(
                                    P2pSenderService.getCancelIntent(state.taskId),
                                    INTERNAL_BROADCAST_PERMISSION,
                                )
                            },
                            onClose = {
                                sendCardStates = sendCardStates.withoutSendTask(state.taskId)
                            },
                        )
                    }
                    receiveCardStates.forEach { state ->
                        ReceiveTransferCard(
                            state = state,
                            onAccept = {
                                context.sendBroadcast(
                                    P2pReceiverService.getAcceptIntent(state.taskId),
                                    INTERNAL_BROADCAST_PERMISSION,
                                )
                            },
                            onReject = {
                                context.sendBroadcast(
                                    P2pReceiverService.getDismissIntent(state.taskId),
                                    INTERNAL_BROADCAST_PERMISSION,
                                )
                                receiveCardStates = receiveCardStates.withoutReceiveTask(state.taskId)
                            },
                            onCancel = {
                                context.sendBroadcast(
                                    P2pReceiverService.getCancelIntent(state.taskId),
                                    INTERNAL_BROADCAST_PERMISSION,
                                )
                            },
                            onClose = {
                                receiveCardStates = receiveCardStates.withoutReceiveTask(state.taskId)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            SettingsSection(title = stringResource(R.string.catshare_category_transfer)) {
                item {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.discoverable),
                        summary = stringResource(R.string.discoverable_desc),
                        checked = checked,
                        iconContent = {
                            SettingsHomepageIcon(iconRes = R.drawable.ic_feature_search)
                        },
                        onCheckedChange = {
                            if (it) {
                                GattServerService.start(context)
                            } else {
                                GattServerService.stop(context)
                            }
                        },
                    )
                }
                item {
                    PreferenceRow(
                        title = stringResource(R.string.send),
                        summary = stringResource(R.string.send_desc),
                        iconContent = {
                            SettingsHomepageIcon(imageVector = Icons.Filled.Share)
                        },
                        onClick = { pickFilesLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
        DevicePickerSheet(
            visible = pendingSendFiles.isNotEmpty(),
            onDismiss = { pendingSendFiles = emptyList() },
            onDeviceSelected = { device ->
                val task = TaskInfo(
                    id = Random.nextInt(),
                    device = device,
                    files = pendingSendFiles,
                )
                if (P2pSenderService.startTaskChecked(context, task)) {
                    pendingSendFiles = emptyList()
                }
            },
        )
    }
}

private enum class ReceiveCardPhase {
    Asking,
    Receiving,
    Completed,
    Failed,
}

@Composable
private fun DevicePickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 22.dp)
                        .padding(WindowInsets.navigationBars.asPaddingValues()),
                ) {
                    Text(
                        text = stringResource(R.string.choose_device),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    DevicePickerContent(
                        onDeviceSelected = onDeviceSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicePickerContent(
    onDeviceSelected: (DiscoveredDevice) -> Unit,
) {
    val devices = deviceScanner()
    if (devices.isEmpty()) {
        Text(
            text = stringResource(R.string.scanning_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    Column {
        devices.forEach { device ->
            DevicePickerRow(
                device = device,
                onClick = { onDeviceSelected(device) },
            )
        }
    }
}

@Composable
private fun DevicePickerRow(
    device: DiscoveredDevice,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp),
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (BuildConfig.DEBUG) {
                    "${device.name} (${device.id}, ${device.device.address})"
                } else {
                    device.name
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.brand ?: stringResource(R.string.unknown),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class SendCardPhase {
    Preparing,
    Connecting,
    Sending,
    Completed,
    Failed,
}

private data class SendCardState(
    val phase: SendCardPhase,
    val taskId: Int,
    val targetName: String,
    val fileName: String,
    val fileCount: Int,
    val totalSize: Long,
    val processedSize: Long,
) {
    val progress: Float?
        get() = if (totalSize > 0L) {
            (processedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    companion object {
        fun updateList(
            intent: Intent,
            current: List<SendCardState>,
        ): List<SendCardState> {
            val taskId = intent.getIntExtra(P2pSenderService.EXTRA_SEND_CARD_TASK_ID, -1)
            val previous = current.firstOrNull { it.taskId == taskId } ?: current.firstOrNull()
            val state = fromIntent(intent, previous) ?: return current
            val next = current.filterNot { it.taskId == state.taskId }
            return listOf(state) + next
        }

        private fun fromIntent(intent: Intent, previous: SendCardState?): SendCardState? {
            return when (
                intent.getStringExtra(P2pSenderService.EXTRA_SEND_CARD_STATE)
            ) {
                P2pSenderService.SEND_CARD_STATE_PREPARING -> SendCardPhase.Preparing
                P2pSenderService.SEND_CARD_STATE_CONNECTING -> SendCardPhase.Connecting
                P2pSenderService.SEND_CARD_STATE_SENDING -> SendCardPhase.Sending
                P2pSenderService.SEND_CARD_STATE_COMPLETED -> SendCardPhase.Completed
                P2pSenderService.SEND_CARD_STATE_FAILED -> SendCardPhase.Failed
                else -> return null
            }.let { phase ->
                SendCardState(
                    phase = phase,
                    taskId = intent.getIntExtra(P2pSenderService.EXTRA_SEND_CARD_TASK_ID, -1),
                    targetName = intent.getStringExtra(
                        P2pSenderService.EXTRA_SEND_CARD_TARGET_NAME
                    ) ?: previous?.targetName.orEmpty(),
                    fileName = intent.getStringExtra(
                        P2pSenderService.EXTRA_SEND_CARD_FILE_NAME
                    ) ?: previous?.fileName.orEmpty(),
                    fileCount = intent.getIntExtra(
                        P2pSenderService.EXTRA_SEND_CARD_FILE_COUNT,
                        previous?.fileCount ?: 0,
                    ),
                    totalSize = intent.getLongExtra(
                        P2pSenderService.EXTRA_SEND_CARD_TOTAL_SIZE,
                        previous?.totalSize ?: 0L,
                    ),
                    processedSize = intent.getLongExtra(
                        P2pSenderService.EXTRA_SEND_CARD_PROCESSED_SIZE,
                        previous?.processedSize ?: 0L,
                    ),
                )
            }
        }
    }
}

private fun List<SendCardState>.withoutSendTask(taskId: Int) = filterNot { it.taskId == taskId }

private data class ReceiveCardState(
    val phase: ReceiveCardPhase,
    val taskId: Int,
    val senderName: String,
    val fileName: String,
    val fileCount: Int,
    val totalSize: Long,
    val processedSize: Long,
    val isText: Boolean,
    val previewText: String?,
    val previewUri: String?,
    val previewMimeType: String?,
) {
    val progress: Float?
        get() = if (totalSize > 0L) {
            (processedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    companion object {
        fun updateList(
            intent: Intent,
            current: List<ReceiveCardState>,
        ): List<ReceiveCardState> {
            val taskId = intent.getIntExtra(P2pReceiverService.EXTRA_RECEIVE_CARD_TASK_ID, -1)
            val previous = current.firstOrNull { it.taskId == taskId } ?: current.firstOrNull()
            val state = fromIntent(intent, previous)
                ?: return current.filterNot { it.phase.isTransient }
            val next = current.filterNot { it.taskId == state.taskId }
            return listOf(state) + next
        }

        private fun fromIntent(intent: Intent, previous: ReceiveCardState?): ReceiveCardState? {
            return when (
                intent.getStringExtra(P2pReceiverService.EXTRA_RECEIVE_CARD_STATE)
            ) {
                P2pReceiverService.RECEIVE_CARD_STATE_ASKING -> ReceiveCardPhase.Asking
                P2pReceiverService.RECEIVE_CARD_STATE_RECEIVING -> ReceiveCardPhase.Receiving
                P2pReceiverService.RECEIVE_CARD_STATE_COMPLETED -> ReceiveCardPhase.Completed
                P2pReceiverService.RECEIVE_CARD_STATE_FAILED -> ReceiveCardPhase.Failed
                else -> return null
            }.let { phase ->
                ReceiveCardState(
                    phase = phase,
                    taskId = intent.getIntExtra(P2pReceiverService.EXTRA_RECEIVE_CARD_TASK_ID, -1),
                    senderName = intent.getStringExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_SENDER_NAME
                    ) ?: previous?.senderName.orEmpty(),
                    fileName = intent.getStringExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_FILE_NAME
                    ) ?: previous?.fileName.orEmpty(),
                    fileCount = intent.getIntExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_FILE_COUNT,
                        previous?.fileCount ?: 0,
                    ),
                    totalSize = intent.getLongExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_TOTAL_SIZE,
                        previous?.totalSize ?: 0L,
                    ),
                    processedSize = intent.getLongExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_PROCESSED_SIZE,
                        previous?.processedSize ?: 0L,
                    ),
                    isText = intent.getBooleanExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_IS_TEXT,
                        previous?.isText ?: false,
                    ),
                    previewText = intent.getStringExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_PREVIEW_TEXT
                    ) ?: previous?.previewText,
                    previewUri = intent.getStringExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_PREVIEW_URI
                    ) ?: previous?.previewUri,
                    previewMimeType = intent.getStringExtra(
                        P2pReceiverService.EXTRA_RECEIVE_CARD_PREVIEW_MIME_TYPE
                    ) ?: previous?.previewMimeType,
                )
            }
        }
    }
}

private val ReceiveCardPhase.isTransient: Boolean
    get() = this == ReceiveCardPhase.Asking || this == ReceiveCardPhase.Receiving

private fun List<ReceiveCardState>.withoutReceiveTask(taskId: Int) = filterNot { it.taskId == taskId }

@Composable
private fun SendTransferCard(
    state: SendCardState,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(280, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SendCardIcon(state.phase)
                Spacer(modifier = Modifier.width(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.phase) {
                            SendCardPhase.Preparing -> stringResource(R.string.send_card_preparing)
                            SendCardPhase.Connecting -> stringResource(R.string.send_card_connecting)
                            SendCardPhase.Sending -> stringResource(R.string.send_card_sending)
                            SendCardPhase.Completed -> stringResource(R.string.send_card_completed)
                            SendCardPhase.Failed -> stringResource(R.string.send_card_failed)
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sendCardSubtitle(state),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (state.phase) {
                SendCardPhase.Preparing,
                SendCardPhase.Connecting,
                SendCardPhase.Sending -> SendCardProgress(state, onCancel)
                SendCardPhase.Completed,
                SendCardPhase.Failed -> ReceiveCardCloseButton(onClose)
            }
        }
    }
}

@Composable
private fun SendCardIcon(phase: SendCardPhase) {
    val icon = when (phase) {
        SendCardPhase.Completed -> R.drawable.ic_done
        SendCardPhase.Failed -> R.drawable.ic_warning
        else -> R.drawable.ic_upload_file
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun SendCardProgress(
    state: SendCardState,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val progress = if (state.phase == SendCardPhase.Sending) state.progress else null
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sendCardProgressText(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun sendCardSubtitle(state: SendCardState): String {
    val fileName = state.fileName.ifBlank {
        if (state.fileCount > 1) {
            stringResource(R.string.receive_card_multiple_files, state.fileCount)
        } else {
            stringResource(R.string.unknown)
        }
    }
    return if (state.targetName.isBlank()) {
        fileName
    } else {
        "$fileName - ${state.targetName}"
    }
}

@Composable
private fun sendCardProgressText(state: SendCardState): String {
    val context = LocalContext.current
    return if (state.phase == SendCardPhase.Sending && state.totalSize > 0L) {
        val processed = Formatter.formatShortFileSize(context, state.processedSize)
        val total = Formatter.formatShortFileSize(context, state.totalSize)
        val percent = ((state.progress ?: 0f) * 100).toInt()
        "$processed / $total | $percent%"
    } else {
        when (state.phase) {
            SendCardPhase.Preparing -> stringResource(R.string.preparing)
            SendCardPhase.Connecting -> stringResource(R.string.noti_connecting)
            SendCardPhase.Sending -> stringResource(R.string.preparing)
            SendCardPhase.Completed -> stringResource(R.string.send_card_completed)
            SendCardPhase.Failed -> stringResource(R.string.send_card_failed)
        }
    }
}

@Composable
private fun ReceiveTransferCard(
    state: ReceiveCardState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(280, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReceiveCardIcon(state.phase)
                Spacer(modifier = Modifier.width(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.phase) {
                            ReceiveCardPhase.Asking -> stringResource(R.string.receive_card_new_file)
                            ReceiveCardPhase.Receiving -> stringResource(R.string.receive_card_receiving)
                            ReceiveCardPhase.Completed -> stringResource(R.string.receive_card_completed)
                            ReceiveCardPhase.Failed -> stringResource(R.string.receive_card_failed)
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = receiveCardSubtitle(state),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ReceiveCardPreview(state)
            when (state.phase) {
                ReceiveCardPhase.Asking -> ReceiveCardDecisionButtons(onReject, onAccept)
                ReceiveCardPhase.Receiving -> ReceiveCardProgress(state, onCancel)
                ReceiveCardPhase.Completed,
                ReceiveCardPhase.Failed -> ReceiveCardCloseButton(onClose)
            }
        }
    }
}

@Composable
private fun ReceiveCardPreview(state: ReceiveCardState) {
    val previewText = state.previewText
    if (state.isText && !previewText.isNullOrBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = previewText,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        return
    }

    val previewUri = state.previewUri
    if (!previewUri.isNullOrBlank() && state.previewMimeType?.startsWith("image/") == true) {
        val context = LocalContext.current
        val bitmap = remember(previewUri) {
            loadPreviewBitmap(context, previewUri)
        }
        if (bitmap != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

private fun loadPreviewBitmap(context: Context, uriString: String): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}

@Composable
private fun ReceiveCardIcon(phase: ReceiveCardPhase) {
    val icon = when (phase) {
        ReceiveCardPhase.Completed -> R.drawable.ic_done
        ReceiveCardPhase.Failed -> R.drawable.ic_warning
        else -> R.drawable.ic_download
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun ReceiveCardDecisionButtons(
    onReject: () -> Unit,
    onAccept: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
    ) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier
                .heightIn(min = 52.dp)
                .width(132.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.reject),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Button(
            onClick = onAccept,
            modifier = Modifier
                .heightIn(min = 52.dp)
                .width(132.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = stringResource(R.string.accept),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReceiveCardCloseButton(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .heightIn(min = 52.dp)
                .width(132.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.close),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReceiveCardProgress(
    state: ReceiveCardState,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = receiveCardProgressText(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun receiveCardSubtitle(state: ReceiveCardState): String {
    if (state.isText) return stringResource(R.string.noti_request_desc_text)
    val fileName = state.fileName.ifBlank {
        if (state.fileCount > 1) {
            stringResource(R.string.receive_card_multiple_files, state.fileCount)
        } else {
            stringResource(R.string.unknown)
        }
    }
    return if (state.senderName.isBlank()) {
        fileName
    } else {
        "$fileName - ${state.senderName}"
    }
}

@Composable
private fun receiveCardProgressText(state: ReceiveCardState): String {
    val context = LocalContext.current
    return if (state.totalSize > 0L) {
        val processed = Formatter.formatShortFileSize(context, state.processedSize)
        val total = Formatter.formatShortFileSize(context, state.totalSize)
        val percent = ((state.progress ?: 0f) * 100).toInt()
        "$processed / $total | $percent%"
    } else {
        stringResource(R.string.preparing)
    }
}
