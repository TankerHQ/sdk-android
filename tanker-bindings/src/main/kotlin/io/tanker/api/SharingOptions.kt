package io.tanker.api

/**
 * Options used for sharing to users and groups
 */
open class SharingOptions {
    internal var recipientPublicIdentities: Array<out String> = arrayOf()
    internal var recipientGids: Array<out String> = arrayOf()

    /**
    * Sets the list of recipients User IDs
    * @param recipientPublicIdentities A list of the recipients user IDs
    */
    fun shareWithUsers(vararg recipientPublicIdentities: String): SharingOptions {
        this.recipientPublicIdentities = recipientPublicIdentities
        return this
    }

    /**
     * Sets the list of recipients Group IDs
     * @param recipientGids A list of the recipients group IDs
     */
    fun shareWithGroups(vararg recipientGids: String): SharingOptions {
        this.recipientGids = recipientGids
        return this
    }
}
