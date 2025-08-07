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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

class NetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetScreen(
                onBackClick = { finish() },
                onShareClick = { shareApp() },
                onPrivacyPolicyClick = { openPrivacyPolicy() }
            )
        }
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${packageName}")
        }
        try {
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (ex: Exception) {
            // Handle error
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://sites.google.com/view/brights-cleaner/home".toUri()
        }
        startActivity(intent)
    }
}

@Composable
fun NetScreen(
    onBackClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F5FC))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Image(
                painter = painterResource(id = R.drawable.ic_back_b),
                contentDescription = "Back",
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(8.dp)
            )

            // Title centered in remaining space
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Settings",
                    fontSize = 15.sp,
                    color = Color(0xFF0A100F),
                    fontWeight = FontWeight.Medium
                )
            }

            // Spacer to balance the back button
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Share menu item
        SettingsMenuItem(
            icon = R.drawable.ic_share,
            text = "Share",
            onClick = onShareClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy policy menu item
        SettingsMenuItem(
            icon = R.drawable.ic_pp,
            text = "Privacy policy",
            onClick = onPrivacyPolicyClick
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start icon
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Text
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF0A100F),
            modifier = Modifier.weight(1f)
        )

        // End arrow icon
        Image(
            painter = painterResource(id = R.drawable.ic_vector),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NetScreenPreview() {
    NetScreen()
}