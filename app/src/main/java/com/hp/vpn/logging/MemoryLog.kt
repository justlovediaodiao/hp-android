package com.hp.vpn.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MemoryLog {
    private const val MAX_LINES = 2000
    private const val MAX_CHARS = 1_000_000
    private val lines = ArrayDeque<String>()
    private var chars = 0
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val entries = mutable.asStateFlow()

    @Synchronized fun appendCaptured(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$time ${message.take(1000)}"
        lines.addLast(line)
        chars += line.length
        while (lines.size > MAX_LINES || chars > MAX_CHARS) chars -= lines.removeFirst().length
        mutable.value = lines.toList()
    }

    @Synchronized fun clear() {
        lines.clear()
        chars = 0
        mutable.value = emptyList()
    }
}
