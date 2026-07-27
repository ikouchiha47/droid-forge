package com.forge.skeleton.base

interface UseCase<in In, out Out> {
    suspend fun execute(input: In): Result<Out>
}

object NoInput
