package com.tabslify.core.activities

import android.app.Application
import android.content.Context
import android.widget.Toast
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.PrefsBackup
import com.tabslify.core.objects.prvt
import com.tabslify.quicksettingsfunctions.BatteryDataRepository
import com.tabslify.tabs.ainotify.AI_NOTIFY_TOPIC
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.mozilla.javascript.ScriptableObject
import java.time.Instant
import org.mozilla.javascript.Context as RhinoContext

class Tabslify : Application() {

    companion object {
        lateinit var coroutineExceptionHandler: CoroutineExceptionHandler
        val appScope by lazy {
            CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)
        }
        val serviceScope by lazy {
            CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
        }
    }

    override fun onCreate() {
        if (prvt()) {
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }

        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runBlocking {
                try {
                    errorInsert(
                        "UncaughtException: ${thread.name}",
                        throwable.stackTraceToString().take(8000),
                        Instant.now().toString(),
                        "ERROR"

                    )
                } catch (_: Exception) {
                }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        coroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
            errorInsert(
                "CoroutineException: $context",
                throwable.stackTraceToString().take(8000),
                Instant.now().toString(),
                "ERROR"

            )
        }

        if (prvt()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        FirebaseApp.initializeApp(this)

        if (prvt()) {
            FirebaseMessaging.getInstance().subscribeToTopic(AI_NOTIFY_TOPIC)
            FirebaseMessaging.getInstance().subscribeToTopic("emails")
            serviceScope.launch {
                client.auth.awaitInitialization()
                runCatching { PrefsBackup.autoBackup(this@Tabslify) }
            }
        }

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        Config.init(this)
        BatteryDataRepository.init(this)
        com.tabslify.tabs.focusguard.data.FocusGuardRepository.init(this)
    }
}

@Serializable
data class Script(val code: String)


suspend fun fetchAndRun(scriptName: String, context: Context) {
    val script = Config.safeCall {
        client.from("scripts")
            .select { filter { eq("name", scriptName) } }
            .decodeSingle<Script>()
    }

    val result = executeJs(script.code, context)
    println("Ergebnis: $result")
}
@Suppress("unused")
class AppBridge(private val androidContext: Context) {

    // Du musst die Methode für Rhino zugänglich machen
    fun showNotification(title: String, message: String) {
        // Hier baust du später deinen echten Android Notification-Code ein!
        // Fürs Erste zeigen wir hier zur Demo einen Toast:
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(androidContext, "$title: $message", Toast.LENGTH_LONG).show()
        }
    }

    fun logEvent(event: String) {
        println("JS Event geloggt: $event")
    }
}

fun executeJs(code: String, appContext: Context): String {
    val cx = RhinoContext.enter()
    @Suppress("DEPRECATION")
    cx.optimizationLevel = -1

    cx.setClassShutter { className ->
        className == "com.tabslify.core.activities.AppBridge" ||
                className == "java.lang.String" ||
                className == "java.lang.Object" ||
                className == "java.lang.Boolean" ||
                className == "java.lang.Number" ||
                className == "java.lang.Integer" ||
                className == "java.lang.Double"
    }

    return try {
        val scope = cx.initStandardObjects()

        // 3. Brücke initialisieren und ins JS injizieren
        val bridge = AppBridge(appContext)
        val wrappedBridge = RhinoContext.javaToJS(bridge, scope)
        // Wir nennen das Objekt in JavaScript "Tabslify"
        ScriptableObject.putProperty(scope, "Tabslify", wrappedBridge)

        // Skript ausführen
        val result = cx.evaluateString(scope, code, "remote", 1, null)
        RhinoContext.toString(result)
    } catch (e: Exception) {
        "JS Execution Error: ${e.message}"
    } finally {
        RhinoContext.exit()
    }
}
