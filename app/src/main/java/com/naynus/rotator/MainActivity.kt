package com.naynus.rotator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.naynus.rotator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.buttonGrantWriteSettings.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.buttonToggleService.setOnClickListener {
            if (RotationMonitorService.isRunning) {
                stopService(Intent(this, RotationMonitorService::class.java))
            } else {
                if (hasOverlayPermission() && hasWriteSettingsPermission()) {
                    startForegroundService(Intent(this, RotationMonitorService::class.java))
                }
            }
            refreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun hasWriteSettingsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)

    private fun isAutoRotateOn(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun refreshStatus() {
        val overlayGranted = hasOverlayPermission()
        val writeSettingsGranted = hasWriteSettingsPermission()

        binding.textOverlayStatus.setText(
            if (overlayGranted) R.string.status_overlay_granted else R.string.status_overlay_missing
        )
        binding.textWriteSettingsStatus.setText(
            if (writeSettingsGranted) R.string.status_write_settings_granted else R.string.status_write_settings_missing
        )
        binding.textAutoRotateStatus.setText(
            if (isAutoRotateOn()) R.string.status_auto_rotate_on else R.string.status_auto_rotate_off
        )
        binding.textServiceStatus.setText(
            if (RotationMonitorService.isRunning) R.string.status_service_running else R.string.status_service_stopped
        )
        binding.buttonToggleService.setText(
            if (RotationMonitorService.isRunning) R.string.action_stop_service else R.string.action_start_service
        )
        binding.buttonToggleService.isEnabled = RotationMonitorService.isRunning || (overlayGranted && writeSettingsGranted)
        binding.textPermissionsHint.visibility =
            if (!overlayGranted || !writeSettingsGranted) android.view.View.VISIBLE else android.view.View.GONE
    }
}
