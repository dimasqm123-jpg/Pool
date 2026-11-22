package com.dilan.poolguide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.dilan.poolguide.STOP"
        const val CHANNEL = "overlay"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var table: TableView? = null
    private var panel: LinearLayout? = null
    private lateinit var tableLp: WindowManager.LayoutParams
    private lateinit var panelLp: WindowManager.LayoutParams
    private var locked = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (table == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            buildTable()
            buildPanel()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Pool Guide activo")
            .setContentText("Toca Detener para cerrar el overlay")
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_delete), "Detener", stop
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun tableFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (locked) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun buildTable() {
        val tv = TableView(this)
        table = tv
        tableLp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            tableFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            // Android 12+: los toques solo atraviesan overlays con opacidad <= 0.8
            alpha = 0.8f
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(tv, tableLp)
    }

    private fun baseBtn(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        minimumWidth = dp(44)
        minimumHeight = dp(44)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    /** Botón que repite la acción mientras lo mantienes apretado. */
    private fun holdBtn(label: String, step: () -> Unit): TextView {
        val t = baseBtn(label)
        val rep = object : Runnable {
            override fun run() {
                step()
                handler.postDelayed(this, 50)
            }
        }
        t.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    step()
                    handler.postDelayed(rep, 350)
                    v.alpha = 0.5f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(rep)
                    v.alpha = 1f
                }
            }
            true
        }
        return t
    }

    private fun buildPanel() {
        val tv = table ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 20, 20, 20))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        panel = row

        val drag = baseBtn("✥")
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        drag.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = panelLp.x; oy = panelLp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    panelLp.x = ox + (e.rawX - sx).toInt()
                    panelLp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(row, panelLp)
                }
            }
            true
        }

        val lock = baseBtn("🔒")
        lock.setOnClickListener {
            locked = !locked
            tv.editMode = !locked
            tableLp.flags = tableFlags()
            wm.updateViewLayout(tv, tableLp)
            tv.invalidate()
            lock.text = if (locked) "✏️" else "🔒"
        }

        val rotL = holdBtn("⟲") { tv.angle -= 0.1f; tv.invalidate() }
        val rotR = holdBtn("⟳") { tv.angle += 0.1f; tv.invalidate() }
        val smaller = holdBtn("−") { tv.tw *= 0.997f; tv.invalidate() }
        val bigger = holdBtn("+") { tv.tw *= 1.003f; tv.invalidate() }

        val alpha = baseBtn("◐")
        alpha.setOnClickListener {
            tv.tableAlpha = if (tv.tableAlpha <= 0.35f) 1f else tv.tableAlpha - 0.2f
            tv.invalidate()
        }

        val close = baseBtn("✕")
        close.setOnClickListener { stopSelf() }

        for (v in listOf<View>(drag, lock, rotL, rotR, smaller, bigger, alpha, close)) row.addView(v)

        panelLp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(8)
        }
        wm.addView(row, panelLp)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { table?.let { wm.removeView(it) } }
        runCatching { panel?.let { wm.removeView(it) } }
        table = null
        panel = null
        super.onDestroy()
    }
}
