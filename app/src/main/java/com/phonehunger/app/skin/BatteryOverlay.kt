package com.phonehunger.app.skin

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.phonehunger.app.battery.BatterySnapshot

class BatteryOverlay(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    fun show(snapshot: BatterySnapshot?, color: Int, size: Int, left: Boolean) {
        if (!Settings.canDrawOverlays(context)) { hide(); return }
        if (view == null) {
            val density = context.resources.displayMetrics.density
            val status = context.resources.getIdentifier("status_bar_height", "dimen", "android").takeIf { it > 0 }?.let(context.resources::getDimensionPixelSize) ?: (24 * density).toInt()
            val text = TextView(context).apply {
                textSize = 13f; setTextColor(android.graphics.Color.BLACK)
                setPadding((10*density).toInt(), (5*density).toInt(), (10*density).toInt(), (5*density).toInt())
            }
            val layout = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; y = status + (8*density).toInt(); x = (8*density).toInt() }
            try { manager.addView(text, layout); view = text; params = layout } catch (_: Exception) { return }
        }
        view?.apply {
            text = snapshot?.let { "${it.level}% ${if (it.plugged) "⚡" else if (it.level <= 20) "☹" else "☺"}" } ?: "—"
            textSize = 13f * size.coerceIn(160, 280) / 220f
            background = GradientDrawable().apply { setColor(color); cornerRadius = 20 * context.resources.displayMetrics.density }
        }
        params?.let { layout ->
            layout.gravity = Gravity.TOP or (if (left) Gravity.START else Gravity.END)
            view?.let { try { manager.updateViewLayout(it, layout) } catch (_: Exception) {} }
        }
    }
    fun hide() { view?.let { try { manager.removeView(it) } catch (_: Exception) {} }; view = null; params = null }
}
