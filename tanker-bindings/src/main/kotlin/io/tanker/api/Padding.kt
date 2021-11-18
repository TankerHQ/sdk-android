package io.tanker.api

/**
 * Padding control for data encryption.
 */
sealed class Padding(internal val native_value: Int) {
    /**
     * Allowed values for Padding variables
     */
    companion object {
        /**
         * This is the default option.
         */
        @JvmStatic val auto : Padding = Auto

        /**
         * Disables padding.
         */
        @JvmStatic val off : Padding = Off

        /**
         * Pads the data up to a multiple of value before encryption.
         * To disable padding, use Off().
         * @param value A >= 2 integer.
         */
        @JvmStatic fun step(value: Int): Padding = Step(value)
    }

    private object Auto : Padding(0)
    private object Off : Padding(1)

    private class Step(value: Int) : Padding(value) {
        init {
            if (value <= 1)
                throw IllegalArgumentException("Invalid padding step, the value must be >= 2.")
        }
    }
}
