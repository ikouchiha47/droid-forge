package com.forge.skeleton.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    showLoading: Boolean = false,
    content: @Composable PaddingValues.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = actions,
            )
            if (showLoading) LinearProgressIndicator()
        },
        content = { padding -> content(padding) },
    )
}
