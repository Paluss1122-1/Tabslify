package com.tabslify.core.functions

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val APP_CHECK_LOG = "AppCheckToken"

suspend fun getAppCheckToken(): String? = suspendCancellableCoroutine { cont ->
    try {
        Firebase.appCheck.getAppCheckToken(false)
            .addOnSuccessListener { token ->
                if (cont.isActive) cont.resume(token.token)
            }
            .addOnFailureListener { error ->
                Log.w(APP_CHECK_LOG, "App-Check-Token nicht abrufbar: ${error.message}")
                if (cont.isActive) cont.resume(null)
            }
    } catch (e: Exception) {
        Log.w(APP_CHECK_LOG, "App-Check-Token nicht abrufbar: ${e.message}", e)
        if (cont.isActive) cont.resume(null)
    }
}