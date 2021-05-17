package io.tanker.api

/**
 * Error codes in a TankerException
 * @see TankerException
 */
enum class ErrorCode(val value: Int) {
    NO_ERROR(0),
    INVALID_ARGUMENT(1),
    INTERNAL_ERROR(2),
    NETWORK_ERROR(3),
    PRECONDITION_FAILED(4),
    OPERATION_CANCELED(5),

    DECRYPTION_FAILED(6),

    @Deprecated("This enum value is deprecated", ReplaceWith("GROUP_TOO_BIG"))
    INVALID_GROUP_TOO_BIG(7),
    GROUP_TOO_BIG(7),

    INVALID_VERIFICATION(8),
    TOO_MANY_ATTEMPTS(9),
    EXPIRED_VERIFICATION(10),
    IO_ERROR(11),
    DEVICE_REVOKED(12),

    CONFLICT(13),
    UPGRADE_REQUIRED(14),
    IDENTITY_ALREADY_ATTACHED(15),
}
