package io.tanker.api

import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.util.concurrent.Future

@RequiresApi(26)
internal class TankerAsynchronousByteChannelWrapper(internal val streamChannel: TankerAsynchronousByteChannel) : AsynchronousByteChannel {
    override fun read(dst: ByteBuffer?): Future<Int> {
        throw UnsupportedOperationException()
    }

    override fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        try {
            return streamChannel.read(dst, attachment, object : TankerCompletionHandler<Int, A> {
                override fun completed(result: Int, attachment: A) {
                    handler.completed(result, attachment)
                }

                override fun failed(exc: Throwable, attachment: A) {
                    handler.failed(exc, attachment)
                }
            })
        } catch (_: TankerPendingReadException) {
            throw ReadPendingException()
        }
    }

    override fun close() {
        return streamChannel.close()
    }

    override fun write(src: ByteBuffer?): Future<Int> {
        throw UnsupportedOperationException()
    }

    override fun <A : Any?> write(src: ByteBuffer?, attachment: A, handler: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }


    override fun isOpen(): Boolean {
        return streamChannel.isOpen
    }

}
