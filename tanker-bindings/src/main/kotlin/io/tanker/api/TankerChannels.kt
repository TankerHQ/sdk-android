package io.tanker.api

import androidx.annotation.RequiresApi
import java.io.InputStream
import java.nio.channels.AsynchronousByteChannel

class TankerChannels {

    companion object {
        @JvmStatic
        fun toInputStream(channel: TankerAsynchronousByteChannel): InputStream {
            return TankerInputStream(channel)
        }

        @JvmStatic
        fun fromInputStream(stream: InputStream): TankerAsynchronousByteChannel {
            return InputStreamWrapper(stream)
        }

        @RequiresApi(26)
        @JvmStatic
        fun toAsynchronousByteChannel(channel: TankerAsynchronousByteChannel): AsynchronousByteChannel {
            return TankerAsynchronousByteChannelWrapper(channel)
        }

        @RequiresApi(26)
        @JvmStatic
        fun fromAsynchronousByteChannel(channel: AsynchronousByteChannel): TankerAsynchronousByteChannel {
            return AsynchronousByteChannelWrapper(channel)
        }
    }
}