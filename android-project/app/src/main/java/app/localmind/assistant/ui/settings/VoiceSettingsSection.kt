package app.localmind.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.speech.SpeechEngine
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.QuietButton

/**
 * Settings → Voice: which text-to-speech engine reads aloud. Automatic is the tablet's default
 * (and if it won't start, the others are tried); or pick one, such as Google's or the tablet
 * maker's. A sample sentence tries the pick, and a link opens the tablet's voice settings.
 */
@Composable
internal fun VoiceSettingsSection() {
    val context = LocalContext.current
    val engines = remember { SpeechEngine.installed(context) }
    val tabletDefault = remember { SpeechEngine.tabletDefault(context) }
    var picked by remember { mutableStateOf(SpeechEngine.preferred(context)) }
    val speaker = rememberTutorSpeaker()
    fun pick(pkg: String?) {
        picked = pkg
        SpeechEngine.setPreferred(context, pkg)
    }
    Section("Voice") {
        Text(
            "Which text-to-speech engine reads aloud: tutors, Core words, the dictionary, flashcards and the Reader. " +
                "Only voices kept on the tablet are used.",
            color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        val defaultLabel = engines.firstOrNull { it.pkg == tabletDefault }?.label
        EngineRow(
            "Automatic",
            (defaultLabel?.let { "The tablet's default: $it" } ?: "The tablet's default") + ". If it won't start, the others are tried.",
            picked == null
        ) { pick(null) }
        engines.forEach { e ->
            EngineRow(
                e.label,
                listOfNotNull(
                    if (e.pkg == SpeechEngine.GOOGLE) "Google's voices" else null,
                    if (e.pkg == tabletDefault) "the tablet's default" else null
                ).joinToString(" · ").ifBlank { e.pkg },
                picked == e.pkg
            ) { pick(e.pkg) }
        }
        if (engines.isEmpty()) {
            Text(
                "No text-to-speech engine is installed. Speech Services by Google is free from the Play Store.",
                color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Try it in Mandarin") { speaker.speak("你好！我是你的普通话老师。", SpeechLanguage.MANDARIN) }
            QuietButton("Voices and speed") { SpeechEngine.openSettings(context) }
        }
    }
}

@Composable
private fun EngineRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) P.Accent else Color.Transparent)
                .border(if (selected) 0.dp else 1.5.dp, if (selected) P.Accent else P.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(P.OnAccent))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp)
        }
    }
}
