package com.kinandcarta.create.proxytoggle.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile
import com.kinandcarta.create.proxytoggle.core.ui.theme.ProxyToggleTheme
import com.kinandcarta.create.proxytoggle.manager.view.screen.BlockAppScreen
import com.kinandcarta.create.proxytoggle.manager.view.screen.ProfileEditorScreen
import com.kinandcarta.create.proxytoggle.manager.view.screen.ProfileListScreen
import com.kinandcarta.create.proxytoggle.manager.view.screen.ProxyManagerScreen
import com.kinandcarta.create.proxytoggle.manager.viewmodel.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val useDarkTheme by viewModel.useDarkTheme.collectAsState()

            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val heightSizeClass = calculateWindowSizeClass(this).heightSizeClass
            val useVerticalLayout = heightSizeClass != WindowHeightSizeClass.Compact

            MainScreen(useDarkTheme = useDarkTheme, useVerticalLayout = useVerticalLayout)
        }
    }
}

private enum class Screen {
    MAIN, PROFILE_LIST, PROFILE_EDITOR
}

@Composable
private fun MainScreen(useDarkTheme: Boolean, useVerticalLayout: Boolean) {
    val showBlockDialog = ContextCompat.checkSelfPermission(
        LocalContext.current,
        Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_DENIED

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var editingProfile by remember { mutableStateOf<ProxyProfile?>(null) }

    ProxyToggleTheme(darkTheme = useDarkTheme) {
        if (showBlockDialog) {
            BlockAppScreen()
        } else {
            when (currentScreen) {
                Screen.MAIN -> {
                    ProxyManagerScreen(
                        useVerticalLayout = useVerticalLayout,
                        onNavigateToProfiles = { currentScreen = Screen.PROFILE_LIST }
                    )
                }
                Screen.PROFILE_LIST -> {
                    val profileViewModel: ProfileViewModel = hiltViewModel()
                    ProfileListScreen(
                        viewModel = profileViewModel,
                        onNavigateBack = { currentScreen = Screen.MAIN },
                        onEditProfile = { profile ->
                            editingProfile = profile
                            currentScreen = Screen.PROFILE_EDITOR
                        },
                        onCreateProfile = {
                            editingProfile = null
                            currentScreen = Screen.PROFILE_EDITOR
                        },
                        onConnectProfile = { profile ->
                            profileViewModel.connectWithProfile(profile)
                            currentScreen = Screen.MAIN
                        }
                    )
                }
                Screen.PROFILE_EDITOR -> {
                    val profileViewModel: ProfileViewModel = hiltViewModel()
                    ProfileEditorScreen(
                        existingProfile = editingProfile,
                        onNavigateBack = { currentScreen = Screen.PROFILE_LIST },
                        onSave = { profile ->
                            profileViewModel.saveProfile(profile)
                            currentScreen = Screen.PROFILE_LIST
                        }
                    )
                }
            }
        }
    }
}
