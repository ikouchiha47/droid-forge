package com.forge.skeleton.network.model

data class SignalEnvelope(
    val from: String,
    val to: String,
    val type: String,
    val payload: String,
)
