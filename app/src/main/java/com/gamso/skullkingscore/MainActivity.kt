package com.gamso.skullkingscore

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gamso.skullkingscore.data.backup.AutoBackupManager
import com.gamso.skullkingscore.domain.repository.SettingsRepository
import com.gamso.skullkingscore.ui.components.LocalKeepScreenOnSetting
import com.gamso.skullkingscore.ui.navigation.SkullKingNavHost
import com.gamso.skullkingscore.ui.theme.SkullKingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var autoBackup: AutoBackupManager

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { autoBackup.runIfDue() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            // System bar icons follow the system theme: dark icons on a light theme, light icons on a dark one.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val appSettings by settings.settings.collectAsStateWithLifecycle()
            SkullKingTheme {
                CompositionLocalProvider(LocalKeepScreenOnSetting provides appSettings.keepScreenOn) {
                    SkullKingNavHost()
                }
            }
        }
    }
}
