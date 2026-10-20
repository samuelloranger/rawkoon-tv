package cloud.rawkoon.tv.launcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import cloud.rawkoon.tv.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps the launcher rows fresh when the app isn't opened: every few hours, with a network, for the last used profile. */
class LauncherSyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as App
        work = scope.launch {
            try {
                app.launcher.syncLastUsed(app.profiles.lastUsed())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skipped this round; the next period tries again
            } finally {
                // A job the system stopped is already finished as far as it is concerned
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    /** The system wants the job's constraints back: stop, and run again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 3001
        private const val PERIOD_MS = 3 * 60 * 60 * 1000L

        /** Schedules the job unless it already is: it survives restarts, so this is cheap on every start. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, LauncherSyncJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }
    }
}
