package com.swiftshare.common

import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Abstraction for positional chunk writes across JVM File and Android ParcelFileDescriptor.
 */
interface PartSink : Closeable {
    fun write(offset: Long, buffer: ByteBuffer): Int
    fun write(offset: Long, data: ByteArray, byteOffset: Int, byteCount: Int): Int
    fun flush()
}

/**
 * JVM File implementation of PartSink using NIO FileChannel.
 */
class FilePartSink(val file: File) : PartSink {
    private val raf = RandomAccessFile(file, "rw")
    private val channel: FileChannel = raf.channel

    override fun write(offset: Long, buffer: ByteBuffer): Int {
        return channel.write(buffer, offset)
    }

    override fun write(offset: Long, data: ByteArray, byteOffset: Int, byteCount: Int): Int {
        val buf = ByteBuffer.wrap(data, byteOffset, byteCount)
        return channel.write(buf, offset)
    }

    override fun flush() {
        channel.force(true)
    }

    override fun close() {
        try {
            channel.close()
        } finally {
            raf.close()
        }
    }
}

/**
 * Android ParcelFileDescriptor implementation using Os.pwrite and FileChannel.
 * Marked as NOT VERIFIED on physical device.
 */
class ParcelFileDescriptorPartSink(
    val pfd: ParcelFileDescriptor
) : PartSink {
    private val fos = FileOutputStream(pfd.fileDescriptor)
    private val channel: FileChannel = fos.channel

    override fun write(offset: Long, buffer: ByteBuffer): Int {
        return channel.write(buffer, offset)
    }

    override fun write(offset: Long, data: ByteArray, byteOffset: Int, byteCount: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.system.Os.pwrite(pfd.fileDescriptor, data, byteOffset, byteCount, offset)
            } catch (_: Throwable) {
                val buf = ByteBuffer.wrap(data, byteOffset, byteCount)
                channel.write(buf, offset)
            }
        } else {
            val buf = ByteBuffer.wrap(data, byteOffset, byteCount)
            channel.write(buf, offset)
        }
    }

    override fun flush() {
        channel.force(true)
    }

    override fun close() {
        try {
            channel.close()
        } finally {
            try {
                fos.close()
            } finally {
                pfd.close()
            }
        }
    }
}
