package io.tanker.api

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channel
import java.util.concurrent.Future

// nio.channels.AsynchronousByteChannel requires API 26
// provide our own interface as a replacement

interface TankerAsynchronousByteChannel : Channel {
    public abstract fun <A : Any?> read(dst: ByteBuffer?, attachment: A, handler: TankerCompletionHandler<Int, in A>?)
}