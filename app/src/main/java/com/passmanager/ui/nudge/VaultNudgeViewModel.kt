package com.passmanager.ui.nudge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/** When the app is allowed to bring up backup and device-binding prompts. */
object VaultNudgePolicy {
    /** A backup older than this is worth mentioning; roughly "you have changed things since". */
    const val BACKUP_OVERDUE_AFTER_DAYS = 30
    /** How long a dismissed backup reminder stays quiet. */
    const val BACKUP_SNOOZE_DAYS = 7
    /** Below this, a vault has little to lose and a backup prompt is just noise. */
    const val FIRST_BACKUP_ITEM_THRESHOLD = 5
    const val DAY_MS = 24L * 60 * 60 * 1000
}

/** At most one prompt is ever shown, and only on entry to the main screen. */
sealed interface VaultNudge {
    /** A pre-v2 vault that has never been offered device binding. Shown exactly once, ever. */
    data object DeviceBinding : VaultNudge
    /** A vault with real content and no backup file at all. */
    data object FirstBackup : VaultNudge
    data class BackupOverdue(val days: Int) : VaultNudge
}

/**
 * Decides whether to prompt the user about device binding or a backup, once per entry to the main
 * screen.
 *
 * Evaluated a single time in [init] rather than continuously: a prompt that appears mid-session
 * because a flow re-emitted is the kind of interruption people learn to dismiss without reading,
 * which is the opposite of what these two prompts are for.
 */
@HiltViewModel
class VaultNudgeViewModel @Inject constructor(
    private val appSettings: AppSettingsPort,
    private val metadataRepository: MetadataRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _nudge = MutableStateFlow<VaultNudge?>(null)
    val nudge: StateFlow<VaultNudge?> = _nudge.asStateFlow()

    init {
        viewModelScope.launch { evaluate(System.currentTimeMillis()) }
    }

    internal suspend fun evaluate(now: Long) {
        val metadata = metadataRepository.get() ?: return
        val declined = appSettings.deviceBindingPromptDeclined.first()

        if (!metadata.isDeviceBound && !declined) {
            _nudge.value = VaultNudge.DeviceBinding
            return
        }

        val snoozedAt = appSettings.backupReminderSnoozedAtMs.first()
        val snoozeActive = snoozedAt != null &&
            daysBetween(snoozedAt, now) < VaultNudgePolicy.BACKUP_SNOOZE_DAYS
        if (snoozeActive) return

        val lastExportAt = appSettings.lastExportAtMs.first()
        if (lastExportAt == null) {
            // A device-bound vault with no backup is the sharpest edge in the app: nothing else
            // can recover it. Below the threshold there is little to lose, so it stays quiet.
            if (vaultRepository.count() >= VaultNudgePolicy.FIRST_BACKUP_ITEM_THRESHOLD) {
                _nudge.value = VaultNudge.FirstBackup
            }
            return
        }

        val age = daysBetween(lastExportAt, now)
        if (age >= VaultNudgePolicy.BACKUP_OVERDUE_AFTER_DAYS) {
            _nudge.value = VaultNudge.BackupOverdue(age)
        }
    }

    /**
     * The device-binding prompt is one-shot by contract: whether the user goes to Settings or
     * waves it away, it never appears again and the offer lives in Settings from then on.
     */
    fun dismissDeviceBindingPrompt() {
        _nudge.value = null
        viewModelScope.launch { appSettings.setDeviceBindingPromptDeclined(true) }
    }

    fun snoozeBackupReminder() {
        _nudge.value = null
        viewModelScope.launch { appSettings.setBackupReminderSnoozedAt(System.currentTimeMillis()) }
    }

    private fun daysBetween(fromMs: Long, toMs: Long): Int =
        (max(0L, toMs - fromMs) / VaultNudgePolicy.DAY_MS).toInt()
}
