package com.kite.folding.pape.rmobileclean

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kite.folding.pape.rmobileclean.databinding.ActivityMainBinding
import com.kite.folding.pape.rmobileclean.file.FileScanComposeActivity
import com.kite.folding.pape.rmobileclean.img.PicCleanComposeActivity
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // 权限请求码
    private val PERMISSION_REQUEST_CODE = 100
    var jumpType = 0 //0:clean 1:img 2:file

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startGarbageCleanActivity()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        updateStorageInfo()
    }

    private fun initViews() {
        binding.imgSetting.setOnClickListener {
            startActivity(Intent(this, NetActivity::class.java))
        }
        binding.tvClean.setOnClickListener {
            jumpType = 0
            checkPermissionAndStartClean()
        }
        binding.atvImg.setOnClickListener {
            jumpType = 1
            checkPermissionAndStartClean()
        }
        binding.atvFile.setOnClickListener {
            jumpType = 2
            checkPermissionAndStartClean()
        }

        binding.tvCancel.setOnClickListener {
            hidePermissionDialog()
        }

        binding.tvYes.setOnClickListener {
            hidePermissionDialog()
            requestStoragePermission()
        }
    }

    private fun getTotalStorageSpace(): Long {
        return try {
            val storageStatsManager =
                getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid = StorageManager.UUID_DEFAULT
            storageStatsManager.getTotalBytes(uuid)
        } catch (e: Exception) {
            e.printStackTrace()
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        }
    }


    private fun getAvailableStorageSpace(): Long {
        return try {
            val storageStatsManager =
                getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid = StorageManager.UUID_DEFAULT
            storageStatsManager.getFreeBytes(uuid)
        } catch (e: Exception) {
            e.printStackTrace()
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }
    }
    private fun updateStorageInfo() {
        try {
            val totalBytes = getTotalStorageSpace()
            val availableBytes = getAvailableStorageSpace()
            val usedBytes = totalBytes - availableBytes

            val totalFormatted = formatStorageSize(totalBytes)
            val usedFormatted = formatStorageSize(usedBytes)

            val usedPercentage = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

            binding.tvStorageTo.text = " of $totalFormatted"
            binding.tvStorageUse.text = usedFormatted
            binding.tvProgressNum.text = usedPercentage.toString()
            binding.pc.progress = usedPercentage

            Log.d("StorageInfo", "Total: $totalFormatted, Used: $usedFormatted, Percentage: $usedPercentage%")

        } catch (e: Exception) {
            Log.e("StorageInfo", "Error getting storage info", e)
            binding.tvStorageTo.text = " of 128GB"
            binding.tvStorageUse.text = "100GB"
            binding.tvProgressNum.text = "78"
            binding.pc.progress = 78
        }
    }


    private fun formatStorageSize(bytes: Long): String {
        val df = DecimalFormat("#.#")

        return when {
            bytes >= 1000 * 1000 * 1000 -> {
                val gb = bytes / (1000.0 * 1000.0 * 1000.0)
                "${df.format(gb)}GB"
            }
            bytes >= 1000 * 1000 -> {
                val mb = bytes / (1000.0 * 1000.0)
                "${df.format(mb)}MB"
            }
            else -> {
                val kb = bytes / 1000.0
                "${df.format(kb)}KB"
            }
        }
    }


    private fun checkPermissionAndStartClean() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    startGarbageCleanActivity()
                } else {
                    showPermissionDialog()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                if (hasLegacyStoragePermissions()) {
                    startGarbageCleanActivity()
                } else {
                    showPermissionDialog()
                }
            }
            else -> {
                startGarbageCleanActivity()
            }
        }
    }


    private fun hasLegacyStoragePermissions(): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED
    }


    private fun showPermissionDialog() {
        binding.llDialog.visibility = View.VISIBLE
    }


    private fun hidePermissionDialog() {
        binding.llDialog.visibility = View.GONE
    }


    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStoragePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStoragePermissionLauncher.launch(intent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startGarbageCleanActivity()
            } else {
                if (permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                    showPermissionRationaleDialog()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }
    }


    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Storage permissions are required")
            .setMessage("In order to scan and clean junk files, you need to grant storage access.")
            .setPositiveButton("Reauthorization") { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission denied")
            .setMessage("Storage permissions are denied and junk files cannot be scanned. Please manually go to the settings to enable permissions.")
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening app settings", e)
        }
    }


    private fun startGarbageCleanActivity() {
        when(jumpType){
            0 -> {
                val intent = Intent(this, GarbageCleanActivity::class.java)
                startActivity(intent)
            }
            1 -> {
                val intent = Intent(this, PicCleanComposeActivity::class.java)
                startActivity(intent)
            }
            2 -> {
                val intent = Intent(this, FileScanComposeActivity::class.java)
                startActivity(intent)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        updateStorageInfo()
    }
}