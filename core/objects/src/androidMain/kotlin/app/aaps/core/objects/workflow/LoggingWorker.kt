package app.aaps.core.objects.workflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Base class for all expedited WorkManager workers (notably the BG-source runners that
 * pull new glucose values and push them into the loop).
 *
 * On API 31+ `setExpedited` runs the task immediately as a foreground service. On pre-31 the
 * same API is simulated **by WorkManager** via foreground service — which requires us to
 * provide a [getForegroundInfo] implementation. Without it WorkManager throws
 * `IllegalStateException: Not implemented` and the worker silently drops its payload.
 *
 * Three non-negotiable lines to keep the data pipeline alive (SKILL.md §4):
 *   1. `setExpedited` stays — this is what gives us "run right now" behaviour. Never downgrade
 *      to a regular work request (JobScheduler can defer it for minutes → missed pump status).
 *   2. This [getForegroundInfo] returns a single **IMPORTANCE_MIN silent notification** that
 *      WorkManager can piggyback on for the duration of the expedited task. The channel is
 *      created on demand so we don't leak it across the process lifetime.
 *   3. We do NOT cancel `setExpedited` on the caller's side when `getForegroundInfo` exists.
 *      The two pieces are the correct combination.
 */
abstract class LoggingWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val dispatcher: CoroutineDispatcher,
    val aapsLogger: AAPSLogger,
    val fabricPrivacy: FabricPrivacy
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result =
        withContext(dispatcher) {
            doWorkAndLog().also {
                aapsLogger.debug(LTag.WORKER, "Worker result ${it::class.java.simpleName.uppercase()} for ${this@LoggingWorker::class.java} ${it.outputData}")
            }
        }

    /**
     * IMPORTANCE_MIN silent notification. WorkManager shows it briefly on expedited tasks
     * on pre-31; on 31+ it is still required for the API contract but the actual behaviour
     * is handled elsewhere by the system. The channel is created only if missing so workers
     * that rarely run don't spam the notification manager.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        synchronized(CHANNEL_LOCK) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AAPS Background Work",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    setBypassDnd(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
        val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("AAPS")
            .setContentText(" ")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    abstract suspend fun doWorkAndLog(): Result

    companion object {
        private const val CHANNEL_ID = "aaps_worker_expedited"
        private const val NOTIFICATION_ID = 0xA5A5 // stable across rebuilds
        private val CHANNEL_LOCK = Any()
    }
}
