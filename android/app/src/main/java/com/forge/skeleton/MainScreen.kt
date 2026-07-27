package com.forge.skeleton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.forge.skeleton.base.AppState
import com.forge.skeleton.ui.AppScaffold

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    AppScaffold(title = stringResource(R.string.app_name)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(this),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is AppState.Loading -> CircularProgressIndicator()
                is AppState.Success -> Text(text = s.data)
                is AppState.Error -> Text(text = s.message)
            }
        }
    }
}
