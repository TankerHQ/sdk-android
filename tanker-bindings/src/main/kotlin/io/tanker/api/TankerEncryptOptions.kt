package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.Structure

/**
 * Options used for tanker encryption
 *
 * Please use the use the builder interface and the setter methods instead
 * of accessing the fields directly. Those are not part of the public API and
 * are subject to change.
 */
open class TankerEncryptOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var recipientUids = Pointer(0)
    @JvmField var nbRecipientUids = 0
    @JvmField var recipientGids = Pointer(0)
    @JvmField var nbRecipientGids = 0

    /**
     * JNA does not support having a StringArray directly in a struct,
     * so we hide it in a private var and put a Pointer to it in the actual struct...
     */
    private var recipientUidsArray: StringArray = StringArray(arrayOf<String>())
    private var recipientGidsArray: StringArray = StringArray(arrayOf<String>())

    /**
     * Sets the list of recipients User IDs
     * @param recipientUids A list of the recipients user IDs
     */
    fun shareWithUsers(vararg recipientUids: String): TankerEncryptOptions {
        this.recipientUidsArray = StringArray(recipientUids)
        this.recipientUids = recipientUidsArray
        this.nbRecipientUids = recipientUids.size
        return this
    }

    /**
     * Sets the list of recipients Group IDs
     * @param recipientGids A list of the recipients group IDs
     */
    fun shareWithGroups(vararg recipientGids: String): TankerEncryptOptions {
        this.recipientGidsArray = StringArray(recipientGids)
        this.recipientGids = recipientGidsArray
        this.nbRecipientGids = recipientGids.size
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "recipientUids", "nbRecipientUids", "recipientGids", "nbRecipientGids")
    }
}
