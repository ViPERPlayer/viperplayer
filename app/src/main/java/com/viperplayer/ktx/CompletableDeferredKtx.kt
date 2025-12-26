package com.viperplayer.ktx

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

fun <T> CompletableDeferred<T>.awaitBlocking(): T =
    if (isCompleted) {
        getCompleted()
    } else {
        runBlocking { await() }
    }