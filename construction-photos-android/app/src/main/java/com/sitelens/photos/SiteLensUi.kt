package com.sitelens.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import coil.compose.AsyncImage

private val Ink = Color(0xFF182E28)
private val Green = Color(0xFF216B51)
private val Mint = Color(0xFFE2F2E9)
private val Amber = Color(0xFF865315)
private val Cream = Color(0xFFFFEDCD)
private val Canvas = Color(0xFFF7F8F3)
private val Muted = Color(0xFF61716A)

@Composable
fun SiteLensApp(state: ScreenState, allowed: Boolean, full: Boolean, model: PhotosViewModel, connect: (String) -> Unit, permissions: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Canvas, surface = Color.White, onBackground = Ink, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(24.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Green) { Icon(Icons.Default.CameraAlt, null, Modifier.padding(10.dp), tint = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    Text("SiteLens", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("FIELD PHOTOS", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.progress.ifEmpty { "Working…" }, Modifier.padding(24.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                state.message?.let { message ->
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = Mint, shape = RoundedCornerShape(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { model.message(null) }) { Icon(Icons.Default.Close, "Dismiss message") }
                        }
                    }
                }
                when {
                    state.picking -> FolderPicker(state, model)
                    state.destination == null -> Welcome(state.busy) { connect("browse") }
                    else -> Gallery(state, allowed, full, model, connect, permissions)
                }
            }
        }
        state.conflict?.let { ReplacementDialog(it, model::answerConflict) }
    }
}

@Composable
private fun ReplacementDialog(conflict: UploadConflict, answer: (ConflictDecision) -> Unit) {
    var targetId by remember(conflict) { mutableStateOf(conflict.matches.singleOrNull()?.id) }
    AlertDialog(
        onDismissRequest = { answer(ConflictDecision.Cancel) },
        title = { Text("File already exists") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("${conflict.photo.name} matches an existing filename or photo in this Drive folder. Replace its contents with this original photo, including GPS metadata?")
                if (conflict.matches.size > 1) Text("Choose exactly one file to replace:", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                conflict.matches.forEach { remote ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { targetId = remote.id }) {
                        RadioButton(targetId == remote.id, { targetId = remote.id })
                        Column(Modifier.weight(1f)) {
                            Text(remote.name)
                            Text("${if (remote.hash == conflict.photo.hash) "Same photo" else "Same name, different contents"} · ID …${remote.id.takeLast(8)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("The existing Drive filename and link will be kept.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { answer(ConflictDecision.Cancel) }) { Text("Cancel remaining uploads") }
            }
        },
        confirmButton = { TextButton(onClick = { targetId?.let { answer(ConflictDecision.Replace(it)) } }, enabled = targetId != null) { Text("Replace") } },
        dismissButton = { TextButton(onClick = { answer(ConflictDecision.Skip) }) { Text("Skip this photo") } }
    )
}

@Composable
private fun Welcome(busy: Boolean, connect: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(12.dp))
        Surface(color = Mint, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Construction, null, Modifier.size(56.dp), tint = Green)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(20.dp), tint = Green)
                Icon(Icons.Default.CloudDone, null, Modifier.size(56.dp), tint = Green)
            }
        }
        Text("Your jobsite photos.\nOne place.", fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Text("Find the construction photos in your camera roll and keep them organized in Google Drive.", color = Muted, fontSize = 16.sp)
        WelcomeStep("1", "Choose your Drive folder", "Pick where your project photos should go.")
        WelcomeStep("2", "Scan last week and review", "Start with last week. Scan more dates whenever you need.")
        WelcomeStep("3", "Upload with confidence", "Green tags show photos already in your folder.")
        Button(onClick = connect, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("Choose Google Drive folder")
        }
        Text("You’ll authorize Google Drive to browse folders and check existing photos. Nothing uploads until you tap Upload.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WelcomeStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Mint, shape = RoundedCornerShape(10.dp)) { Text(number, Modifier.padding(12.dp, 8.dp), color = Green, fontWeight = FontWeight.Bold) }
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FolderPicker(state: ScreenState, model: PhotosViewModel) {
    BackHandler(enabled = !state.busy) { model.cancelPicker() }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("Choose a destination", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(state.accountName, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.browse() }, enabled = !state.busy) { Text("My Drive") }
            OutlinedButton(onClick = { model.browse(shared = true) }, enabled = !state.busy) { Text("Shared with me") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.path.size > 1 || (state.shared && state.path.isNotEmpty())) IconButton(onClick = { model.browse(up = true) }, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Parent folder") }
            Text(state.path.joinToString(" / ") { it.name }.ifEmpty { "Shared with me" }, Modifier.padding(vertical = 16.dp), fontWeight = FontWeight.Medium)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.folders.isEmpty()) item { Text("No subfolders here. You can use the current folder.", color = Muted, modifier = Modifier.padding(12.dp)) }
            items(state.folders, key = { it.id }) { folder ->
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { model.browse(folder) }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = Green)
                        Text(folder.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                        Icon(Icons.Default.ChevronRight, "Open ${folder.name}")
                    }
                }
            }
        }
        Button(onClick = model::chooseFolder, enabled = !state.busy && state.path.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) { Text("Use this folder") }
        TextButton(onClick = model::cancelPicker, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
    }
}

@Composable
private fun Gallery(state: ScreenState, allowed: Boolean, full: Boolean, model: PhotosViewModel, connect: (String) -> Unit, permissions: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var preview by remember { mutableStateOf<Photo?>(null) }
    var scanMore by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf(false) }
    var firstDate by remember { mutableStateOf(LocalDate.now().minusWeeks(1)) }
    var lastDate by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val destination = state.destination!!
    val dated = state.datedPhotos
    val selected = state.uploadSelection.size
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Ready for the record.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Review your photos. Keep the progress.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = Green)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(destination.folder.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(destination.accountName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted)
                    }
                    TextButton(onClick = { connect("browse") }, enabled = !state.busy) { Text("Change") }
                }
            }
        }
        if (!allowed) {
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(48.dp), tint = Green)
                Text("Let’s find your site photos", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                Text("Allow photos and photo-location access to keep the original GPS information in your Drive uploads. This reads location already saved in photos; it does not track your phone. You can allow your whole library or selected photos.", color = Muted)
                Button(onClick = permissions, enabled = !state.busy, modifier = Modifier.padding(top = 20.dp)) { Text("Allow photos and metadata") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = model::scan, enabled = !state.busy) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Text(" Scan") }
                TextButton(onClick = { connect("sync") }, enabled = !state.busy) { Text("Check Drive") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = permissions, enabled = !state.busy) { Text("Access") }
            }
            if (!full) Text("Selected-photo access • use Access to include more photos", Modifier.padding(horizontal = 24.dp), color = Amber, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Scanning: ${state.dateRange.preset.label}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                TextButton(onClick = { scanMore = true }, enabled = !state.busy) { Text("Scan more") }
            }
            Text(state.dateRange.description, Modifier.padding(horizontal = 24.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(if (state.synced) "Drive checked · green = uploaded, amber = selected" else "Saved upload status · tap Check Drive to refresh", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Selected", "Uploaded", "Unselected").forEach { title ->
                    val count = when (title) { "Selected" -> selected; "Uploaded" -> dated.count { it.uploaded }; "Unselected" -> dated.count { !it.selected && !it.uploaded }; else -> dated.size }
                    FilterChip(selected = filter == title, onClick = { filter = title }, label = { Text("$title $count") })
                }
            }
            val visible = dated.filter { when (filter) { "Selected" -> it.selected; "Uploaded" -> it.uploaded; "Unselected" -> !it.selected && !it.uploaded; else -> true } }
            if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (state.photos.isEmpty()) "No photos in this date range. Use Scan more to choose other dates." else "No photos in this view.", color = Muted)
            } else LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.key }) { photo -> PhotoCard(photo, !state.busy, { model.select(photo.key, it) }, { preview = photo }) }
            }
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(20.dp, 12.dp)) {
                    Text("Original photos include saved GPS metadata. Review selections before uploading.", color = Muted, fontSize = 11.sp)
                    Button(onClick = { connect("upload") }, enabled = !state.busy && selected > 0, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(10.dp)); Text("Upload $selected ${if (selected == 1) "photo" else "photos"}")
                    }
                }
            }
        }
    }
    if (scanMore) AlertDialog(
        onDismissRequest = { scanMore = false },
        title = { Text("Scan more photos") },
        text = {
            Column {
                Text("Only photos in the chosen date range will be scanned. All dates scans your entire accessible library.")
                listOf(DatePreset.LAST_WEEK, DatePreset.YESTERDAY, DatePreset.LAST_MONTH, DatePreset.ALL).forEach { preset ->
                    TextButton(onClick = { scanMore = false; model.setDateRange(preset) }, enabled = !state.busy) {
                        Text("Scan ${preset.label.lowercase()}")
                    }
                }
                TextButton(onClick = { scanMore = false; customRange = true }, enabled = !state.busy) { Text("Choose date range…") }
            }
        },
        confirmButton = { TextButton(onClick = { scanMore = false }) { Text("Cancel") } }
    )
    if (customRange) AlertDialog(
        onDismissRequest = { customRange = false },
        title = { Text("Scan a date range") },
        text = {
            Column {
                Text("Includes both start and end dates. Only photos in this range will be scanned.")
                TextButton(onClick = {
                    android.app.DatePickerDialog(context, { _, year, month, day -> firstDate = LocalDate.of(year, month + 1, day) },
                        firstDate.year, firstDate.monthValue - 1, firstDate.dayOfMonth).show()
                }) { Text("Start: $firstDate") }
                TextButton(onClick = {
                    android.app.DatePickerDialog(context, { _, year, month, day -> lastDate = LocalDate.of(year, month + 1, day) },
                        lastDate.year, lastDate.monthValue - 1, lastDate.dayOfMonth).show()
                }) { Text("End: $lastDate") }
                if (lastDate.isBefore(firstDate)) Text("End date must be on or after start date.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            customRange = false
            model.setDateRange(PhotoDateRange.custom(firstDate, lastDate))
        }, enabled = !state.busy && !lastDate.isBefore(firstDate)) { Text("Scan dates") } },
        dismissButton = { TextButton(onClick = { customRange = false }) { Text("Cancel") } }
    )
    preview?.let { initial ->
        val photo = state.photos.find { it.key == initial.key } ?: initial
        Dialog(onDismissRequest = { preview = null }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    AsyncImage(photo.uri, photo.name, Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(.85f), contentScale = ContentScale.Fit)
                    Text(photo.name, fontWeight = FontWeight.Bold)
                    Text(photo.reason, color = Muted)
                    photo.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(photo.selected, { model.select(photo.key, it) }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Include ${photo.name} in upload" })
                        Text(if (photo.uploaded) "Uploaded · select to replace" else "Include in upload")
                    }
                    TextButton(onClick = { preview = null }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(photo: Photo, enabled: Boolean, select: (Boolean) -> Unit, preview: () -> Unit) {
    val tint = if (photo.uploaded) Green else if (photo.selected) Amber else Muted
    val background = if (photo.uploaded) Mint else if (photo.selected) Cream else Color(0xFFEEF0ED)
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(if (photo.selected || photo.uploaded) 2.dp else 1.dp, if (photo.selected || photo.uploaded) tint else Color(0xFFE0E5DD))) {
        Column {
            Box {
                AsyncImage(photo.uri, "Preview ${photo.name}", Modifier.fillMaxWidth().aspectRatio(1f).background(background).clickable(onClick = preview), contentScale = ContentScale.Crop)
                Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(10.dp), color = Color.White) {
                    Checkbox(photo.selected, select, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Select ${photo.name} for ${if (photo.uploaded) "replacement" else "upload"}" })
                }
            }
            Column(Modifier.fillMaxWidth().background(background).padding(10.dp)) {
                Text(when { photo.uploaded && photo.selected -> "✓ Uploaded · replace?"; photo.uploaded -> "✓ Uploaded"; photo.selected && photo.override == true -> "Selected by you"; photo.selected -> "Construction · selected"; photo.override == false -> "Not selected"; else -> "Review photo" }, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(photo.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Text(photo.error ?: photo.reason, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, lineHeight = 13.sp, color = if (photo.error != null) MaterialTheme.colorScheme.error else Muted)
            }
        }
    }
}
