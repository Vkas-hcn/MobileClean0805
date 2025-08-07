@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.folding.pape.rmobileclean.file

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.*
import java.io.File
import java.text.DecimalFormat
import com.kite.folding.pape.rmobileclean.R
import com.kite.folding.pape.rmobileclean.FinishActivity

// Data classes and enums from original code
enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCS, DOWNLOAD, ZIP, OTHER
}

data class FileItem(
    val file: File,
    val name: String,
    val size: Long,
    val sizeFormatted: String,
    val unit: String,
    val type: FileType,
    val lastModified: Long,
    var isSelected: Boolean = false
)

enum class DropdownType {
    TYPE, SIZE, TIME
}

class FileScanComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileScanScreen()
        }
    }
}

@Composable
fun FileScanScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val decimalFormat = remember { DecimalFormat("#.#") }

    var allFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var filteredFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCount by remember { mutableStateOf(0) }

    // Filter states
    var currentFileType by remember { mutableStateOf<FileType?>(null) }
    var currentSizeFilter by remember { mutableStateOf(0L) }
    var currentTimeFilter by remember { mutableStateOf(0L) }
    var activeDropdown by remember { mutableStateOf<DropdownType?>(null) }

    // UI states
    var showScanDialog by remember { mutableStateOf(true) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var cleanProgress by remember { mutableIntStateOf(0) }

    // Filter display texts
    var typeDisplayText by remember { mutableStateOf("All types") }
    var sizeDisplayText by remember { mutableStateOf("All Size") }
    var timeDisplayText by remember { mutableStateOf("All Time") }

    // Functions
    fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                activity.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFileType(file: File): FileType {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg" -> FileType.IMAGE
            "mp4", "avi", "mkv", "mov", "3gp", "wmv", "flv", "webm", "m4v" -> FileType.VIDEO
            "mp3", "wav", "aac", "flac", "ogg", "wma", "m4a" -> FileType.AUDIO
            "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "rtf" -> FileType.DOCS
            "zip", "rar", "7z", "tar", "gz", "bz2" -> FileType.ZIP
            "apk", "exe", "deb", "dmg" -> FileType.OTHER
            else -> {
                val path = file.absolutePath.lowercase()
                when {
                    path.contains("download") -> FileType.DOWNLOAD
                    path.contains("dcim") || path.contains("pictures") || path.contains("camera") -> FileType.IMAGE
                    path.contains("movies") || path.contains("video") -> FileType.VIDEO
                    path.contains("music") || path.contains("audio") -> FileType.AUDIO
                    path.contains("documents") -> FileType.DOCS
                    else -> FileType.OTHER
                }
            }
        }
    }

    fun formatFileSize(bytes: Long): Pair<String, String> {
        return when {
            bytes >= 1024 * 1024 * 1024 -> {
                val gb = bytes.toDouble() / (1024 * 1024 * 1024)
                Pair(decimalFormat.format(gb), "GB")
            }

            bytes >= 1024 * 1024 -> {
                val mb = bytes.toDouble() / (1024 * 1024)
                Pair(decimalFormat.format(mb), "MB")
            }

            else -> {
                val kb = bytes.toDouble() / 1024
                Pair(decimalFormat.format(kb), "KB")
            }
        }
    }

    fun applyFilters() {
        val filtered = allFiles.filter { file ->
            var include = true

            if (currentFileType != null && file.type != currentFileType) {
                include = false
            }

            if (currentSizeFilter > 0 && file.size < currentSizeFilter) {
                include = false
            }

            if (currentTimeFilter > 0 && file.lastModified < currentTimeFilter) {
                include = false
            }

            include
        }

        filteredFiles = filtered
        selectedCount = filtered.count { it.isSelected }
    }

    fun loadFiles() {
        if (!hasStoragePermissions()) {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        scope.launch {
            try {
                isLoading = true
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(context, ::getFileType, ::formatFileSize)
                }

                allFiles = files
                applyFilters()
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to load files: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                isLoading = false
            }
        }
    }

    // Load files on first composition
    LaunchedEffect(Unit) {
        // Show scan dialog animation
        launch {
            while (scanProgress < 100) {
                delay(15)
                scanProgress++
            }
            showScanDialog = false
            loadFiles()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEFF7FF))
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { activity.finish() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_b),
                        contentDescription = "Back",
                        tint = Color(0xFF0A100F)
                    )
                }

                Text(
                    text = "Large File Clean",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF0A100F),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.size(32.dp))
            }

            // Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterButton(
                    text = typeDisplayText,
                    onClick = {
                        activeDropdown =
                            if (activeDropdown == DropdownType.TYPE) null else DropdownType.TYPE
                    }
                )
                FilterButton(
                    text = sizeDisplayText,
                    onClick = {
                        activeDropdown =
                            if (activeDropdown == DropdownType.SIZE) null else DropdownType.SIZE
                    }
                )
                FilterButton(
                    text = timeDisplayText,
                    onClick = {
                        activeDropdown =
                            if (activeDropdown == DropdownType.TIME) null else DropdownType.TIME
                    }
                )
            }

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                if (filteredFiles.isEmpty() && !isLoading) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_liebiaoweikong),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No file yet.",
                            color = Color(0xFFA3A3A3),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    // Files List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(top = 8.dp)
                    ) {
                        itemsIndexed(filteredFiles) { index, fileItem ->
                            FileItemRow(
                                fileItem = fileItem,
                                onItemClick = {
                                    val updatedFiles = filteredFiles.toMutableList()
                                    updatedFiles[index] =
                                        fileItem.copy(isSelected = !fileItem.isSelected)
                                    filteredFiles = updatedFiles

                                    // Update allFiles as well
                                    val allIndex =
                                        allFiles.indexOfFirst { it.file.absolutePath == fileItem.file.absolutePath }
                                    if (allIndex != -1) {
                                        val updatedAllFiles = allFiles.toMutableList()
                                        updatedAllFiles[allIndex] =
                                            updatedAllFiles[allIndex].copy(isSelected = !fileItem.isSelected)
                                        allFiles = updatedAllFiles
                                    }

                                    selectedCount = filteredFiles.count { it.isSelected }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Controls
            if (filteredFiles.isNotEmpty()) {
                BottomSection(
                    isAllSelected = selectedCount == filteredFiles.size && filteredFiles.isNotEmpty(),
                    hasSelectedFiles = selectedCount > 0,
                    selectedCount = selectedCount,
                    totalCount = filteredFiles.size,
                    onSelectAll = {
                        val allSelected =
                            selectedCount == filteredFiles.size && filteredFiles.isNotEmpty()
                        val newSelectedState = !allSelected
                        val updatedFiltered =
                            filteredFiles.map { it.copy(isSelected = newSelectedState) }
                        filteredFiles = updatedFiltered

                        // Update allFiles
                        val updatedAll = allFiles.map { allFile ->
                            val filteredFile =
                                updatedFiltered.find { it.file.absolutePath == allFile.file.absolutePath }
                            filteredFile ?: allFile
                        }
                        allFiles = updatedAll

                        selectedCount = if (newSelectedState) filteredFiles.size else 0
                    },
                    onDelete = {
                        if (selectedCount > 0) {
                            showCleanDialog = true
                            scope.launch {
                                cleanProgress = 0
                                val selectedFiles = filteredFiles.filter { it.isSelected }
                                var totalDeletedSize = 0L
                                var deletedCount = 0

                                withContext(Dispatchers.IO) {
                                    for (fileItem in selectedFiles) {
                                        try {
                                            if (fileItem.file.delete()) {
                                                totalDeletedSize += fileItem.size
                                                deletedCount++
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }

                                while (cleanProgress < 100) {
                                    delay(15)
                                    cleanProgress++
                                }

                                if (deletedCount > 0) {
                                    val (sizeFormatted, unit) = formatFileSize(totalDeletedSize)
                                    val cleanedSizeText = "$sizeFormatted $unit"

                                    // Navigate to finish activity
                                    val intent = Intent(context, FinishActivity::class.java).apply {
                                        putExtra("cleaned_size", cleanedSizeText)
                                    }
                                    context.startActivity(intent)
                                    activity.finish()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to delete files",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showCleanDialog = false
                            }
                        } else {
                            Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Dropdown overlay
        if (activeDropdown != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { activeDropdown = null }
                    .zIndex(1f)
            ) {
                when (activeDropdown) {
                    DropdownType.TYPE -> {
                        FileTypeDropdown(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 130.dp)
                                .width(120.dp),
                            onItemSelected = { type, text ->
                                currentFileType = type
                                typeDisplayText = text
                                activeDropdown = null
                                applyFilters()
                            }
                        )
                    }

                    DropdownType.SIZE -> {
                        FileSizeDropdown(
                            modifier = Modifier
                                .padding(top = 130.dp)
                                .width(120.dp)
                                .wrapContentHeight()
                                .offset(x = with(LocalDensity.current) {
                                    (LocalContext.current.resources.displayMetrics.widthPixels / 2 - 60.dp.toPx()).toDp()
                                }),
                            onItemSelected = { size, text ->
                                currentSizeFilter = size
                                sizeDisplayText = text
                                activeDropdown = null
                                applyFilters()
                            }
                        )
                    }

                    DropdownType.TIME -> {
                        FileTimeDropdown(
                            modifier = Modifier
                                .padding(end = 10.dp, top = 130.dp)
                                .width(140.dp)
                                .wrapContentHeight()
                                .offset(x = with(LocalDensity.current) {
                                    (LocalContext.current.resources.displayMetrics.widthPixels - 150.dp.toPx()).toDp()
                                }),
                            onItemSelected = { time, text ->
                                currentTimeFilter = time
                                timeDisplayText = text
                                activeDropdown = null
                                applyFilters()
                            }
                        )
                    }

                    else -> {}
                }
            }
        }

        // Scan Dialog
        if (showScanDialog) {
            NewScanDialog(
                progress = scanProgress,
                title = "Scanning...",
                onCancel = {
                    showScanDialog = false
                    activity.finish()
                }
            )
        }

        // Clean Dialog
        if (showCleanDialog) {
            NewScanDialog(
                progress = cleanProgress,
                title = "Cleaning...",
                onCancel = {
                    showCleanDialog = false
                }
            )
        }
    }
}

@Composable
fun BottomSection(
    isAllSelected: Boolean,
    hasSelectedFiles: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Select All section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onSelectAll() }
                .padding(8.dp)
        ) {
            Image(
                painter = painterResource(
                    id = if (isAllSelected) R.drawable.ic_selected else R.drawable.ic_dis_selected
                ),
                contentDescription = "Select All",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select All",
                fontSize = 12.sp,
                color = Color(0xFF0A100F)
            )
        }

        Spacer(modifier = Modifier.width(40.dp))



        // Delete Button
        Button(
            onClick = onDelete,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (hasSelectedFiles) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF64AFF9),
                                    Color(0xFF1972D9)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF707072),
                                    Color(0xFF424242)
                                )
                            )
                        },
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Delete",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FilterButton(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color(0xFFA3A3A3),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_san),
            contentDescription = null,
            tint = Color(0xFFA3A3A3),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
fun FileItemRow(
    fileItem: FileItem,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            when (fileItem.type) {
                FileType.IMAGE, FileType.VIDEO -> {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { imageView ->
                            Glide.with(imageView.context)
                                .load(fileItem.file)
                                .apply(
                                    RequestOptions()
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .error(R.drawable.ic_file_logo)
                                        .placeholder(R.drawable.ic_file_logo)
                                )
                                .into(imageView)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    Image(
                        painter = painterResource(id = R.drawable.ic_file_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // File Info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = fileItem.name,
                color = Color(0xFF0A100F),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = fileItem.sizeFormatted,
                    color = Color(0xFFA3A3A3),
                    fontSize = 12.sp
                )
                Text(
                    text = fileItem.unit,
                    color = Color(0xFFA3A3A3),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        // Selection Icon
        Image(
            painter = painterResource(
                id = if (fileItem.isSelected) R.drawable.ic_selected else R.drawable.ic_dis_selected
            ),
            contentDescription = "Select",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun FileTypeDropdown(
    modifier: Modifier = Modifier,
    onItemSelected: (FileType?, String) -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            DropdownItem("All Type") { onItemSelected(null, "All types") }
            DropdownItem("Image") { onItemSelected(FileType.IMAGE, "Image") }
            DropdownItem("Video") { onItemSelected(FileType.VIDEO, "Video") }
            DropdownItem("Audio") { onItemSelected(FileType.AUDIO, "Audio") }
            DropdownItem("Docs") { onItemSelected(FileType.DOCS, "Docs") }
            DropdownItem("Download") { onItemSelected(FileType.DOWNLOAD, "Download") }
            DropdownItem("Zip") { onItemSelected(FileType.ZIP, "Zip") }
        }
    }
}

@Composable
fun FileSizeDropdown(
    modifier: Modifier = Modifier,
    onItemSelected: (Long, String) -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            DropdownItem("All Size") { onItemSelected(0, "All Size") }
            DropdownItem(">10MB") { onItemSelected(10 * 1024 * 1024L, ">10MB") }
            DropdownItem(">20MB") { onItemSelected(20 * 1024 * 1024L, ">20MB") }
            DropdownItem(">50MB") { onItemSelected(50 * 1024 * 1024L, ">50MB") }
            DropdownItem(">100MB") { onItemSelected(100 * 1024 * 1024L, ">100MB") }
            DropdownItem(">200MB") { onItemSelected(200 * 1024 * 1024L, ">200MB") }
            DropdownItem(">500MB") { onItemSelected(500 * 1024 * 1024L, ">500MB") }
        }
    }
}

@Composable
fun FileTimeDropdown(
    modifier: Modifier = Modifier,
    onItemSelected: (Long, String) -> Unit
) {
    val currentTime = System.currentTimeMillis()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            DropdownItem("All Time") { onItemSelected(0, "All Time") }
            DropdownItem("Within 1 day") {
                onItemSelected(
                    currentTime - 24 * 60 * 60 * 1000L,
                    "Within 1 day"
                )
            }
            DropdownItem("Within 1 week") {
                onItemSelected(
                    currentTime - 7 * 24 * 60 * 60 * 1000L,
                    "Within 1 week"
                )
            }
            DropdownItem("Within 1 month") {
                onItemSelected(
                    currentTime - 30 * 24 * 60 * 60 * 1000L,
                    "Within 1 month"
                )
            }
            DropdownItem("Within 3 month") {
                onItemSelected(
                    currentTime - 90 * 24 * 60 * 60 * 1000L,
                    "Within 3 month"
                )
            }
            DropdownItem("Within 6 month") {
                onItemSelected(
                    currentTime - 180 * 24 * 60 * 60 * 1000L,
                    "Within 6 month"
                )
            }
        }
    }
}

@Composable
fun DropdownItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .height(40.dp)
            .wrapContentHeight(align = Alignment.CenterVertically)
            .padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = Color(0xFF0A100F)
    )
}

// New ScanDialog based on XML layout
@Composable
fun NewScanDialog(
    progress: Int,
    title: String,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Using a gradient background similar to bg_clean_page
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8CC6FE),
                            Color(0xFF2770C3)
                        )
                    )
                )
        ) {
            // Back button (only show for scanning)
            if (title == "Scanning...") {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // Center content
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                // Circular Progress Indicator
                CircularProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.size(144.dp),
                    color = Color(0xFF2EE5A5),
                    strokeWidth = 12.dp,
                    trackColor = Color.Transparent
                )

                // Inner oval background
                Box(
                    modifier = Modifier
                        .size(114.dp)
                        .background(
                            color = Color(0xFFFFFFFF),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Center icon
                    Image(
                        painter = painterResource(id = R.drawable.ic_file),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Bottom text
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 96.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function to scan files (extracted from original activity)
suspend fun scanAllFiles(
    context: Context,
    getFileType: (File) -> FileType,
    formatFileSize: (Long) -> Pair<String, String>
): List<FileItem> = withContext(Dispatchers.IO) {
    val fileList = mutableListOf<FileItem>()
    val scannedFiles = mutableSetOf<String>()

    val directories = mutableListOf<File>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (Environment.isExternalStorageManager()) {
            addStorageDirectories(directories)
        } else {
            addLimitedDirectories(context, directories)
        }
    } else {
        addStorageDirectories(directories)
    }

    for (directory in directories) {
        try {
            if (directory.exists() && directory.canRead()) {
                scanDirectory(
                    directory,
                    fileList,
                    mutableSetOf(),
                    scannedFiles,
                    getFileType,
                    formatFileSize
                )
            }
        } catch (e: Exception) {
            Log.w("FileScan", "Exception accessing directory: ${directory.absolutePath}", e)
        }
    }

    fileList.sortedByDescending { it.size }
}

private fun addStorageDirectories(directories: MutableList<File>) {
    Environment.getExternalStorageDirectory()?.let { dir ->
        if (dir.exists()) directories.add(dir)
    }

    val publicDirs = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_DCIM
    )

    for (dirType in publicDirs) {
        Environment.getExternalStoragePublicDirectory(dirType)?.let { dir ->
            if (dir.exists() && !directories.contains(dir)) {
                directories.add(dir)
            }
        }
    }
}

private fun addLimitedDirectories(context: Context, directories: MutableList<File>) {
    context.getExternalFilesDir(null)?.let { directories.add(it) }
    context.externalCacheDir?.let { directories.add(it) }

    try {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.let { dir ->
            if (dir.exists() && dir.canRead()) directories.add(dir)
        }
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            if (dir.exists() && dir.canRead()) directories.add(dir)
        }
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { dir ->
            if (dir.exists() && dir.canRead()) directories.add(dir)
        }
    } catch (e: Exception) {
        Log.w("FileScan", "Cannot access public directories", e)
    }
}

private fun scanDirectory(
    directory: File,
    fileList: MutableList<FileItem>,
    visitedDirs: MutableSet<String>,
    scannedFiles: MutableSet<String>,
    getFileType: (File) -> FileType,
    formatFileSize: (Long) -> Pair<String, String>
) {
    try {
        val canonicalPath = directory.canonicalPath
        if (visitedDirs.contains(canonicalPath)) {
            return
        }
        visitedDirs.add(canonicalPath)

        val files = directory.listFiles()
        if (files == null) {
            Log.w("FileScan", "Cannot list files in: ${directory.absolutePath}")
            return
        }

        for (file in files) {
            try {
                if (file.isFile && file.length() > 0 && file.canRead()) {
                    val canonicalFilePath = file.canonicalPath
                    if (!scannedFiles.contains(canonicalFilePath)) {
                        scannedFiles.add(canonicalFilePath)

                        val fileType = getFileType(file)
                        val (sizeFormatted, unit) = formatFileSize(file.length())

                        fileList.add(
                            FileItem(
                                file = file,
                                name = file.name,
                                size = file.length(),
                                sizeFormatted = sizeFormatted,
                                unit = unit,
                                type = fileType,
                                lastModified = file.lastModified()
                            )
                        )
                    }
                } else if (file.isDirectory && file.canRead() && !file.name.startsWith(".")) {
                    scanDirectory(
                        file,
                        fileList,
                        visitedDirs,
                        scannedFiles,
                        getFileType,
                        formatFileSize
                    )
                }
            } catch (e: SecurityException) {
                Log.w("FileScan", "Permission denied for file: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.w("FileScan", "Error processing file: ${file.absolutePath}", e)
            }
        }
    } catch (e: Exception) {
        Log.e("FileScan", "Error scanning directory: ${directory.absolutePath}", e)
    }
}

@Preview(showBackground = true)
@Composable
fun FileScanScreenPreview() {
    FileScanScreen()
}