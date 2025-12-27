package app.gamenative.ui.component.dialog

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.data.DepotInfo
import app.gamenative.service.SteamService
import app.gamenative.service.SteamService.Companion.INVALID_APP_ID
import app.gamenative.ui.component.LoadingScreen
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.utils.StorageUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.orEmpty

private data class InstallSizeInfo(
    val downloadSize: String,
    val installSize: String,
    val availableSpace: String,
    val installBytes: Long,
    val availableBytes: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameManagerDialog(
    visible: Boolean,
    onGetDisplayInfo: @Composable (Context) -> GameDisplayInfo,
    onInstall: (List<Int>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val downloadableDepots = remember { mutableStateMapOf<Int, DepotInfo>() }
    val allDownloadableApps = remember { mutableStateListOf<Pair<Int, DepotInfo>>() }
    val selectedAppIds = remember { mutableStateMapOf<Int, Boolean>() }

    val displayInfo = onGetDisplayInfo(context)
    val gameId = displayInfo.gameId

    val appInfo = SteamService.getAppInfoOf(gameId)!!
    val installedApp = SteamService.getInstalledApp(gameId)
    val installedDlcIds = installedApp?.dlcDepots.orEmpty()

    val indirectDlcAppIds = SteamService.getDownloadableDlcAppsOf(gameId).orEmpty().map { it.id }

    // Filter out DLCs that are not in the appInfo, this can happen for DLCs that are not in the appInfo
    val hiddenDlcIds = SteamService.getHiddenDlcAppsOf(gameId).orEmpty().map { it.id }.filter { id -> appInfo.depots[id] == null }

    LaunchedEffect(visible) {
        scrollState.animateScrollTo(0)

        downloadableDepots.clear()
        allDownloadableApps.clear()

        // Get Downloadable Depots
        downloadableDepots.putAll(SteamService.getDownloadableDepots(gameId))

        // Add Base Game
        allDownloadableApps.add(Pair(gameId, downloadableDepots.toSortedMap().values.first()))
        selectedAppIds.put(gameId, true)

        // Add DLCs
        downloadableDepots
            .toSortedMap()
            .filter { (_, depot) ->
                return@filter depot.dlcAppId != INVALID_APP_ID // Skip Main App
            }.values
                .groupBy { it.dlcAppId }
                .mapValues { it.value.first() }
                .toMap()
            .forEach { (_, depotInfo) ->
                allDownloadableApps.add(Pair(depotInfo.dlcAppId, depotInfo))
                selectedAppIds.put(depotInfo.dlcAppId,
                    !indirectDlcAppIds.contains(depotInfo.dlcAppId) || installedDlcIds.contains(depotInfo.dlcAppId))
            }

    }

    fun getDepotAppName(depotInfo: DepotInfo): String {
        if (depotInfo.dlcAppId == INVALID_APP_ID) {
            return displayInfo.name
        }

        val app = SteamService.getAppInfoOf(depotInfo.dlcAppId)
        if (app != null) {
            return app.name
        }

        return "DLC ${depotInfo.dlcAppId}"
    }

    fun getInstallSizeInfo(): InstallSizeInfo {
        val availableBytes = StorageUtils.getAvailableSpace(SteamService.defaultStoragePath)

        // For Base Game
        val baseGameInstallBytes = appInfo.depots
            .filter { (_, depot) ->
                depot.dlcAppId == INVALID_APP_ID
            }.values.sumOf { it.manifests["public"]?.size ?: 0 }

        val baseGameDownloadBytes = appInfo.depots
            .filter { (_, depot) ->
                depot.dlcAppId == INVALID_APP_ID
            }.values.sumOf { it.manifests["public"]?.download ?: 0 }

        // For Selected DLCs
        val selectedInstallBytes = downloadableDepots
            .filter { (_, depot) ->
                selectedAppIds[depot.dlcAppId] == true
            }
            .values.sumOf { it.manifests["public"]?.size ?: 0 }

        val selectedDownloadBytes = downloadableDepots
            .filter { (_, depot) ->
                selectedAppIds[depot.dlcAppId] == true
            }
            .values.sumOf { it.manifests["public"]?.download ?: 0 }

        return InstallSizeInfo(
            downloadSize = StorageUtils.formatBinarySize(baseGameDownloadBytes + selectedDownloadBytes),
            installSize = StorageUtils.formatBinarySize(baseGameInstallBytes + selectedInstallBytes),
            availableSpace = StorageUtils.formatBinarySize(availableBytes),
            installBytes = baseGameInstallBytes + selectedInstallBytes,
            availableBytes = availableBytes
        )
    }

    fun installSizeDisplay() : String {
        val installSizeInfo = getInstallSizeInfo()
        return context.getString(
            R.string.steam_install_space,
            installSizeInfo.downloadSize,
            installSizeInfo.installSize,
            installSizeInfo.availableSpace
        )
    }

    fun installButtonEnabled() : Boolean {
        val installSizeInfo = getInstallSizeInfo()
        if (installSizeInfo.availableBytes < installSizeInfo.installBytes) {
            return false
        }

        if (installedApp != null) {
            return (selectedAppIds.filter { it.value }.size - installedDlcIds.size - 1) > 0 // -1 for main app
        }

        return selectedAppIds.filter { it.value }.isNotEmpty()
    }

    when {
        visible -> {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = false,
                ),
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        // Hero Section with Game Image Background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            // Hero background image
                            if (displayInfo.heroImageUrl != null) {
                                CoilImage(
                                    modifier = Modifier.fillMaxSize(),
                                    imageModel = { displayInfo.heroImageUrl },
                                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                    loading = { LoadingScreen() },
                                    failure = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            // Gradient background as fallback
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                color = MaterialTheme.colorScheme.primary
                                            ) { }
                                        }
                                    },
                                    previewPlaceholder = painterResource(R.drawable.testhero),
                                )
                            } else {
                                // Fallback gradient background when no hero image
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary
                                ) { }
                            }

                            // Gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.8f)
                                            )
                                        )
                                    )
                            )

                            // Back button (top left)
                            Box(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                BackButton(onClick = onDismissRequest)
                            }

                            // Game title and subtitle
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = displayInfo.name,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 10f
                                        )
                                    ),
                                    color = Color.White
                                )

                                Text(
                                    text = "${displayInfo.developer} • ${
                                        remember(displayInfo.releaseDate) {
                                            if (displayInfo.releaseDate > 0) {
                                                SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(displayInfo.releaseDate * 1000))
                                            } else {
                                                ""
                                            }
                                        }
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allDownloadableApps.forEach { (dlcAppId, depotInfo) ->
                                val checked = selectedAppIds[dlcAppId] ?: false

                                // Disable selection if DLC is not downloadable or it is already installed
                                val enabled = dlcAppId != gameId &&
                                                !installedDlcIds.contains(dlcAppId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, start = 16.dp, bottom = 8.dp, end = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        modifier = Modifier.weight(0.9f),
                                        text = getDepotAppName(depotInfo) +
                                                (if (BuildConfig.DEBUG) {
                                                    "\ndepotId: " + depotInfo.depotId + ", dlcAppId: " + depotInfo.dlcAppId
                                                } else {
                                                    ""
                                                })
                                    )
                                    Row(
                                        modifier = Modifier.weight(0.1f),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            enabled = enabled,
                                            onCheckedChange = { isChecked ->
                                                // Update the local (unsaved) state only
                                                selectedAppIds[dlcAppId] = isChecked
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, start = 8.dp, bottom = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier.weight(0.5f),
                                    text = installSizeDisplay()
                                )

                                Button(
                                    enabled = installButtonEnabled(),
                                    onClick = {
                                        onInstall(selectedAppIds.filter { it.key != gameId && !hiddenDlcIds.contains(it.key) }.filter { it.value }.keys.toList())
                                    }
                                ) {
                                    Text(stringResource(R.string.install))
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun Preview_GameManagerDialog() {
    val fakeApp = fakeAppInfo(1)
    val displayInfo = GameDisplayInfo(
        name = fakeApp.name,
        developer = fakeApp.developer,
        releaseDate = fakeApp.releaseDate,
        heroImageUrl = fakeApp.getHeroUrl(),
        iconUrl = fakeApp.iconUrl,
        gameId = fakeApp.id,
        appId = "STEAM_${fakeApp.id}",
        installLocation = null,
        sizeOnDisk = null,
        sizeFromStore = null,
        lastPlayedText = null,
        playtimeText = null,
    )

    PluviaTheme {
        GameManagerDialog(
            visible = true,
            onGetDisplayInfo = {
                return@GameManagerDialog displayInfo
            },
            onInstall = {},
            onDismissRequest = {}
        )
    }
}
