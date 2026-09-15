package com.tabslify.tabs.authenticator

import com.tabslify.core.functions.errorInsert
import com.tabslify.core.objects.Config
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant

@Suppress("PropertyName")
@Serializable
data class TwoFaEntrySupabase(
    val id: String? = null,
    val account_name: String? = null,
    val issuer: String? = null,
    val secret: String? = null
)

fun TwoFAEntry.toSupabase(): TwoFaEntrySupabase {
    return TwoFaEntrySupabase(
        account_name = this.name,
        secret = CloudCrypto.encryptForCloud(this.secret)
    )
}

suspend fun saveTwoFaEntryToSupabase(entry: TwoFAEntry, db: TwoFADatabase? = null): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val supabaseEntry = entry.toSupabase()

            val response = Config.client.postgrest
                .from("two_fa_entries")
                .insert(supabaseEntry) {
                    select()
                }
                .decodeSingle<TwoFaEntrySupabase>()

            if (db != null && response.id != null) {
                val updatedEntry = entry.copy(supabaseId = response.id)
                db.twoFADao().update(updatedEntry)
            }

            true
        } catch (e: Exception) {
            errorInsert(
                "TwoFARepository",
                "❌ Supabase Fehler: ${e.message}",
                Instant.now().toString(),
                "ERROR"

            )
            false
        }
    }
}

data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val total: Int,
    val pendingConflicts: List<SyncConflict> = emptyList(),
    val error: String? = null
)