package za.org.rtc.community.feature.publicreports.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.core.media.MediaPreparation
import za.org.rtc.community.feature.publicreports.domain.PublicReportCategory
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReportEvidencePending
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportLocationMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportMediaKind
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation
import za.org.rtc.community.ui.navigation.ResidentComposerPrefill

data class StagedEvidence(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val mimeType: String,
    val kind: PublicReportMediaKind,
    val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
    val stagedPath: String? = null,
)

data class PublicReportComposerState(
    val clientRequestId: String,
    val title: String = "",
    val description: String = "",
    val startedAt: Instant? = null,
    val startedUnknown: Boolean = true,
    val categoryId: String? = null,
    val categories: List<PublicReportCategory> = emptyList(),
    val urgency: PublicReportUrgency = PublicReportUrgency.NORMAL,
    val identityMode: PublicReportIdentityMode = PublicReportIdentityMode.NAMED,
    val publicLocationLabel: String = "",
    val exactAddress: String = "",
    val evidence: List<StagedEvidence> = emptyList(),
    val cannotProvideEvidence: Boolean = false,
    val noEvidenceReason: String = "",
    val contactPermission: Boolean = false,
    val guidelinesAccepted: Boolean = false,
    val guidelinesVersion: String = "",
    val submitting: Boolean = false,
    val submissionPhase: PublicReportSubmissionPhase = PublicReportSubmissionPhase.EDITING,
    val submittedReportId: String? = null,
    val message: String? = null,
    val mapUnavailableNotice: String = "A map picker is not configured. Enter a landmark or address manually.",
) {
    val showCriticalNotice: Boolean get() = urgency == PublicReportUrgency.CRITICAL
}

enum class PublicReportSubmissionPhase {
    EDITING,
    DRAFT_CREATED,
    UPLOADING,
    PARTIAL_UPLOAD,
    READY_FOR_REVIEW,
    FAILED_RETRYABLE,
}

@HiltViewModel
class PublicReportComposerViewModel @Inject constructor(
    private val repository: PublicReportRepository,
    private val mediaPreparation: MediaPreparation,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val requestKey = "public_report_client_request_id"
    private val retainedId = savedStateHandle.get<String>(requestKey) ?: UUID.randomUUID().toString().also {
        savedStateHandle[requestKey] = it
    }
    private val descriptionPrefill = savedStateHandle
        .remove<String>(ResidentComposerPrefill.PUBLIC_REPORT_DESCRIPTION)
        ?.trim()
        ?.take(PublicReportValidation.DESCRIPTION_MAX)
        .orEmpty()

    private val draftTitle = savedStateHandle.get<String>("draft_title").orEmpty()
    private val draftDesc = savedStateHandle.get<String>("draft_desc") ?: descriptionPrefill
    private val draftPublicLocation = savedStateHandle.get<String>("draft_public_location").orEmpty()
    private val draftExactAddress = savedStateHandle.get<String>("draft_exact_address").orEmpty()

    private val _state = MutableStateFlow(
        PublicReportComposerState(
            clientRequestId = retainedId,
            title = draftTitle,
            description = draftDesc,
            publicLocationLabel = draftPublicLocation,
            exactAddress = draftExactAddress,
        ),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.categories().onSuccess { categories ->
                _state.update { it.copy(categories = categories.filter { category -> category.isActive }) }
            }
        }
    }

    fun setTitle(value: String) {
        val trimmed = value.take(PublicReportValidation.TITLE_MAX)
        savedStateHandle["draft_title"] = trimmed
        _state.update { it.copy(title = trimmed) }
    }

    fun setDescription(value: String) {
        val trimmed = value.take(PublicReportValidation.DESCRIPTION_MAX)
        savedStateHandle["draft_desc"] = trimmed
        _state.update { it.copy(description = trimmed) }
    }
    fun setStartedAt(value: Instant?) = _state.update { it.copy(startedAt = value, startedUnknown = value == null) }
    fun setCategory(id: String) = _state.update { it.copy(categoryId = id) }
    fun setUrgency(value: PublicReportUrgency) = _state.update { it.copy(urgency = value) }
    fun setIdentity(value: PublicReportIdentityMode) = _state.update { it.copy(identityMode = value) }
    fun setPublicLocation(value: String) {
        val trimmed = value.take(PublicReportValidation.LOCATION_MAX)
        savedStateHandle["draft_public_location"] = trimmed
        _state.update { it.copy(publicLocationLabel = trimmed) }
    }

    fun setExactAddress(value: String) {
        val trimmed = value.take(PublicReportValidation.ADDRESS_MAX)
        savedStateHandle["draft_exact_address"] = trimmed
        _state.update { it.copy(exactAddress = trimmed) }
    }
    fun setCannotProvideEvidence(value: Boolean) = _state.update { it.copy(cannotProvideEvidence = value) }
    fun setNoEvidenceReason(value: String) = _state.update { it.copy(noEvidenceReason = value.take(PublicReportValidation.EXCEPTION_MAX)) }
    fun setContactPermission(value: Boolean) = _state.update { it.copy(contactPermission = value) }
    fun setGuidelines(accepted: Boolean, version: String) = _state.update { it.copy(guidelinesAccepted = accepted, guidelinesVersion = version) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun consumeSubmittedId() = _state.update { it.copy(submittedReportId = null) }

    fun addEvidence(uri: Uri) {
        if (_state.value.evidence.size >= PublicReportValidation.EVIDENCE_MAX) {
            _state.update { it.copy(message = "Attach at most ${PublicReportValidation.EVIDENCE_MAX} files.") }
            return
        }
        viewModelScope.launch {
            runCatching { mediaPreparation.prepare(uri) }
                .onSuccess { prepared ->
                    val kind = if (prepared.kind.name == "VIDEO") PublicReportMediaKind.VIDEO else PublicReportMediaKind.IMAGE
                    PublicReportValidation.evidenceMime(prepared.mimeType, kind)?.let { message ->
                        prepared.file.delete(); _state.update { it.copy(message = message) }; return@onSuccess
                    }
                    PublicReportValidation.evidenceSize(kind, prepared.byteSize, prepared.durationSeconds)?.let { message ->
                        prepared.file.delete(); _state.update { it.copy(message = message) }; return@onSuccess
                    }
                    _state.update { state ->
                        state.copy(
                            cannotProvideEvidence = false,
                            evidence = state.evidence + StagedEvidence(
                                uri = uri,
                                mimeType = prepared.mimeType,
                                kind = kind,
                                byteSize = prepared.byteSize,
                                width = prepared.width,
                                height = prepared.height,
                                durationSeconds = prepared.durationSeconds,
                                stagedPath = prepared.file.absolutePath,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = SafeUiError.generic(error, "That file could not be prepared.")) }
                }
        }
    }

    fun removeEvidence(id: String) {
        val removed = _state.value.evidence.firstOrNull { it.id == id }
        removed?.stagedPath?.let { path -> java.io.File(path).delete() }
        _state.update { it.copy(evidence = it.evidence.filterNot { item -> item.id == id }) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.submittedReportId != null) return
        val draft = PublicReportDraft(
            clientRequestId = current.clientRequestId,
            title = current.title,
            description = current.description,
            startedAt = if (current.startedUnknown) null else current.startedAt,
            categoryId = current.categoryId.orEmpty(),
            urgency = current.urgency,
            identityMode = current.identityMode,
            locationMode = PublicReportLocationMode.MANUAL,
            publicLocationLabel = current.publicLocationLabel,
            latitude = null,
            longitude = null,
            exactAddress = current.exactAddress,
            noEvidenceReason = current.noEvidenceReason.takeIf { current.cannotProvideEvidence },
            contactPermission = current.contactPermission,
            guidelinesVersion = current.guidelinesVersion,
        )
        val error = PublicReportValidation.draft(
            draft = draft,
            evidenceCount = current.evidence.size,
            cannotProvideEvidence = current.cannotProvideEvidence,
            guidelinesAccepted = current.guidelinesAccepted,
        )
        if (error != null) {
            _state.update { it.copy(message = error) }
            return
        }
        _state.update {
            it.copy(
                submitting = true,
                submissionPhase = PublicReportSubmissionPhase.EDITING,
                message = null,
            )
        }
        viewModelScope.launch {
            repository.create(draft)
                .onSuccess { reportId ->
                    _state.update { it.copy(submissionPhase = PublicReportSubmissionPhase.DRAFT_CREATED) }
                    repository.enqueueEvidence(
                        reportId = reportId,
                        clientRequestId = current.clientRequestId,
                        items = current.evidence.mapIndexed { index, item ->
                            PublicReportEvidencePending(
                                id = item.id,
                                stagedPath = item.stagedPath ?: error("Evidence could not be read."),
                                mediaKind = item.kind,
                                mimeType = item.mimeType,
                                byteSize = item.byteSize,
                                width = item.width,
                                height = item.height,
                                durationSeconds = item.durationSeconds,
                                position = index + 1,
                            )
                        },
                    )
                        .fold(
                            onSuccess = {
                                _state.update { it.copy(submissionPhase = PublicReportSubmissionPhase.UPLOADING) }
                                repository.resumeEvidence(reportId)
                                    .onSuccess {
                                        _state.update {
                                            it.copy(
                                                submitting = false,
                                                submissionPhase = PublicReportSubmissionPhase.READY_FOR_REVIEW,
                                                submittedReportId = reportId,
                                                message = null,
                                            )
                                        }
                                    }
                                    .onFailure { failure ->
                                        _state.update {
                                            it.copy(
                                                submitting = false,
                                                submissionPhase = PublicReportSubmissionPhase.PARTIAL_UPLOAD,
                                                message = SafeUiError.generic(
                                                    failure,
                                                    "Your report was created, but its evidence could not be attached. Your files were kept; try publishing again.",
                                                ),
                                            )
                                        }
                                    }
                            },
                            onFailure = { failure ->
                                _state.update {
                                    it.copy(
                                        submitting = false,
                                        submissionPhase = PublicReportSubmissionPhase.FAILED_RETRYABLE,
                                        message = SafeUiError.generic(failure, "Report evidence could not be queued. Try again."),
                                    )
                                }
                            },
                        )
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            submitting = false,
                            submissionPhase = PublicReportSubmissionPhase.FAILED_RETRYABLE,
                            message = SafeUiError.generic(failure, failure.message ?: "The Public Report could not be submitted."),
                        )
                    }
                }
        }
    }

}
