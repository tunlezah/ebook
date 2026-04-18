package com.shelfwise.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.shelfwise.app.EBookApp
import com.shelfwise.app.R
import com.shelfwise.app.server.HttpFileServer
import com.shelfwise.app.ui.MainActivity
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileServerService : Service() {

    private var server: HttpFileServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer(intent.getIntExtra(EXTRA_PORT, 8080))
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY // Don't auto-restart
    }

    private fun startServer(port: Int) {
        if (server != null) return

        val uploadDir = File(filesDir, "books")
        uploadDir.mkdirs()

        server = HttpFileServer(port, uploadDir) { fileName ->
            // Reset auto-stop timer on file activity
            resetAutoStopTimer()
        }

        try {
            server!!.start()
        } catch (e: Exception) {
            stopSelf()
            return
        }

        // Acquire WiFi lock to keep WiFi alive with screen off
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ShelfWise:FileServer")
        wifiLock?.acquire()

        // Acquire partial wake lock to keep CPU running during transfers
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShelfWise:FileServer")
        wakeLock?.acquire(60 * 60 * 1000L) // Max 1 hour

        val ipAddress = getDeviceIpAddress()
        // Surface the tokenized URL everywhere so users always see the auth-
        // bearing address. The unauthenticated host:port alone is useless now.
        val url = server!!.authenticatedUrl(ipAddress)

        val notification = buildNotification(url)
        startForeground(NOTIFICATION_ID, notification)

        // Set auto-stop timer
        val prefs = (application as EBookApp).container.preferencesManager
        resetAutoStopTimer(prefs.serverAutoStopMinutes)

        // Broadcast server started
        sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, true)
            putExtra(EXTRA_URL, url)
            setPackage(packageName)
        })
    }

    private fun stopServer() {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        server?.stop()
        server = null

        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, false)
            setPackage(packageName)
        })

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resetAutoStopTimer(minutes: Int = 30) {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = Runnable { stopServer() }
        handler.postDelayed(autoStopRunnable!!, minutes * 60 * 1000L)
    }

    private fun buildNotification(url: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FileServerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, EBookApp.CHANNEL_FILE_SERVER)
            .setContentTitle(getString(R.string.transfer_notification_title))
            .setContentText(getString(R.string.transfer_notification_text, url))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.transfer_stop), stopPendingIntent)
            .build()
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        val ipBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()
        return InetAddress.getByAddress(ipBytes).hostAddress ?: "0.0.0.0"
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.shelfwise.START_SERVER"
        const val ACTION_STOP = "com.shelfwise.STOP_SERVER"
        const val EXTRA_PORT = "port"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_URL = "url"
        const val BROADCAST_STATE_CHANGED = "com.shelfwise.SERVER_STATE"
        private const val NOTIFICATION_ID = 1

        fun startServer(context: Context, port: Int) {
            val intent = Intent(context, FileServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            context.startForegroundService(intent)
        }

        fun stopServer(context: Context) {
            val intent = Intent(context, FileServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
