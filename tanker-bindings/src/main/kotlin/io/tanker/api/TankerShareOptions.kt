package io.tanker.api

/**
 * Options used for sharing to users and groups
 */
class TankerShareOptions {
    internal var recipientUids: Array<out String> = arrayOf()
    internal var recipientGids: Array<out String> = arrayOf()

    /**
    * Sets the list of recipients User IDs
    * @param recipientUids A list of the recipients user IDs
    */
    fun shareWithUsers(vararg recipientUids: String): TankerShareOptions {
        this.recipientUids = recipientUids
        return this
    }

    /**
     * Sets the list of recipients Group IDs
     * @param recipientGids A list of the recipients group IDs
     */
    fun shareWithGroups(vararg recipientGids: String): TankerShareOptions {
        this.recipientGids = recipientGids
        return this
    }
}
