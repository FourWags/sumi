package dev.fourwags.sumi.inputmethod.compat

import android.content.Context
import android.os.UserManager
import androidx.annotation.IntDef

/** Public platform APIs are available throughout Sumi's Android 10+ range. */
object UserManagerCompatUtils {
    const val LOCK_STATE_UNKNOWN = 0
    const val LOCK_STATE_UNLOCKED = 1
    const val LOCK_STATE_LOCKED = 2
    /**
     * Check if the calling user is running in an "unlocked" state. A user is unlocked only after
     * they've entered their credentials (such as a lock pattern or PIN), and credential-encrypted
     * private app data storage is available.
     * @param context context from which [UserManager] should be obtained.
     * @return One of [LockState].
     */
    @kotlin.jvm.JvmStatic
    @LockState
    fun getUserLockState(context: Context): Int {
        val userManager = context.getSystemService(UserManager::class.java)
                ?: return LOCK_STATE_UNKNOWN
        val result = userManager.isUserUnlocked
        return if (result) LOCK_STATE_UNLOCKED else LOCK_STATE_LOCKED
    }

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(LOCK_STATE_UNKNOWN, LOCK_STATE_UNLOCKED, LOCK_STATE_LOCKED)
    annotation class LockState

}
