package com.tabslify.tabs.authenticator

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillEventHistory
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.net.toUri
import com.tabslify.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.core.content.edit

class TabslifyAutofillService : AutofillService() {

    companion object {
        private const val TAG = "Tabslify_AUTOFILL"
        private const val LAST_USED_PREFS = "autofill_last_used"
        private const val PW_KEY_PREFIX = "pw_"
        private const val OTP_KEY_PREFIX = "otp_"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        try {
            recordSelectedDatasets()
        } catch (_: Exception) {
        }
        val structure = request.fillContexts.last().structure
        val loginFields = findLoginFields(structure)

        if (loginFields.usernameId == null && loginFields.passwordId == null && loginFields.otpId == null) {
            callback.onSuccess(null)
            return
        }

        val domain = extractRequestDomain(structure)
        val targetPackageName = structure.activityComponent.packageName
        Log.d(
            TAG,
            "AutoFill request – domain: $domain  pkg: $targetPackageName"
        )

        val inlineRequest = request.inlineSuggestionsRequest
        val responseBuilder = FillResponse.Builder()
        var hasDataset = false
        var presentationIndex = 0
        val lastUsedPrefs = getSharedPreferences(LAST_USED_PREFS, MODE_PRIVATE)

        if (loginFields.usernameId != null || loginFields.passwordId != null) {
            val db = PasswordDatabase.getDatabase(applicationContext)
            val entries = runBlocking(Dispatchers.IO) {
                if (domain.isNotEmpty()) {
                    db.passwordDao().findByDomain(domain)
                } else {
                    val tokens = packageTokens(targetPackageName)
                    if (tokens.isEmpty()) {
                        db.passwordDao().search(targetPackageName)
                    } else {
                        tokens.flatMap { db.passwordDao().search(it) }.distinctBy { it.id }
                    }
                }
            }
            val sortedEntries = entries.sortedWith(
                compareByDescending<PasswordEntry> { lastUsedPrefs.getLong(PW_KEY_PREFIX + it.id, 0L) }
                    .thenBy { it.name.lowercase() }
            )
            sortedEntries.take(5).forEach { entry ->
                val ds = Dataset.Builder().setId(PW_KEY_PREFIX + entry.id)

                loginFields.usernameId?.let { id ->
                    val field = Field.Builder()
                        .setValue(AutofillValue.forText(entry.username))
                        .setPresentations(buildPresentations(entry, presentationIndex, inlineRequest))
                        .build()
                    ds.setField(id, field)
                }

                loginFields.passwordId?.let { id ->
                    val field = Field.Builder()
                        .setValue(AutofillValue.forText(entry.password))
                        .setPresentations(buildPresentations(entry, presentationIndex, inlineRequest))
                        .build()
                    ds.setField(id, field)
                }

                responseBuilder.addDataset(ds.build())
                hasDataset = true
                presentationIndex++
            }
        }

        loginFields.otpId?.let { otpFieldId ->
            val twoFaDb = TwoFADatabase.getDatabase(applicationContext)
            val twoFaEntries = runBlocking(Dispatchers.IO) { twoFaDb.twoFADao().getAll() }
            val tokens = packageTokens(targetPackageName)
            val matched = twoFaEntries.filter { entry ->
                if (entry.secret.isBlank() || entry.name.isBlank()) return@filter false
                val nameKey = entry.name.lowercase().replace(" ", "")
                if (domain.isNotEmpty()) {
                    entry.url.contains(domain, ignoreCase = true) ||
                            nameKey.contains(domain.substringBefore(".")) ||
                            domain.contains(nameKey)
                } else {
                    tokens.any { token ->
                        entry.url.contains(token, ignoreCase = true) || nameKey.contains(token)
                    }
                }
            }
            val sortedMatched = matched.sortedWith(
                compareByDescending<TwoFAEntry> { lastUsedPrefs.getLong(OTP_KEY_PREFIX + it.id, 0L) }
                    .thenBy { it.name.lowercase() }
            )
            sortedMatched.take(3).forEach { entry ->
                val code = TotpGenerator.generateTOTP(entry.secret)
                if (code == "ERROR" || code.isBlank()) return@forEach
                val field = Field.Builder()
                    .setValue(AutofillValue.forText(code))
                    .setPresentations(
                        buildPresentations(
                            PasswordEntry(
                                name = entry.name,
                                username = code,
                                password = "",
                                totpSecret = null
                            ),
                            presentationIndex, inlineRequest
                        )
                    )
                    .build()
                val ds = Dataset.Builder().setId(OTP_KEY_PREFIX + entry.id).setField(otpFieldId, field)
                responseBuilder.addDataset(ds.build())
                hasDataset = true
                presentationIndex++
            }
        }

        val saveIds = listOfNotNull(loginFields.usernameId, loginFields.passwordId)
        var hasSaveInfo = false
        if (loginFields.passwordId != null && saveIds.isNotEmpty()) {
            var saveType = SaveInfo.SAVE_DATA_TYPE_PASSWORD
            if (loginFields.usernameId != null) saveType = saveType or SaveInfo.SAVE_DATA_TYPE_USERNAME
            responseBuilder.setSaveInfo(
                SaveInfo.Builder(saveType, saveIds.toTypedArray()).build()
            )
            hasSaveInfo = true
        }

        if (!hasDataset && !hasSaveInfo) {
            callback.onSuccess(null); return
        }
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.last().structure
        val fields = findLoginFields(structure)
        val values = collectValues(structure, setOfNotNull(fields.usernameId, fields.passwordId))
        val username = fields.usernameId?.let { values[it] }?.trim().orEmpty()
        val password = fields.passwordId?.let { values[it] }?.trim().orEmpty()
        if (password.isEmpty()) {
            callback.onSuccess(); return
        }

        val domain = extractRequestDomain(structure)
        val targetPackageName = structure.activityComponent.packageName
        val tokens = packageTokens(targetPackageName)
        val label = domain.ifEmpty { tokens.firstOrNull() ?: targetPackageName }
        val urlValue = domain.ifEmpty { targetPackageName }

        val usedId = runBlocking(Dispatchers.IO) {
            val dao = PasswordDatabase.getDatabase(applicationContext).passwordDao()
            val existing = (
                    if (domain.isNotEmpty()) dao.findByDomain(domain)
                    else tokens.flatMap { dao.search(it) }.distinctBy { it.id }
                    ).firstOrNull { it.username.equals(username, ignoreCase = true) }
            if (existing != null) {
                dao.update(existing.copy(password = password, updatedAt = System.currentTimeMillis()))
                existing.id
            } else {
                dao.insert(
                    PasswordEntry(
                        name = label,
                        url = urlValue,
                        username = username,
                        password = password,
                        totpSecret = null
                    )
                ).toInt()
            }
        }
        try {
            markDatasetUsed(PW_KEY_PREFIX + usedId)
        } catch (_: Exception) {
        }
        callback.onSuccess()
    }

    private fun markDatasetUsed(datasetId: String) {
        getSharedPreferences(LAST_USED_PREFS, MODE_PRIVATE)
            .edit {
                putLong(datasetId, System.currentTimeMillis())
            }
    }

    private fun recordSelectedDatasets() {
        @Suppress("DEPRECATION")
        val history = fillEventHistory ?: return
        val editor = getSharedPreferences(LAST_USED_PREFS, MODE_PRIVATE).edit()
        var changed = false
        for (event in history.events.orEmpty()) {
            if (event.type == FillEventHistory.Event.TYPE_DATASET_SELECTED) {
                val datasetId = event.datasetId ?: continue
                if (datasetId.startsWith(PW_KEY_PREFIX) || datasetId.startsWith(OTP_KEY_PREFIX)) {
                    editor.putLong(datasetId, System.currentTimeMillis())
                    changed = true
                }
            }
        }
        if (changed) editor.apply()
    }

    private fun collectValues(
        structure: AssistStructure,
        ids: Set<AutofillId>
    ): Map<AutofillId, String> {
        val out = HashMap<AutofillId, String>()
        fun walk(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            val v = node.autofillValue
            if (id != null && id in ids && v != null && v.isText) {
                out[id] = v.textValue.toString()
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
        return out
    }

    data class LoginFields(
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val otpId: AutofillId? = null
    )

    private fun findLoginFields(structure: AssistStructure): LoginFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var otpId: AutofillId? = null

        fun searchNode(node: AssistStructure.ViewNode) {
            val hints = node.autofillHints
            val inputType = node.inputType
            val hint = node.hint?.lowercase() ?: ""
            val idEntry = node.idEntry?.lowercase() ?: ""
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val inputVariation = inputType and InputType.TYPE_MASK_VARIATION
            val isTextField = inputClass == InputType.TYPE_CLASS_TEXT
            val htmlAttributes =
                node.htmlInfo?.attributes?.associate { it.first.lowercase() to (it.second?.lowercase() ?: "") }
                    ?: emptyMap()
            val htmlType = htmlAttributes["type"] ?: ""
            val htmlAutocomplete = htmlAttributes["autocomplete"] ?: ""
            val htmlName = htmlAttributes["name"] ?: ""

            val isOtp = (
                    hints?.any { h ->
                        h.contains("one-time-code", true) || h.contains(
                            "otp",
                            true
                        )
                    } == true
                            || hint.contains("otp") || hint.contains("einmal") || hint.contains("token") || hint.contains(
                        "authenticator"
                    ) || hint.contains("pin")
                            || idEntry.contains("otp") || idEntry.contains("totp") || idEntry.contains(
                        "token"
                    ) || idEntry.contains("mfa") || idEntry.contains("tfa") || idEntry.contains("pin")
                            || htmlAutocomplete.contains("one-time-code") || htmlAutocomplete.contains("otp")
                    )

            val isPassword = !isOtp && (
                    hints?.any { h ->
                        h.contains(
                            "password",
                            true
                        ) || h == "current-password" || h == "new-password"
                    } == true
                            || (isTextField && inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                            || (isTextField && inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                            || (isTextField && inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                            || hint.contains("passwort") || hint.contains("password")
                            || idEntry.contains("password") || idEntry.contains("passwd") || idEntry.contains(
                        "pwd"
                    )
                            || htmlType == "password"
                            || htmlAutocomplete == "current-password" || htmlAutocomplete == "new-password"
                            || htmlName.contains("pass") || htmlName.contains("pwd")
                    )

            val isUsername = !isOtp && !isPassword && (
                    hints?.any { h ->
                        h.contains("username", true) || h.contains(
                            "email",
                            true
                        ) || h == "username" || h == "email"
                    } == true
                            || hint.contains("user") || hint.contains("email") || hint.contains("benutzername")
                            || hint.contains("login") || hint.contains("e-mail")
                            || idEntry.contains("user") || idEntry.contains("email") || idEntry.contains(
                        "login"
                    )
                            || (isTextField && inputVariation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                            || (isTextField && inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
                            || htmlType == "email"
                            || htmlAutocomplete == "username" || htmlAutocomplete == "email"
                            || htmlName.contains("user") || htmlName.contains("email") || htmlName.contains(
                        "login"
                    )
                    )

            if (node.autofillId != null && node.autofillType != android.view.View.AUTOFILL_TYPE_NONE) {
                if (isOtp && otpId == null) otpId = node.autofillId
                if (isPassword && passwordId == null) passwordId = node.autofillId
                if (isUsername && usernameId == null) usernameId = node.autofillId
            }

            for (i in 0 until node.childCount) searchNode(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            searchNode(structure.getWindowNodeAt(i).rootViewNode)
        }

        return LoginFields(usernameId, passwordId, otpId)
    }

    private val multiPartTlds = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk",
        "co.jp", "co.kr", "co.in", "co.nz",
        "com.au", "com.br", "com.cn"
    )

    private fun rootDomain(host: String): String {
        val labels = host.split(".")
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in multiPartTlds) labels.takeLast(3).joinToString(".") else lastTwo
    }

    private fun extractDomain(raw: String): String {
        return try {
            val normalized = if (raw.contains("://")) raw else "https://$raw"
            val host = normalized.toUri().host ?: return ""
            rootDomain(host.lowercase().removePrefix("www.").trim())
        } catch (_: Exception) {
            ""
        }
    }

    private val packageStopwords = setOf(
        "com", "org", "net", "io", "app", "apps", "android",
        "mobile", "inc", "co", "www", "client", "prod"
    )

    private fun packageTokens(pkg: String): List<String> {
        return pkg.lowercase()
            .split(".")
            .filter { it.isNotBlank() && it !in packageStopwords && it.length >= 3 }
    }

    private fun extractRequestDomain(structure: AssistStructure): String {
        fun searchWebDomain(node: AssistStructure.ViewNode): String? {
            node.webDomain?.takeIf { it.isNotBlank() }?.let { return it }
            for (i in 0 until node.childCount) {
                searchWebDomain(node.getChildAt(i))?.let { return it }
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val domain = searchWebDomain(structure.getWindowNodeAt(i).rootViewNode)
            if (!domain.isNullOrBlank()) return extractDomain(domain)
        }

        for (i in 0 until structure.windowNodeCount) {
            val title = structure.getWindowNodeAt(i).title?.toString() ?: ""
            val urlRegex = Regex("""https?://[^\s/$.?#].\S*""")
            urlRegex.find(title)?.value?.let { return extractDomain(it) }
        }

        return ""
    }

    private fun createInlinePresentation(
        inlineRequest: InlineSuggestionsRequest,
        index: Int,
        entry: PasswordEntry
    ): InlinePresentation? {
        val specs = inlineRequest.inlinePresentationSpecs
        if (specs.isEmpty()) return null
        val spec = if (index < specs.size) specs[index] else specs.last()
        return try {
            createInlineChip(spec, entry, 0xA710 + index)
        } catch (e: Exception) {
            Log.e(TAG, "createInlinePresentation failed: ${e.message}")
            null
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createInlineChip(
        spec: InlinePresentationSpec,
        entry: PasswordEntry,
        requestCode: Int
    ): InlinePresentation? {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
            val pendingIntent = PendingIntent.getActivity(
                this, requestCode,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            val content = InlineSuggestionUi.newContentBuilder(pendingIntent)
                .setTitle(entry.name)
                .setSubtitle(entry.username.ifEmpty { getString(R.string.kein_benutzername) })
                .setStartIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_lock))
                .build()
            InlinePresentation(content.slice, spec, false)
        } catch (e: Exception) {
            Log.e(TAG, "createInlineChip failed: ${e.message}")
            null
        }
    }

    private fun buildPresentations(
        entry: PasswordEntry,
        index: Int,
        inlineRequest: InlineSuggestionsRequest?
    ): Presentations {
        val remoteView = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, "☁️ ${entry.name}")
            setTextViewText(android.R.id.text2, entry.username.ifEmpty { getString(R.string.kein_benutzername_2) })
        }
        val builder = Presentations.Builder().setMenuPresentation(remoteView)
        inlineRequest?.let {
            createInlinePresentation(
                it,
                index,
                entry
            )?.let { ip -> builder.setInlinePresentation(ip) }
        }
        return builder.build()
    }
}