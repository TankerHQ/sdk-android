package io.tanker.api

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Future

class InputStreamWrapper(private val inputStream: InputStream) : TankerAsynchronousByteChannel {
    companion object {
        private var isClosed = false
    }

    override fun <A> read(dst: ByteBuffer?, attachment: A, handler: TankerCompletionHandler<Int, in A>?) {
        TankerFuture.threadPool.execute {
            try {
                val b = ByteArray(dst!!.remaining())
                val nbRead = inputStream.read(b)
                if (nbRead != -1) {
                    dst.put(b, 0, nbRead)
                }
                handler!!.completed(nbRead, attachment)
            } catch (e: Throwable) {
                handler!!.failed(e, attachment)
            }
        }
    }
    override fun isOpen(): Boolean {
        return !isClosed
    }

    override fun close() {
        inputStream.close()
        isClosed = true
    }
}
