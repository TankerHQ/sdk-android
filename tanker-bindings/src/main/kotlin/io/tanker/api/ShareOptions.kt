package io.tanker.api

/**
 * Options used for sharing to users and groups
 */
class ShareOptions {
    internal var recipientPublicIdentities: Array<out String> = arrayOf()
    internal var recipientGids: Array<out String> = arrayOf()

    /**
    * Sets the list of recipients User IDs
    * @param recipientPublicIdentities A list of the recipients user IDs
    */
    fun shareWithUsers(vararg recipientPublicIdentities: String): ShareOptions {
        this.recipientPublicIdentities = recipientPublicIdentities
        return this
    }

    /**
     * Sets the list of recipients Group IDs
     * @param recipientGids A list of the recipients group IDs
     */
    fun shareWithGroups(vararg recipientGids: String): ShareOptions {
        this.recipientGids = recipientGids
        return this
    }
}
