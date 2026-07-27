package com.forge.skeleton.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class BaseViewModel<S> : ViewModel() {
    private val _state = MutableStateFlow<AppState<S>>(AppState.Loading)
    val state: StateFlow<AppState<S>> = _state

    protected fun emit(state: AppState<S>) { _state.value = state }
}
