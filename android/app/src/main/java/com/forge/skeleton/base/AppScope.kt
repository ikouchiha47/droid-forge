package com.forge.skeleton.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob()
}
