package io.tanker.api

import java.nio.ByteBuffer
import java.nio.channels.Channel

// nio.channels.AsynchronousByteChannel requires API 26
// provide our own interface as a replacement

interface TankerAsynchronousByteChannel : Channel {
    fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>)
}