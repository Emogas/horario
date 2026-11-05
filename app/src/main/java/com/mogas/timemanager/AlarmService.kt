package com.mogas.timemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wl: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishAll() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val id = i?.getIntExtra("id", -1) ?: -1
        when (i?.action) {
            "STOP" -> {
                finishAll()
                return START_NOT_STICKY
            }
            "SNOOZE" -> {
                Store.get(this, id)?.let { Scheduler.snooze(this, it) }
                finishAll()
                return START_NOT_STICKY
            }
        }
        val a = Store.get(this, id) ?: Alarm(id, 0, 0, "Alarme")
        startRinging(a)
        return START_NOT_STICKY
    }

    private fun startRinging(a: Alarm) {
        cleanup()
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("alarm", "Alarmes", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)

        val full = PendingIntent.getActivity(
            this, a.id,
            Intent(this, AlarmActivity::class.java).putExtra("id", a.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction("SNOOZE").putExtra("id", a.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (a.label.isEmpty()) "Alarme" else a.label)
            .setContentText(if (a.lead > 0) "Começa às " + a.timeText() else a.timeText())
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "Adiar ${a.snooze} min", snoozePi)
            .addAction(0, "Parar", stopPi)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }

        try {
            val pm = getSystemService(PowerManager::class.java)
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mtm:alarm")
            wl?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
        }

        try {
            val am = getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max * 0.6) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt(), 0)
            }
        } catch (e: Exception) {
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uris = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (u in uris) {
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, u)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                break
            } catch (e: Exception) {
                player?.release()
                player = null
            }
        }

        try {
            val vib = getSystemService(Vibrator::class.java)
            @Suppress("DEPRECATION")
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attrs)
        } catch (e: Exception) {
        }

        h.postDelayed(timeout, 10 * 60 * 1000L)
    }

    private fun cleanup() {
        h.removeCallbacks(timeout)
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        try {
            getSystemService(Vibrator::class.java)?.cancel()
        } catch (e: Exception) {
        }
        try {
            if (wl?.isHeld == true) wl?.release()
        } catch (e: Exception) {
        }
    }

    private fun finishAll() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent("com.mogas.timemanager.DONE").setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
