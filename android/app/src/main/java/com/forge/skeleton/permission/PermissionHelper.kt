package com.forge.skeleton.permission

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PermissionHelper(activity: ComponentActivity) {

    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null

    private var onAllGranted: (() -> Unit)? = null
    private var onMultiDenied: (() -> Unit)? = null

    private val single: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onGranted?.invoke() else onDenied?.invoke()
        }

    private val multiple: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) onAllGranted?.invoke() else onMultiDenied?.invoke()
        }

    fun request(permission: String, onGranted: () -> Unit, onDenied: () -> Unit) {
        this.onGranted = onGranted
        this.onDenied = onDenied
        single.launch(permission)
    }

    fun requestMultiple(permissions: Array<String>, onAllGranted: () -> Unit, onDenied: () -> Unit) {
        this.onAllGranted = onAllGranted
        this.onMultiDenied = onDenied
        multiple.launch(permissions)
    }
}
