package io.github.spoonart1.cleanarchwithagent.sync

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers where the last pull left off.
 *
 * Behind an interface so sync tests can supply an in-memory implementation
 * rather than touching real preferences.
 */
interface SyncTokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

@Singleton
class PreferencesSyncTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncTokenStore {

    private val preferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_SYNC_TOKEN, null)

    override fun write(token: String) {
        preferences.edit { putString(KEY_SYNC_TOKEN, token) }
    }

    override fun clear() {
        preferences.edit { remove(KEY_SYNC_TOKEN) }
    }

    private companion object {
        const val KEY_SYNC_TOKEN = "sync_token"
    }
}
