package com.ghaniram.zoya

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max

/** Floating Anu companion. */
class ZoyaOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var companionView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastTapAt = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var moved = false

    private val idleRunnable = object : Runnable {
        override fun run() {
            val view = companionView ?: return
            when ((SystemClock.uptimeMillis() / 1000L).toInt() % 5) {
                0 -> view.animate().cancel().also { view.animate().scaleX(1.035f).scaleY(1.035f).setDuration(900).withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(900).start() }.start() }
                1 -> view.animate().cancel().also { view.animate().rotationBy(2.2f).setDuration(420).withEndAction { view.animate().rotation(0f).setDuration(420).start() }.start() }
                2 -> view.animate().cancel().also { view.animate().translationY(-5f).setDuration(650).withEndAction { view.animate().translationY(0f).setDuration(650).start() }.start() }
                3 -> view.animate().cancel().also { view.animate().alpha(0.82f).setDuration(120).withEndAction { view.animate().alpha(1f).setDuration(160).start() }.start() }
            }
            handler.postDelayed(this, 2500L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (companionView == null) showCompanion()
        return START_STICKY
    }

    private fun showCompanion() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val image = ImageView(this).apply {
            setImageResource(R.drawable.anu_floating_companion)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = false
            contentDescription = "Anu floating companion"
            setOnTouchListener(::handleTouch)
        }
        val size = (80 * resources.displayMetrics.density).toInt()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val layoutParams = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 18; y = 180 }
        try { windowManager?.addView(image, layoutParams); companionView = image; params = layoutParams; handler.removeCallbacks(idleRunnable); handler.postDelayed(idleRunnable, 1200L) } catch (_: Exception) { stopSelf() }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val wm = windowManager ?: return false
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downRawX = event.rawX; downRawY = event.rawY; downX = lp.x; downY = lp.y; moved = false; return true }
            MotionEvent.ACTION_MOVE -> { val dx = (event.rawX-downRawX).toInt(); val dy = (event.rawY-downRawY).toInt(); if (abs(dx)>4 || abs(dy)>4) moved=true; lp.x=downX+dx; lp.y=downY+dy; try { wm.updateViewLayout(view,lp) } catch (_:Exception) { return false }; return true }
            MotionEvent.ACTION_UP -> { if (!moved) { val now=SystemClock.uptimeMillis(); if (now-lastTapAt<320L) { openAnu(); lastTapAt=0L } else { lastTapAt=now; view.performClick() } } else snapToEdge(); return true }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun snapToEdge() {
        val wm=windowManager ?: return; val lp=params ?: return; val view=companionView ?: return
        val displayWidth=resources.displayMetrics.widthPixels
        val targetX=if (lp.x+view.width/2<displayWidth/2) 10 else max(10,displayWidth-view.width-10)
        val startX=lp.x; val distance=abs(targetX-startX); val duration=(180L+distance*2L).coerceAtMost(520L); val startTime=SystemClock.uptimeMillis()
        val snapRunnable=object:Runnable { override fun run() { val elapsed=SystemClock.uptimeMillis()-startTime; val t=(elapsed.toFloat()/duration).coerceIn(0f,1f); val eased=1f-(1f-t)*(1f-t); lp.x=(startX+(targetX-startX)*eased).toInt(); try { wm.updateViewLayout(view,lp) } catch (_:Exception) { return }; if(t<1f) handler.postDelayed(this,16L) } }
        handler.post(snapRunnable)
    }

    private fun openAnu() { try { startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }) } catch (_:Exception) {} }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); companionView?.animate()?.cancel(); companionView?.let { try { windowManager?.removeView(it) } catch (_:Exception) {} }; companionView=null; params=null; windowManager=null; super.onDestroy() }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: android.content.Context) {
            context.startService(Intent(context, ZoyaOverlayService::class.java))
        }
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ZoyaOverlayService::class.java))
        }
    }
}

