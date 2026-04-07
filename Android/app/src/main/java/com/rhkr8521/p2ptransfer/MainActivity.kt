package com.rhkr8521.p2ptransfer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.rhkr8521.p2ptransfer.core.IncomingTransferRequest
import com.rhkr8521.p2ptransfer.core.P2pUiState
import com.rhkr8521.p2ptransfer.core.PeerDevice
import com.rhkr8521.p2ptransfer.core.TransferMode
import com.rhkr8521.p2ptransfer.core.TransferProgressUi
import com.rhkr8521.p2ptransfer.core.formatRemaining
import com.rhkr8521.p2ptransfer.core.humanReadableSize
import com.rhkr8521.p2ptransfer.ui.theme.Rhkr8521P2PTransferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Rhkr8521P2PTransferTheme {
                val viewModel: P2pViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var pendingPickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var showHelp by remember { mutableStateOf(false) }
                var showInitialTunnelChoice by remember { mutableStateOf(false) }
                var showNotificationPermissionDialog by remember { mutableStateOf(false) }
                var showUpdateRequiredDialog by remember { mutableStateOf(false) }
                var hasCheckedForUpdate by remember { mutableStateOf(false) }
                var lastBackPressedAt by remember { mutableStateOf(0L) }

                BackHandler(
                    enabled = !showHelp &&
                        pendingPickedUris.isEmpty() &&
                        !showInitialTunnelChoice &&
                        !showNotificationPermissionDialog &&
                        !showUpdateRequiredDialog &&
                        uiState.pendingRequest == null,
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAt < 2_000L) {
                        finish()
                    } else {
                        lastBackPressedAt = now
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.back_press_exit_hint),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                val filePicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
                    if (uris.isEmpty()) return@rememberLauncherForActivityResult
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                    if (uris.size == 1) {
                        viewModel.sendSelectedFiles(uris, useZip = false)
                    } else {
                        pendingPickedUris = uris
                    }
                }

                val directoryPicker = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    viewModel.rememberSaveDirectory(uri)
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
                    if (granted && notificationsAllowed()) {
                        showNotificationPermissionDialog = false
                    } else {
                        finish()
                    }
                }

                val notificationSettingsLauncher =
                    rememberLauncherForActivityResult(StartActivityForResult()) {
                        if (notificationsAllowed()) {
                            showNotificationPermissionDialog = false
                        } else {
                            finish()
                        }
                    }

                LaunchedEffect(Unit) {
                    if (!notificationsAllowed()) {
                        showNotificationPermissionDialog = true
                    }

                    viewModel.events.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(showNotificationPermissionDialog) {
                    if (!showNotificationPermissionDialog && !hasCheckedForUpdate) {
                        hasCheckedForUpdate = true
                        checkForPlayStoreUpdate {
                            showUpdateRequiredDialog = true
                        }
                    }
                }

                P2pAppScreen(
                    state = uiState,
                    snackbarHostState = snackbarHostState,
                    onToggleMode = {
                        if (uiState.mode == TransferMode.LAN && viewModel.shouldShowInitialTunnelChoice()) {
                            showInitialTunnelChoice = true
                        } else {
                            viewModel.toggleMode()
                        }
                    },
                    onSelectPeer = viewModel::selectPeer,
                    onRefresh = viewModel::manualRefresh,
                    onShowHelp = { showHelp = true },
                    onChooseSaveFolder = { directoryPicker.launch(null) },
                    onOpenFilePicker = { filePicker.launch(arrayOf("*/*")) },
                    onUsePublicTunnelChanged = viewModel::updateUsePublicTunnel,
                    onTunnelHostChanged = viewModel::updateTunnelHost,
                    onTunnelTokenChanged = viewModel::updateTunnelToken,
                    onTunnelSslChanged = viewModel::updateTunnelSsl,
                    onApplyTunnelSettings = viewModel::applyTunnelSettings,
                    onCancelTransfer = viewModel::cancelTransfer,
                    onRespondToRequest = viewModel::respondToPendingRequest,
                )

                if (pendingPickedUris.isNotEmpty()) {
                    PeerSendBottomSheet(onDismissRequest = { pendingPickedUris = emptyList() }) {
                        SheetTitle(stringResource(R.string.zip_choice_title))
                        SheetBodyText(stringResource(R.string.zip_choice_body, pendingPickedUris.size))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextButton(
                                onClick = { pendingPickedUris = emptyList() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.sendSelectedFiles(pendingPickedUris, useZip = false)
                                    pendingPickedUris = emptyList()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.no))
                            }
                            Button(
                                onClick = {
                                    viewModel.sendSelectedFiles(pendingPickedUris, useZip = true)
                                    pendingPickedUris = emptyList()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.yes))
                            }
                        }
                    }
                }

                if (showHelp) {
                    HelpDialog(onDismiss = { showHelp = false })
                }

                if (showInitialTunnelChoice) {
                    PeerSendBottomSheet(onDismissRequest = { }) {
                        SheetTitle(stringResource(R.string.initial_tunnel_choice_title))
                        SheetBodyText(stringResource(R.string.initial_tunnel_choice_body))
                        Button(
                            onClick = {
                                viewModel.rememberInitialTunnelChoice(true)
                                showInitialTunnelChoice = false
                                viewModel.toggleMode()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.initial_tunnel_choice_public))
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.rememberInitialTunnelChoice(false)
                                showInitialTunnelChoice = false
                                viewModel.toggleMode()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.initial_tunnel_choice_external))
                        }
                    }
                }

                if (showNotificationPermissionDialog) {
                    PeerSendBottomSheet(onDismissRequest = { finish() }) {
                        SheetTitle(stringResource(R.string.notification_permission_title))
                        SheetBodyText(stringResource(R.string.notification_permission_body))
                        Button(
                            onClick = {
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notificationSettingsLauncher.launch(buildNotificationSettingsIntent())
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.notification_permission_allow))
                        }
                    }
                }

                if (showUpdateRequiredDialog) {
                    PeerSendBottomSheet(onDismissRequest = { finish() }) {
                        SheetTitle(stringResource(R.string.update_required_title))
                        SheetBodyText(stringResource(R.string.update_required_body))
                        Button(
                            onClick = {
                                openPlayStorePage()
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_now))
                        }
                    }
                }
            }
        }
    }

    private fun checkForPlayStoreUpdate(onUpdateRequired: () -> Unit) {
        runCatching {
            val appUpdateManager = AppUpdateManagerFactory.create(this)
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        onUpdateRequired()
                    }
                }
                .addOnFailureListener {
                    // Ignore failures for sideloaded/debug installs.
                }
        }
    }

    private fun notificationsAllowed(): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun buildNotificationSettingsIntent(): Intent {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        return if (notificationIntent.resolveActivity(packageManager) != null) {
            notificationIntent
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        }
    }

    private fun openPlayStorePage() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        )
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )

        runCatching {
            startActivity(marketIntent)
        }.getOrElse {
            runCatching { startActivity(webIntent) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerSendBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SheetBodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
    )
}

@Composable
private fun P2pAppScreen(
    state: P2pUiState,
    snackbarHostState: SnackbarHostState,
    onToggleMode: () -> Unit,
    onSelectPeer: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowHelp: () -> Unit,
    onChooseSaveFolder: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onUsePublicTunnelChanged: (Boolean) -> Unit,
    onTunnelHostChanged: (String) -> Unit,
    onTunnelTokenChanged: (String) -> Unit,
    onTunnelSslChanged: (Boolean) -> Unit,
    onApplyTunnelSettings: () -> Unit,
    onCancelTransfer: () -> Unit,
    onRespondToRequest: (Boolean) -> Unit,
) {
    val peers = if (state.mode == TransferMode.LAN) state.lanPeers else state.tunnelPeers
    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            Color(0xFFDCE9F7),
                        ),
                    ),
                )
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroSection(
                state = state,
                onToggleMode = onToggleMode,
                onRefresh = onRefresh,
                onShowHelp = onShowHelp,
            )

            ActionSection(
                state = state,
                peers = peers,
                onChooseSaveFolder = onChooseSaveFolder,
                onOpenFilePicker = onOpenFilePicker,
            )

            if (state.mode == TransferMode.TUNNEL) {
                TunnelSettingsCard(
                    state = state,
                    onUsePublicTunnelChanged = onUsePublicTunnelChanged,
                    onTunnelHostChanged = onTunnelHostChanged,
                    onTunnelTokenChanged = onTunnelTokenChanged,
                    onTunnelSslChanged = onTunnelSslChanged,
                    onApplyTunnelSettings = onApplyTunnelSettings,
                )
            }

            PeerListCard(
                mode = state.mode,
                peers = peers,
                selectedPeerId = state.selectedPeerId,
                onSelectPeer = onSelectPeer,
            )

            state.transferProgress?.let { progress ->
                ProgressCard(
                    progress = progress,
                    onCancelTransfer = onCancelTransfer,
                )
            }
        }
    }

    state.pendingRequest?.let { request ->
        IncomingRequestDialog(
            request = request,
            onRespond = onRespondToRequest,
        )
    }
}

@Composable
private fun HeroSection(
    state: P2pUiState,
    onToggleMode: () -> Unit,
    onRefresh: () -> Unit,
    onShowHelp: () -> Unit,
) {
    val displayedDeviceName = if (state.mode == TransferMode.TUNNEL && state.tunnelSubdomain.isNotBlank()) {
        state.tunnelSubdomain
    } else {
        state.myName
    }
    val deviceTextStyle = when {
        displayedDeviceName.length >= 24 -> MaterialTheme.typography.labelSmall
        displayedDeviceName.length >= 16 -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelLarge
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onShowHelp) {
                    Text(stringResource(R.string.help))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Pill(
                    text = stringResource(R.string.device_name_label, displayedDeviceName),
                    modifier = Modifier.weight(1f),
                    textStyle = deviceTextStyle,
                )
                Pill(text = stringResource(R.string.ip_label, state.myIp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isLan = state.mode == TransferMode.LAN
                Button(
                    onClick = {
                        if (!isLan) onToggleMode()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLan,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mode_lan),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Button(
                    onClick = {
                        if (isLan) onToggleMode()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isLan,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mode_tunnel),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.refresh),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Text(
                text = if (state.mode == TransferMode.LAN) stringResource(R.string.lan_searching) else state.tunnelStatus,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActionSection(
    state: P2pUiState,
    peers: List<PeerDevice>,
    onChooseSaveFolder: () -> Unit,
    onOpenFilePicker: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.action_section_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = state.saveLocationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onChooseSaveFolder,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Text(stringResource(R.string.save_folder))
                    }
                }
                Button(
                    onClick = onOpenFilePicker,
                    modifier = Modifier.weight(1f),
                    enabled = peers.any { it.id == state.selectedPeerId } && !state.isBusy,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                        Text(stringResource(R.string.send_files))
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelSettingsCard(
    state: P2pUiState,
    onUsePublicTunnelChanged: (Boolean) -> Unit,
    onTunnelHostChanged: (String) -> Unit,
    onTunnelTokenChanged: (String) -> Unit,
    onTunnelSslChanged: (Boolean) -> Unit,
    onApplyTunnelSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.tunnel_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (state.usePublicTunnel) {
                            stringResource(R.string.public_tunnel_summary)
                        } else {
                            state.tunnelHost.ifBlank { stringResource(R.string.external_tunnel_summary) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.tunnelStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.public_tunnel_access))
                    Checkbox(
                        checked = state.usePublicTunnel,
                        onCheckedChange = onUsePublicTunnelChanged,
                    )
                }

                if (state.usePublicTunnel) {
                    Text(
                        text = stringResource(R.string.public_tunnel_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    OutlinedTextField(
                        value = state.tunnelHost,
                        onValueChange = onTunnelHostChanged,
                        label = { Text(stringResource(R.string.tunnel_server_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.tunnelToken,
                        onValueChange = onTunnelTokenChanged,
                        label = { Text(stringResource(R.string.tunnel_token)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.ssl_enabled))
                        Switch(
                            checked = state.tunnelSsl,
                            onCheckedChange = onTunnelSslChanged,
                        )
                    }
                    if (!state.tunnelSsl) {
                        Text(
                            text = stringResource(R.string.ssl_disabled_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Button(onClick = onApplyTunnelSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.apply_reconnect))
                }
            }
        }
    }
}

@Composable
private fun PeerListCard(
    mode: TransferMode,
    peers: List<PeerDevice>,
    selectedPeerId: String?,
    onSelectPeer: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (mode == TransferMode.LAN) stringResource(R.string.peer_list_lan) else stringResource(R.string.peer_list_tunnel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (peers.isEmpty()) {
                Text(
                    text = if (mode == TransferMode.LAN) stringResource(R.string.no_lan_devices) else stringResource(R.string.no_tunnel_peers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                peers.forEachIndexed { index, peer ->
                    PeerRow(
                        peer = peer,
                        selected = peer.id == selectedPeerId,
                        onClick = { onSelectPeer(peer.id) },
                    )
                    if (index != peers.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: PeerDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.title, fontWeight = FontWeight.SemiBold)
            Text(
                peer.addressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Pill(text = if (peer.isTunnel) stringResource(R.string.peer_badge_tunnel) else stringResource(R.string.peer_badge_lan))
    }
}

@Composable
private fun ProgressCard(
    progress: TransferProgressUi,
    onCancelTransfer: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(progress.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                progress.itemLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress.showItemProgress) {
                LinearProgressIndicator(
                    progress = { fraction(progress.itemTransferredBytes, progress.itemBytes) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${humanReadableSize(progress.itemTransferredBytes)} / ${humanReadableSize(progress.itemBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LinearProgressIndicator(
                progress = { fraction(progress.transferredBytes, progress.totalBytes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Text(
                "${humanReadableSize(progress.transferredBytes)} / ${humanReadableSize(progress.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(
                    R.string.progress_speed_remaining,
                    formatSpeed(progress.speedBytesPerSecond),
                    formatRemaining(progress.remainingSeconds),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            OutlinedButton(onClick = onCancelTransfer, modifier = Modifier.fillMaxWidth()) {
                Text(if (progress.isReceiving) stringResource(R.string.cancel_receive) else stringResource(R.string.cancel_send))
            }
        }
    }
}

@Composable
private fun IncomingRequestDialog(
    request: IncomingTransferRequest,
    onRespond: (Boolean) -> Unit,
) {
    PeerSendBottomSheet(onDismissRequest = { onRespond(false) }) {
        SheetTitle(stringResource(R.string.incoming_request_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${request.senderName} (${request.senderAddress})")
            Text(request.displayName, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.incoming_request_size, humanReadableSize(request.totalBytes)))
            Text(
                if (request.fileCount > 1) {
                    pluralStringResource(R.plurals.total_file_count, request.fileCount, request.fileCount)
                } else {
                    stringResource(R.string.incoming_request_single)
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onRespond(false) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.reject))
            }
            Button(
                onClick = { onRespond(true) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.receive))
            }
        }
    }
}

@Composable
private fun HelpDialog(
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()

    PeerSendBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.help))
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.help_text_lan))
            Text(stringResource(R.string.help_text_tunnel))
            Text(stringResource(R.string.help_text_refresh))
            Text(stringResource(R.string.help_text_save_folder))
            Text(stringResource(R.string.help_text_send_files))
            Text(stringResource(R.string.help_text_background))
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = textStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun fraction(done: Long, total: Long): Float {
    if (total <= 0L) return 0f
    return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun formatSpeed(bytesPerSecond: Double): String {
    if (bytesPerSecond <= 0.0) return "0 MB/s"
    return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
}
