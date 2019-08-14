package io.tanker.api

import io.tanker.bindings.TankerError
import io.tanker.bindings.TankerLib
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor


internal class TankerInputStream constructor(private val channel: TankerAsynchronousByteChannel) : InputStream() {

    override fun read(): Int {
        val buffer = ByteArray(1)
        if (read(buffer, 0, 1) == -1)
            return -1
        return buffer[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val fut = FutureTask {}
        var nbRead = 0
        var err: Throwable? = null

        val buffer = ByteBuffer.wrap(b, off, len)
        channel.read(buffer, Unit, object : TankerCompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                nbRead = result
                fut.run()
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                err = exc
                fut.run()
            }
        })
        fut.get()
        if (err != null) {
            if (err is ClosedChannelException) {
                throw IOException("Stream is closed", err)
            }
            throw err!!
        }
        return nbRead
    }

    override fun markSupported(): Boolean {
        return false
    }

    override fun available(): Int {
        return 0
    }

    override fun close() {
        channel.close()
    }

}