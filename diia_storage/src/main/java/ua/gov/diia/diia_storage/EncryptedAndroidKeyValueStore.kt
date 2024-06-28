package ua.gov.diia.diia_storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import ua.gov.diia.diia_storage.model.KeyValueStore

class EncryptedAndroidKeyValueStore(
    private val context: Context,
    private val configuration: PreferenceConfiguration,
) : AndroidKeyValueStore(), PinStore, ServiceUserUidStore {

    private var sharedPreferences: SharedPreferences

    init {
        sharedPreferences = buildKeyValue()
    }

    private fun buildKeyValue(): SharedPreferences {
        val masterKeyAlias: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            configuration.preferenceName,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getSharedPreferences(): SharedPreferences = sharedPreferences

    override fun allowedScopes() = configuration.allowedScopes

    override suspend fun savePin(pin: String) {
        sharedPreferences.edit().putString(PIN, pin).apply()
    }

    override suspend fun isPinValid(pinInput: String): Boolean {
        if (sharedPreferences.contains(PIN)) {
            val pin = sharedPreferences.getString(PIN, KeyValueStore.DEFAULT_STRING_VALUE)
            return pin == pinInput
        }
        return false
    }

    override fun pinPresent(): Boolean {
        return sharedPreferences.contains(PIN)
    }

    override suspend fun saveServiceUserUUID(uuid: String) {
        sharedPreferences.edit().putString(SERVICE_USER_UUID, uuid).apply()
    }

    override suspend fun getServiceUserUUID(): String? {
        val result = sharedPreferences.getString(SERVICE_USER_UUID, KeyValueStore.DEFAULT_STRING_VALUE)
        return if (result != KeyValueStore.DEFAULT_STRING_VALUE) {
            result
        } else {
            null
        }
    }

    override fun clear() {
        context.getSharedPreferences(
            configuration.preferenceName,
            Context.MODE_PRIVATE
        ).edit().clear().apply()
        sharedPreferences = buildKeyValue()
        set(CommonPreferenceKeys.IsFirst, false)
    }

    companion object {
        const val PIN = "pin"
        const val SERVICE_USER_UUID = "service_user_uuid"
    }

}
