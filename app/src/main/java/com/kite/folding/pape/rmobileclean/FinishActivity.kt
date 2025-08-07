package com.kite.folding.pape.rmobileclean

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kite.folding.pape.rmobileclean.file.FileScanComposeActivity
import com.kite.folding.pape.rmobileclean.img.PicCleanComposeActivity
import java.text.DecimalFormat

class FinishActivity : ComponentActivity() {

    private val decimalFormat = DecimalFormat("#.#")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            FinishScreen(
                cleanedSize = intent.getStringExtra("cleaned_size") ?: "",
                onBackClick = ::navigateToMain,
                onPictureCleanClick = ::startPictureClean,
                onFileCleanClick = ::startFileClean,
                onJunkCleanClick = ::startScanClean
            )
        }
    }

    private fun startPictureClean() {
         val intent = Intent(this, PicCleanComposeActivity::class.java)
         startActivity(intent)
         finish()
    }

    private fun startFileClean() {
        val intent = Intent(this, FileScanComposeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun startScanClean() {
        val intent = Intent(this, GarbageCleanActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishScreen(
    cleanedSize: String,
    onBackClick: () -> Unit,
    onPictureCleanClick: () -> Unit,
    onFileCleanClick: () -> Unit,
    onJunkCleanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF7FF))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // 顶部绿色区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(203.dp)
                .background(Color(0xFF00CC73))
        ) {
            // 返回按钮
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back_b),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // 标题
            Text(
                text = "Result",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )

            // 中央内容
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 20.dp)
            ) {
                // 完成图标
                Image(
                    painter = painterResource(id = R.drawable.ic_finish_top),
                    contentDescription = "Finish",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // CLEAN FINISHED 文本
                Text(
                    text = "CLEAN FINISHED",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 保存空间文本
                Text(
                    text = if (cleanedSize.isBlank()) {
                        "No junk files found to clean"
                    } else {
                        "Saved $cleanedSize space for you"
                    },
                    color = Color(0xFFEAFFEB),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 清理选项列表
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Picture Clean
            CleanOptionCard(
                icon = R.drawable.ic_img,
                title = "Picture Clean",
                onMainClick = onPictureCleanClick,
                onCleanClick = onPictureCleanClick
            )

            // File Clean
            CleanOptionCard(
                icon = R.drawable.ic_file,
                title = "File Clean",
                onMainClick = onFileCleanClick,
                onCleanClick = onFileCleanClick
            )


            // Junk Clean
            CleanOptionCard(
                icon = R.drawable.ic_clean_logo,
                title = "Clean",
                onMainClick = onJunkCleanClick,
                onCleanClick = onJunkCleanClick
            )
        }
    }
}

@Composable
fun CleanOptionCard(
    icon: Int,
    title: String,
    onMainClick: () -> Unit,
    onCleanClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMainClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp)
            )

            // 标题
            Text(
                text = title,
                color = Color(0xFF0A0C10),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Clean 按钮
            Button(
                onClick = onCleanClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF20C0FD)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Clean",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}