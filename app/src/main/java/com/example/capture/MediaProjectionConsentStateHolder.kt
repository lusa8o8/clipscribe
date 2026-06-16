package com.example.capture

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaProjectionConsentStateHolder {
    private val _consentState = MutableStateFlow(CaptureSessionConsentState.NOT_REQUESTED)
    val consentState: StateFlow<CaptureSessionConsentState> = _consentState.asStateFlow()

    @Volatile
    private var latestResultCode: Int? = null

    @Volatile
    private var latestData: Intent? = null

    @Volatile
    private var approvedAtMillis: Long? = null

    @Synchronized
    fun markRequesting() {
        _consentState.value = CaptureSessionConsentState.REQUESTING
    }

    @Synchronized
    fun markApproved(resultCode: Int, data: Intent) {
        _consentState.value = CaptureSessionConsentState.APPROVED
        latestResultCode = resultCode
        latestData = data
        approvedAtMillis = System.currentTimeMillis()
    }

    @Synchronized
    fun markDenied() {
        _consentState.value = CaptureSessionConsentState.DENIED
        latestResultCode = null
        latestData = null
        approvedAtMillis = null
    }

    @Synchronized
    fun markError() {
        _consentState.value = CaptureSessionConsentState.ERROR
        latestResultCode = null
        latestData = null
        approvedAtMillis = null
    }

    @Synchronized
    fun clear() {
        _consentState.value = CaptureSessionConsentState.NOT_REQUESTED
        latestResultCode = null
        latestData = null
        approvedAtMillis = null
    }

    fun getLatestResultCode(): Int? = latestResultCode

    fun getLatestData(): Intent? = latestData

    fun getApprovedAtMillis(): Long? = approvedAtMillis
}
