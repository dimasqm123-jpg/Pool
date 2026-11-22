package com.dilan.poolguide

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            textSize = 16f
            text = "Pool Guide\n\n" +
                "1) Da el permiso de \"mostrar sobre otras apps\".\n" +
                "2) Toca Iniciar y abre 8 Ball Pool.\n" +
                "3) Con 1 dedo arrastras la mesa; con 2 dedos haces zoom y la giras.\n" +
                "4) Ajuste fino con los botones del panel (mantén apretado).\n" +
                "5) Toca el candado: el overlay deja pasar los toques al juego.\n"
        })

        fun button(label: String, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            })
        }

        button("1. Dar permiso de superposición") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        button("2. Iniciar overlay") {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            } else {
                Toast.makeText(this, "Primero da el permiso (paso 1)", Toast.LENGTH_LONG).show()
            }
        }
        button("Detener overlay") {
            stopService(Intent(this, OverlayService::class.java))
        }

        setContentView(root)
    }
}
