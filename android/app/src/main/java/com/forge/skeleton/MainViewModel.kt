package com.forge.skeleton

import com.forge.skeleton.base.AppIntent
import com.forge.skeleton.base.AppState
import com.forge.skeleton.base.BaseViewModel

sealed class MainIntent : AppIntent {
    object Refresh : MainIntent()
}

class MainViewModel : BaseViewModel<String, MainIntent>() {
    init {
        emit(AppState.Success("skeleton ready"))
    }

    override fun handle(intent: MainIntent) {
        when (intent) {
            is MainIntent.Refresh -> emit(AppState.Success("skeleton ready"))
        }
    }
}
