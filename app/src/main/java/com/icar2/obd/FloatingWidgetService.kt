package com.icar2.obd

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*

// Floating widget blur - chạy trên ĐIỆN THOẠI, mirror qua Screen2Auto lên xe.
// Blur: Android 12+ dùng FLAG_BLUR_BEHIND + blurBehindRadius. Cũ hơn dùng nền translucent.
class FloatingWidgetService : Service() {
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var t1: TextView? = null
    private var t2: TextView? = null
    private var t3: TextView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectJob: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        showWidget()
        startCollect()
        return START_STICKY
    }

    private fun showWidget() {
        if (root != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Nền blur giả: translucent đen 55% + bo góc. Trên Android 12+ hệ thống blur thêm phía sau.
            setBackgroundColor(0x8C1A1A1ACC.toInt())
            setPadding(24, 18, 24, 18)
            // Kéo để di chuyển
            var sx = 0; var sy = 0; var px = 0f; var py = 0f
            setOnTouchListener { v, e ->
                val p = v.layoutParams as WindowManager.LayoutParams
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX.toInt(); sy=e.rawY.toInt(); px=p.x.toFloat(); py=p.y.toFloat() }
                    MotionEvent.ACTION_MOVE -> { p.x = (px + e.rawX - sx).toInt(); p.y = (py + e.rawY - sy).toInt(); wm?.updateViewLayout(v, p) }
                }
                false
            }
        }
        t1 = TextView(this).apply { text="-- km/h"; setTextColor(0xFFFFFFFF.toInt()); textSize=26f }
        t2 = TextView(this).apply { text="-- rpm"; setTextColor(0xFFFFFFFF.toInt()); textSize=16f }
        t3 = TextView(this).apply { text="ECT --°C • Thr --%"; setTextColor(0xFFCCCCCC.toInt()); textSize=13f }
        val close = TextView(this).apply {
            text="✕ thu gọn"; setTextColor(0xFFAAAAAA.toInt()); textSize=11f
            setOnClickListener { stopSelf() }
        }
        root!!.addView(t1); root!!.addView(t2); root!!.addView(t3); root!!.addView(close)

        // RenderEffect blur cho chính cục widget (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { root!!.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(18f, 18f, android.graphics.Shader.TileMode.CLAMP)) } catch (_: Exception) {}
        }

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 200
            // Blur phía SAU widget (thấy Maps mờ mờ xuyên qua) - chỉ S+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    flags = flags or 0x00000004 // FLAG_BLUR_BEHIND
                    blurBehindRadius = 60
                } catch (_: Exception) {}
            }
        }
        // Bo góc bằng outline
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root!!.clipToOutline = true
        }
        wm?.addView(root, params)
    }

    private fun startCollect() {
        collectJob?.cancel()
        collectJob = scope.launch {
            ObdService.liveData.collect { map ->
                val speed = map["0D"]?.toInt()?.toString() ?: "--"
                val rpm = map["0C"]?.toInt()?.toString() ?: "--"
                val ect = map["05"]?.toInt()?.toString() ?: "--"
                val thr = map["11"]?.toInt()?.toString() ?: "--"
                val maf = map["10"]?.let { String.format("%.1f", it) } ?: "--"
                val volt = map["42"]?.let { String.format("%.1f", it) } ?: "--"
                t1?.text = "$speed km/h"
                t2?.text = "$rpm rpm • $maf g/s • $volt V"
                t3?.text = "ECT $ect°C • Thr $thr%"
            }
        }
    }

    override fun onDestroy() {
        collectJob?.cancel(); scope.cancel()
        try { if (root!=null) wm?.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }
}
