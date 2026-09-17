package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.clipboard.MasterClipboardEngine
import com.example.model.ClipboardItem
import com.example.model.ClipboardStats
import com.example.model.KeyboardSettings
import com.example.sync.CloudSyncEngine
import com.example.sync.SyncState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCardSurface
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.SettingsRepository
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var clipboardEngine: MasterClipboardEngine
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var syncEngine: CloudSyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        clipboardEngine = MasterClipboardEngine.getInstance(this)
        settingsRepo = SettingsRepository.getInstance(this)
        syncEngine = CloudSyncEngine.getInstance(this)

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    clipboardEngine = clipboardEngine,
                    settingsRepo = settingsRepo,
                    syncEngine = syncEngine,
                    onOpenImeSettings = { openImeSettings() },
                    onShowImePicker = { showImePicker() }
                )
            }
        }
    }

    private fun openImeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Klavye ayarları açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImePicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }
}

enum class MainTab(val title: String) {
    SETUP("Kurulum & Test"),
    CLIPBOARD("Master Pano"),
    SETTINGS("Ayarlar & Bulut")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    clipboardEngine: MasterClipboardEngine,
    settingsRepo: SettingsRepository,
    syncEngine: CloudSyncEngine,
    onOpenImeSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.CLIPBOARD) }
    val clips by clipboardEngine.itemsFlow.collectAsState()
    val stats by clipboardEngine.statsFlow.collectAsState()
    val settings by settingsRepo.settingsFlow.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanAccent.copy(alpha = 0.15f))
                                .border(1.dp, CyanAccent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🛡️", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "KALKAN KLAVYE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Sonsuz Pano & Ultra Hızlı IME",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CyanAccent,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldAccent.copy(alpha = 0.15f))
                            .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${clips.size} Kayıt",
                            color = EmeraldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDarkSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CyberDarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETUP,
                    onClick = { selectedTab = MainTab.SETUP },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Kurulum") },
                    label = { Text("Kurulum") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberBlack,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CLIPBOARD,
                    onClick = { selectedTab = MainTab.CLIPBOARD },
                    icon = { Icon(Icons.Default.List, contentDescription = "Pano") },
                    label = { Text("Pano (${clips.size})") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberBlack,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { selectedTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberBlack,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        },
        containerColor = CyberBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                MainTab.SETUP -> SetupWizardTab(
                    onOpenImeSettings = onOpenImeSettings,
                    onShowImePicker = onShowImePicker
                )
                MainTab.CLIPBOARD -> MasterClipboardTab(
                    clipboardEngine = clipboardEngine,
                    clips = clips,
                    stats = stats,
                    syncEngine = syncEngine,
                    syncState = syncState
                )
                MainTab.SETTINGS -> SettingsTab(
                    settings = settings,
                    onUpdateSettings = { settingsRepo.updateSettings(it) },
                    syncEngine = syncEngine,
                    clipboardEngine = clipboardEngine
                )
            }
        }
    }
}

// =============================================================================
// TAB 1: KURULUM & CANLI TEST
// =============================================================================
@Composable
fun SetupWizardTab(
    onOpenImeSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    var testText by remember { mutableStateOf("") }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 Kalkan Klavyeyi Aktif Et",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Klavyeyi telefonunda kullanabilmek için 2 basit adımı tamamla:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 1 Button
                    Button(
                        onClick = onOpenImeSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1️⃣", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Klavye Listesinden Kalkan'ı Etkinleştir",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step 2 Button
                    Button(
                        onClick = onShowImePicker,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("2️⃣", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Kalkan Klavyeyi Varsayılan Olarak Seç",
                                color = CyberBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Canlı Test & Hız Deneme Alanı",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aşağıdaki alana tıkla; Kalkan Klavye açılacak. Haptic titreşimi, sayı satırını, Türkçe karakterleri ve üstteki pano çiplerini hemen dene!",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buraya dokun ve hızlıca yazmaya başla...", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedContainerColor = CyberCardSurface,
                            unfocusedContainerColor = CyberCardSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { testText = "" }
                        ) {
                            Text("Metni Temizle", color = AmberAccent)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚡ Motor & Mimari Raporu",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ArchitectureMetricRow("Arayüz Mimarisi", "Saf Native Android View (Sıfır Lag)")
                    ArchitectureMetricRow("Soğuk Açılış Gecikmesi", "< 12 ms (Anında Tepki)")
                    ArchitectureMetricRow("Pano Karakter Sınırı", "SINIRSIZ (Kırpma Yok)")
                    ArchitectureMetricRow("Kayıt Süre Aşımı (TTL)", "YOK (Asla Silinmez)")
                    ArchitectureMetricRow("Yerel Dosya Yazımı", "JSON & Stream TXT Eşzamanlı")
                    ArchitectureMetricRow("SQLCipher Yükü", "KALDIRILDI (Hafif & Hızlı)")
                }
            }
        }
    }
}

@Composable
fun ArchitectureMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = EmeraldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// =============================================================================
// TAB 2: MASTER PANO (INFINITE CLIPBOARD)
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterClipboardTab(
    clipboardEngine: MasterClipboardEngine,
    clips: List<ClipboardItem>,
    stats: ClipboardStats,
    syncEngine: CloudSyncEngine,
    syncState: SyncState
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyPinned by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filteredClips = clips.filter { item ->
        val matchesSearch = if (searchQuery.isBlank()) true else item.text.contains(searchQuery, ignoreCase = true)
        val matchesPin = if (filterOnlyPinned) item.isPinned else true
        matchesSearch && matchesPin
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Statistics Grid Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatColumnItem("📋 Toplam Kayıt", "${stats.totalClips}", CyanAccent)
                StatColumnItem("⭐ Sabitlenen", "${stats.pinnedClips}", AmberAccent)
                StatColumnItem("📊 Karakter", "${stats.totalCharacters}", EmeraldAccent)
                StatColumnItem("💾 Dosya", String.format("%.1f KB", stats.jsonFileSizeKb), TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Sınırsız panoda ara (kod, metin, link)...", color = TextTertiary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text("✕", color = TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = CyberCardBorder,
                focusedContainerColor = CyberCardSurface,
                unfocusedContainerColor = CyberCardSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter and Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !filterOnlyPinned,
                    onClick = { filterOnlyPinned = false },
                    label = { Text("Tümü (${clips.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CyberCardSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                        selectedLabelColor = CyanAccent
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = filterOnlyPinned,
                    onClick = { filterOnlyPinned = true },
                    label = { Text("⭐ Sabit (${stats.pinnedClips})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CyberCardSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = AmberAccent.copy(alpha = 0.2f),
                        selectedLabelColor = AmberAccent
                    )
                )
            }

            Row {
                IconButton(
                    onClick = { showClearDialog = true }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Temizle",
                        tint = CrimsonAccent.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = {
                        shareExportFile(context, clipboardEngine)
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Paylaş / Dışa Aktar",
                        tint = CyanAccent
                    )
                }
            }
        }

        // Sync state banner if syncing or status available
        AnimatedVisibility(visible = syncState !is SyncState.Idle) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (syncState) {
                        is SyncState.Success -> EmeraldAccent.copy(alpha = 0.15f)
                        is SyncState.Error -> CrimsonAccent.copy(alpha = 0.15f)
                        else -> CyanAccent.copy(alpha = 0.15f)
                    }
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (syncState) {
                        is SyncState.Syncing -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buluta senkronize ediliyor...", color = CyanAccent, fontSize = 11.sp)
                        }
                        is SyncState.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text((syncState as SyncState.Success).message, color = EmeraldAccent, fontSize = 11.sp)
                        }
                        is SyncState.Error -> {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CrimsonAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text((syncState as SyncState.Error).error, color = CrimsonAccent, fontSize = 11.sp)
                        }
                        else -> {}
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Clips List
        if (filteredClips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛡️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "'$searchQuery' ile eşleşen kayıt bulunamadı" else "Pano henüz boş",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Herhangi bir uygulamadan kopyaladığın tüm metinler ve kodlar anında buraya sınırsız olarak kaydedilir.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredClips, key = { it.id }) { item ->
                    ClipboardItemCard(
                        item = item,
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("KalkanClip", item.text))
                            Toast.makeText(context, "Panoya kopyalandı (${item.charCount} karakter)", Toast.LENGTH_SHORT).show()
                        },
                        onTogglePin = { clipboardEngine.togglePin(item.id) },
                        onDelete = { clipboardEngine.deleteClip(item.id) },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, item.text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Metni Paylaş"))
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Sabitlenmemişleri Temizle", color = TextPrimary) },
            text = {
                Text(
                    "Sabitlenmemiş tüm öğeler silinecektir. Yıldız ile sabitlediğin öğeler KORUNUR ve silinmez. Devam edilsin mi?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardEngine.clearUnpinned()
                        showClearDialog = false
                        Toast.makeText(context, "Sabitlenmemiş öğeler temizlendi.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAccent)
                ) {
                    Text("Temizle", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal", color = TextSecondary)
                }
            },
            containerColor = CyberDarkSurface
        )
    }
}

@Composable
fun StatColumnItem(title: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ClipboardItemCard(
    item: ClipboardItem,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPinned) Color(0xFF131F33) else CyberCardSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isPinned) CyanAccent.copy(alpha = 0.6f) else CyberCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Date, Character count, Lines, Pin & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedDate,
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${item.charCount} krk",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    if (item.lineCount > 1) {
                        Text(
                            text = " • ${item.lineCount} satır",
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (item.isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Sabitle",
                            tint = if (item.isPinned) AmberAccent else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Create,
                            contentDescription = "Kopyala",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Paylaş",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = CrimsonAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Text Content
            Text(
                text = item.text,
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = if (item.text.contains("{") || item.text.contains("class ") || item.text.contains("fun ")) FontFamily.Monospace else FontFamily.Default,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (item.text.length > 120 && !isExpanded) {
                Text(
                    text = "Daha fazlasını göster...",
                    color = CyanAccent.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// =============================================================================
// TAB 3: AYARLAR & BULUT ENTEGRASYONU
// =============================================================================
@Composable
fun SettingsTab(
    settings: KeyboardSettings,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    syncEngine: CloudSyncEngine,
    clipboardEngine: MasterClipboardEngine
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val scope = rememberCoroutineScope()

    var tempSyncUrl by remember(settings.cloudSyncUrl) { mutableStateOf(settings.cloudSyncUrl) }
    var tempSyncSecret by remember(settings.cloudSyncSecret) { mutableStateOf(settings.cloudSyncSecret) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Haptic & Vibration Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Dokunmatik Titreşim (Haptic)", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("Her tuş vuruşunda ultra hafif haptic titreşim", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = settings.hapticFeedback,
                            onCheckedChange = { onUpdateSettings(settings.copy(hapticFeedback = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberBlack,
                                checkedTrackColor = CyanAccent
                            )
                        )
                    }

                    if (settings.hapticFeedback) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Titreşim Süresi: ${settings.hapticDuration} ms",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = settings.hapticDuration.toFloat(),
                            onValueChange = { value ->
                                val intVal = value.toInt()
                                onUpdateSettings(settings.copy(hapticDuration = intVal))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(intVal.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                                }
                            },
                            valueRange = 5f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanAccent,
                                activeTrackColor = CyanAccent,
                                inactiveTrackColor = CyberCardBorder
                            )
                        )
                    }
                }
            }
        }

        // Layout & Typing Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⌨️ Klavye Düzeni & Tuşlar", color = CyanAccent, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Sayı Satırı (1 2 3 ...)",
                        subtitle = "Klavyenin en üstünde sabit sayı tuşları",
                        checked = settings.showNumberRow,
                        onCheckedChange = { onUpdateSettings(settings.copy(showNumberRow = it)) }
                    )

                    SettingToggleRow(
                        title = "Doğrudan Türkçe Tuşlar (Ğ, Ü, Ş, İ, Ö, Ç)",
                        subtitle = "Bas-çek kolay Türkçe harfler",
                        checked = settings.turkishSpecialKeys,
                        onCheckedChange = { onUpdateSettings(settings.copy(turkishSpecialKeys = it)) }
                    )

                    SettingToggleRow(
                        title = "Tuş Tıklama Sesi",
                        subtitle = "Yazarken hafif sistem tıklama sesi",
                        checked = settings.soundFeedback,
                        onCheckedChange = { onUpdateSettings(settings.copy(soundFeedback = it)) }
                    )
                }
            }
        }

        // Cloud Backup & Instant Push Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("☁️ Anında Bulut & Webhook Yedekleme", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Kopyalanan her öğeyi veya tam veritabanını Gmail/Drive köprü servisine, kişisel sunucuna veya Webhook'a anında push et.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Otomatik Push (Her Kopyalamada)",
                        subtitle = "Metin kopyalandığı saniyede buluta gönderilir",
                        checked = settings.autoSyncOnCopy,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoSyncOnCopy = it)) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempSyncUrl,
                        onValueChange = {
                            tempSyncUrl = it
                            onUpdateSettings(settings.copy(cloudSyncUrl = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bulut Webhook / REST Endpoint URL") },
                        placeholder = { Text("https://your-webhook.com/sync", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempSyncSecret,
                        onValueChange = {
                            tempSyncSecret = it
                            onUpdateSettings(settings.copy(cloudSyncSecret = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Yetkilendirme Belirteci (Opsiyonel Auth Token)") },
                        placeholder = { Text("Bearer token veya secret key", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isPinging = true
                                syncEngine.testPing(tempSyncUrl, tempSyncSecret) { success, msg ->
                                    isPinging = false
                                    pingResult = msg
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCardSurface),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
                            } else {
                                Text("🔌 Test Ping", color = CyanAccent, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                syncEngine.pushFullBackup(tempSyncUrl, tempSyncSecret)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = CyberBlack, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tümünü Push Et", color = CyberBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    pingResult?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = msg, color = if (msg.contains("Başarılı")) EmeraldAccent else AmberAccent, fontSize = 11.sp)
                    }
                }
            }
        }

        // Local Storage Path & Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, tint = AmberAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📁 Yerel Dosya Konumları", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Master JSON: ${clipboardEngine.getMasterJsonFilePath()}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stream TXT: ${clipboardEngine.getStreamTxtFilePath()}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { shareExportFile(context, clipboardEngine) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardSurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Master JSON Dosyasını Dışa Aktar & Paylaş", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBlack,
                checkedTrackColor = CyanAccent
            )
        )
    }
}

fun shareExportFile(context: Context, engine: MasterClipboardEngine) {
    try {
        val file = File(engine.getMasterJsonFilePath())
        if (!file.exists()) {
            Toast.makeText(context, "Dışa aktarılacak dosya bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Master Pano JSON Paylaş"))
    } catch (e: Exception) {
        // Fallback to text share
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, File(engine.getMasterJsonFilePath()).readText(Charsets.UTF_8))
        }
        context.startActivity(Intent.createChooser(intent, "Pano Metnini Paylaş"))
    }
}
