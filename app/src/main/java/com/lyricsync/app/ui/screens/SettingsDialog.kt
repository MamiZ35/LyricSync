package com.lyricsync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyricsync.app.BuildConfig
import com.lyricsync.app.data.remote.UpdateChecker
import com.lyricsync.app.domain.OverlayConfig
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.domain.TranslationEngine
import com.lyricsync.app.ui.UpdateUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SETTINGS_TABS = listOf(
    "🎨 Görünüm",
    "🪟 Overlay",
    "🌐 Çeviri",
    "🚀 Güncelleme"
)

@Composable
internal fun SettingsAndModelsDialog(
    themeMode: ThemeMode,
    keepScreenOn: Boolean,
    autoLaunchSpotify: Boolean,
    overlayConfig: OverlayConfig,
    translationEngine: TranslationEngine,
    wifiOnlyDownload: Boolean,
    autoHideOverlayOnPause: Boolean,
    updateUiState: UpdateUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetAutoLaunchSpotify: (Boolean) -> Unit,
    onPreviewOverlayConfig: (OverlayConfig) -> Unit,
    onCommitOverlayConfig: (OverlayConfig) -> Unit,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    onSetWifiOnlyDownload: (Boolean) -> Unit,
    onSetAutoHideOverlayOnPause: (Boolean) -> Unit,
    onLoadCacheAndModels: suspend () -> Pair<Int, List<String>>,
    onClearCache: suspend () -> Unit,
    onDeleteModel: suspend (String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallApk: (String) -> Unit,
    onOpenDownloadUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var cachedCount by remember { mutableStateOf(0) }
    var downloadedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refreshStats() {
        scope.launch {
            isRefreshing = true
            val (count, models) = onLoadCacheAndModels()
            cachedCount = count
            downloadedModels = models
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 380.dp, max = 660.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ayarlar",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4-tab pill bar
                SettingsTabPillBar(
                    tabs = SETTINGS_TABS,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Active tab content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> AppearanceSettingsTab(
                            themeMode = themeMode,
                            keepScreenOn = keepScreenOn,
                            autoLaunchSpotify = autoLaunchSpotify,
                            onSelectThemeMode = onSelectThemeMode,
                            onSetKeepScreenOn = onSetKeepScreenOn,
                            onSetAutoLaunchSpotify = onSetAutoLaunchSpotify
                        )
                        1 -> OverlaySettingsTab(
                            overlayConfig = overlayConfig,
                            autoHideOverlayOnPause = autoHideOverlayOnPause,
                            onPreviewOverlayConfig = onPreviewOverlayConfig,
                            onCommitOverlayConfig = onCommitOverlayConfig,
                            onSetAutoHideOverlayOnPause = onSetAutoHideOverlayOnPause
                        )
                        2 -> TranslationSettingsTab(
                            translationEngine = translationEngine,
                            wifiOnlyDownload = wifiOnlyDownload,
                            cachedCount = cachedCount,
                            downloadedModels = downloadedModels,
                            isRefreshing = isRefreshing,
                            onSelectTranslationEngine = onSelectTranslationEngine,
                            onSetWifiOnlyDownload = onSetWifiOnlyDownload,
                            onClearCache = {
                                scope.launch {
                                    onClearCache()
                                    refreshStats()
                                }
                            },
                            onDeleteModel = { langCode ->
                                scope.launch {
                                    onDeleteModel(langCode)
                                    refreshStats()
                                }
                            }
                        )
                        else -> UpdateSettingsTab(
                            updateUiState = updateUiState,
                            onCheckForUpdate = onCheckForUpdate,
                            onDownloadAndInstall = {
                                val targetUrl = updateUiState.releaseInfo?.downloadUrl
                                    ?: UpdateChecker.LATEST_APK_URL
                                onDownloadAndInstallApk(targetUrl)
                            },
                            onOpenBrowser = {
                                onOpenDownloadUrl(
                                    updateUiState.releaseInfo?.downloadUrl ?: UpdateChecker.LATEST_APK_URL
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabPillBar(
    tabs: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .clickable { onSelectTab(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsTab(
    themeMode: ThemeMode,
    keepScreenOn: Boolean,
    autoLaunchSpotify: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetAutoLaunchSpotify: (Boolean) -> Unit
) {
    SettingsSectionCard(title = "Uygulama Teması") {
        ThemeMode.entries.chunked(2).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowModes.forEach { mode ->
                    val selected = mode == themeMode
                    val modeIcon = when (mode) {
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.AMOLED -> Icons.Default.NightsStay
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            )
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectThemeMode(mode) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seçili",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsSectionCard(title = "Oynatma & Başlangıç") {
        SettingsSwitchRow(
            icon = Icons.Default.BrightnessHigh,
            title = "Ekranı Açık Tut",
            subtitle = "Şarkı sözü okurken ekranın otomatik kapanmasını engeller.",
            checked = keepScreenOn,
            onCheckedChange = onSetKeepScreenOn
        )
        SettingsSwitchRow(
            icon = Icons.Default.PlayArrow,
            title = "Açılışta Spotify'ı Otomatik Başlat",
            subtitle = "Uygulama açılınca aktif şarkı yoksa Spotify'ı arka planda uyandırıp geri döner.",
            checked = autoLaunchSpotify,
            onCheckedChange = onSetAutoLaunchSpotify
        )
    }
}

@Composable
private fun OverlaySettingsTab(
    overlayConfig: OverlayConfig,
    autoHideOverlayOnPause: Boolean,
    onPreviewOverlayConfig: (OverlayConfig) -> Unit,
    onCommitOverlayConfig: (OverlayConfig) -> Unit,
    onSetAutoHideOverlayOnPause: (Boolean) -> Unit
) {
    var sliderFontSize by remember(overlayConfig.fontSizeSp) {
        mutableFloatStateOf(overlayConfig.fontSizeSp)
    }
    var sliderOpacity by remember(overlayConfig.opacity) {
        mutableFloatStateOf(overlayConfig.opacity)
    }

    OverlayLivePreviewCard(overlayConfig = overlayConfig)

    SettingsSectionCard(title = "Yazı Boyutu & Opaklık") {
        SliderHeaderRow(
            label = "Yazı Boyutu",
            valueText = "${sliderFontSize.roundToInt()} sp"
        )
        Slider(
            value = sliderFontSize,
            onValueChange = { newSize ->
                sliderFontSize = newSize
                onPreviewOverlayConfig(overlayConfig.copy(fontSizeSp = newSize))
            },
            onValueChangeFinished = {
                onCommitOverlayConfig(overlayConfig.copy(fontSizeSp = sliderFontSize))
            },
            valueRange = 12f..24f
        )

        SliderHeaderRow(
            label = "Arka Plan Opaklığı",
            valueText = "%${(sliderOpacity * 100).roundToInt()}"
        )
        Slider(
            value = sliderOpacity,
            onValueChange = { newOpacity ->
                sliderOpacity = newOpacity
                onPreviewOverlayConfig(overlayConfig.copy(opacity = newOpacity))
            },
            onValueChangeFinished = {
                onCommitOverlayConfig(overlayConfig.copy(opacity = sliderOpacity))
            },
            valueRange = 0.35f..1.0f
        )
    }

    SettingsSectionCard(title = "Davranış & Kilit") {
        SettingsSwitchRow(
            title = "Sıradaki Satırı Önizle",
            subtitle = "Aktif sözün altında bir sonraki satırı küçük gösterir.",
            checked = overlayConfig.showNextLine,
            onCheckedChange = { show ->
                onCommitOverlayConfig(overlayConfig.copy(showNextLine = show))
            }
        )
        SettingsSwitchRow(
            title = "Müzik Durunca Otomatik Gizle",
            subtitle = "Şarkı duraklatılınca 3.5 sn sonra altyazıyı gizler, çalınca geri getirir.",
            checked = autoHideOverlayOnPause,
            onCheckedChange = onSetAutoHideOverlayOnPause
        )
        SettingsSwitchRow(
            title = "Konum & Buton Kilidi",
            subtitle = "Açıkken pencere sürüklenemez ve kontrol butonları gizlenir; 🔒 simgesiyle açabilirsiniz.",
            checked = overlayConfig.isTouchLocked,
            onCheckedChange = { locked ->
                onCommitOverlayConfig(overlayConfig.copy(isTouchLocked = locked))
            }
        )
    }
}

@Composable
private fun OverlayLivePreviewCard(overlayConfig: OverlayConfig) {
    val bgAlpha = overlayConfig.opacity.coerceIn(0.35f, 1.0f)
    val previewShape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(previewShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A).copy(alpha = bgAlpha),
                        Color(0xFF1E293B).copy(alpha = bgAlpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    if (overlayConfig.isTouchLocked) {
                        listOf(Color(0x66FBBF24), Color(0x33F59E0B))
                    } else {
                        listOf(Color(0x8838BDF8), Color(0x44818CF8))
                    }
                ),
                shape = previewShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Önizleme • Canlı Altyazı",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = if (overlayConfig.isTouchLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (overlayConfig.isTouchLocked) Color(0xFFFBBF24) else Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Can't help falling in love",
            color = Color.White,
            fontSize = overlayConfig.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Aşık olmaktan kendimi alamıyorum",
            color = Color(0xFF38BDF8),
            fontSize = (overlayConfig.fontSizeSp - 2f).coerceAtLeast(11f).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        if (overlayConfig.showNextLine) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "▸ Like a river flows surely to the sea",
                color = Color(0xFF94A3B8),
                fontSize = (overlayConfig.fontSizeSp - 3f).coerceIn(10f, 14f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TranslationSettingsTab(
    translationEngine: TranslationEngine,
    wifiOnlyDownload: Boolean,
    cachedCount: Int,
    downloadedModels: List<String>,
    isRefreshing: Boolean,
    onSelectTranslationEngine: (TranslationEngine) -> Unit,
    onSetWifiOnlyDownload: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onDeleteModel: (String) -> Unit
) {
    SettingsSectionCard(title = "Çeviri Motoru") {
        TranslationEngine.entries.forEach { engine ->
            val selected = engine == translationEngine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectTranslationEngine(engine) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelectTranslationEngine(engine) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = engine.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (engine == TranslationEngine.CLOUD_CONTEXT) {
                            "Tüm şarkıyı bağlamıyla tek seferde anlamlı çevirir (~0.3 sn)."
                        } else {
                            "İnternetsiz çalışır (cihazda dil modeli indirir)."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSwitchRow(
            title = "Sadece Wi-Fi ile Model İndir",
            subtitle = "Mobil veri harcamamak için dil modellerini yalnızca Wi-Fi'da indirir.",
            checked = wifiOnlyDownload,
            onCheckedChange = onSetWifiOnlyDownload
        )
    }

    SettingsSectionCard(title = "Önbellek & Dil Modelleri") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Önbellekte $cachedCount şarkı (Favoriler korunur)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onClearCache,
                enabled = cachedCount > 0 && !isRefreshing,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Temizle", fontSize = 12.sp)
            }
        }

        Text(
            text = "İndirilen ML Kit Dil Modelleri",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            isRefreshing -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            downloadedModels.isEmpty() -> {
                Text(
                    text = "Henüz indirilmiş dil modeli yok.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                downloadedModels.forEach { langCode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Model: ${langCode.uppercase()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { onDeleteModel(langCode) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Modeli Sil",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsTab(
    updateUiState: UpdateUiState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    SettingsSectionCard(title = "Uygulama Güncellemesi") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Yüklü Sürüm: v${BuildConfig.VERSION_NAME}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = updateUiState.statusMessage
                    ?: "GitHub Releases üzerinden yeni sürümü tek dokunuşla kurabilirsiniz.",
                fontSize = 12.sp,
                fontWeight = if (updateUiState.statusMessage != null) FontWeight.Medium else FontWeight.Normal,
                color = if (updateUiState.releaseInfo?.isUpdateAvailable == true) {
                    Color(0xFF10B981)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (updateUiState.isDownloading) {
            LinearProgressIndicator(
                progress = { updateUiState.downloadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Text(
                text = "APK indiriliyor: %${updateUiState.downloadProgress}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCheckForUpdate,
                enabled = !updateUiState.isChecking && !updateUiState.isDownloading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (updateUiState.isChecking) "Kontrol..." else "Kontrol Et",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onDownloadAndInstall,
                enabled = !updateUiState.isDownloading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (updateUiState.isDownloading) "%${updateUiState.downloadProgress}" else "İndir & Kur",
                    fontSize = 12.sp
                )
            }
        }

        TextButton(
            onClick = onOpenBrowser,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Tarayıcıdan İndir", fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun SliderHeaderRow(
    label: String,
    valueText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
