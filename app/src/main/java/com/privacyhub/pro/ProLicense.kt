package com.privacyhub.pro

data class ProLicense(
    val licenseId: String,
    val holder: String,
    val issuedAtMillis: Long?,
    val expiresAtMillis: Long?,
    val features: Set<ProFeature>,
    val allFeatures: Boolean,
    val version: Int,
    val createdAtMillis: Long?,
    val deviceLimit: Int
) {
    val requiresDeviceBinding: Boolean
        get() = version >= DEVICE_BINDING_LICENSE_VERSION && deviceLimit > 0

    fun allows(feature: ProFeature): Boolean {
        return allFeatures || feature in features
    }

    companion object {
        const val DEVICE_BINDING_LICENSE_VERSION = 2
    }
}

data class DeviceBindingStatus(
    val required: Boolean,
    val active: Boolean,
    val deviceLimit: Int = 0,
    val slotIndex: Int? = null,
    val message: String
)

data class ProLicenseCheck(
    val active: Boolean,
    val message: String,
    val license: ProLicense? = null,
    val deviceBinding: DeviceBindingStatus? = null
) {
    val licenseId: String?
        get() = license?.licenseId

    val holder: String?
        get() = license?.holder
}
