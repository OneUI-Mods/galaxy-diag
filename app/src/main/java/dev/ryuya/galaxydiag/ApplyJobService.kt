package dev.ryuya.galaxydiag

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Future

class ApplyJobService : JobService() {
    private var work: Future<*>? = null
    @Volatile private var stopped = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        if (!RootRunner.prefs(this).getBoolean("auto_boot", false)) return false
        stopped = false
        work =
            RootRunner.executor.submit {
                val result = RootRunner.run(this, "enable")
                handler.post {
                    if (stopped) return@post
                    jobFinished(params, false)
                    val attempt = params.extras.getInt("attempt", 0)
                    if (
                        !result.success &&
                            DiagProtocol.shouldRetry(
                                attempt,
                                RootRunner.prefs(this).getBoolean("auto_boot", false),
                            )
                    ) {
                        BootReceiver.schedule(this, attempt + 1)
                    }
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        work?.cancel(true)
        return false
    }
}
