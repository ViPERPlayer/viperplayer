package com.viperplayer.presentation.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QueueScreen(
    contentWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(contentWindowInsets)
    ) {
        items(100) {
            Text("Item $it")
        }
    }

}