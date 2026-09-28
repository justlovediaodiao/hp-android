package com.hp.vpn.logging

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.concurrent.thread

object AppLog {
    fun write(component: String, message: String) {
        System.err.println("[$component] $message")
    }
}

object ProcessLog {
    private var stdout: FileDescriptor? = null
    private var stderr: FileDescriptor? = null
    private var writeEnd: ParcelFileDescriptor? = null

    fun start() {
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: Exception) {
            return
        }
        val read = pipe[0]
        val write = pipe[1]
        try {
            stdout = Os.dup2(write.fileDescriptor, 1)
            stderr = Os.dup2(write.fileDescriptor, 2)
        } catch (_: Exception) {
            read.close()
            write.close()
            return
        }
        writeEnd = write
        System.setOut(PrintStream(FileOutputStream(stdout!!), true))
        System.setErr(PrintStream(FileOutputStream(stderr!!), true))
        thread(name = "process-log", isDaemon = true) {
            ParcelFileDescriptor.AutoCloseInputStream(read)
                .bufferedReader()
                .useLines { lines -> lines.forEach(MemoryLog::appendCaptured) }
        }
    }

    fun restoreOutput() {
        val write = writeEnd ?: return
        Os.dup2(write.fileDescriptor, 1)
        Os.dup2(write.fileDescriptor, 2)
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true))
        System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true))
    }
}
