package de.coldtea.smplr.smplralarm.receivers

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.coldtea.smplr.smplralarm.alarmlogs.LogsRepository
import de.coldtea.smplr.smplralarm.alarmlogs.RangAlarmObject
import de.coldtea.smplr.smplralarm.extensions.getTimeExactForAlarmInMiliseconds
import de.coldtea.smplr.smplralarm.extensions.showNotification
import de.coldtea.smplr.smplralarm.extensions.showNotificationWithIntent
import de.coldtea.smplr.smplralarm.managers.AlarmNotificationManager
import de.coldtea.smplr.smplralarm.managers.ChannelManager
import de.coldtea.smplr.smplralarm.models.IntentNotificationItem
import de.coldtea.smplr.smplralarm.models.NotificationChannelItem
import de.coldtea.smplr.smplralarm.models.NotificationItem
import de.coldtea.smplr.smplralarm.repository.AlarmNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

internal class AlarmReceiver : BroadcastReceiver() {
    private var repository: AlarmNotificationRepository? = null

    override fun onReceive(context: Context, intent: Intent) {
            try {
                repository = AlarmNotificationRepository(context)

                val logsRepository = LogsRepository(context.applicationContext)

                val requestId = intent.getIntExtra(SmplrAlarmReceiverObjects.SMPLR_ALARM_RECEIVER_INTENT_ID, -1)

                Timber.v("SmplrAlarm.AlarmReceiver.onReceive --> $requestId")

                if(requestId == -1) return

                CoroutineScope(Dispatchers.IO).launch {

                    repository?.let {

                        val alarmNotification = it.getAlarmNotification(requestId)

                        context.showNotificationWithIntent(
                            alarmNotification.notificationChannelItem,
                            IntentNotificationItem(
                                alarmNotification.fullScreenIntent,
                                alarmNotification.notificationItem
                            )
                        )

                        if(!it.deleteAlarmNotificationWithResult(requestId)){
                            resetTheAlarmForTheNextDayOnTheList(context, requestId)
                        }

                    }

                }

                val now = Calendar.getInstance().dateTime()
                val alarmInitTime = intent.getStringExtra("AlarmInitTime")

                logsRepository.logAlarm(
                    RangAlarmObject(
                        now.first,
                        now.second
                    )
                )

            } catch (e: Exception) {
                Timber.e("SmplrAlarm.AlarmReceiver.onReceive: exception --> $e")
            }
    }

    private suspend fun resetTheAlarmForTheNextDayOnTheList(context: Context, requestId: Int) = repository?.let{
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance()
        val alarm = it.getAlarmNotification(requestId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.alarmNotificationId,
            build(context).putExtra(
                SmplrAlarmReceiverObjects.SMPLR_ALARM_RECEIVER_INTENT_ID,
                alarm.alarmNotificationId
            ),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.getTimeExactForAlarmInMiliseconds(
                alarm.hour,
                alarm.min,
                alarm.weekDays,
                1
            ),
            pendingIntent
        )

    }

    private fun Calendar.dateTime():Pair<String, String>{
        val sdfDate = SimpleDateFormat("dd/M/yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm:ss", Locale.getDefault())

        return sdfDate.format(time) to sdfTime.format(time)
    }

    companion object {
        fun build(context: Context): Intent {
            return Intent(context, AlarmReceiver::class.java)
        }
    }

}