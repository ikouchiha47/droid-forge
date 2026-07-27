package com.forge.skeleton

import com.forge.skeleton.base.AppState
import com.forge.skeleton.base.BaseViewModel

class MainViewModel : BaseViewModel<String>() {
    init {
        emit(AppState.Success("skeleton ready"))
    }
}
