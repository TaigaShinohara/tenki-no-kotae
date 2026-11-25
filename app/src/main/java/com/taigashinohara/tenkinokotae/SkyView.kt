package com.taigashinohara.tenkinokotae

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.PowerManager
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 生きている空 (v0.1.9): the background of every screen is the sky of
 * the selected place, right now — a slow animation driven only by data
 * the app already fetched (today's weather code / precipitation and
 * sunrise / sunset) plus the current time. No new data source.
 *
 * What the sky says before any text is read:
 * - the gradient's phase (dawn / day / dusk / night) comes from the
 *   time of day, anchored on today's sunrise / sunset;
 * - the sun and the moon travel a small arc in the upper right;
 * - clouds drift — their amount and darkness follow the current
 *   hour's weather code; rain falls as quiet streaks, snow as slow
 *   flakes, fog as drifting bands, stars appear on clear nights.
 *
 * Motion discipline (ART_DIRECTION §6): everything is slow — clouds
 * cross the screen in minutes, rain/snow fall gently, nothing blinks
 * or flashes (thunder is drawn as dark clouds + rain, never lightning).
 * The frame loop runs only while the Activity is resumed and this
 * view is visible, capped near 30 fps; when the system asks for
 * reduced motion (animator duration scale 0 / animators disabled) or
 * battery saver is on, the sky is a still picture of the same state.
 *
 * Contrast discipline (ART_DIRECTION §3 v0.1.9 追記): every sky color
 * and overlay alpha in here and in MainActivity.skyStops was verified
 * by a numerical scan over all 7 day bands x light/dark x the full
 * phase grid, assuming worst-case overlaps (a glyph sitting exactly
 * on a glow peak or under two stacked clouds). Text never drops below
 * its gate in any sky state. That is why the dark-mode sun and moon
 * are soft glows rather than bright discs: a bright disc behind the
 * conclusion's accent text would break the 3:1 gate, and the gate
 * wins over the picture.
 *
 * v0.1.10 (visual-calm pass, docs/INFO_REVIEW_v0.1.10.md): the sky
 * itself stays, but its presence *behind the glass cards* was tuned
 * down — clouds ride a narrower, fainter band, rain falls slower
 * and thinner, fog and stars keep to the open sky — so the moving
 * layer competes less with the text layer. The contrast scan was
 * re-run for the new constants (all gates still pass; the margins
 * behind glass improved).
 */
class SkyView(ctx: Context) : View(ctx) {

    /**
     * Everything the sky needs, computed by MainActivity from the
     * forecast model. [top]/[mid]/[bot] are the phase-adjusted
     * gradient stops (opaque). [glowColor] is the ambient tint glow
     * behind the conclusion, alpha included.
     */
    class State(
        val top: Int,
        val mid: Int,
        val bot: Int,
        val dark: Boolean,
        val glowColor: Int,
        val sunriseMin: Int,
        val sunsetMin: Int,
        val dayness: Float,
        val cloudiness: Float,
        val cloudDark: Float,
        val rainLevel: Int,
        val snow: Boolean,
        val fog: Boolean
    )

    var skyState: State? = null
        set(value) {
            field = value
            invalidate()
        }

    internal var clockMillis: () -> Long = System::currentTimeMillis

    // ---------- animation loop ----------

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastFrameMs = 0L
    private var elapsedSec = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val nowMs = frameTimeNanos / 1_000_000L
            if (lastFrameMs != 0L) {
                val dt = ((nowMs - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
                elapsedSec += dt
                updateParticles(dt)
            }
            lastFrameMs = nowMs
            invalidate()
            choreographer.postFrameCallbackDelayed(this, 33L)
        }
    }

    private fun reducedMotion(): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        } catch (e: Exception) {
            1f
        }
        if (scale == 0f) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }

    fun startAnimation() {
        if (running) return
        if (reducedMotion()) {
            // Still picture of the same sky state.
            invalidate()
            return
        }
        running = true
        lastFrameMs = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    fun stopAnimation() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    // ---------- particles (fractional coords, seeded = stable layout) ----------

    private class Cloud(
        val seedX: Float, val yFrac: Float, val scaleDp: Float,
        val speedDp: Float, val alphaMul: Float
    )

    private class Drop(var x: Float, var y: Float, val speed: Float, val lenDp: Float)
    private class Flake(
        var x: Float, var y: Float, val rDp: Float, val speed: Float, val phase: Float
    )

    private class Star(val x: Float, val y: Float, val rDp: Float, val alpha: Int)

    private val clouds: List<Cloud>
    private val drops: List<Drop>
    private val flakes: List<Flake>
    private val stars: List<Star>

    init {
        val rnd = Random(20261119)
        clouds = List(7) {
            Cloud(
                seedX = rnd.nextFloat(),
                // v0.1.10: the band narrows (was 0.02..0.38) so clouds
                // mostly ride the open sky above the card stack.
                yFrac = 0.02f + rnd.nextFloat() * 0.30f,
                scaleDp = 36f + rnd.nextFloat() * 38f,
                speedDp = 3f + rnd.nextFloat() * 4f,
                alphaMul = 0.7f + rnd.nextFloat() * 0.3f
            )
        }
        drops = List(90) {
            Drop(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                // v0.1.10: slower fall (was 620..980 dp/s) — rain reads
                // in the open sky without flickering past the text.
                speed = 470f + rnd.nextFloat() * 290f,
                lenDp = 9f + rnd.nextFloat() * 6f
            )
        }
        flakes = List(60) {
            Flake(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                rDp = 1.5f + rnd.nextFloat() * 1.6f,
                speed = 24f + rnd.nextFloat() * 20f,
                phase = rnd.nextFloat() * 6.28f
            )
        }
        stars = List(70) {
            Star(
                x = rnd.nextFloat(),
                // v0.1.10: stars keep to the top 40% (was 50%) so they
                // stay behind the conclusion zone, not the cards.
                y = rnd.nextFloat() * rnd.nextFloat() * 0.40f,
                rDp = 0.7f + rnd.nextFloat() * 0.7f,
                alpha = 50 + rnd.nextInt(90)
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val st = skyState ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        if (st.rainLevel > 0) {
            val active = st.rainLevel * 30
            drops.forEachIndexed { i, dr ->
                if (i >= active) return@forEachIndexed
                dr.y += dr.speed * d * dt / h
                dr.x += dr.speed * 0.22f * d * dt / w
                if (dr.y > 1.05f) {
                    dr.y = -0.05f
                    dr.x = Math.random().toFloat()
                }
                if (dr.x > 1.05f) dr.x -= 1.1f
            }
        }
        if (st.snow) {
            for (f in flakes) {
                f.y += f.speed * d * dt / h
                if (f.y > 1.03f) {
                    f.y = -0.03f
                    f.x = Math.random().toFloat()
                }
            }
        }
    }

    // ---------- helpers ----------

    private fun mix(c: Int, other: Int, ratio: Float): Int {
        val r = (Color.red(c) + (Color.red(other) - Color.red(c)) * ratio).toInt()
        val g = (Color.green(c) + (Color.green(other) - Color.green(c)) * ratio).toInt()
        val b = (Color.blue(c) + (Color.blue(other) - Color.blue(c)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun withAlpha(c: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val u = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    /** Current time in Asia/Tokyo, in minutes (fractional). */
    private fun nowMinutes(): Float {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            timeInMillis = clockMillis()
        }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60f +
            cal.get(java.util.Calendar.MINUTE) +
            cal.get(java.util.Calendar.SECOND) / 60f
    }

    private fun radialPaint(cx: Float, cy: Float, radius: Float, color: Int): Paint {
        val a = Color.alpha(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                color,
                withAlpha(color, (a * 0.55f).toInt()),
                withAlpha(color, 0)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        return p
    }

    // ---------- drawing ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val st = skyState ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density

        // 1) Phase gradient.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(st.top, st.mid, st.bot),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2) Ambient tint glow behind the conclusion (the v0.1.6 main
        // light, kept as the sky's ambient light).
        canvas.drawCircle(
            w * 0.30f, h * 0.17f, 230f * d,
            radialPaint(w * 0.30f, h * 0.17f, 230f * d, st.glowColor)
        )

        val now = nowMinutes()
        val sunVis = smoothstep(0.10f, 0.45f, st.dayness) *
            (1f - st.cloudiness * 0.75f)
        val moonVis = (1f - smoothstep(0.20f, 0.60f, st.dayness)) *
            (1f - st.cloudiness * 0.75f)

        // 3) Stars (clear nights, dark mode only — the light sky is
        // never dark enough for them to read).
        if (st.dark) {
            val starScale = (1f - smoothstep(0.20f, 0.60f, st.dayness)) *
                (1f - st.cloudiness * 1.4f).coerceIn(0f, 1f)
            if (starScale > 0.02f) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                for (s in stars) {
                    sp.alpha = (s.alpha * starScale).toInt().coerceIn(0, 255)
                    canvas.drawCircle(s.x * w, s.y * h, s.rDp * d, sp)
                }
            }
        }

        // 4) Sun and moon on their arc (upper right).
        val sunrise = st.sunriseMin.toFloat()
        val sunset = st.sunsetMin.toFloat()
        if (now > sunrise && now < sunset && sunVis > 0.02f) {
            val p = (now - sunrise) / (sunset - sunrise)
            val cx = w * (0.56f + 0.36f * p)
            // v0.1.10: the arc rides a little higher and flatter (was
            // base 0.185h, rise 0.105h) so the disc stays in the top
            // band, clear of the conclusion's first line, and the glow
            // is a touch smaller (110 -> 96dp).
            val cy = h * 0.168f - sin(PI * p).toFloat() * h * 0.092f
            val glowCol = if (st.dark) Color.rgb(0xF5, 0xC9, 0x7B) else Color.rgb(0xFF, 0xE9, 0xB0)
            val glowA = if (st.dark) 24 else 150
            canvas.drawCircle(
                cx, cy, 96f * d,
                radialPaint(cx, cy, 96f * d, withAlpha(glowCol, (glowA * sunVis).toInt()))
            )
            val coreCol = if (st.dark) Color.rgb(0xE8, 0xA3, 0x4F) else Color.rgb(0xFF, 0xD7, 0x6A)
            val coreA = if (st.dark) 20 else 235
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = coreCol
                alpha = (coreA * sunVis).toInt().coerceIn(0, 255)
            }
            canvas.drawCircle(cx, cy, 16f * d, cp)
        }
        val nightLen = 1440f - (sunset - sunrise)
        val moonP: Float? = when {
            now >= sunset -> (now - sunset) / nightLen
            now < sunrise -> (now + 1440f - sunset) / nightLen
            else -> null
        }
        if (moonP != null && moonVis > 0.02f) {
            val cx = w * (0.56f + 0.36f * moonP)
            // Same lifted arc as the sun (v0.1.10).
            val cy = h * 0.168f - sin(PI * moonP).toFloat() * h * 0.092f
            val moonCol = Color.rgb(0x9F, 0xB8, 0xCC)
            if (st.dark) {
                canvas.drawCircle(
                    cx, cy, 85f * d,
                    radialPaint(cx, cy, 85f * d, withAlpha(moonCol, (20 * moonVis).toInt()))
                )
                val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = moonCol
                    alpha = (30 * moonVis).toInt().coerceIn(0, 255)
                }
                canvas.drawCircle(cx, cy, 13f * d, mp)
            } else {
                // Light mode: a pale daytime moon, mostly glow.
                canvas.drawCircle(
                    cx, cy, 70f * d,
                    radialPaint(cx, cy, 70f * d, withAlpha(Color.WHITE, (60 * moonVis).toInt()))
                )
                val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(0xF4, 0xF8, 0xFB)
                    alpha = (110 * moonVis).toInt().coerceIn(0, 255)
                }
                canvas.drawCircle(cx, cy, 12f * d, mp)
            }
        }

        // 5) Clouds. Amount (how many of the pool are awake) and
        // darkness follow the weather; they drift slowly left to right.
        val cloudCol = if (st.dark) {
            mix(Color.rgb(0x46, 0x58, 0x6A), Color.rgb(0x3E, 0x4C, 0x5A), st.cloudDark)
        } else {
            mix(Color.WHITE, Color.rgb(0xA9, 0xBF, 0xD2), st.cloudDark)
        }
        // v0.1.10: fainter blobs (was 96 / 34) — clouds still read in
        // the open sky, but through the glass they sit quieter behind
        // the text. The contrast scan was re-run at these values.
        val blobA = if (st.dark) 28 else 80
        val blobs = arrayOf(
            floatArrayOf(0f, 0f, 1.0f),
            floatArrayOf(-0.95f, 0.18f, 0.72f),
            floatArrayOf(0.95f, 0.15f, 0.78f),
            floatArrayOf(0.30f, -0.30f, 0.66f),
            floatArrayOf(-0.35f, -0.22f, 0.60f)
        )
        clouds.forEachIndexed { i, cl ->
            val wake = (st.cloudiness * clouds.size - i).coerceIn(0f, 1f)
            if (wake <= 0.01f) return@forEachIndexed
            val frac = ((cl.seedX + elapsedSec * cl.speedDp * d / w) % 1.3f + 1.3f) % 1.3f
            val cx = (frac - 0.15f) * w
            val cy = cl.yFrac * h
            val s = cl.scaleDp * d
            for (b in blobs) {
                val bx = cx + b[0] * s
                val by = cy + b[1] * s
                val br = s * b[2]
                val a = (blobA * wake * cl.alphaMul).toInt().coerceIn(0, 255)
                canvas.drawCircle(bx, by, br, radialPaint(bx, by, br, withAlpha(cloudCol, a)))
            }
        }

        // 6) Fog: soft horizontal bands drifting very slowly.
        if (st.fog) {
            val fogCol = if (st.dark) Color.rgb(0x8F, 0xA6, 0xB8) else Color.rgb(0xE9, 0xEF, 0xF3)
            // v0.1.10: paler (was 60 / 26) — the bands drift right
            // behind the action cards, so they give up some presence.
            val fogA = if (st.dark) 22 else 46
            for (i in 0..2) {
                val fy = h * (0.30f + i * 0.12f)
                val fx = w * (0.30f + 0.40f * ((i * 0.37f + elapsedSec * 0.004f) % 1f))
                val rp = radialPaint(fx, fy, w * 0.62f, withAlpha(fogCol, fogA))
                canvas.save()
                canvas.scale(1f, 0.16f, fx, fy)
                canvas.drawCircle(fx, fy, w * 0.62f, rp)
                canvas.restore()
            }
        }

        // 7) Rain streaks (front). Thin lines — they cross glyphs
        // without changing their measured contrast (see ART_DIRECTION).
        if (st.rainLevel > 0) {
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * d
                strokeCap = Paint.Cap.ROUND
                // v0.1.10: thinner presence over the glass (was 115 / 100).
                color = if (st.dark) Color.argb(82, 0xCF, 0xE4, 0xF2)
                    else Color.argb(92, 0x5B, 0x7A, 0x94)
            }
            val active = st.rainLevel * 30
            drops.forEachIndexed { i, dr ->
                if (i >= active) return@forEachIndexed
                val x = dr.x * w
                val y = dr.y * h
                val len = dr.lenDp * d
                canvas.drawLine(x - len * 0.22f, y - len, x, y, rp)
            }
        }

        // 8) Snowflakes (front), slow with a gentle sway.
        if (st.snow) {
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(if (st.dark) Color.WHITE else Color.rgb(0xC7, 0xD6, 0xE2), 190)
            }
            for (f in flakes) {
                val fx = f.x * w + sin(elapsedSec * 0.7f + f.phase) * 8f * d
                val fy = f.y * h
                canvas.drawCircle(fx, fy, f.rDp * d, fp)
            }
        }
    }
}
