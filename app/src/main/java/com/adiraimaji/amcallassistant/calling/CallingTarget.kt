package com.adiraimaji.amcallassistant.calling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The profiles activity asks the main calling screen to load one profile for
 * editing. A small flow instead of an intent extra: the main screen lives in
 * MainActivity, while the list is its own activity.
 */
internal object CallingTarget {
    private val _request = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val request = _request.asSharedFlow()

    fun edit(id: String) {
        _request.tryEmit(id)
    }
}
