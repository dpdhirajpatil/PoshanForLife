package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * All fields optional — absent (null) ones are left unchanged server-side
 * (explicitNulls = false means a null field is omitted from the request
 * body entirely, not sent as literal JSON null). Only fcmToken is used by
 * this app today (name/phone edits aren't built yet); other backend-side
 * fields (role/isActive/dateOfBirth) are admin-only and irrelevant here.
 */
@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    val fcmToken: String? = null,
)
