package dev.ryuya.galaxydiag

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
                intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        )
            return
        if (RootRunner.prefs(context).getBoolean("auto_boot", false)) schedule(context, 0)
    }

    companion object {
        const val JOB_ID = 8101

        fun schedule(context: Context, attempt: Int) {
            val extras = PersistableBundle().apply { putInt("attempt", attempt) }
            context
                .getSystemService(JobScheduler::class.java)
                .schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(context, ApplyJobService::class.java))
                        .setMinimumLatency(if (attempt == 0) 15_000 else 30_000)
                        .setOverrideDeadline(120_000)
                        .setExtras(extras)
                        .build()
                )
        }
    }
}
