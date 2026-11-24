package com.mx61.control

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val BG = 0xFF0D0F14.toInt()
val PANEL = 0xFF171A22.toInt()
val PANEL2 = 0xFF222736.toInt()
val TEXT = 0xFFE9ECF3.toInt()
val DIM = 0xFF8B92A5.toInt()
val ACCENT = 0xFF4FD1C5.toInt()
val RED = 0xFFF56565.toInt()
val AMBER = 0xFFF6AD55.toInt()
val CH_COLORS = IntArray(16) { Color.HSVToColor(floatArrayOf(it * 22.5f + 170f, 0.55f, 0.97f)) }

fun Context.dp(v: Float) = v * resources.displayMetrics.density
fun Context.dpi(v: Float) = (v * resources.displayMetrics.density).toInt()

fun roundRect(ctx: Context, color: Int, radiusDp: Float, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = ctx.dp(radiusDp)
        if (stroke != 0) setStroke(ctx.dpi(1.2f), stroke)
    }

/** Botão pílula que liga/desliga (M, S, sustain...). */
fun pill(ctx: Context, text: String, onColor: Int, initial: Boolean, textSp: Float = 12f, click: (Boolean) -> Unit): TextView {
    var on = initial
    val t = TextView(ctx)
    t.text = text; t.gravity = Gravity.CENTER; t.textSize = textSp
    t.setPadding(ctx.dpi(10f), ctx.dpi(6f), ctx.dpi(10f), ctx.dpi(6f))
    fun paint() {
        t.background = roundRect(ctx, if (on) onColor else PANEL2, 8f)
        t.setTextColor(if (on) 0xFF101218.toInt() else DIM)
        t.setTypeface(null, Typeface.BOLD)
    }
    paint()
    t.setOnClickListener { on = !on; paint(); click(on) }
    t.tag = { v: Boolean -> on = v; paint() }
    return t
}

@Suppress("UNCHECKED_CAST")
fun setPill(t: TextView, v: Boolean) { (t.tag as? (Boolean) -> Unit)?.invoke(v) }

fun button(ctx: Context, text: String, color: Int = PANEL2, fg: Int = TEXT, click: () -> Unit): TextView =
    TextView(ctx).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 13f; setTextColor(fg)
        setTypeface(null, Typeface.BOLD)
        setPadding(ctx.dpi(14f), ctx.dpi(10f), ctx.dpi(14f), ctx.dpi(10f))
        background = roundRect(ctx, color, 10f)
        setOnClickListener { click() }
    }

/** Knob com arraste vertical; toque longo volta ao valor padrão. */
class Knob(
    ctx: Context, private val label: String, private val min: Int, private val max: Int,
    value: Int, private val def: Int, private val color: Int, private val bipolar: Boolean = false,
    private val fmt: ((Int) -> String)? = null,
    private val onChange: (Int) -> Unit,
) : View(ctx) {
    var value = value
        private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var downY = 0f
    private var startVal = 0
    private var moved = false
    private val longPress = Runnable { if (!moved) { set(def, true); performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } }

    fun set(v: Int, notify: Boolean) {
        val nv = v.coerceIn(min, max)
        if (nv == value) return
        value = nv; invalidate(); if (notify) onChange(nv)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val labelH = context.dp(14f)
        val d = min(w, h - labelH)
        val stroke = d * 0.13f
        val r = d / 2 - stroke
        val cx = w / 2; val cy = (h - labelH) / 2
        rect.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE; p.strokeWidth = stroke; p.strokeCap = Paint.Cap.ROUND
        p.color = PANEL2; c.drawArc(rect, 135f, 270f, false, p)
        val frac = (value - min).toFloat() / (max - min)
        p.color = color
        if (bipolar) c.drawArc(rect, 270f, (frac - 0.5f) * 270f, false, p)
        else if (frac > 0f) c.drawArc(rect, 135f, frac * 270f, false, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.color = TEXT
        val shown = fmt?.invoke(value) ?: if (bipolar) (value - (min + max + 1) / 2).let { if (it > 0) "+$it" else "$it" } else "$value"
        p.textSize = d * (if (shown.length > 4) 0.2f else 0.27f); p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(shown, cx, cy + p.textSize * 0.35f, p)
        p.color = DIM; p.textSize = context.dp(10f); p.typeface = Typeface.DEFAULT
        c.drawText(label, cx, h - context.dp(2f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y; startVal = value; moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
                postDelayed(longPress, 600)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = downY - e.y
                if (abs(dy) > context.dp(6f)) { moved = true; removeCallbacks(longPress) }
                if (moved) set(startVal + (dy / context.dp(160f) * (max - min)).toInt(), true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return true
    }
}

/** Fader vertical com medidor de nível das notas recebidas. */
class Fader(
    ctx: Context, private val color: Int, value: Int, private val meterCh: Int,
    private val onChange: (Int) -> Unit,
) : View(ctx) {
    var value = value
        private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var meter = 0f

    fun set(v: Int, notify: Boolean) {
        val nv = v.coerceIn(0, 127)
        if (nv == value) return
        value = nv; invalidate(); if (notify) onChange(nv)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = context.dp(14f)
        val top = pad; val bot = h - pad
        val cx = w / 2
        val trackW = context.dp(8f)
        p.style = Paint.Style.FILL
        // marcas
        p.color = PANEL2; p.strokeWidth = 1f
        for (i in 0..10) { val y = bot - (bot - top) * i / 10f; c.drawLine(cx - trackW * 2.2f, y, cx - trackW * 1.2f, y, p) }
        // trilho
        p.color = PANEL2; c.drawRoundRect(cx - trackW / 2, top, cx + trackW / 2, bot, trackW, trackW, p)
        val y = bot - (bot - top) * value / 127f
        p.color = color; p.alpha = 150
        c.drawRoundRect(cx - trackW / 2, y, cx + trackW / 2, bot, trackW, trackW, p)
        p.alpha = 255
        // medidor
        val target = Rig.level[meterCh]
        meter = max(target, meter * 0.9f)
        Rig.level[meterCh] = target * 0.9f
        val mh = (bot - top) * meter
        p.color = if (meter > 0.85f) RED else if (meter > 0.6f) AMBER else ACCENT
        c.drawRoundRect(cx + trackW * 1.4f, bot - mh, cx + trackW * 2.2f, bot, 3f, 3f, p)
        // manopla
        val kw = context.dp(34f); val kh = context.dp(22f)
        p.color = 0xFFE9ECF3.toInt()
        c.drawRoundRect(cx - kw / 2, y - kh / 2, cx + kw / 2, y + kh / 2, 8f, 8f, p)
        p.color = color; c.drawRect(cx - kw / 2 + 5f, y - 1.5f, cx + kw / 2 - 5f, y + 1.5f, p)
        if (meter > 0.01f) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_CANCEL || e.actionMasked == MotionEvent.ACTION_UP) return true
        val pad = context.dp(14f)
        val frac = 1f - ((e.y - pad) / (height - 2 * pad)).coerceIn(0f, 1f)
        set((frac * 127).toInt(), true)
        return true
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); postInvalidateOnAnimation() }
}

/** Raia de uma track: notas recebidas/tocadas rolando da direita para a esquerda. */
class LaneView(ctx: Context, private val ch: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowMs = 8000L

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val col = CH_COLORS[ch]
        p.style = Paint.Style.FILL
        p.color = if (Rig.selected == ch) 0xFF1B2030.toInt() else PANEL
        c.drawRoundRect(0f, 0f, w, h, context.dp(8f), context.dp(8f), p)
        // grade a cada segundo
        p.color = 0xFF262B3A.toInt(); p.strokeWidth = 1f
        for (s in 0..8) { val x = w - ((s * 1000L + (now % 1000)) / windowMs.toFloat()) * w; c.drawLine(x, 0f, x, h, p) }
        val lo = 36; val hi = 96
        val nh = max(context.dp(3f), h / (hi - lo + 1))
        for (e in Rig.notes) {
            if (e.ch != ch) continue
            val end = if (e.t1 == 0L) now else e.t1
            if (now - end > windowMs) continue
            val x0 = w - (now - e.t0) / windowMs.toFloat() * w
            val x1 = w - (now - end) / windowMs.toFloat() * w
            val y = h - (e.pitch.coerceIn(lo, hi) - lo) / (hi - lo).toFloat() * (h - nh)
            p.color = col; p.alpha = 90 + e.vel
            c.drawRoundRect(max(0f, x0), y - nh, max(x0 + context.dp(3f), x1), y, 3f, 3f, p)
            p.alpha = 255
        }
        p.color = TEXT; p.alpha = 120; c.drawRect(w - 2f, 0f, w, h, p); p.alpha = 255
        postInvalidateOnAnimation()
    }
}

/** Teclado de 3 oitavas multitoque; a posição vertical define a velocity. */
class KeysView(ctx: Context, private val ch: () -> Int, var baseNote: Int = 48) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whites = 21
    private val wSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val pointers = HashMap<Int, Int>()
    private val down = HashSet<Int>()
    private val blackAfter = setOf(0, 1, 3, 4, 5)

    private fun whiteNote(i: Int) = baseNote + (i / 7) * 12 + wSemis[i % 7]

    private fun blackRect(i: Int, kw: Float, bh: Float) =
        RectF((i + 1) * kw - kw * 0.3f, 0f, (i + 1) * kw + kw * 0.3f, bh)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val kw = w / whites; val bh = h * 0.6f
        p.style = Paint.Style.FILL
        for (i in 0 until whites) {
            val n = whiteNote(i)
            p.color = if (n in down) CH_COLORS[ch()] else 0xFFF1F3F8.toInt()
            c.drawRoundRect(i * kw + 1.5f, 0f, (i + 1) * kw - 1.5f, h, 6f, 6f, p)
            if (n % 12 == 0) {
                p.color = 0xFF55607A.toInt(); p.textSize = context.dp(10f); p.textAlign = Paint.Align.CENTER
                c.drawText("C${n / 12 - 1}", i * kw + kw / 2, h - context.dp(6f), p)
            }
        }
        for (i in 0 until whites - 1) {
            if (i % 7 !in blackAfter) continue
            val n = whiteNote(i) + 1
            p.color = if (n in down) CH_COLORS[ch()] else 0xFF14171F.toInt()
            c.drawRoundRect(blackRect(i, kw, bh), 5f, 5f, p)
        }
    }

    private fun noteAt(x: Float, y: Float): Int {
        val kw = width.toFloat() / whites; val bh = height * 0.6f
        if (y < bh) for (i in 0 until whites - 1) {
            if (i % 7 in blackAfter && blackRect(i, kw, bh).contains(x, y)) return whiteNote(i) + 1
        }
        return whiteNote((x / kw).toInt().coerceIn(0, whites - 1))
    }

    private fun vel(y: Float) = (50 + (y / height).coerceIn(0f, 1f) * 77).toInt()

    private fun press(id: Int, x: Float, y: Float) {
        val n = noteAt(x, y)
        val old = pointers[id]
        if (old == n) return
        if (old != null) release(id)
        pointers[id] = n; down.add(n); Rig.noteOn(ch(), n, vel(y))
    }

    private fun release(id: Int) {
        val n = pointers.remove(id) ?: return
        if (!pointers.containsValue(n)) { down.remove(n); Rig.noteOff(ch(), n) }
    }

    fun releaseAll() { pointers.keys.toList().forEach { release(it) }; invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; press(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) press(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> pointers.keys.toList().forEach { release(it) }
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); releaseAll() }
}
