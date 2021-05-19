package io.tanker.api.errors

import io.tanker.api.ErrorCode
import io.tanker.api.TankerException
import io.tanker.bindings.TankerError

class InvalidArgument(message: String) : TankerException(ErrorCode.INVALID_ARGUMENT, message)
class InternalError(message: String) : TankerException(ErrorCode.INTERNAL_ERROR, message)
class NetworkError(message: String) : TankerException(ErrorCode.NETWORK_ERROR, message)
class PreconditionFailed(message: String) : TankerException(ErrorCode.PRECONDITION_FAILED, message)
class OperationCanceled(message: String) : TankerException(ErrorCode.OPERATION_CANCELED, message)

class DecryptionFailed(message: String) : TankerException(ErrorCode.DECRYPTION_FAILED, message)
class GroupTooBig(message: String) : TankerException(ErrorCode.GROUP_TOO_BIG, message)

class InvalidVerification(message: String) : TankerException(ErrorCode.INVALID_VERIFICATION, message)
class TooManyAttempts(message: String) : TankerException(ErrorCode.TOO_MANY_ATTEMPTS, message)
class ExpiredVerification(message: String) : TankerException(ErrorCode.EXPIRED_VERIFICATION, message)
class DeviceRevoked(message: String) : TankerException(ErrorCode.DEVICE_REVOKED, message)

class Conflict(message: String) : TankerException(ErrorCode.CONFLICT, message)
class UpgradeRequired(message: String) : TankerException(ErrorCode.UPGRADE_REQUIRED, message)
class IdentityAlreadyAttached(message: String) : TankerException(ErrorCode.IDENTITY_ALREADY_ATTACHED, message)

internal fun toError(error: TankerError): TankerException =
        when (error.errorCode) {
            ErrorCode.INVALID_ARGUMENT.value -> InvalidArgument(error.getErrorMessage())
            ErrorCode.INTERNAL_ERROR.value -> InternalError(error.getErrorMessage())
            ErrorCode.NETWORK_ERROR.value -> NetworkError(error.getErrorMessage())
            ErrorCode.PRECONDITION_FAILED.value -> PreconditionFailed(error.getErrorMessage())
            ErrorCode.OPERATION_CANCELED.value -> OperationCanceled(error.getErrorMessage())
            ErrorCode.DECRYPTION_FAILED.value -> DecryptionFailed(error.getErrorMessage())
            ErrorCode.GROUP_TOO_BIG.value -> GroupTooBig(error.getErrorMessage())
            ErrorCode.INVALID_VERIFICATION.value -> InvalidVerification(error.getErrorMessage())
            ErrorCode.TOO_MANY_ATTEMPTS.value -> TooManyAttempts(error.getErrorMessage())
            ErrorCode.EXPIRED_VERIFICATION.value -> ExpiredVerification(error.getErrorMessage())
            ErrorCode.DEVICE_REVOKED.value -> DeviceRevoked(error.getErrorMessage())
            ErrorCode.CONFLICT.value -> Conflict(error.getErrorMessage())
            ErrorCode.UPGRADE_REQUIRED.value -> UpgradeRequired(error.getErrorMessage())
            ErrorCode.IDENTITY_ALREADY_ATTACHED.value -> IdentityAlreadyAttached(error.getErrorMessage())
            else -> InternalError("Unknown error: ${error.errorCode}: ${error.errorMessage}")
        }
