package com.tabslify.tabs.authenticator

import android.content.Context
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.tabslify.R
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.getDeviceName
import com.tabslify.privatetabslifyapp.isOnline
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "NULL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val totpSecret: String?
)


@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<PasswordEntry>

    @Query(
        """
        SELECT * FROM passwords
        WHERE name LIKE '%' || :q || '%'
           OR username LIKE '%' || :q || '%'
           OR url LIKE '%' || :q || '%'
        ORDER BY name ASC
    """
    )
    suspend fun search(q: String): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE :domain != '' AND url LIKE '%' || :domain || '%'")
    suspend fun findByDomain(domain: String): List<PasswordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun count(): Int

    @Query("DELETE FROM passwords")
    suspend fun deleteAll()
}


@Database(entities = [PasswordEntry::class], version = 5, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao

    companion object {
        @Volatile
        private var INSTANCE: PasswordDatabase? = null

        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "tabslify_passwords_v1.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

    }
}


object CloudCrypto {

    private val fixedSalt = "cloud_sync_salt_v1".toByteArray(Charsets.UTF_8).copyOf(16)
    private var cachedKeyPassword: String = ""
    private var cachedKeyValue: SecretKey? = null

    @Synchronized
    private fun cachedKey(): SecretKey {
        val currentPassword = Config.masterPassword
        return cachedKeyValue?.takeIf { cachedKeyPassword == currentPassword }
            ?: Config.deriveKey(currentPassword, fixedSalt).also {
                cachedKeyValue = it
                cachedKeyPassword = currentPassword
            }
    }

    fun encryptForCloud(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return encryptWithKey(plaintext, cachedKey())
    }

    fun decryptFromCloud(ciphertext: String): String? {
        if (ciphertext.isEmpty()) return ""
        return try {
            decryptWithKey(ciphertext, cachedKey())
        } catch (e: Exception) {
            errorInsert(
                "CloudCrypto",
                "Decrypt fehlgeschlagen: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            null
        }
    }

    private fun encryptWithKey(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val bb = ByteBuffer.allocate(12 + encrypted.size)
        bb.put(iv)
        bb.put(encrypted)
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP)
    }

    private fun decryptWithKey(ciphertext: String, key: SecretKey): String {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val data = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data).toString(Charsets.UTF_8)
    }
}


object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private const val AMBIGUOUS = "0OIl1"

    fun generate(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        noAmbiguous: Boolean = false
    ): String {
        val pool = buildString {
            if (lower) append(LOWER)
            if (upper) append(UPPER)
            if (digits) append(DIGITS)
            if (symbols) append(SYMBOLS)
        }.let { if (noAmbiguous) it.filter { c -> c.toString() !in AMBIGUOUS } else it }

        if (pool.isEmpty()) return ""

        val rng = SecureRandom.getInstanceStrong()
        val sb = StringBuilder(length)

        val guaranteed = buildList {
            if (lower) add(LOWER[rng.nextInt(LOWER.length)])
            if (upper) add(UPPER[rng.nextInt(UPPER.length)])
            if (digits) add(DIGITS[rng.nextInt(DIGITS.length)])
            if (symbols) add(SYMBOLS[rng.nextInt(SYMBOLS.length)])
        }

        repeat(length - guaranteed.size) { sb.append(pool[rng.nextInt(pool.length)]) }

        val all = (sb.toString().toList() + guaranteed).toMutableList()
        for (i in all.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val tmp = all[i]; all[i] = all[j]; all[j] = tmp
        }

        return all.take(length).joinToString("")
    }

    fun score(password: String): Int {
        if (password.isEmpty()) return 0
        var s = 0
        s += (password.length * 4).coerceAtMost(40)
        if (password.any { it.isLowerCase() }) s += 10
        if (password.any { it.isUpperCase() }) s += 10
        if (password.any { it.isDigit() }) s += 10
        if (password.any { !it.isLetterOrDigit() }) s += 20
        val unique = password.toSet().size
        s += (unique * 2).coerceAtMost(10)
        return s.coerceIn(0, 100)
    }

    fun strength(password: String): PasswordStrength {
        return when (score(password)) {
            in 0..25 -> PasswordStrength.WEAK
            in 26..49 -> PasswordStrength.FAIR
            in 50..74 -> PasswordStrength.GOOD
            in 75..89 -> PasswordStrength.STRONG
            else -> PasswordStrength.EXCELLENT
        }
    }
}


enum class PasswordStrength(
    val labelRes: Int,
    val fraction: Float,
    val color: Color
) {
    WEAK(R.string.schwach, 0.20f, Color(0xFFD32F2F)),
    FAIR(R.string.ausreichend, 0.40f, Color(0xFFE64A19)),
    GOOD(R.string.gut, 0.60f, Color(0xFFFBC02D)),
    STRONG(R.string.stark, 0.80f, Color(0xFF388E3C)),
    EXCELLENT(R.string.ausgezeichnet, 1.00f, Color(0xFF1B5E20))
}

@Suppress("PropertyName")
@Serializable
data class PasswordEntrySupabase(
    val id: String? = null,
    val name: String,
    val url: String? = null,
    val username: String? = null,
    val encrypted_password: String = "",
    val notes: String? = null,
    val totp_secret: String? = null
)

enum class SyncConflictType {
    CLOUD_ONLY_ENTRY,
    LOCAL_ONLY_ENTRY,
    TOTP_CLOUD_ONLY,
    TOTP_LOCAL_ONLY,
    TOTP_DIFFERENT,
    PASSWORD_DIFFERENT
}

data class SyncConflict(
    val type: SyncConflictType,
    val cloudEntry: PasswordEntrySupabase? = null,
    val localEntry: PasswordEntry? = null,
    val localTotp: String? = null
)

enum class SyncConflictDecision {
    KEEP_CLOUD,
    KEEP_LOCAL,
    DELETE_CLOUD,
    UPLOAD_LOCAL
}

private suspend fun pushCloudEntry(
    cloud: PasswordEntrySupabase,
    password: String,
    totpSecret: String?
) {
    val id = cloud.id ?: return
    Config.client.postgrest.from("password_entries").update(
        PasswordEntrySupabase(
            name = cloud.name,
            url = cloud.url,
            username = cloud.username,
            encrypted_password = CloudCrypto.encryptForCloud(password),
            notes = cloud.notes,
            totp_secret = totpSecret
        )
    ) {
        filter { eq("id", id) }
    }
}

suspend fun syncPasswordEntriesWithCloud(
    passwordDb: PasswordDatabase,
    twoFaDb: TwoFADatabase,
    context: Context
): SyncResult {
    if (!prvt()) {
        return SyncResult(uploaded = 0, downloaded = 0, total = 0)
    }
    if (!isOnline(context)) {
        return SyncResult(uploaded = 0, downloaded = 0, total = 0, error = context.getString(R.string.kein_internet))
    }
    return withContext(Dispatchers.IO) {
        try {
            val localPasswords = passwordDb.passwordDao().getAll()
            val localTwoFa = twoFaDb.twoFADao().getAll()
            val cloudEntries = try {
                Config.client.postgrest.from("password_entries")
                    .select().decodeList<PasswordEntrySupabase>()
            } catch (e: Exception) {
                errorInsert(
                    "PasswordRepository",
                    "Cloud-Laden fehlgeschlagen: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"

                )
                return@withContext SyncResult(
                    uploaded = 0,
                    downloaded = 0,
                    total = 0,
                    error = e.message
                )
            }

            val pendingConflicts = mutableListOf<SyncConflict>()
            var autoUpdated = 0
            var autoDownloaded = 0

            cloudEntries.forEach { cloud ->
                val cloudUser = cloud.username ?: ""
                val existing =
                    localPasswords.find { it.name == cloud.name && it.username == cloudUser }

                if (existing == null) {
                    val matchedSecret = localTwoFa.firstOrNull { fa ->
                        val n = fa.name.lowercase()
                        val ln = cloud.name.lowercase()
                        val lu = cloud.url?.lowercase() ?: ""
                        n.contains(ln) || ln.contains(n) || (lu.isNotEmpty() && n.split(" ").any { lu.contains(it) })
                    }?.secret
                    pendingConflicts += SyncConflict(
                        type = SyncConflictType.CLOUD_ONLY_ENTRY,
                        cloudEntry = cloud,
                        localTotp = matchedSecret
                    )
                    return@forEach
                }

                val decryptedPw = CloudCrypto.decryptFromCloud(cloud.encrypted_password)
                val matchedSecret = localTwoFa.firstOrNull { fa ->
                    val n = fa.name.lowercase()
                    val ln = existing.name.lowercase()
                    val lu = existing.url.lowercase()
                    n.contains(ln) || ln.contains(n) || (lu.isNotEmpty() && n.split(" ")
                        .any { lu.contains(it) })
                }?.secret

                val hasCloudTotp = !cloud.totp_secret.isNullOrEmpty()
                val cloudTotpDecrypted =
                    cloud.totp_secret?.takeIf { it.isNotEmpty() }?.let { CloudCrypto.decryptFromCloud(it) }

                if (hasCloudTotp && matchedSecret == null) {
                    pendingConflicts += SyncConflict(
                        type = SyncConflictType.TOTP_CLOUD_ONLY,
                        cloudEntry = cloud,
                        localEntry = existing
                    )
                } else if (!hasCloudTotp && matchedSecret != null) {
                    pendingConflicts += SyncConflict(
                        type = SyncConflictType.TOTP_LOCAL_ONLY,
                        cloudEntry = cloud,
                        localEntry = existing,
                        localTotp = matchedSecret
                    )
                } else if (hasCloudTotp && matchedSecret != null && cloudTotpDecrypted != matchedSecret) {
                    pendingConflicts += SyncConflict(
                        type = SyncConflictType.TOTP_DIFFERENT,
                        cloudEntry = cloud,
                        localEntry = existing,
                        localTotp = matchedSecret
                    )
                }

                val hasTotpConflict = pendingConflicts.any { it.cloudEntry?.id == cloud.id }
                val localPw = existing.password

                if (!hasTotpConflict && decryptedPw != localPw) {
                    try {
                        when {
                            decryptedPw == null -> {
                                errorInsert(
                                    "PasswordRepository",
                                    "Cloud-Passwort nicht lesbar, Eintrag unverändert: ${cloud.name}",
                                    Instant.now().toString(),
                                    "ERROR"
                                )
                            }

                            localPw.isEmpty() -> {
                                passwordDb.passwordDao().update(
                                    existing.copy(
                                        password = decryptedPw,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                autoDownloaded++
                            }

                            decryptedPw.isEmpty() -> {
                                pushCloudEntry(cloud, localPw, cloud.totp_secret)
                                autoUpdated++
                            }

                            else -> {
                                pendingConflicts += SyncConflict(
                                    type = SyncConflictType.PASSWORD_DIFFERENT,
                                    cloudEntry = cloud,
                                    localEntry = existing
                                )
                            }
                        }
                    } catch (e: Exception) {
                        errorInsert(
                            "PasswordRepository",
                            "Passwort-Abgleich fehlgeschlagen: ${e.message}",
                            Instant.now().toString(),
                            "ERROR"
                        )
                    }
                }
            }

            val cloudPairs = cloudEntries.map { it.name to (it.username ?: "") }.toSet()
            localPasswords
                .filter { (it.name to it.username) !in cloudPairs }
                .forEach { local ->
                    val matchedSecret = localTwoFa.firstOrNull { fa ->
                        val n = fa.name.lowercase()
                        val ln = local.name.lowercase()
                        val lu = local.url.lowercase()
                        n.contains(ln) || ln.contains(n) || (lu.isNotEmpty() && n.split(" ")
                            .any { lu.contains(it) })
                    }?.secret
                    pendingConflicts += SyncConflict(
                        type = SyncConflictType.LOCAL_ONLY_ENTRY,
                        localEntry = local,
                        localTotp = matchedSecret
                    )
                }

            SyncResult(
                uploaded = autoUpdated,
                downloaded = autoDownloaded,
                total = localPasswords.size,
                pendingConflicts = pendingConflicts
            )
        } catch (e: Exception) {
            errorInsert(
                "PasswordRepository",
                "Sync-Exception: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            SyncResult(uploaded = 0, downloaded = 0, total = 0, error = e.message)
        }
    }
}

suspend fun applySyncConflictDecision(
    conflict: SyncConflict,
    decision: SyncConflictDecision,
    passwordDb: PasswordDatabase,
    twoFaDb: TwoFADatabase
): Boolean {
    return prvt() && withContext(Dispatchers.IO) {
        try {
            when (conflict.type) {
                SyncConflictType.CLOUD_ONLY_ENTRY -> {
                    if (decision == SyncConflictDecision.DELETE_CLOUD) {
                        conflict.cloudEntry?.id?.let { id ->
                            Config.client.postgrest.from("password_entries").delete {
                                filter { eq("id", id) }
                            }
                        }
                    } else if (decision == SyncConflictDecision.KEEP_CLOUD) {
                        val cloud = conflict.cloudEntry ?: return@withContext true
                        val decryptedPw = CloudCrypto.decryptFromCloud(cloud.encrypted_password)
                        val decryptedTotp = cloud.totp_secret?.takeIf { it.isNotEmpty() }
                            ?.let { CloudCrypto.decryptFromCloud(it) }
                        if (decryptedPw == null || (!cloud.totp_secret.isNullOrEmpty() && decryptedTotp == null)) {
                            errorInsert(
                                "PasswordRepository",
                                "Cloud-Download abgebrochen: Entschlüsselung fehlgeschlagen",
                                Instant.now().toString(),
                                "ERROR"
                            )
                            return@withContext false
                        }
                        passwordDb.passwordDao().insert(
                            PasswordEntry(
                                name = cloud.name,
                                url = cloud.url ?: "",
                                username = cloud.username ?: "",
                                password = decryptedPw,
                                totpSecret = null
                            )
                        )
                        if (conflict.localTotp != null) {
                            cloud.id?.let { id ->
                                Config.client.postgrest.from("password_entries").update(
                                    PasswordEntrySupabase(
                                        name = cloud.name,
                                        url = cloud.url,
                                        username = cloud.username,
                                        encrypted_password = cloud.encrypted_password,
                                        notes = cloud.notes,
                                        totp_secret = CloudCrypto.encryptForCloud(conflict.localTotp)
                                    )
                                ) {
                                    filter { eq("id", id) }
                                }
                            }
                        } else if (!decryptedTotp.isNullOrEmpty()) {
                            twoFaDb.twoFADao().insertOrIgnore(
                                TwoFAEntry(
                                    name = cloud.name,
                                    secret = decryptedTotp,
                                    url = cloud.url ?: ""
                                )
                            )
                        }
                    }
                }

                SyncConflictType.LOCAL_ONLY_ENTRY -> {
                    if (decision == SyncConflictDecision.UPLOAD_LOCAL) {
                        val local = conflict.localEntry ?: return@withContext true
                        val matchedSecret = conflict.localTotp
                        Config.client.postgrest.from("password_entries").insert(
                            PasswordEntrySupabase(
                                name = local.name,
                                url = local.url,
                                username = local.username,
                                encrypted_password = CloudCrypto.encryptForCloud(local.password),
                                notes = local.notes,
                                totp_secret = matchedSecret?.let { CloudCrypto.encryptForCloud(it) }
                            )
                        )
                    }
                }

                SyncConflictType.TOTP_CLOUD_ONLY -> {
                    if (decision == SyncConflictDecision.DELETE_CLOUD) {
                        val cloud = conflict.cloudEntry ?: return@withContext true
                        cloud.id?.let { id ->
                            Config.client.postgrest.from("password_entries").update(
                                PasswordEntrySupabase(
                                    name = cloud.name,
                                    url = cloud.url,
                                    username = cloud.username,
                                    encrypted_password = cloud.encrypted_password,
                                    notes = cloud.notes,
                                    totp_secret = ""
                                )
                            ) {
                                filter { eq("id", id) }
                            }
                        }
                    } else if (decision == SyncConflictDecision.KEEP_CLOUD) {
                        val cloud = conflict.cloudEntry ?: return@withContext true
                        val decryptedTotp = cloud.totp_secret?.takeIf { it.isNotEmpty() }
                            ?.let { CloudCrypto.decryptFromCloud(it) }
                        if (decryptedTotp.isNullOrEmpty()) {
                            errorInsert(
                                "PasswordRepository",
                                "TOTP-Download abgebrochen: Entschlüsselung fehlgeschlagen",
                                Instant.now().toString(),
                                "ERROR"
                            )
                            return@withContext false
                        }
                        twoFaDb.twoFADao().insertOrIgnore(
                            TwoFAEntry(
                                name = cloud.name,
                                secret = decryptedTotp,
                                url = cloud.url ?: ""
                            )
                        )
                    }
                }

                SyncConflictType.PASSWORD_DIFFERENT -> {
                    val cloud = conflict.cloudEntry ?: return@withContext true
                    val local = conflict.localEntry ?: return@withContext true
                    if (decision == SyncConflictDecision.KEEP_CLOUD) {
                        val decryptedPw = CloudCrypto.decryptFromCloud(cloud.encrypted_password)
                        if (decryptedPw.isNullOrEmpty()) {
                            errorInsert(
                                "PasswordRepository",
                                "Passwort-Download abgebrochen: Cloud-Wert leer oder nicht entschlüsselbar",
                                Instant.now().toString(),
                                "ERROR"
                            )
                            return@withContext false
                        }
                        passwordDb.passwordDao().update(
                            local.copy(
                                password = decryptedPw,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else if (decision == SyncConflictDecision.UPLOAD_LOCAL) {
                        if (local.password.isEmpty()) {
                            errorInsert(
                                "PasswordRepository",
                                "Passwort-Upload abgebrochen: lokaler Wert leer",
                                Instant.now().toString(),
                                "ERROR"
                            )
                            return@withContext false
                        }
                        pushCloudEntry(cloud, local.password, cloud.totp_secret)
                    }
                }

                SyncConflictType.TOTP_LOCAL_ONLY,
                SyncConflictType.TOTP_DIFFERENT -> {
                    if (decision == SyncConflictDecision.UPLOAD_LOCAL) {
                        val cloud = conflict.cloudEntry ?: return@withContext true
                        val localTotp = conflict.localTotp ?: return@withContext true
                        cloud.id?.let { id ->
                            Config.client.postgrest.from("password_entries").update(
                                PasswordEntrySupabase(
                                    name = cloud.name,
                                    url = cloud.url,
                                    username = cloud.username,
                                    encrypted_password = cloud.encrypted_password,
                                    notes = cloud.notes,
                                    totp_secret = CloudCrypto.encryptForCloud(localTotp)
                                )
                            ) {
                                filter { eq("id", id) }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            errorInsert(
                "PasswordRepository",
                "Sync-Entscheidung fehlgeschlagen: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            false
        }
    }
}

data class PasswordSyncEntryDiagnosis(
    val name: String,
    val username: String,
    val url: String,
    val passwordLength: Int,
    val totpInCloud: Boolean,
    val passwordDecryptOk: Boolean,
    val totpDecryptOk: Boolean
)

data class PasswordSyncDiagnosis(
    val deviceName: String,
    val realDevice: Boolean,
    val prvtMode: Boolean,
    val masterPasswordSet: Boolean,
    val online: Boolean,
    val localCount: Int,
    val cloudCount: Int,
    val cloudError: String?,
    val lastSyncMillis: Long,
    val entries: List<PasswordSyncEntryDiagnosis>
)

suspend fun diagnosePasswordSync(
    passwordDb: PasswordDatabase,
    context: Context
): PasswordSyncDiagnosis = withContext(Dispatchers.IO) {
    val localCount = try {
        passwordDb.passwordDao().getAll().size
    } catch (_: Exception) {
        -1
    }
    var cloudCount = -1
    var cloudError: String? = null
    val results = mutableListOf<PasswordSyncEntryDiagnosis>()
    try {
        val cloudEntries = Config.client.postgrest.from("password_entries")
            .select().decodeList<PasswordEntrySupabase>()
        cloudCount = cloudEntries.size
        cloudEntries.forEach { cloud ->
            val encrypted = cloud.encrypted_password
            val totpRaw = cloud.totp_secret ?: ""
            val decryptedPw = if (encrypted.isEmpty()) null else CloudCrypto.decryptFromCloud(encrypted)
            results += PasswordSyncEntryDiagnosis(
                name = cloud.name,
                username = cloud.username ?: "",
                url = cloud.url ?: "",
                passwordLength = decryptedPw?.length ?: -1,
                totpInCloud = totpRaw.isNotEmpty(),
                passwordDecryptOk = decryptedPw != null,
                totpDecryptOk = totpRaw.isEmpty() || CloudCrypto.decryptFromCloud(totpRaw) != null
            )
        }
    } catch (e: Exception) {
        cloudError = e.message
    }
    val lastSync = try {
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            .getLong("last_sync_pw_timestamp", 0L)
    } catch (_: Exception) {
        0L
    }
    PasswordSyncDiagnosis(
        deviceName = getDeviceName(),
        realDevice = Config.realDevice,
        prvtMode = prvt(),
        masterPasswordSet = Config.masterPassword.isNotBlank(),
        online = isOnline(context),
        localCount = localCount,
        cloudCount = cloudCount,
        cloudError = cloudError,
        lastSyncMillis = lastSync,
        entries = results.sortedBy { it.name.lowercase() }
    )
}