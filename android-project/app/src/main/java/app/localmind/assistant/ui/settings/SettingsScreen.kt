package app.localmind.assistant.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.BuildConfig
import app.localmind.assistant.R
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.ChineseStyle
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.LightColors
import app.localmind.assistant.ui.theme.OledColors
import app.localmind.assistant.ui.theme.SlothColors
import app.localmind.assistant.ui.theme.ThemeMode
import app.localmind.assistant.ui.theme.SlothPalette as P

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    privacyScreen: Boolean,
    onPrivacyScreenChange: (Boolean) -> Unit,
    chineseStyle: ChineseStyle,
    onChineseScriptChange: (ChineseScript) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onOpenModels: () -> Unit,
    onOpenPersonas: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(title = "Settings", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Section("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeOption(ThemeMode.LIGHT, LightColors, themeMode, onThemeModeChange, Modifier.weight(1f))
                        ThemeOption(ThemeMode.OLED, OledColors, themeMode, onThemeModeChange, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingRow(
                        title = ThemeMode.SYSTEM.label,
                        detail = "Follow the tablet's light / dark setting",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                }

                Section("Chinese") {
                    Text(
                        "How the Mandarin and Cantonese tutors write. Applies from their next reply.",
                        color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    ChineseScript.entries.forEach { script ->
                        SettingRow(
                            title = script.label,
                            detail = script.detail,
                            selected = chineseStyle.script == script,
                            onClick = { onChineseScriptChange(script) }
                        )
                    }
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
                    ToggleRow(
                        title = "Pinyin & Jyutping",
                        detail = "Pinyin with tone marks under Mandarin, Jyutping with tone numbers under Cantonese.",
                        checked = chineseStyle.romanization,
                        onCheckedChange = onRomanizationChange
                    )
                }

                Section("Privacy") {
                    val context = LocalContext.current
                    val networkBlocked = remember {
                        context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                    }
                    StatusRow(
                        ok = networkBlocked,
                        title = if (networkBlocked) "No network access" else "Network access allowed",
                        detail = if (networkBlocked) "This app has no internet permission, so Android blocks every connection. Chats and models can't leave the tablet."
                        else "This build can reach the network."
                    )
                    StatusRow(
                        ok = true,
                        title = "Stays out of backups",
                        detail = "Chats, personas and models are excluded from cloud backup and device transfer."
                    )
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
                    ToggleRow(
                        title = "Privacy screen",
                        detail = "Block screenshots and screen recording, and hide chats in Recents.",
                        checked = privacyScreen,
                        onCheckedChange = onPrivacyScreenChange
                    )
                }

                Section("Assistant") {
                    LinkRow("Models", "Import, choose and delete on-device models", onOpenModels)
                    HorizontalDivider(color = P.Border)
                    LinkRow("Personas", "Built-in and custom personas", onOpenPersonas)
                }

                Section("About") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.sloth_llama),
                            contentDescription = null,
                            modifier = Modifier.size(width = 32.dp, height = 28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("SlothLlama ${BuildConfig.VERSION_NAME}", color = P.Text, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Runs entirely on this device with llama.cpp (GGUF) and Google AI Edge (.task / .litertlm). " +
                            "Conversations never leave the tablet.",
                        color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(), color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
        )
        content()
    }
}

/** A miniature preview of the theme: canvas, a user bubble and two text lines. */
@Composable
private fun ThemeOption(
    mode: ThemeMode,
    preview: SlothColors,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current == mode
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) P.Accent else P.Border, RoundedCornerShape(16.dp))
            .clickable { onSelect(mode) }
            .padding(10.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(preview.canvas)
                .border(1.dp, preview.border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.align(Alignment.End).width(56.dp).height(14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(preview.elevated)
            )
            Box(Modifier.fillMaxWidth(0.85f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(preview.muted.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(preview.muted.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                    .background(preview.elevated).border(1.dp, preview.border, RoundedCornerShape(7.dp))
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(8.dp))
            Text(mode.label, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Outlined.Shield else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (ok) P.Success else P.Warning,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.OnAccent,
                checkedTrackColor = P.Accent,
                uncheckedThumbColor = P.Muted,
                uncheckedTrackColor = P.Elevated,
                uncheckedBorderColor = P.BorderStrong
            )
        )
    }
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) P.Accent else Color.Transparent)
            .border(if (selected) 0.dp else 1.5.dp, if (selected) P.Accent else P.BorderStrong, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = P.OnAccent, modifier = Modifier.size(14.dp))
    }
}
