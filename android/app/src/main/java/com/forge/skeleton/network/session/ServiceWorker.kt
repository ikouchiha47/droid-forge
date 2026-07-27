package com.forge.skeleton.network.session

import kotlinx.coroutines.CoroutineScope

fun interface ServiceWorker {
    suspend fun run(scope: CoroutineScope)
}
