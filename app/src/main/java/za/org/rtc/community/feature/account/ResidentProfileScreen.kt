package za.org.rtc.community.feature.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ResidentProfileScreen(
    session: RtcSession?,
    isLoading: Boolean,
    photoSaveInProgress: Boolean = false,
    photoSaveSucceeded: Boolean = false,
    photoMessage: String? = null,
    onSave: (displayName: String, bio: String, interests: List<String>) -> Unit,
    onUploadProfilePhoto: (Uri) -> Unit = {},
    onDeleteProfilePhoto: () -> Unit = {},
    onBack: () -> Unit,
) {
    when {
        isLoading -> RtcScreenScaffold {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(RtcSpacing.standard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
                ) {
                    CircularProgressIndicator()
                    Text("Loading your profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        session == null -> RtcScreenScaffold {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(RtcSpacing.standard),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
                ) {
                    RtcSectionHeader("Profile unavailable", "Sign in to view and edit your resident profile.")
                    TextButton(onClick = onBack) { Text("Back to Account") }
                }
            }
        }

        else -> ResidentProfileForm(
            session = session,
            photoSaveInProgress = photoSaveInProgress,
            photoSaveSucceeded = photoSaveSucceeded,
            photoMessage = photoMessage,
            onSave = onSave,
            onUploadProfilePhoto = onUploadProfilePhoto,
            onDeleteProfilePhoto = onDeleteProfilePhoto,
            onBack = onBack,
        )
    }
}

@Composable
private fun ResidentProfileForm(
    session: RtcSession,
    photoSaveInProgress: Boolean,
    photoSaveSucceeded: Boolean,
    photoMessage: String?,
    onSave: (String, String, List<String>) -> Unit,
    onUploadProfilePhoto: (Uri) -> Unit,
    onDeleteProfilePhoto: () -> Unit,
    onBack: () -> Unit,
) {
    var displayName by rememberSaveable(session.id) { mutableStateOf(session.displayName) }
    var bio by rememberSaveable(session.id) { mutableStateOf(session.bio) }
    var interests by rememberSaveable(session.id) { mutableStateOf(session.interests.joinToString(", ")) }
    var selectedPhotoUri by rememberSaveable(session.id) { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedPhotoUri = uri
    }

    LaunchedEffect(session.id, session.displayName, session.bio, session.interests) {
        displayName = session.displayName
        bio = session.bio
        interests = session.interests.joinToString(", ")
    }
    LaunchedEffect(photoSaveInProgress, photoSaveSucceeded, photoMessage) {
        if (!photoSaveInProgress && photoSaveSucceeded && photoMessage?.startsWith("Profile photo") == true) {
            selectedPhotoUri = null
        }
    }

    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                title = "Profile",
                subtitle = "Your identity is linked to your authenticated resident session.",
                trailing = { TextButton(onClick = onBack) { Text("Back") } },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                Text(session.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(session.handle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.isGuest) {
                    Text(
                        "Guest profiles cannot be modified. Please register a full account to customize your profile.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ProfilePhotoControls(
                    session = session,
                    selectedPhotoUri = selectedPhotoUri,
                    isSaving = photoSaveInProgress,
                    message = photoMessage,
                    saveSucceeded = photoSaveSucceeded,
                    onChoosePhoto = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onUpload = { selectedPhotoUri?.let(onUploadProfilePhoto) },
                    onClearSelection = { selectedPhotoUri = null },
                    onRemove = onDeleteProfilePhoto,
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(120) },
                    enabled = !session.isGuest && !photoSaveInProgress,
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(600) },
                    enabled = !session.isGuest && !photoSaveInProgress,
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = interests,
                    onValueChange = { interests = it.take(400) },
                    enabled = !session.isGuest && !photoSaveInProgress,
                    label = { Text("Interests, separated by commas") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (session.isGuest) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Guest profiles cannot be modified. Please register a full account to customize your profile.")
                            }
                        } else {
                            onSave(displayName.trim(), bio.trim(), interests.split(","))
                        }
                    },
                    enabled = !photoSaveInProgress && displayName.trim().length in 2..120 && bio.trim().length <= 600,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save profile") }
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}

@Composable
private fun ProfilePhotoControls(
    session: RtcSession,
    selectedPhotoUri: Uri?,
    isSaving: Boolean,
    message: String?,
    saveSucceeded: Boolean,
    onChoosePhoto: () -> Unit,
    onUpload: () -> Unit,
    onClearSelection: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
        Text("Profile photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small),
        ) {
            if (selectedPhotoUri == null) {
                ProfileAvatar(session = session, modifier = Modifier.size(RtcSize.avatarLarge))
            } else {
                AsyncImage(
                    model = selectedPhotoUri,
                    contentDescription = "Selected profile photo preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(RtcSize.avatarLarge).clip(MaterialTheme.shapes.extraLarge),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(
                    if (selectedPhotoUri == null) {
                        "Choose a photo to upload or replace your current picture."
                    } else {
                        "Review the selected image, then save it to your private account folder."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Photos are cropped on-device and stored privately. Initials remain the fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = onChoosePhoto,
            enabled = !session.isGuest && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (selectedPhotoUri == null) "Choose profile photo" else "Choose another photo") }
        selectedPhotoUri?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = onUpload,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(if (isSaving) "Saving…" else "Save photo") }
            }
        }
        if (selectedPhotoUri == null && session.avatarUrl != null) {
            TextButton(
                onClick = onRemove,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove profile photo") }
        }
        message?.takeIf { it.startsWith("Profile photo") }?.let { photoMessage ->
            Text(
                photoMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (saveSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
