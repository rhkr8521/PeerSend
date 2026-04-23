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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.rhkr8521.p2ptransfer.core.IncomingTransferRequest
import com.rhkr8521.p2ptransfer.core.P2pUiState
import com.rhkr8521.p2ptransfer.core.PeerDevice
import com.rhkr8521.p2ptransfer.core.ReceivedFile
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
                var showInitialTunnelChoice by remember { mutableStateOf(false) }
                var showNotificationPermissionDialog by remember { mutableStateOf(false) }
                var showUpdateRequiredDialog by remember { mutableStateOf(false) }
                var hasCheckedForUpdate by remember { mutableStateOf(false) }
                var selectedTab by remember { mutableStateOf(0) }
                var lastBackPressedAt by remember { mutableStateOf(0L) }

                BackHandler(
                    enabled = pendingPickedUris.isEmpty() &&
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

                LaunchedEffect(selectedTab) {
                    if (selectedTab == 1) viewModel.refreshReceivedFiles()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        PeerSendBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                        )
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> TransferScreen(
                            state = uiState,
                            innerPadding = innerPadding,
                            onToggleMode = {
                                if (uiState.mode == TransferMode.LAN && viewModel.shouldShowInitialTunnelChoice()) {
                                    showInitialTunnelChoice = true
                                } else {
                                    viewModel.toggleMode()
                                }
                            },
                            onSelectPeer = viewModel::selectPeer,
                            onRefresh = viewModel::manualRefresh,
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
                        1 -> GalleryScreen(
                            files = uiState.receivedFiles,
                            innerPadding = innerPadding,
                            onDeleteFiles = { uris -> viewModel.deleteReceivedFiles(uris) },
                        )
                        2 -> HelpScreen(innerPadding = innerPadding)
                        3 -> AppInfoScreen(innerPadding = innerPadding)
                    }
                }

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
                            ) { Text(stringResource(R.string.cancel)) }
                            OutlinedButton(
                                onClick = {
                                    viewModel.sendSelectedFiles(pendingPickedUris, useZip = false)
                                    pendingPickedUris = emptyList()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.no)) }
                            Button(
                                onClick = {
                                    viewModel.sendSelectedFiles(pendingPickedUris, useZip = true)
                                    pendingPickedUris = emptyList()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.yes)) }
                        }
                    }
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
                        ) { Text(stringResource(R.string.initial_tunnel_choice_public)) }
                        OutlinedButton(
                            onClick = {
                                viewModel.rememberInitialTunnelChoice(false)
                                showInitialTunnelChoice = false
                                viewModel.toggleMode()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.initial_tunnel_choice_external)) }
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
                        ) { Text(stringResource(R.string.notification_permission_allow)) }
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
                        ) { Text(stringResource(R.string.update_now)) }
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
                .addOnFailureListener { }
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
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
    }

    private fun openPlayStorePage() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        runCatching { startActivity(marketIntent) }.getOrElse { runCatching { startActivity(webIntent) } }
    }
}

// ─────────────────────────────────────────
// Transfer Screen
// ─────────────────────────────────────────

@Composable
private fun TransferScreen(
    state: P2pUiState,
    innerPadding: PaddingValues,
    onToggleMode: () -> Unit,
    onSelectPeer: (String) -> Unit,
    onRefresh: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, Color(0xFFDCE9F7)),
                ),
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroSection(state = state, onToggleMode = onToggleMode, onRefresh = onRefresh)
        ActionSection(state = state, peers = peers, onChooseSaveFolder = onChooseSaveFolder, onOpenFilePicker = onOpenFilePicker)
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
        PeerListCard(mode = state.mode, peers = peers, selectedPeerId = state.selectedPeerId, onSelectPeer = onSelectPeer)
        state.transferProgress?.let { progress ->
            ProgressCard(progress = progress, onCancelTransfer = onCancelTransfer)
        }
    }

    state.pendingRequest?.let { request ->
        IncomingRequestDialog(request = request, onRespond = onRespondToRequest)
    }
}

// ─────────────────────────────────────────
// Gallery Screen
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    files: List<ReceivedFile>,
    innerPadding: PaddingValues,
    onDeleteFiles: (List<Uri>) -> Unit,
) {
    var selectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_gallery), fontWeight = FontWeight.Bold) },
            actions = {
                if (files.isNotEmpty()) {
                    TextButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedUris = emptySet()
                    }) {
                        Text(
                            if (selectionMode) stringResource(R.string.cancel)
                            else stringResource(R.string.gallery_select_all)
                        )
                    }
                    if (selectionMode) {
                        TextButton(onClick = {
                            val newSet = if (selectedUris.size == files.size) emptySet() else files.map { it.uri }.toSet()
                            selectedUris = newSet
                            if (newSet.isEmpty()) selectionMode = false
                        }) {
                            Text(
                                if (selectedUris.size == files.size) stringResource(R.string.gallery_deselect)
                                else stringResource(R.string.gallery_select_all)
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(2.dp),
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    GalleryTile(
                        file = file,
                        isSelected = selectedUris.contains(file.uri),
                        selectionMode = selectionMode,
                        onTap = {
                            if (selectionMode) {
                                val newSet = if (selectedUris.contains(file.uri)) {
                                    selectedUris - file.uri
                                } else {
                                    selectedUris + file.uri
                                }
                                selectedUris = newSet
                                if (newSet.isEmpty()) selectionMode = false
                            } else {
                                selectionMode = true
                                selectedUris = setOf(file.uri)
                            }
                        },
                    )
                }
            }
        }

        if (selectionMode && selectedUris.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.gallery_selected_count, selectedUris.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Button(
                    onClick = {
                        onDeleteFiles(selectedUris.toList())
                        selectedUris = emptySet()
                        selectionMode = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.gallery_delete))
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(
    file: ReceivedFile,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file.uri)
                .apply { if (file.isVideo) videoFrameMillis(0) }
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            )
        }

        if (file.isVideo) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }

        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp),
            )
        }
    }
}

// ─────────────────────────────────────────
// Help Screen
// ─────────────────────────────────────────

private data class HelpItem(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpScreen(innerPadding: PaddingValues) {
    val helpItems = listOf(
        HelpItem(Icons.Outlined.Wifi, R.string.mode_lan, R.string.help_text_lan),
        HelpItem(Icons.Outlined.Cloud, R.string.mode_tunnel, R.string.help_text_tunnel),
        HelpItem(Icons.Outlined.Refresh, R.string.refresh, R.string.help_text_refresh),
        HelpItem(Icons.Outlined.Folder, R.string.save_folder, R.string.help_text_save_folder),
        HelpItem(Icons.AutoMirrored.Outlined.Send, R.string.send_files, R.string.help_text_send_files),
        HelpItem(Icons.Outlined.NightsStay, R.string.help_background_title, R.string.help_text_background),
        HelpItem(Icons.Outlined.PhotoLibrary, R.string.tab_gallery, R.string.help_text_gallery),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_help), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            helpItems.forEach { item ->
                HelpItemCard(item)
            }
        }
    }
}

@Composable
private fun HelpItemCard(item: HelpItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(item.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────
// Shared Bottom Sheet
// ─────────────────────────────────────────

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
    Text(text = text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun SheetBodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
    )
}

// ─────────────────────────────────────────
// Transfer Screen Components
// ─────────────────────────────────────────

@Composable
private fun HeroSection(
    state: P2pUiState,
    onToggleMode: () -> Unit,
    onRefresh: () -> Unit,
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

    var refreshAngle by remember { mutableFloatStateOf(0f) }
    val animatedRefreshAngle by animateFloatAsState(
        targetValue = refreshAngle,
        animationSpec = tween(durationMillis = 500),
        label = "refresh",
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Pill(text = stringResource(R.string.device_name_label, displayedDeviceName), modifier = Modifier.weight(1f), textStyle = deviceTextStyle)
                Pill(text = stringResource(R.string.ip_label, state.myIp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isLan = state.mode == TransferMode.LAN
                Button(
                    onClick = { if (!isLan) onToggleMode() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLan,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) { Text(text = stringResource(R.string.mode_lan), maxLines = 1, softWrap = false) }
                Button(
                    onClick = { if (isLan) onToggleMode() },
                    modifier = Modifier.weight(1f),
                    enabled = isLan,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) { Text(text = stringResource(R.string.mode_tunnel), maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = { refreshAngle += 360f; onRefresh() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).rotate(animatedRefreshAngle),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.refresh), maxLines = 1, softWrap = false)
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
            modifier = Modifier.fillMaxWidth().padding(18.dp),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onChooseSaveFolder, modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Text(stringResource(R.string.save_folder))
                    }
                }
                Button(
                    onClick = onOpenFilePicker,
                    modifier = Modifier.weight(1f),
                    enabled = peers.any { it.id == state.selectedPeerId } && !state.isBusy,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
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
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.tunnel_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (state.usePublicTunnel) stringResource(R.string.public_tunnel_summary) else state.tunnelHost.ifBlank { stringResource(R.string.external_tunnel_summary) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = state.tunnelStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.public_tunnel_access))
                    Checkbox(checked = state.usePublicTunnel, onCheckedChange = onUsePublicTunnelChanged)
                }
                if (state.usePublicTunnel) {
                    Text(text = stringResource(R.string.public_tunnel_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                } else {
                    OutlinedTextField(value = state.tunnelHost, onValueChange = onTunnelHostChanged, label = { Text(stringResource(R.string.tunnel_server_address)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = state.tunnelToken, onValueChange = onTunnelTokenChanged, label = { Text(stringResource(R.string.tunnel_token)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.ssl_enabled))
                        Switch(checked = state.tunnelSsl, onCheckedChange = onTunnelSslChanged)
                    }
                    if (!state.tunnelSsl) {
                        Text(text = stringResource(R.string.ssl_disabled_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (mode == TransferMode.LAN) stringResource(R.string.peer_list_lan) else stringResource(R.string.peer_list_tunnel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (peers.isEmpty()) {
                Text(text = if (mode == TransferMode.LAN) stringResource(R.string.no_lan_devices) else stringResource(R.string.no_tunnel_peers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            } else {
                peers.forEachIndexed { index, peer ->
                    PeerRow(peer = peer, selected = peer.id == selectedPeerId, onClick = { onSelectPeer(peer.id) })
                    if (index != peers.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: PeerDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.title, fontWeight = FontWeight.SemiBold)
            Text(peer.addressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Pill(text = if (peer.isTunnel) stringResource(R.string.peer_badge_tunnel) else stringResource(R.string.peer_badge_lan))
    }
}

@Composable
private fun ProgressCard(progress: TransferProgressUi, onCancelTransfer: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(progress.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(progress.itemLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (progress.showItemProgress) {
                LinearProgressIndicator(progress = { fraction(progress.itemTransferredBytes, progress.itemBytes) }, modifier = Modifier.fillMaxWidth())
                Text("${humanReadableSize(progress.itemTransferredBytes)} / ${humanReadableSize(progress.itemBytes)}", style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = { fraction(progress.transferredBytes, progress.totalBytes) }, modifier = Modifier.fillMaxWidth().height(10.dp))
            Text("${humanReadableSize(progress.transferredBytes)} / ${humanReadableSize(progress.totalBytes)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.progress_speed_remaining, formatSpeed(progress.speedBytesPerSecond), formatRemaining(progress.remainingSeconds)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            OutlinedButton(onClick = onCancelTransfer, modifier = Modifier.fillMaxWidth()) {
                Text(if (progress.isReceiving) stringResource(R.string.cancel_receive) else stringResource(R.string.cancel_send))
            }
        }
    }
}

@Composable
private fun IncomingRequestDialog(request: IncomingTransferRequest, onRespond: (Boolean) -> Unit) {
    PeerSendBottomSheet(onDismissRequest = { onRespond(false) }) {
        SheetTitle(stringResource(R.string.incoming_request_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${request.senderName} (${request.senderAddress})")
            Text(request.displayName, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.incoming_request_size, humanReadableSize(request.totalBytes)))
            Text(
                if (request.fileCount > 1) pluralStringResource(R.plurals.total_file_count, request.fileCount, request.fileCount) else stringResource(R.string.incoming_request_single),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onRespond(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reject)) }
            Button(onClick = { onRespond(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.receive)) }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = textStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ─────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────

private data class BottomTabItem(
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val labelRes: Int,
)

@Composable
private fun PeerSendBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        BottomTabItem(Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Filled.Send, R.string.tab_transfer),
        BottomTabItem(Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary, R.string.tab_gallery),
        BottomTabItem(Icons.Outlined.Help, Icons.Filled.Help, R.string.tab_help),
        BottomTabItem(Icons.Outlined.Info, Icons.Filled.Info, R.string.tab_app_info),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────
// App Info Screen
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInfoScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
    }
    val copyrightYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_app_info), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppInfoRow(stringResource(R.string.app_info_app_name), "PeerSend")
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(stringResource(R.string.app_info_version), versionName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(stringResource(R.string.app_info_developer), "rhkr8521")
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoLinkRow(stringResource(R.string.app_info_contact), "mailto:rhkr8521@rhkr8521.com", context)
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppInfoLinkRow(stringResource(R.string.app_info_homepage), "https://www.peersend.kro.kr", context)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoLinkRow(stringResource(R.string.app_info_privacy), "https://www.peersend.kro.kr/privacy", context)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoLinkRow(stringResource(R.string.app_info_terms), "https://www.peersend.kro.kr/terms", context)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoLinkRow(stringResource(R.string.app_info_open_source), "https://www.peersend.kro.kr/open-source-licenses", context)
                    }
                }
            }

            Text(
                text = "© $copyrightYear rhkr8521. All Rights Reserved.",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun AppInfoLinkRow(label: String, url: String, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = if (url.startsWith("mailto:")) {
                    Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                }
                context.startActivity(intent)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
