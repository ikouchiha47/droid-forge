package com.forge.skeleton.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class BaseViewModel<S, I : AppIntent> : ViewModel() {
    private val _state = MutableStateFlow<AppState<S>>(AppState.Loading)
    val state: StateFlow<AppState<S>> = _state

    abstract fun handle(intent: I)

    protected fun emit(state: AppState<S>) { _state.value = state }
}
