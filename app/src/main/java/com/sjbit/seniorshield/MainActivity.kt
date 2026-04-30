package com.sjbit.seniorshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sjbit.seniorshield.ui.SeniorShieldApp
import com.sjbit.seniorshield.ui.SeniorShieldViewModel

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()

        val sharedText = intent.extractSharedText()
        val viewModel = SeniorShieldViewModel(applicationContext, sharedText)

        setContent {
            SeniorShieldApp(viewModel = viewModel)
        }
    }

    private fun requestRuntimePermissions() {
        val smsPermissions = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        ).filterNot(::hasPermission)

        if (smsPermissions.isNotEmpty()) {
            smsPermissionLauncher.launch(smsPermissions.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Intent?.extractSharedText(): String? {
    if (this?.action != Intent.ACTION_SEND) return null
    return getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
}
