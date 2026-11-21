package app.localmind.assistant.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.projects.ProjectBook
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.projects.Brief
import app.localmind.assistant.domain.projects.Decision
import app.localmind.assistant.domain.projects.DocKind
import app.localmind.assistant.domain.projects.Milestone
import app.localmind.assistant.domain.projects.Phase
import app.localmind.assistant.domain.projects.Project
import app.localmind.assistant.domain.projects.ProjectAsk
import app.localmind.assistant.domain.projects.Projects
import app.localmind.assistant.domain.projects.Projects.upsert
import app.localmind.assistant.domain.projects.Rag
import app.localmind.assistant.domain.projects.Risk
import app.localmind.assistant.domain.projects.Stakeholder
import app.localmind.assistant.domain.projects.StatusUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Projects: the list, the open project and the document you're reading. One for the whole app
 * (see MainActivity), so a write-up kept from a chat lands in the project that asked for it.
 */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val book: ProjectBook,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore
) : ViewModel() {

    val projects: StateFlow<List<Project>> = book.projects

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    private val _docId = MutableStateFlow<String?>(null)
    val docId: StateFlow<String?> = _docId.asStateFlow()

    /** The open project, kept current as it changes. */
    val open: StateFlow<Project?> = combine(book.projects, _openId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Who a project asked in a chat, for the chat's keep button. */
    val pending: StateFlow<ProjectAsk?> = book.pending

    init {
        viewModelScope.launch { book.load() }
    }

    private fun today(): LocalDate = LocalDate.now()
    private fun todayText(): String = today().toString()
    private fun newId(): String = UUID.randomUUID().toString()

    fun openProject(id: String?) {
        _openId.value = id
        _docId.value = null
    }

    fun openDoc(id: String?) {
        _docId.value = id
    }

    fun create(name: String) = viewModelScope.launch {
        val p = book.create(name)
        openProject(p.id)
    }

    private fun edit(edit: (Project) -> Project) {
        val id = _openId.value ?: return
        viewModelScope.launch { book.update(id, edit = edit) }
    }

    fun rename(name: String) {
        val clean = name.trim().take(Projects.MAX_NAME)
        if (clean.isNotEmpty()) edit { it.copy(name = clean) }
    }

    fun setPhase(phase: Phase) = edit { it.copy(phase = phase) }

    fun saveBrief(brief: Brief) = edit {
        it.copy(
            brief = Brief(
                goal = Projects.clip(brief.goal), sponsor = Projects.clip(brief.sponsor, 200), scope = Projects.clip(brief.scope),
                outOfScope = Projects.clip(brief.outOfScope), constraints = Projects.clip(brief.constraints),
                success = Projects.clip(brief.success), deadline = brief.deadline.trim()
            )
        )
    }

    // --- milestones ---

    fun saveMilestone(m: Milestone) {
        if (m.title.isBlank()) return
        val item = m.copy(id = m.id.ifBlank { newId() }, title = Projects.clip(m.title, 200))
        edit { p -> p.copy(milestones = p.milestones.upsert(item) { it.id }) }
    }

    fun toggleMilestone(id: String) = edit { p ->
        p.copy(milestones = p.milestones.map { if (it.id == id) it.copy(doneOn = if (it.done) "" else todayText()) else it })
    }

    fun deleteMilestone(id: String) = edit { p -> p.copy(milestones = p.milestones.filterNot { it.id == id }) }

    // --- risks ---

    fun saveRisk(r: Risk) {
        if (r.title.isBlank()) return
        val item = r.copy(
            id = r.id.ifBlank { newId() }, title = Projects.clip(r.title, 200), owner = Projects.clip(r.owner, 120),
            response = Projects.clip(r.response), likelihood = r.likelihood.coerceIn(1, 3), impact = r.impact.coerceIn(1, 3),
            raisedOn = r.raisedOn.ifBlank { todayText() }
        )
        edit { p -> p.copy(risks = p.risks.upsert(item) { it.id }) }
    }

    fun closeRisk(id: String, closed: Boolean) = edit { p ->
        p.copy(risks = p.risks.map { if (it.id == id) it.copy(closedOn = if (closed) todayText() else "") else it })
    }

    fun deleteRisk(id: String) = edit { p -> p.copy(risks = p.risks.filterNot { it.id == id }) }

    // --- people ---

    fun saveStakeholder(s: Stakeholder) {
        if (s.name.isBlank()) return
        val item = s.copy(id = s.id.ifBlank { newId() }, name = Projects.clip(s.name, 120), role = Projects.clip(s.role, 120))
        edit { p -> p.copy(stakeholders = p.stakeholders.upsert(item) { it.id }) }
    }

    fun deleteStakeholder(id: String) = edit { p -> p.copy(stakeholders = p.stakeholders.filterNot { it.id == id }) }

    // --- decisions and updates ---

    fun saveDecision(d: Decision) {
        if (d.title.isBlank()) return
        val item = d.copy(id = d.id.ifBlank { newId() }, on = d.on.ifBlank { todayText() }, title = Projects.clip(d.title, 200), why = Projects.clip(d.why))
        edit { p -> p.copy(decisions = p.decisions.upsert(item) { it.id }) }
    }

    fun deleteDecision(id: String) = edit { p -> p.copy(decisions = p.decisions.filterNot { it.id == id }) }

    fun addUpdate(status: Rag, text: String) {
        if (text.isBlank()) return
        val u = StatusUpdate(newId(), todayText(), status, Projects.clip(text))
        edit { p -> p.copy(updates = listOf(u) + p.updates) }
    }

    fun deleteUpdate(id: String) = edit { p -> p.copy(updates = p.updates.filterNot { it.id == id }) }

    // --- documents ---

    /** A risk register or stakeholder map, made here from the record; it opens straight away. */
    fun build(kind: DocKind) {
        val p = open.value ?: return
        val text = Projects.built(kind, p, today()) ?: return
        viewModelScope.launch {
            book.addDoc(p.id, kind, Projects.docTitle(kind, today()), text)?.let { _docId.value = it.id }
        }
    }

    /** Opens a chat with the Scribe, the facts sheet attached and the request waiting to send. */
    fun writeUp(kind: DocKind) {
        val p = open.value ?: return
        if (kind.byCode) return build(kind)
        ask(p, kind, BuiltInPersonas.SCRIBE.id, Projects.prompt(kind, p, today()))
    }

    /** Opens a chat with the Grill about this project; its decision record can be kept here. */
    fun grill() {
        val p = open.value ?: return
        ask(p, DocKind.DECISION, BuiltInPersonas.GRILL.id, Projects.grillPrompt(p))
    }

    private fun ask(p: Project, kind: DocKind, personaId: String, prompt: String) {
        book.asked(ProjectAsk(p.id, kind, personaId))
        askInbox.post(
            LaunchRequest.Shared(
                prompt = prompt,
                document = attachments.textDocument("Facts sheet · ${p.name}", Projects.factsSheet(p, today())),
                personaId = personaId
            )
        )
    }

    /** Keeps a chat reply in the project that asked; the message to show. */
    suspend fun keep(text: String): String {
        val (project, doc) = book.keep(text) ?: return "Couldn't keep it: open the project and ask again"
        return "Kept in ${project.name}: ${doc.kind.label.lowercase()}"
    }

    fun renameDoc(id: String, title: String) {
        val clean = title.trim().take(120)
        if (clean.isNotEmpty()) edit { p -> p.copy(docs = p.docs.map { if (it.id == id) it.copy(title = clean) else it }) }
    }

    fun deleteDoc(id: String) {
        if (_docId.value == id) _docId.value = null
        edit { p -> p.copy(docs = p.docs.filterNot { it.id == id }) }
    }

    // --- the project itself ---

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch {
        book.update(id, touch = false) { it.copy(archived = archived) }
    }

    fun delete(id: String) = viewModelScope.launch {
        if (_openId.value == id) openProject(null)
        book.delete(id)
    }
}
