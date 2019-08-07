package io.tanker.api

import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler

@RequiresApi(26)
class AsynchronousByteChannelWrapper(private val channel: AsynchronousByteChannel) : TankerAsynchronousByteChannel {
    override fun close() {
        channel.close()
    }

    override fun isOpen(): Boolean {
        return channel.isOpen
    }

    override fun <A> read(dst: ByteBuffer?, attachment: A, handler: TankerCompletionHandler<Int, in A>?) {
        channel.read(dst, attachment, object : CompletionHandler<Int, A> {
            override fun completed(result: Int, attachment: A) {
                handler!!.completed(result, attachment)
            }

            override fun failed(exc: Throwable, attachment: A) {
                handler!!.failed(exc, attachment)
            }
        })
    }
}