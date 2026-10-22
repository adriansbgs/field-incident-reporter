package com.incidentreporter.field.feature.form

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.incidentreporter.field.R
import com.incidentreporter.field.core.designsystem.ConfirmDialog
import com.incidentreporter.field.core.designsystem.FieldTheme
import com.incidentreporter.field.core.designsystem.PhotoReviewOverlay
import com.incidentreporter.field.core.designsystem.PhotoThumb
import com.incidentreporter.field.core.designsystem.label
import com.incidentreporter.field.core.designsystem.previewIncident
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.MAX_TITLE_LENGTH
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.usecase.FormField
import com.incidentreporter.field.domain.usecase.ValidationError

@Composable
fun IncidentFormScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    viewModel: IncidentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // The system photo picker needs no storage permission, and returns one image or nothing.
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onPhotoPicked(uri.toString())
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FormEvent.NavigateToList -> onDone()
                is FormEvent.ShowMessage -> snackbarHostState.showSnackbar(resources.getString(event.text))
            }
        }
    }

    // Leaving a new report asks before throwing content away, and drops an empty draft silently. An edit just goes back.
    fun leave() {
        when {
            state.mode == FormMode.EDIT -> onBack()
            state.hasContent -> confirmDiscard = true
            else -> viewModel.onDiscard()
        }
    }
    BackHandler(onBack = ::leave)

    IncidentFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = ::leave,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCategoryChange = viewModel::onCategoryChange,
        onSeverityChange = viewModel::onSeverityChange,
        onLocationLabelChange = viewModel::onLocationLabelChange,
        onFieldBlur = viewModel::onFieldBlur,
        onTakePhoto = onTakePhoto,
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onReview = viewModel::onReview,
        onSubmit = viewModel::onSubmit,
    )

    state.reviewing?.let { photo ->
        PhotoReviewOverlay(
            photo = photo,
            onClose = { viewModel.onReview(null) },
            // Photos are fixed once a report is submitted, so an edit only looks.
            onDelete = if (state.mode == FormMode.CREATE) ({ viewModel.onRemovePhoto(photo.id) }) else null,
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_body),
            confirmLabel = stringResource(R.string.discard_confirm),
            dismissLabel = stringResource(R.string.discard_keep),
            onConfirm = {
                confirmDiscard = false
                viewModel.onDiscard()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** Stateless: draws [state] and reports taps. Previews call this one, since they have no ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentFormScreen(
    state: FormUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onSeverityChange: (Severity) -> Unit,
    onLocationLabelChange: (String) -> Unit,
    onFieldBlur: (FormField) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onReview: (Photo) -> Unit,
    onSubmit: () -> Unit,
) {
    val create = state.mode == FormMode.CREATE
    val fields = state.fields
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (create) R.string.form_title_new else R.string.form_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
            )
        },
        bottomBar = { SubmitBar(create, state.isSubmitting, onSubmit) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isLoading) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (create || state.photos.isNotEmpty()) {
                    PhotoSection(
                        state.photos,
                        canAdd = create && state.photos.size < MAX_PHOTOS,
                        onTakePhoto,
                        onPickPhoto,
                        onReview
                    )
                }
                val titleFocus = remember { FocusRequester() }
                if (create) LaunchedEffect(Unit) { titleFocus.requestFocus() }
                FormTextField(
                    value = fields.title,
                    onValueChange = onTitleChange,
                    label = R.string.form_field_title,
                    error = state.errors[FormField.TITLE],
                    onBlur = { onFieldBlur(FormField.TITLE) },
                    supporting = stringResource(
                        R.string.form_counter,
                        fields.title.length,
                        MAX_TITLE_LENGTH
                    ),
                    modifier = Modifier.focusRequester(titleFocus),
                )
                FormTextField(
                    value = fields.description,
                    onValueChange = onDescriptionChange,
                    label = R.string.form_field_description,
                    error = state.errors[FormField.DESCRIPTION],
                    onBlur = { onFieldBlur(FormField.DESCRIPTION) },
                    singleLine = false,
                    minLines = 4,
                )
                CategoryChips(fields.category, state.errors[FormField.CATEGORY], onCategoryChange)
                SeverityButtons(fields.severity, onSeverityChange)
                FormTextField(
                    value = fields.locationLabel,
                    onValueChange = onLocationLabelChange,
                    label = R.string.form_field_location,
                    error = state.errors[FormField.LOCATION],
                    onBlur = { onFieldBlur(FormField.LOCATION) },
                    placeholder = stringResource(R.string.form_location_placeholder),
                )
            }
        }
    }
}

/** A labelled block of the form, with an optional note on the right of the label. */
@Composable
private fun Section(
    @StringRes label: Int,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (note != null) Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

/** No remove button on a thumbnail: a gloved tap opens the review overlay instead of deleting evidence. */
@Composable
private fun PhotoSection(
    photos: List<Photo>,
    canAdd: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onReview: (Photo) -> Unit,
) {
    Section(
        R.string.form_photos,
        note = stringResource(R.string.form_photo_count, photos.size, MAX_PHOTOS)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            photos.forEachIndexed { index, photo ->
                PhotoThumb(
                    file = photo.file,
                    size = 76.dp,
                    contentDescription = stringResource(
                        R.string.form_photo,
                        index + 1,
                        photos.size
                    ),
                    modifier = Modifier.clickable { onReview(photo) },
                )
            }
            if (canAdd) AddPhotoButton(onTakePhoto, onPickPhoto)

        }
    }
}

/** The add tile offers both ways in: the camera, or a photo already in the gallery. */
@Composable
private fun AddPhotoButton(onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(76.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.form_add_photo), modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_take)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    open = false
                    onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_from_gallery)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    open = false
                    onPickPhoto()
                },
            )
        }
    }
}

/** An outlined field that reports when the technician leaves it, so its error can appear then. */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: ValidationError?,
    onBlur: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supporting: String? = null,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val support = if (error != null) stringResource(error.message()) else supporting
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = support?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused) hadFocus = true else if (hadFocus) onBlur()
            },
    )
}

/** All six categories visible, one tap each, two to a row. */
@Composable
private fun CategoryChips(
    selected: Category?,
    error: ValidationError?,
    onSelect: (Category) -> Unit
) {
    Section(R.string.form_field_category) {
        Category.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    FilterChip(
                        selected = category == selected,
                        onClick = { onSelect(category) },
                        label = { Text(stringResource(category.label())) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                }
            }
        }
        if (error != null) {
            Text(
                stringResource(error.message()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SeverityButtons(selected: Severity, onSelect: (Severity) -> Unit) {
    Section(R.string.form_field_severity) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Severity.entries.forEachIndexed { index, severity ->
                SegmentedButton(
                    selected = severity == selected,
                    onClick = { onSelect(severity) },
                    shape = SegmentedButtonDefaults.itemShape(index, Severity.entries.size),
                    icon = {}, // no check mark, "Critical" needs the room
                    label = { Text(stringResource(severity.label()), maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Pinned above the keyboard, so Submit is reachable without scrolling while typing with gloves on. */
@Composable
private fun SubmitBar(create: Boolean, submitting: Boolean, onSubmit: () -> Unit) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.form_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(stringResource(if (create) R.string.form_submit else R.string.form_save))
            }
        }
    }
}

@StringRes
private fun ValidationError.message(): Int = when (this) {
    ValidationError.TITLE_REQUIRED -> R.string.form_error_title_required
    ValidationError.TITLE_TOO_LONG -> R.string.form_error_title_too_long
    ValidationError.DESCRIPTION_TOO_LONG -> R.string.form_error_description_too_long
    ValidationError.CATEGORY_REQUIRED -> R.string.form_error_category_required
    ValidationError.LOCATION_REQUIRED -> R.string.form_error_location_required
    ValidationError.TOO_MANY_PHOTOS -> R.string.form_error_too_many_photos
}

@Composable
private fun FormPreview(state: FormUiState) {
    FieldTheme {
        IncidentFormScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onTitleChange = {},
            onDescriptionChange = {},
            onCategoryChange = {},
            onSeverityChange = {},
            onLocationLabelChange = {},
            onFieldBlur = {},
            onTakePhoto = {},
            onPickPhoto = {},
            onReview = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun NewReportPreview() = FormPreview(
    FormUiState(
        fields = FormFields(
            title = "Hydraulic press leaking",
            category = Category.EQUIPMENT_FAILURE,
            severity = Severity.HIGH,
            loaded = true
        ),
        photos = previewIncident().photos,
        isLoading = false,
    ),
)

/** What Submit on an empty form shows. */
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun ErrorsPreview() = FormPreview(
    FormUiState(
        errors = mapOf(
            FormField.TITLE to ValidationError.TITLE_REQUIRED,
            FormField.CATEGORY to ValidationError.CATEGORY_REQUIRED,
            FormField.LOCATION to ValidationError.LOCATION_REQUIRED,
        ),
        isLoading = false,
    ),
)

/** Photos are fixed after submit: no add tile, and the thumbnails only open a read-only review. */
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun EditPreview() = FormPreview(
    FormUiState(
        mode = FormMode.EDIT,
        fields = FormFields(
            title = "Hydraulic press leaking",
            category = Category.EQUIPMENT_FAILURE,
            locationLabel = "Bay 3",
            loaded = true
        ),
        photos = previewIncident().photos,
        isLoading = false,
    ),
)
