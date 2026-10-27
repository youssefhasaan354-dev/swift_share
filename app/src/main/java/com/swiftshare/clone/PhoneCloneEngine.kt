package com.swiftshare.clone

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import com.swiftshare.explorer.MediaStoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class CloneCategory {
    CONTACTS,
    CALL_LOGS,
    SMS,
    PHOTOS,
    VIDEOS,
    MUSIC,
    DOCUMENTS,
    APPS
}

data class CategoryMigrationStatus(
    val category: CloneCategory,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val isComplete: Boolean = false
)

@Serializable
data class CallLogEntry(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
)

class PhoneCloneEngine(
    private val context: Context,
    private val mediaRepo: MediaStoreRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _cloneProgress = MutableStateFlow<Map<CloneCategory, CategoryMigrationStatus>>(emptyMap())
    val cloneProgress: StateFlow<Map<CloneCategory, CategoryMigrationStatus>> = _cloneProgress.asStateFlow()

    fun exportContactsVcf(): File {
        val vcfFile = File(context.cacheDir, "contacts_clone.vcf")
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            null,
            null,
            null
        )
        cursor?.use { c ->
            FileOutputStream(vcfFile).use { fos ->
                val buffer = ByteArray(4096)
                while (c.moveToNext()) {
                    val lookupKey = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
                    try {
                        val afd: AssetFileDescriptor? = context.contentResolver.openAssetFileDescriptor(uri, "r")
                        afd?.createInputStream()?.use { input ->
                            var len: Int
                            while (input.read(buffer).also { len = it } != -1) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return vcfFile
    }

    @SuppressLint("MissingPermission")
    fun exportCallLogs(): File {
        val file = File(context.cacheDir, "call_logs.json")
        val logs = mutableListOf<CallLogEntry>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val num = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: ""
                    val type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val dur = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                    logs.add(CallLogEntry(num, type, date, dur))
                }
            }
        } catch (_: Exception) {}

        file.writeText(Json.encodeToString(logs))
        return file
    }

    @SuppressLint("MissingPermission")
    fun importCallLogs(file: File) {
        if (!file.exists()) return
        try {
            val jsonStr = file.readText()
            val list = Json.decodeFromString<List<CallLogEntry>>(jsonStr)
            for (entry in list) {
                val values = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, entry.number)
                    put(CallLog.Calls.TYPE, entry.type)
                    put(CallLog.Calls.DATE, entry.date)
                    put(CallLog.Calls.DURATION, entry.duration)
                    put(CallLog.Calls.NEW, 0)
                }
                context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
            }
        } catch (e: Exception) {
            com.swiftshare.common.AppLogger.e("PhoneClone", "Failed to restore call log: ${e.message}", e, notifyUser = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun importContactsVcf(vcfFile: File): Int {
        if (!vcfFile.exists()) return 0
        var importedCount = 0
        try {
            val lines = vcfFile.readLines()
            val ops = ArrayList<android.content.ContentProviderOperation>()
            var insideVcard = false
            var currentFn = ""
            val currentPhones = mutableListOf<String>()
            val currentEmails = mutableListOf<String>()

            fun flushContact() {
                if (currentFn.isBlank() && currentPhones.isEmpty()) return
                val rawContactIndex = ops.size
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                val name = currentFn.ifBlank { currentPhones.firstOrNull() ?: "Contact" }
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        .build()
                )

                for (phone in currentPhones) {
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                            .build()
                    )
                }

                for (email in currentEmails) {
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                            .build()
                    )
                }
                importedCount++

                if (ops.size >= 100) {
                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (e: Exception) {
                        com.swiftshare.common.AppLogger.e("PhoneClone", "Batch contact insertion failed: ${e.message}", e)
                    }
                    ops.clear()
                }
            }

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.equals("BEGIN:VCARD", ignoreCase = true)) {
                    insideVcard = true
                    currentFn = ""
                    currentPhones.clear()
                    currentEmails.clear()
                } else if (line.equals("END:VCARD", ignoreCase = true)) {
                    if (insideVcard) {
                        flushContact()
                        insideVcard = false
                    }
                } else if (insideVcard) {
                    val upper = line.uppercase()
                    when {
                        upper.startsWith("FN:") || upper.startsWith("FN;") -> {
                            currentFn = line.substringAfter(":").trim()
                        }
                        upper.startsWith("N:") && currentFn.isBlank() -> {
                            currentFn = line.substringAfter(":").replace(";", " ").trim()
                        }
                        upper.startsWith("TEL:") || upper.startsWith("TEL;") -> {
                            val num = line.substringAfter(":").trim()
                            if (num.isNotBlank() && !currentPhones.contains(num)) {
                                currentPhones.add(num)
                            }
                        }
                        upper.startsWith("EMAIL:") || upper.startsWith("EMAIL;") -> {
                            val email = line.substringAfter(":").trim()
                            if (email.isNotBlank() && !currentEmails.contains(email)) {
                                currentEmails.add(email)
                            }
                        }
                    }
                }
            }

            if (ops.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
        } catch (e: Exception) {
            com.swiftshare.common.AppLogger.e("PhoneClone", "Failed to parse contacts VCF: ${e.message}", e, notifyUser = true)
        }
        return importedCount
    }

    fun autoRestoreReceivedCloneFiles(dir: File) {
        val vcf = File(dir, "contacts_clone.vcf")
        if (vcf.exists()) {
            importContactsVcf(vcf)
        }
        val callLogs = File(dir, "call_logs.json")
        if (callLogs.exists()) {
            importCallLogs(callLogs)
        }
    }

    fun prepareCloneManifest(): Map<CloneCategory, Int> {
        val map = mutableMapOf<CloneCategory, Int>()
        map[CloneCategory.PHOTOS] = mediaRepo.getPhotos().size
        map[CloneCategory.VIDEOS] = mediaRepo.getVideos().size
        map[CloneCategory.MUSIC] = mediaRepo.getAudio().size
        map[CloneCategory.DOCUMENTS] = mediaRepo.getDocuments().size
        map[CloneCategory.APPS] = mediaRepo.getInstalledApps().size
        map[CloneCategory.CONTACTS] = 1
        map[CloneCategory.CALL_LOGS] = 1
        return map
    }
}
