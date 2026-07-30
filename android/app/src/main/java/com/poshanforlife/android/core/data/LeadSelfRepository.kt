package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result

/** Self-service actions for a mobile self-signup account (role LEAD) — separate from LeadRepository's staff-facing CRM. */
interface LeadSelfRepository {
    suspend fun requestConsultation(preferredContactTime: String?, message: String?): Result<Unit>
}
