package dev.mangelov.tune

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val model: TunerViewModel by viewModels()
    private var requested: Boolean
        get() = getPreferences(MODE_PRIVATE).getBoolean("permission_requested", false)
        set(value) { getPreferences(MODE_PRIVATE).edit { putBoolean("permission_requested", value) } }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.start()
        else model.needsPermission(requested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        setContent {
            TunerScreen(model, onAllow = { requested = true; permission.launch(Manifest.permission.RECORD_AUDIO) },
                onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) })
        }
    }
    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.start()
        else model.needsPermission(requested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
    }
    override fun onStop() { if (!isChangingConfigurations) model.stop(); super.onStop() }
}
