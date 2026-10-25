package com.skullking.score

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
import com.skullking.score.data.backup.AutoBackupManager
import com.skullking.score.domain.repository.SettingsRepository
import com.skullking.score.ui.components.LocalKeepScreenOnSetting
import com.skullking.score.ui.navigation.SkullKingNavHost
import com.skullking.score.ui.theme.SkullKingTheme
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
            // Les icônes des barres système doivent suivre le thème choisi dans l'app, pas seulement celui du système.
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
