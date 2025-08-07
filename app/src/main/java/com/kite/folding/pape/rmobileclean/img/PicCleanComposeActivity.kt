package com.kite.folding.pape.rmobileclean.img

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.kite.folding.pape.rmobileclean.FinishActivity
import com.kite.folding.pape.rmobileclean.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class PicCleanComposeActivity : ComponentActivity() {
    private val photoGroups = mutableListOf<PhotoGroup>()
    private val decimalFormat = DecimalFormat("#.#")
    private var jumpJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PicCleanScreen(
                onBack = { finish() },
                onDeletePhotos = { photosToDelete -> deleteSelectedPhotos(photosToDelete) }
            )
        }
    }

    @Composable
    fun PicCleanScreen(
        onBack: () -> Unit,
        onDeletePhotos: (List<PhotoItem>) -> Unit
    ) {
        var photoGroups by remember { mutableStateOf(listOf<PhotoGroup>()) }
        var isAllSelected by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var scanProgress by remember { mutableStateOf(0) }
        var scanningText by remember { mutableStateOf("Scanning...") }
        var isScanning by remember { mutableStateOf(true) }

        LaunchedEffect(isScanning) {
            if (isScanning) {
                while (scanProgress < 100) {
                    delay(15)
                    scanProgress++
                }
                isScanning = false
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val photos = getPhotosFromDevice()
                val groups = groupPhotosByDate(photos)
                withContext(Dispatchers.Main) {
                    photoGroups = groups
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4FAFF))
        ) {
            if (isLoading || isScanning) {
                LoadingScreen(
                    progress = scanProgress,
                    text = scanningText,
                    onBack = {
                        jumpJob?.cancel()
                        isScanning = false
                        isLoading = false
                    }
                )
            } else {
                MainContent(
                    photoGroups = photoGroups,
                    isAllSelected = isAllSelected,
                    onBack = onBack,
                    onPhotoClick = { photo, groupIndex, photoIndex ->
                        Log.e("TAG", "onPhotoClick: ${photoIndex}")
                        photoGroups = photoGroups.mapIndexed { index, group ->
                            if (index == groupIndex) {
                                val updatedPhotos = group.photos.mapIndexed { pIndex, p ->
                                    if (pIndex == photoIndex) {
                                        p.copy(isSelected = !p.isSelected)
                                    } else {
                                        p
                                    }
                                }.toMutableList()

                                val allSelected = updatedPhotos.all { it.isSelected }
                                group.copy(
                                    photos = updatedPhotos,
                                    isAllSelected = allSelected
                                )
                            } else {
                                group
                            }
                        }
                        isAllSelected = photoGroups.all { group -> group.photos.all { it.isSelected } }
                    },

                    onGroupSelectAll = { group, groupIndex ->
                        Log.e("TAG", "onGroupSelectAll: ${groupIndex}")
                        photoGroups = photoGroups.mapIndexed { index, g ->
                            if (index == groupIndex) {
                                val newState = !g.isAllSelected
                                val updatedPhotos = g.photos.map { photo ->
                                    photo.copy(isSelected = newState)
                                }.toMutableList()

                                g.copy(
                                    photos = updatedPhotos,
                                    isAllSelected = newState
                                )
                            } else {
                                g
                            }
                        }
                        isAllSelected = photoGroups.all { group -> group.photos.all { it.isSelected } }
                    },
                    onSelectAll = {
                        Log.e("TAG", "onSelectAll: ${isAllSelected}", )

                        isAllSelected = !isAllSelected
                        photoGroups = photoGroups.map { group ->
                            group.copy(
                                isAllSelected = isAllSelected,
                                photos = group.photos.map { it.copy(isSelected = isAllSelected) }.toMutableList()
                            )
                        }
                    },
                    onDelete = {
                        val selectedPhotos = photoGroups.flatMap { group ->
                            group.photos.filter { it.isSelected }
                        }
                        if (selectedPhotos.isNotEmpty()) {
                            showDeleteConfirmDialog(selectedPhotos) { photosToDelete ->
                                isScanning = true
                                scanProgress = 0
                                scanningText = "Cleaning..."
                                onDeletePhotos(photosToDelete)
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun LoadingScreen(
        progress: Int,
        text: String,
        onBack: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4A90E2),
                            Color(0xFF2E5BBA)
                        )
                    )
                )
        ) {
            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // 中心内容
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                // 进度圆环
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(144.dp),
                        color = Color(0xFF2EE5A5),
                        strokeWidth = 12.dp,
                        trackColor = Color.Transparent,
                    )

                    // 内部背景圆
                    Box(
                        modifier = Modifier
                            .size(114.dp)
                            .background(
                                Color(0xFFFFFFFF),
                                RoundedCornerShape(57.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_img),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    fun MainContent(
        photoGroups: List<PhotoGroup>,
        isAllSelected: Boolean,
        onBack: () -> Unit,
        onPhotoClick: (PhotoItem, Int, Int) -> Unit,
        onGroupSelectAll: (PhotoGroup, Int) -> Unit,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) {
        val selectedPhotos = photoGroups.flatMap { group -> group.photos.filter { it.isSelected } }
        val totalSelectedSize = selectedPhotos.sumOf { it.size }
        val (size, unit) = formatStorage(totalSelectedSize)

        Column(modifier = Modifier.fillMaxSize()) {
            // 头部区域
            HeaderSection(
                size = size,
                unit = unit,
                fileText = when {
                    selectedPhotos.isEmpty() -> "No images selected"
                    selectedPhotos.size == 1 -> "1 image selected"
                    else -> "${selectedPhotos.size} images selected"
                },
                onBack = onBack
            )

            // 照片列表
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                photoGroups.forEachIndexed { groupIndex, group ->
                    // 组头部
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader(
                            group = group,
                            onSelectAll = { onGroupSelectAll(group, groupIndex) }
                        )
                    }

                    // 照片项
                    itemsIndexed(group.photos) { photoIndex, photo ->
                        PhotoItem(
                            photo = photo,
                            onClick = { onPhotoClick(photo, groupIndex, photoIndex) }
                        )
                    }
                }
            }

            // 底部控制区域
            BottomSection(
                isAllSelected = isAllSelected,
                hasSelectedPhotos = selectedPhotos.isNotEmpty(),
                onSelectAll = onSelectAll,
                onDelete = onDelete
            )
        }
    }

    @Composable
    fun HeaderSection(
        size: String,
        unit: String,
        fileText: String,
        onBack: () -> Unit
    ) {
        Box(
            modifier = Modifier.height(200.dp)
        ) {
            // 背景图片
            Image(
                painter = painterResource(id = R.drawable.ic_pic_top),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // 标题
            Text(
                text = "Image",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )

            // 中心数字显示
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = size,
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 8.dp)
                )
            }

            // 底部文件信息
            Text(
                text = fileText,
                color = Color(0xFFEAFFEB),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }

    @Composable
    fun GroupHeader(
        group: PhotoGroup,
        onSelectAll: () -> Unit
    ) {
        val (size, unit) = formatStorage(group.getTotalSize())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = group.date,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Text(
                    text = "$size $unit",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onSelectAll() }
                    .padding(8.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = if (group.isAllSelected) R.drawable.ic_selected else R.drawable.ic_dis_selected
                    ),
                    contentDescription = "Select All",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Select All",
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    @Composable
    fun PhotoItem(
        photo: PhotoItem,
        onClick: () -> Unit
    ) {
        val (size, unit) = formatStorage(photo.size)

        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
        ) {
            // 照片图片
            AsyncImage(
                model = photo.path,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_liebiaoweikong),
                error = painterResource(id = R.drawable.ic_liebiaoweikong)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .clickable { onClick() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = if (photo.isSelected) R.drawable.ic_selected else R.drawable.ic_dis_selected
                    ),
                    contentDescription = "Select",
                    modifier = Modifier.size(16.dp)
                )
            }

            // 文件大小
            Text(
                text = "$size $unit",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }

    @Composable
    fun BottomSection(
        isAllSelected: Boolean,
        hasSelectedPhotos: Boolean,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelectAll() }
            ) {
                Image(
                    painter = painterResource(
                        id = if (isAllSelected) R.drawable.ic_selected else R.drawable.ic_dis_selected
                    ),
                    contentDescription = "Select All",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Select All",
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                )
            }

            Spacer(modifier = Modifier.width(43.dp))

            // 删除按钮
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (hasSelectedPhotos) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF62AFFF),
                                        Color(0xFF2F8EFF)
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
                            shape = RoundedCornerShape(32.dp)
                        ),

                    contentAlignment = Alignment.Center

                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }


    private fun showDeleteConfirmDialog(
        selectedPhotos: List<PhotoItem>,
        onConfirm: (List<PhotoItem>) -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Delete Photos")
            .setMessage("Are you sure you want to delete ${selectedPhotos.size} selected photos? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                onConfirm(selectedPhotos)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getPhotosFromDevice(): List<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Images.Media.SIZE} > ?"
        val selectionArgs = arrayOf("0")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val path = it.getString(dataColumn)
                val size = it.getLong(sizeColumn)
                val dateAdded = it.getLong(dateColumn)
                val displayName = it.getString(nameColumn)

                if (File(path).exists()) {
                    photos.add(PhotoItem(id, path, size, dateAdded, displayName))
                }
            }
        }

        return photos
    }

    private fun groupPhotosByDate(photos: List<PhotoItem>): List<PhotoGroup> {
        val photoGroups = mutableListOf<PhotoGroup>()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val yesterdayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val today = todayFormat.format(Date())
        val yesterday = yesterdayFormat.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

        val groupedPhotos = photos.groupBy { photo ->
            val date = Date(photo.dateAdded * 1000)
            dateFormat.format(date)
        }

        for ((dateString, photoList) in groupedPhotos) {
            val displayDate = when (dateString) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> {
                    val date = dateFormat.parse(dateString)
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
                }
            }

            photoGroups.add(PhotoGroup(displayDate, photoList.toMutableList()))
        }

        return photoGroups
    }

    private fun deleteSelectedPhotos(photosToDelete: List<PhotoItem>) {
        var deletedCount = 0
        var deletedSize = 0L
        jumpJob = lifecycleScope.launch(Dispatchers.Main) {
            var progress = 0
            withContext(Dispatchers.IO) {
                photosToDelete.forEach { photo ->
                    try {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            photo.id
                        )

                        val deleted = contentResolver.delete(uri, null, null)
                        if (deleted > 0) {
                            deletedCount++
                            deletedSize += photo.size

                            photoGroups.forEach { group ->
                                group.photos.removeAll { it.id == photo.id }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                photoGroups.removeAll { it.photos.isEmpty() }
            }

            while (progress < 100) {
                delay(15)
                progress++
            }

            val (sizeFormatted, unit) = formatFileSize(deletedSize)
            val cleanedSizeText = "$sizeFormatted $unit"

            val intent = Intent(this@PicCleanComposeActivity, FinishActivity::class.java).apply {
                putExtra("cleaned_size", cleanedSizeText)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun formatStorage(bytes: Long): Pair<String, String> {
        return when {
            bytes >= 1024 * 1024 * 1024 -> {
                val gb = bytes.toDouble() / (1024 * 1024 * 1024)
                Pair(decimalFormat.format(gb), "GB")
            }
            bytes >= 1024 * 1024 -> {
                val mb = bytes.toDouble() / (1024 * 1024)
                Pair(decimalFormat.format(mb), "MB")
            }
            bytes >= 1024 -> {
                val kb = bytes.toDouble() / 1024
                Pair(decimalFormat.format(kb), "KB")
            }
            else -> {
                Pair(bytes.toString(), "B")
            }
        }
    }

    private fun formatFileSize(bytes: Long): Pair<String, String> {
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
}