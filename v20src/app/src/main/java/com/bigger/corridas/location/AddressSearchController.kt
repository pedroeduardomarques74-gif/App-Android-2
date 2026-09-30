package com.bigger.corridas.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddressSearchController(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 380L
) {
    private var job: Job? = null
    fun submit(query: String, action: suspend (String) -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            action(query)
        }
    }
    fun cancel() { job?.cancel(); job = null }
}
