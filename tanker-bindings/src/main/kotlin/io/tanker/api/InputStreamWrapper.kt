package io.tanker.api

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class InputStreamWrapper(private var inputStream: InputStream?) : TankerAsynchronousByteChannel {
    override fun <A> read(dst: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>) {
        TankerFuture.threadPool.execute {
            if (!isOpen)
                handler.failed(IOException("Stream is closed"), attachment)
            else {
                try {
                    val b = ByteArray(dst.remaining())
                    val nbRead = inputStream!!.read(b)
                    if (nbRead != -1) {
                        dst.put(b, 0, nbRead)
                    }
                    handler.completed(nbRead, attachment)
                } catch (e: Throwable) {
                    handler.failed(e, attachment)
                }
            }
        }
    }

    override fun isOpen(): Boolean {
        return inputStream != null
    }

    override fun close() {
        if (inputStream != null) {
            inputStream!!.close()
            inputStream = null
        }
    }
}
