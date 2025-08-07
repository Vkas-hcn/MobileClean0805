package com.kite.folding.pape.rmobileclean

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.DecimalFormat
import kotlin.math.pow

data class GarbageFile(
    val name: String,
    val path: String,
    val size: Long,
    val isSelected: Boolean = true // 默认选中
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GarbageFile
        return name == other.name &&
                path == other.path &&
                size == other.size &&
                isSelected == other.isSelected
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + isSelected.hashCode()
        return result
    }
}

data class GarbageCategory(
    val type: GarbageCategoryType,
    val name: String,
    val icon: Int,
    val files: MutableSet<GarbageFile> = mutableSetOf(),
    val isExpanded: Boolean = false,
    val isAllSelected: Boolean = true
) {
    val totalSize: Long get() = files.sumOf { it.size }
    val selectedSize: Long get() = files.filter { it.isSelected }.sumOf { it.size }
    val selectedCount: Int get() = files.count { it.isSelected }
    val filesList: List<GarbageFile> get() = files.toList()

    val hasSelectedFiles: Boolean get() = files.any { it.isSelected }
    val hasUnselectedFiles: Boolean get() = files.any { !it.isSelected }
    val isPartiallySelected: Boolean get() = hasSelectedFiles && hasUnselectedFiles
}

enum class GarbageCategoryType {
    APP_CACHE, APK_FILES, LOG_FILES, AD_JUNK, TEMP_FILES
}

enum class ScanState {
    IDLE, SCANNING, COMPLETED
}

class GarbageCleanViewModel(private val context: ComponentActivity) : ViewModel() {
    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _currentScanPath = MutableStateFlow("")
    val currentScanPath: StateFlow<String> = _currentScanPath.asStateFlow()

    private val _totalGarbageSize = MutableStateFlow(0L)
    val totalGarbageSize: StateFlow<Long> = _totalGarbageSize.asStateFlow()

    private val _categories = MutableStateFlow(
        listOf(
            GarbageCategory(GarbageCategoryType.APP_CACHE, "App Cache", R.drawable.ic_cache),
            GarbageCategory(GarbageCategoryType.APK_FILES, "Apk Files", R.drawable.ic_apk_file),
            GarbageCategory(GarbageCategoryType.LOG_FILES, "Log Files", R.drawable.ic_log),
            GarbageCategory(GarbageCategoryType.AD_JUNK, "AD Junk", R.drawable.ic_ad_junk),
            GarbageCategory(GarbageCategoryType.TEMP_FILES, "Temp Files", R.drawable.ic_temp)
        )
    )
    val categories: StateFlow<List<GarbageCategory>> = _categories.asStateFlow()

    private val _showCleanProgress = MutableStateFlow(false)
    val showCleanProgress: StateFlow<Boolean> = _showCleanProgress.asStateFlow()

    private val _cleanProgress = MutableStateFlow(0)
    val cleanProgress: StateFlow<Int> = _cleanProgress.asStateFlow()

    private val scannedPaths = mutableSetOf<String>()

    private val filterStrArr = arrayOf(
        ".*(/|\\\\)logs(/|\\\\|\$).*",
        ".*(/|\\\\)temp(/|\\\\|\$).*",
        ".*(/|\\\\)temporary(/|\\\\|\$).*",
        ".*(/|\\\\)supersonicads(/|\\\\|\$).*",
        ".*(/|\\\\)cache(/|\\\\|\$).*",
        ".*(/|\\\\)Analytics(/|\\\\|\$).*",
        ".*(/|\\\\)thumbnails?(/|\\\\|\$).*",
        ".*(/|\\\\)mobvista(/|\\\\|\$).*",
        ".*(/|\\\\)UnityAdsVideoCache(/|\\\\|\$).*",
        ".*(/|\\\\)albumthumbs?(/|\\\\|\$).*",
        ".*(/|\\\\)LOST.DIR(/|\\\\|\$).*",
        ".*(/|\\\\)\\.Trash(/|\\\\|\$).*",
        ".*(/|\\\\)desktop.ini(/|\\\\|\$).*",
        ".*(/|\\\\)leakcanary(/|\\\\|\$).*",
        ".*(/|\\\\)\\.DS_Store(/|\\\\|\$).*",
        ".*(/|\\\\)\\.spotlight-V100(/|\\\\|\$).*",
        ".*(/|\\\\)fseventsd(/|\\\\|\$).*",
        ".*(/|\\\\)Bugreport(/|\\\\|\$).*",
        ".*(/|\\\\)bugreports(/|\\\\|\$).*",
        ".*(/|\\\\)splashad(/|\\\\|\$).*",
        ".*(/|\\\\)\\.nomedia(/|\\\\|\$).*",
        ".*\\.xapk\$",
        ".*\\.property\$",
        ".*\\.dat\$",
        ".*\\.cached\$",
        ".*\\.logcat\$",
        ".*\\.download\$",
        ".*\\.part\$",
        ".*\\.crdownload\$",
        ".*\\.thumbnails\$",
        ".*\\.thumbdata\$",
        ".*\\.thumb\$",
        ".*\\.crash\$",
        ".*\\.error\$",
        ".*\\.stacktrace\$",
        ".*\\.bak\$",
        ".*\\.backup\$",
        ".*\\.old\$",
        ".*\\.prev\$",
        ".*\\.apks\$",
        ".*\\.apkm\$",
        ".*\\.idea\$",
        ".*\\.iml\$",
        ".*\\.classpath\$",
        ".*\\.project\$",
        ".*\\.webcache\$",
        ".*\\.indexeddb\$",
        ".*\\.localstorage\$",
        ".*\\.tmp\$",
        ".*\\.log\$",
        ".*\\.temp\$",
        ".*\\.logs\$",
        ".*\\.cache\$",
        ".*\\.apk\$",
        ".*\\.exo\$",
        ".*thumbs?\\.db\$",
        ".*\\.thumb[0-9]\$",
        ".*splashad\$"
    )

    private val filterPatterns = filterStrArr.map { it.toRegex(RegexOption.IGNORE_CASE) }

    fun startScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.SCANNING
            _totalGarbageSize.value = 0L
            scannedPaths.clear()

            // 重置所有分类的文件列表和选中状态
            _categories.value = _categories.value.map { category ->
                category.copy(files = mutableSetOf(), isAllSelected = false) // 同时重置选中状态
            }

            try {
                scanGarbageFiles()
                _scanState.value = ScanState.COMPLETED
            } catch (e: Exception) {
                e.printStackTrace()
                _scanState.value = ScanState.IDLE
            }
        }
    }

    private suspend fun scanGarbageFiles() {
        val scanPaths = getScanPaths()

        for (rootPath in scanPaths) {
            if (rootPath.exists() && rootPath.canRead()) {
                try {
                    _currentScanPath.value = "Scanning: ${rootPath.name}..."
                    scanDirectory(rootPath, 0)
                } catch (e: Exception) {
                    continue
                }
            }
        }

        // 更新总大小
        _totalGarbageSize.value = _categories.value.sumOf { it.totalSize }
        _currentScanPath.value = "Scan completed"
    }

    private fun getScanPaths(): List<File> {
        val paths = mutableListOf<File>()

        try {
            Environment.getExternalStorageDirectory()?.let { paths.add(it) }
            paths.add(context.filesDir) // 应用内部存储目录
            context.externalCacheDir?.let { paths.add(it) } // 外部缓存目录

            val commonJunkPaths = listOf(
                "/storage/emulated/0/Android/data",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/.thumbnails",
                "/storage/emulated/0/Pictures/.thumbnails",
                "/storage/emulated/0/DCIM/.thumbnails",
                "/storage/emulated/0/Temp",
                "/storage/emulated/0/Temporary"
            )

            commonJunkPaths.forEach { pathString ->
                val file = File(pathString)
                if (file.exists() && file.canRead()) {
                    paths.add(file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return paths
    }

    private suspend fun scanDirectory(directory: File, depth: Int) {
        if (depth > 6) return

        val directoryPath = directory.absolutePath
        if (scannedPaths.contains(directoryPath)) return
        scannedPaths.add(directoryPath)

        try {
            val files = directory.listFiles() ?: return

            for (file in files.take(200)) {
                _currentScanPath.value = "Scanning: ${file.name}"

                if (file.isDirectory) {
                    scanDirectory(file, depth + 1)
                } else {
                    checkAndAddJunkFile(file)
                }

                delay(5)
            }
        } catch (e: Exception) {
            // 忽略权限或访问错误
        }
    }

    private suspend fun checkAndAddJunkFile(file: File) {
        val fileName = file.name
        val filePath = file.absolutePath
        val fileSize = file.length()

        if (fileSize < 512) return

        val isJunkFile = filterPatterns.any { pattern ->
            pattern.matches(filePath.replace("\\", "/")) ||
                    pattern.matches(fileName) ||
                    pattern.matches("/" + fileName) ||
                    pattern.matches(filePath.replace("\\", "/") + "/")
        }

        if (!isJunkFile) return

        val junkFile = GarbageFile(file.name, file.absolutePath, fileSize, isSelected = true) // 显式设置选中状态

        val categoryType = when {
            filePath.contains("cache", true) || fileName.endsWith(".cache", true) ||
                    filePath.contains("webview", true) || fileName.endsWith(".db-wal", true) ||
                    fileName.endsWith(".db-shm", true) || fileName.endsWith(".cached", true) ||
                    fileName.endsWith(".webcache", true) || fileName.endsWith(".indexeddb", true) ||
                    fileName.endsWith(".localstorage", true) -> {
                GarbageCategoryType.APP_CACHE
            }

            fileName.endsWith(".apk", true) || fileName.endsWith(".apks", true) ||
                    fileName.endsWith(".apkm", true) || fileName.endsWith(".xapk", true) -> {
                GarbageCategoryType.APK_FILES
            }

            fileName.endsWith(".log", true) || fileName.endsWith(".logs", true) ||
                    fileName.endsWith(".logcat", true) || fileName.endsWith(".crash", true) ||
                    fileName.endsWith(".error", true) || fileName.endsWith(".stacktrace", true) ||
                    fileName.endsWith(".trace", true) -> {
                GarbageCategoryType.LOG_FILES
            }

            filePath.contains("ad", true) || filePath.contains("ads", true) ||
                    filePath.contains("supersonicads", true) || filePath.contains("mobvista", true) ||
                    filePath.contains("UnityAdsVideoCache", true) || filePath.contains("splashad", true) ||
                    fileName.endsWith("splashad", true) || filePath.contains("Analytics", true) ||
                    filePath.contains("bugreport", true) || filePath.contains("bugreports", true) -> {
                GarbageCategoryType.AD_JUNK
            }

            else -> {
                GarbageCategoryType.TEMP_FILES
            }
        }

        _categories.value = _categories.value.map { category ->
            if (category.type == categoryType) {
                val newFiles = category.files.toMutableSet()
                newFiles.add(junkFile)

                val allSelected = newFiles.isNotEmpty() && newFiles.all { it.isSelected }

                category.copy(files = newFiles, isAllSelected = allSelected)
            } else {
                category
            }
        }
    }
    fun toggleCategoryExpansion(categoryType: GarbageCategoryType) {
        _categories.value = _categories.value.map { category ->
            if (category.type == categoryType) {
                category.copy(isExpanded = !category.isExpanded)
            } else {
                category
            }
        }
    }


    fun toggleFileSelection(categoryType: GarbageCategoryType, filePath: String) {
        _categories.value = _categories.value.map { category ->
            if (category.type == categoryType) {
                val filesList = category.files.toList()
                val updatedFiles = filesList.map { file ->
                    if (file.path == filePath) {
                        Log.e("TAG", "toggleFileSelection-1--: ${file.isSelected}")
                        file.copy(isSelected = !file.isSelected)
                    } else {
                        file
                    }
                }.toMutableSet()

                val allSelected = updatedFiles.isNotEmpty() && updatedFiles.all { it.isSelected }
                Log.e("TAG", "toggleFileSelection: ---updatedFiles.isNotEmpty()=${updatedFiles.isNotEmpty()}----${updatedFiles.all { it.isSelected }}")
                category.copy(files = updatedFiles, isAllSelected = allSelected)
            } else {
                category
            }
        }
    }

    fun toggleCategorySelection(categoryType: GarbageCategoryType) {
        _categories.value = _categories.value.map { category ->
            if (category.type == categoryType) {
                val hasSelectedFiles = category.files.any { it.isSelected }
                val newSelectionState = !hasSelectedFiles

                val updatedFiles = category.files.map { file ->
                    file.copy(isSelected = newSelectionState)
                }.toMutableSet()
                Log.e("TAG", "toggleCategorySelection: $newSelectionState")

                category.copy(files = updatedFiles, isAllSelected = newSelectionState)
            } else {
                category
            }
        }
    }

    fun cleanSelectedFiles() {
        viewModelScope.launch {
            _showCleanProgress.value = true
            _cleanProgress.value = 0

            val allSelectedFiles = _categories.value.flatMap { category ->
                category.files.filter { it.isSelected }
            }

            var totalDeletedSize = 0L
            var deletedCount = 0

            allSelectedFiles.forEachIndexed { index, file ->
                delay(50)
                _cleanProgress.value = ((index + 1) * 100 / allSelectedFiles.size)

                withContext(Dispatchers.IO) {
                    try {
                        val fileToDelete = File(file.path)
                        if (fileToDelete.exists() && fileToDelete.delete()) {
                            totalDeletedSize += file.size
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            _categories.value = _categories.value.map { category ->
                val remainingFiles = category.files.filter { !it.isSelected }.toMutableSet()
                category.copy(files = remainingFiles, isAllSelected = false, isExpanded = false)
            }

            _totalGarbageSize.value = _categories.value.sumOf { it.totalSize }

            delay(500)
            _showCleanProgress.value = false
            _cleanProgress.value = 0

            if (deletedCount > 0) {
                val (sizeFormatted, unit) = formatFileSize(totalDeletedSize)
                val cleanedSizeText = "$sizeFormatted $unit"

                val intent = Intent(context, FinishActivity::class.java).apply {
                    putExtra("cleaned_size", cleanedSizeText)
                }
                context.startActivity(intent)
                context.finish()
            } else {
                val intent = Intent(context, FinishActivity::class.java).apply {
                    putExtra("cleaned_size", "")
                }
                context.startActivity(intent)
                context.finish()
            }
        }
    }

    fun hasSelectedFiles(): Boolean {
        return _categories.value.any { category ->
            category.files.any { it.isSelected }
        }
    }
}

fun formatFileSize(bytes: Long): Pair<String, String> {
    if (bytes <= 0) return Pair("0", "MB")

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val size = bytes / 1024.0.pow(digitGroups.toDouble())

    val formatter = DecimalFormat("#,##0.#")
    return Pair(formatter.format(size), units[digitGroups])
}

class GarbageCleanActivity : ComponentActivity() {
    private val viewModel: GarbageCleanViewModel by lazy {
        GarbageCleanViewModel(this) // 传入context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GarbageCleanScreen(
                viewModel = viewModel,
                onBackPressed = { finish() },
                onStartScan = {
                    viewModel.startScan()
                }
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarbageCleanScreen(
    viewModel: GarbageCleanViewModel,
    onBackPressed: () -> Unit,
    onStartScan: () -> Unit
) {
    val scanState by viewModel.scanState.collectAsState()
    val currentScanPath by viewModel.currentScanPath.collectAsState()
    val totalGarbageSize by viewModel.totalGarbageSize.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val showCleanProgress by viewModel.showCleanProgress.collectAsState()
    val cleanProgress by viewModel.cleanProgress.collectAsState()

    LaunchedEffect(Unit) {
        onStartScan()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部背景区域
            GarbageCleanHeader(
                scanState = scanState,
                currentScanPath = currentScanPath,
                totalGarbageSize = totalGarbageSize,
                onBackPressed = onBackPressed
            )

            // 垃圾分类列表
            GarbageCategoriesList(
                categories = categories,
                scanState = scanState,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )

            // 清理按钮
            AnimatedVisibility(
                visible = scanState == ScanState.COMPLETED && viewModel.hasSelectedFiles(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(bottom = 40.dp)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    CleanButton(
                        onClick = { viewModel.cleanSelectedFiles() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showCleanProgress) {
            NewScanDialog(
                progress = cleanProgress,
                title = "Cleaning...",
                onCancel = { }
            )
        }
    }
}

@Composable
fun GarbageCleanHeader(
    scanState: ScanState,
    currentScanPath: String,
    totalGarbageSize: Long,
    onBackPressed: () -> Unit
) {
    val (sizeText, unit) = formatFileSize(totalGarbageSize)
    val hasJunk = totalGarbageSize > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // 背景图片
        Image(
            painter = painterResource(
                id = if (hasJunk) R.drawable.bg_junk else R.drawable.bg_no_junk
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 返回按钮和标题
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when (scanState) {
                    ScanState.SCANNING -> "Scanning..."
                    ScanState.COMPLETED -> if (hasJunk) "Junk Found" else "No Junk"
                    else -> "Ready to Scan"
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 16.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // 大小显示
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = sizeText,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit,
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 扫描信息
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
        ) {
            if (scanState == ScanState.SCANNING) {
                Text(
                    text = currentScanPath,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, bottom = 32.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Color(0xFF63ADF8),
                    trackColor = Color(0xFFDBE8FF)
                )
            }
        }
    }
}

@Composable
fun GarbageCategoriesList(
    categories: List<GarbageCategory>,
    scanState: ScanState,
    viewModel: GarbageCleanViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 16.dp)
    ) {
        items(categories) { category ->
            GarbageCategoryItem(
                category = category,
                scanState = scanState,
                onToggleExpansion = { viewModel.toggleCategoryExpansion(category.type) },
                onToggleFileSelection = { filePath ->
                    viewModel.toggleFileSelection(category.type, filePath)
                },
                onToggleCategorySelection = { viewModel.toggleCategorySelection(category.type) }
            )
        }
    }
}

@Composable
fun GarbageCategoryItem(
    category: GarbageCategory,
    scanState: ScanState,
    onToggleExpansion: () -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onToggleCategorySelection: () -> Unit
) {
    Column {
        // 分类头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (category.files.isNotEmpty()) onToggleExpansion() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = category.icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = category.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(start = 12.dp)
            )

            if (category.files.isNotEmpty()) {
                Icon(
                    painter = painterResource(
                        id = if (category.isExpanded) R.drawable.ic_bottom else R.drawable.ic_right_2
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                    tint = Color.Gray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val (sizeText, unit) = formatFileSize(category.totalSize)
            Text(
                text = "$sizeText$unit",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(end = 8.dp)
            )

            if (category.files.isNotEmpty()) {
                val iconResource = if (category.isAllSelected || category.hasSelectedFiles) {
                    R.drawable.ic_selected
                } else {
                    R.drawable.ic_dis_selected
                }

                Image(
                    painter = painterResource(id = iconResource),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onToggleCategorySelection() }
                )
            }
        }

        // 分割线
        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = Color(0xFFE0E0E0)
        )

        // 展开的文件列表
        AnimatedVisibility(
            visible = category.isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                category.filesList.take(50).forEach { file -> // 限制显示数量
                    GarbageFileItem(
                        file = file,
                        onToggleSelection = { onToggleFileSelection(file.path) }
                    )
                }

                if (category.files.size > 50) {
                    Text(
                        text = "... and ${category.files.size - 50} more files",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 52.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun GarbageFileItem(
    file: GarbageFile,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(start = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.path,
                fontSize = 12.sp,
                color = Color(0xFF999999),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        val (sizeText, unit) = formatFileSize(file.size)
        Text(
            text = "$sizeText$unit",
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(end = 8.dp)
        )

        Image(
            painter = painterResource(
                id = if (file.isSelected) R.drawable.ic_selected
                else R.drawable.ic_dis_selected
            ),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .clickable { onToggleSelection() }
        )
    }
}

@Composable
fun CleanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E8EFF)
        )
    ) {
        Text(
            text = "Clean",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

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
            // Back button (only show for scanning, not for cleaning)
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