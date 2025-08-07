package com.kite.folding.pape.rmobileclean

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.kite.folding.pape.rmobileclean.databinding.ActivityActiveBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActiveActivity : AppCompatActivity() {
    val binding by lazy { ActivityActiveBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.active)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback {
        }
        startCountdown()
    }

    private fun startCountdown() {
        lifecycleScope.launch {
            delay(2100L)
            startActivity(Intent(this@ActiveActivity, MainActivity::class.java))
            finish()
        }

    }
}