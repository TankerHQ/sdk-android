package io.tanker.api

/**
 * Options used for the Tanker user unlock
 */
class TankerUnlockOptions {
    internal var email: String? = null
    internal var password: String? = null

    /**
     * Sets the password to use for unlock
     */
    fun setPassword(password: String): TankerUnlockOptions {
        this.password = password
        return this
    }

    /**
     * Sets the email to use for unlock
     */
    fun setEmail(email: String): TankerUnlockOptions {
        this.email = email
        return this
    }
}