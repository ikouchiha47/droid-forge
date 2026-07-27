package com.forge.skeleton.base

sealed class AppState<out T> {
    object Loading : AppState<Nothing>()
    data class Success<T>(val data: T) : AppState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppState<Nothing>()
}
