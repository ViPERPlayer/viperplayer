package com.viperplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.*
import com.viperplayer.presentation.ViperPlayerApp
import com.viperplayer.presentation.common.MiniPlayer
import com.viperplayer.presentation.common.determineLayoutVisibility
import com.viperplayer.presentation.ktx.plus
import com.viperplayer.presentation.navigation.*
import com.viperplayer.presentation.player.PlayerViewModel
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ViperPlayerApp() }
    }
}
