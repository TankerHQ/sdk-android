package io.tanker.bindings

import com.sun.jna.DefaultTypeMapper
import com.sun.jna.FromNativeContext
import com.sun.jna.ToNativeContext
import com.sun.jna.TypeConverter
import io.tanker.api.Status
import io.tanker.api.ErrorCode

class TankerTypeMapper : DefaultTypeMapper() {
    init {
        addTypeConverter(Status::class.java, object : TypeConverter {
            override fun toNative(value: Any?, context: ToNativeContext?): Any {
                val status = value as Status
                return status.value
            }

            override fun fromNative(nativeValue: Any?, context: FromNativeContext?): Any? {
                val value = nativeValue as? Int ?: return null
                return Status.values().find { it.value == value }
            }

            override fun nativeType(): Class<*> {
                return Int::class.java
            }
        })

        addTypeConverter(TankerEvent::class.java, object : TypeConverter {
            override fun toNative(value: Any?, context: ToNativeContext?): Any {
                val status = value as TankerEvent
                return status.value
            }

            override fun fromNative(nativeValue: Any?, context: FromNativeContext?): Any? {
                val value = nativeValue as? Int ?: return null
                return TankerEvent.values().find { it.value == value }
            }

            override fun nativeType(): Class<*> {
                return Int::class.java
            }
        })

        addTypeConverter(ErrorCode::class.java, object : TypeConverter {
            override fun toNative(value: Any?, context: ToNativeContext?): Any {
                val status = value as ErrorCode
                return status.value
            }

            override fun fromNative(nativeValue: Any?, context: FromNativeContext?): Any? {
                val value = nativeValue as? Int ?: return null
                return ErrorCode.values().find { it.value == value }
            }

            override fun nativeType(): Class<*> {
                return Int::class.java
            }
        })
    }
}
