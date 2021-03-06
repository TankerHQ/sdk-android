package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.Structure

/**
 * Options used for sharing to users and groups
 */
open class SharingOptions: Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var shareWithUsers = Pointer(0)
    @JvmField var nbUsers = 0
    @JvmField var shareWithGroups = Pointer(0)
    @JvmField var nbGroups = 0

    /**
     * JNA does not support having a StringArray directly in a struct,
     * so we hide it in a private var and put a Pointer to it in the actual struct...
     */
    private var shareWithUsersArray: StringArray = StringArray(arrayOf<String>())
    private var shareWithGroupsArray: StringArray = StringArray(arrayOf<String>())

    /**
    * Sets the list of recipients User IDs
    * @param shareWithUsers A list of the recipients user IDs
    */
    fun shareWithUsers(vararg shareWithUsers: String): SharingOptions {
        this.shareWithUsersArray = StringArray(shareWithUsers)
        this.shareWithUsers = this.shareWithUsersArray
        this.nbUsers = shareWithUsers.size
        return this
    }

    /**
     * Sets the list of recipients Group IDs
     * @param shareWithGroups A list of the recipients group IDs
     */
    fun shareWithGroups(vararg shareWithGroups: String): SharingOptions {
        this.shareWithGroupsArray = StringArray(shareWithGroups)
        this.shareWithGroups = this.shareWithGroupsArray
        this.nbGroups = shareWithGroups.size
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "shareWithUsers", "nbUsers", "shareWithGroups", "nbGroups")
    }
}
