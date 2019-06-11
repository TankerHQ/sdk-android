package io.tanker.bindings

/**
 * Error codes in a TankerError
 * @see TankerError
 */
enum class TankerErrorCode(val value: Int) {
    NO_ERROR(0),
    INVALID_ARGUMENT(1),
    INTERNAL_ERROR(2),
    NETWORK_ERROR(3),
    PRECONDITION_FAILED(4),
    OPERATION_CANCELED(5),
    OPERATION_FORBIDDEN(6),

    DECRYPTION_FAILED(7),

    INVALID_GROUP_SIZE(8),

    NOT_FOUND(9),
    ALREADY_EXISTS(10),

    INVALID_CREDENTIALS(11),
    TOO_MANY_ATTEMPTS(12),
    EXPIRED(13),
    DEVICE_REVOKED(14),
}
