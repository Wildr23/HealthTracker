package com.gymtracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gymtracker.app.data.db.ExerciseCatalogSeeder
import com.gymtracker.app.data.prefs.AppThemeMode
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.ui.navigation.GymTrackerApp
import com.gymtracker.app.ui.theme.GymTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var seeder: ExerciseCatalogSeeder

    @Inject
    lateinit var prefsRepo: UserPreferencesRepository

    private var openRouteState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRouteState = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        lifecycleScope.launch {
            seeder.ensureSeeded()
        }
        setContent {
            val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = when (prefs.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            GymTrackerTheme(darkTheme = darkTheme) {
                GymTrackerApp(
                    openRoute = openRouteState,
                    onOpenRouteHandled = { openRouteState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRouteState = intent.getStringExtra(EXTRA_OPEN_ROUTE)
    }

    companion object {
        const val EXTRA_OPEN_ROUTE = "open_route"
    }
}
