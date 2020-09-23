package io.tanker.api

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.tanker.bindings.StreamPointer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

internal class TankerStream constructor(private var cStream: StreamPointer?, private var underlyingStream: TankerStreamInputSourceCallback?) : TankerAsynchronousByteChannel {

    val resourceID = initResourceID()
    private var pendingReadOperation = false

    private fun initResourceID(): String {
        if (cStream == null)
            throw IOException("Stream is closed")

        val future = Tanker.lib.tanker_stream_get_resource_id(cStream!!)
        val outStringPtr = TankerFuture<Pointer>(future, Pointer::class.java).get()
        val outString = outStringPtr.getString(0, "UTF-8")
        Tanker.lib.tanker_free_buffer(outStringPtr)
        return outString
    }

    override fun isOpen(): Boolean {
        return cStream != null
    }

    override fun close() {
        if (cStream == null)
            return
        underlyingStream!!.close()
        TankerFuture<Unit>(Tanker.lib.tanker_stream_close(cStream!!), Unit::class.java).get()
        pendingReadOperation = false
        underlyingStream = null
        cStream = null
    }

    override fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>) {
        if (pendingReadOperation)
            throw TankerPendingReadException()
        readTankerInput(dst, attachment, handler)
    }

    private fun <A : Any?> readTankerInput(buffer: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>) {
        val offset = buffer.position()
        val size = buffer.remaining()
        if (cStream == null)
            handler.failed(ClosedChannelException(), attachment)
        else {
            var inBuf: Pointer? = null
            // handle special 0 case, which will trigger a buffering operation
            if (size != 0)
                inBuf = Memory(size.toLong())

            pendingReadOperation = true
            TankerFuture<Int>(Tanker.lib.tanker_stream_read(cStream!!, inBuf, size.toLong()), Int::class.java).then(TankerVoidCallback {
                pendingReadOperation = false
                val err = it.getError()
                if (err != null) {
                    if (underlyingStream!!.streamError != null) {
                        handler.failed(underlyingStream!!.streamError!!, attachment)
                    } else {
                        if ((err as TankerException).errorCode == ErrorCode.OPERATION_CANCELED) {
                            handler.failed(ClosedChannelException(), attachment)
                        } else {
                            handler.failed(err, attachment)
                        }
                    }
                } else {
                    var nbRead = it.get()
                    if (inBuf == null) {
                        handler.completed(nbRead, attachment)
                    } else {
                        if (buffer.hasArray()) {
                            inBuf.read(0, buffer.array(), offset, nbRead)
                        } else {
                            val b = inBuf.getByteBuffer(0, nbRead.toLong())
                            buffer.put(b)
                        }
                        if (nbRead == 0) {
                            nbRead = -1
                        }
                        handler.completed(nbRead, attachment)
                    }
                }
            })
        }
    }
}
