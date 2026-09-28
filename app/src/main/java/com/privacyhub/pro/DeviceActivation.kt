package com.privacyhub.pro

data class DeviceActivation(
    val licenseId: String,
    val activationId: String,
    val slotIndex: Int,
    val deviceLimit: Int,
    val deviceFingerprint: String,
    val createdAtMillis: Long,
    val version: Int
)

data class DeviceActivationCheck(
    val active: Boolean,
    val message: String,
    val activation: DeviceActivation? = null
)

data class DeviceActivationRequest(
    val requestText: String,
    val deviceFingerprint: String,
    val licenseId: String
)

data class DeviceActivationRequestResult(
    val success: Boolean,
    val message: String,
    val request: DeviceActivationRequest? = null
)
