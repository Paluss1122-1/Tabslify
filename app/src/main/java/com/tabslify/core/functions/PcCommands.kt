package com.tabslify.core.functions

import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.objects.Config
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object PcCommands {

    const val LAMP_OFF = "lamp_off"

    private const val RESEND_COOLDOWN_MS = 15 * 60_000L

    private val lastQueuedAt = ConcurrentHashMap<String, Long>()

    fun queue(
        command: String,
        source: String,
        payload: JsonObject = buildJsonObject {},
    ) {
        val now = System.currentTimeMillis()
        val previous = lastQueuedAt[command] ?: 0L
        if (now - previous < RESEND_COOLDOWN_MS) {
            return
        }
        lastQueuedAt[command] = now

        serviceScope.launch {
            try {
                Config.client.from("pc_commands").insert(
                    buildJsonObject {
                        put("command", command)
                        put("source", source)
                        put("payload", payload)
                    }
                )
            } catch (e: Exception) {
                lastQueuedAt.remove(command)
                errorInsert(
                    "PcCommands",
                    "PC-Befehl $command nicht gesendet: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            }
        }
    }
}