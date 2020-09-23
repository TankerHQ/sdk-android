package io.tanker.api

import com.sun.jna.Pointer
import io.tanker.bindings.StreamInputSourceReadOperationPointer
import io.tanker.bindings.TankerLib
import java.nio.ByteBuffer

internal class TankerStreamInputSourceCallback(val channel: TankerAsynchronousByteChannel) : TankerLib.StreamInputSourceCallback {
    var streamError: Throwable? = null
    var closing = false

    fun close() {
        closing = true
        channel.close()
    }

    override fun callback(buffer: Pointer, buffer_size: Long, op: StreamInputSourceReadOperationPointer, userArg: Pointer?) {
        val b = ByteBuffer.allocate(buffer_size.toInt())
        channel.read(b, Unit, object : TankerCompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                if (closing)
                    return

                var nbRead = result
                if (result == -1) {
                    nbRead = 0
                }
                buffer.write(0, b.array(), 0, nbRead)
                Tanker.lib.tanker_stream_read_operation_finish(op, nbRead.toLong())
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                if (closing)
                    return

                Tanker.lib.tanker_stream_read_operation_finish(op, -1)
                streamError = exc
            }
        })
    }
}
