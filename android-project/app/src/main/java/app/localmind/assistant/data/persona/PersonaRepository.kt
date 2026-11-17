package app.localmind.assistant.data.persona

import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.PersonaEntity
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in personas (from code) plus the user's own (from the `personas` table). */
@Singleton
class PersonaRepository @Inject constructor(
    private val dao: LocalMindDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val personas: Flow<List<Persona>> = dao.observeActivePersonas()
        .map { rows -> BuiltInPersonas.all + rows.filterNot { it.isBuiltIn }.map { it.toPersona() } }
        .flowOn(Dispatchers.IO)

    suspend fun byId(id: String?): Persona {
        if (id == null) return BuiltInPersonas.GENERAL_ASSISTANT
        BuiltInPersonas.all.firstOrNull { it.id == id }?.let { return it }
        return withContext(Dispatchers.IO) { dao.getPersonaById(id) }?.toPersona()
            ?: BuiltInPersonas.GENERAL_ASSISTANT
    }

    /** Saves a custom persona; returns it with its id. Built-ins are copied, never overwritten. */
    suspend fun save(persona: Persona): Persona = withContext(Dispatchers.IO) {
        val id = if (persona.isBuiltIn || persona.id.isBlank()) "persona-custom-${UUID.randomUUID()}" else persona.id
        val saved = persona.copy(id = id, isBuiltIn = false)
        val existing = dao.getPersonaById(id)
        dao.upsertPersona(saved.toEntity(createdAt = existing?.createdAt ?: System.currentTimeMillis()))
        saved
    }

    suspend fun delete(personaId: String) = withContext(Dispatchers.IO) {
        dao.deleteCustomPersona(personaId)
    }

    @Serializable
    private data class Extras(
        val speech: String? = null,
        val starters: List<String> = emptyList()
    )

    private fun PersonaEntity.toPersona(): Persona {
        val extras = tutorConfigJson
            ?.let { runCatching { json.decodeFromString(Extras.serializer(), it) }.getOrNull() }
            ?: Extras()
        return Persona(
            id = id,
            name = name,
            badge = iconName.ifBlank { "✦" },
            tagline = description,
            systemInstruction = systemInstruction,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            speech = extras.speech?.let { s -> SpeechLanguage.entries.firstOrNull { it.name == s } },
            starterPrompts = extras.starters,
            isBuiltIn = false
        )
    }

    private fun Persona.toEntity(createdAt: Long) = PersonaEntity(
        id = id,
        name = name.ifBlank { "Untitled persona" },
        description = tagline,
        iconName = badge.ifBlank { "✦" },
        accentHex = "#38BDF8",
        selectedModelId = "",
        systemInstruction = systemInstruction,
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        contextWindowStrategy = "SLIDING_WINDOW",
        enabledCollectionIdsJson = "[]",
        enabledToolsJson = "[]",
        memoryEnabled = false,
        tutorConfigJson = json.encodeToString(Extras.serializer(), Extras(speech?.name, starterPrompts)),
        isArchived = false,
        isBuiltIn = false,
        createdAt = createdAt
    )
}
