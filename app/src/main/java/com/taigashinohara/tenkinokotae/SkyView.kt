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
 * 天気グループ (v0.1.38): the seven sky moods the palette switches
 * between. One mapping from WMO weather codes (Open-Meteo; the
 * backup source's symbols are mapped onto the same codes in MetApi)
 * shared by the app (MainActivity's palette cast) and the widget
 * (TenkiWidget's face choice), so the two never disagree about what
 * kind of sky it is.
 */
enum class SkyGroup { CLEAR, PARTLY, CLOUDY, RAIN, SNOW, THUNDER, FOG }

fun skyGroupForCode(code: Int): SkyGroup = when (code) {
    0 -> SkyGroup.CLEAR
    1, 2 -> SkyGroup.PARTLY
    3 -> SkyGroup.CLOUDY
    45, 48 -> SkyGroup.FOG
    71, 73, 75, 77, 85, 86 -> SkyGroup.SNOW
    95, 96, 99 -> SkyGroup.THUNDER
    else -> if (code < 0) SkyGroup.CLEAR else SkyGroup.RAIN // 51-67 rain, 80-82 showers
}

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
 * view is visible and its window focused, at the display's vsync
 * rate (v0.1.39); when the system asks for reduced motion (animator
 * duration scale 0 / animators disabled), battery saver is on, or
 * the user turns 空の動き off in Settings, the sky is a still
 * picture of the same state.
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
 *
 * v0.1.39 (the sky breathes, docs/FEATURE_v0.1.39.md): the user's
 * ask — "天気はアニメーションにしたい。リアルでヌルヌル動く雨や、
 * 日差しなどを目指して". Three changes, all inside the discipline
 * above:
 * - the frame loop posts at the display's own vsync instead of the
 *   old ~30 fps cap (rain at 470-760 dp/s stepped visibly at
 *   30 fps; smoothness is a frame-rate property, not a speed one).
 *   Battery is guarded by the gates around the loop — resumed,
 *   shown, window-focused — plus the still-picture fallbacks, not
 *   by starving the rate.
 * - every moving element is now a pure function of the single
 *   animation clock [animTimeSec]: rain and snow positions are
 *   computed from seeds in onDraw (they used to be mutated
 *   incrementally), so a frame is fully determined by
 *   (skyState, animTimeSec) and the screenshot tests can pin the
 *   clock to render exact frames.
 * - new quiet motions, each capped at or below the scanned static
 *   values so the contrast scan's worst case still bounds every
 *   animated frame: the sun/moon glow breathes on a 7 s swell
 *   (factor 0.90..1.0, 1.0 at t=0), stars twinkle on slow
 *   per-star cosine swells (period ~6-13 s, factor down to
 *   0.4..0.7, 1.0 at t=0 — a swell, never a blink: no switching,
 *   no flash, and thunder still draws no lightning), and rain
 *   streaks fade in at the top and out near the ground instead of
 *   popping at the wrap. The app setting 空の動き (default on)
 *   joins the reduced-motion / battery-saver fallbacks: off means
 *   the v0.1.38 still picture of the same state.
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

    /**
     * The sky's one animation clock, in seconds. Every moving
     * element — cloud drift, rain, snow, fog, the glow's breathing,
     * the stars' twinkle — is a pure function of this value, so a
     * frame is fully determined by (skyState, animTimeSec). In
     * production the frame loop below is the only thing that
     * advances it (by real Δt); the screenshot tests pin it to
     * render exact frames (the same seam pattern as [clockMillis]).
     */
    internal var animTimeSec: Float = 0f

    /**
     * The app-level escape hatch: 設定「空の動き」(default on),
     * driven by MainActivity from its prefs. Off — like reduced
     * motion and battery saver — means the still picture.
     */
    var motionEnabled: Boolean = true

    /** startAnimation was asked for and not yet revoked. */
    private var wantAnimate = false
    private var loopRunning = false
    private var lastFrameMs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            if (!wantAnimate || !isShown) {
                // Became invisible without an event reaching us
                // first (a parent hid the whole window): stop
                // rather than burn frames nobody can see. The
                // visibility callbacks restart the loop on return.
                loopRunning = false
                return
            }
            val nowMs = frameTimeNanos / 1_000_000L
            if (lastFrameMs != 0L) {
                val dt = ((nowMs - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
                animTimeSec += dt
            }
            lastFrameMs = nowMs
            invalidate()
            // v0.1.39: posted at the display's own vsync — the old
            // 33 ms delayed repost capped the sky near 30 fps and
            // rain stepped visibly. The gates in updateLoop (and
            // MainActivity's resume/pause) are what guard battery.
            choreographer.postFrameCallback(this)
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

    /**
     * The single place that decides whether the loop is posted:
     * wanted by the Activity, allowed by the settings, attached,
     * shown, and not in a still-picture fallback. Every lifecycle
     * event below just re-asks this question.
     */
    private fun updateLoop() {
        val shouldRun = wantAnimate && motionEnabled && isAttachedToWindow &&
            isShown && !reducedMotion()
        if (shouldRun && !loopRunning) {
            loopRunning = true
            lastFrameMs = 0L
            choreographer.postFrameCallback(frameCallback)
        } else if (!shouldRun && loopRunning) {
            loopRunning = false
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    private fun haltLoop() {
        if (loopRunning) {
            loopRunning = false
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    fun startAnimation() {
        wantAnimate = true
        if (!motionEnabled || reducedMotion()) {
            // Still picture of the same sky state.
            invalidate()
            return
        }
        updateLoop()
    }

    fun stopAnimation() {
        wantAnimate = false
        haltLoop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoop()
    }

    override fun onDetachedFromWindow() {
        wantAnimate = false
        haltLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLoop()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Losing the window's focus (another app's window, a system
        // dialog) pauses the sky even though it stays on screen;
        // regaining focus re-asks updateLoop, which restarts it
        // only if it is still wanted and shown.
        if (hasWindowFocus) updateLoop() else haltLoop()
    }

    // ---------- particles (fractional coords, seeded = stable layout) ----------

    private class Cloud(
        val seedX: Float, val yFrac: Float, val scaleDp: Float,
        val speedDp: Float, val alphaMul: Float
    )

    /**
     * Rain drop / snowflake: only seeds are stored. The position at
     * animation time t is computed in onDraw as a pure function of
     * (seed, t) — no incremental mutation (v0.1.39), which is what
     * makes any frame reproducible by pinning [animTimeSec]. The
     * seed layout at t = 0 is exactly the pre-v0.1.39 initial
     * layout, so the still picture (and the t=0 goldens) do not
     * move when motion is off.
     */
    private class Drop(val seedX: Float, val seedY: Float, val speed: Float, val lenDp: Float)
    private class Flake(
        val seedX: Float, val seedY: Float, val rDp: Float, val speed: Float, val phase: Float
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
                seedX = rnd.nextFloat(), seedY = rnd.nextFloat(),
                // v0.1.10: slower fall (was 620..980 dp/s) — rain reads
                // in the open sky without flickering past the text.
                speed = 470f + rnd.nextFloat() * 290f,
                lenDp = 9f + rnd.nextFloat() * 6f
            )
        }
        flakes = List(60) {
            Flake(
                seedX = rnd.nextFloat(), seedY = rnd.nextFloat(),
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

    // ---------- particle positions: pure functions of animTimeSec ----------

    /** Per-drop slant (the wind lean), derived from the drop's own
     *  length so no extra seed is needed: longer drops lean more. */
    private fun dropSlant(dr: Drop): Float = 0.14f + (dr.lenDp - 9f) / 6f * 0.14f

    /**
     * A drop's position at time [t], as fractions of (w, h). The
     * fall wraps over a 1.1-tall cycle (from just above the top
     * edge to just below the ground); each wrap shifts the column
     * by a fixed irrational-ish step, which reads as a fresh random
     * column without any state. At t = 0 this returns the seed
     * position exactly.
     */
    private fun dropPos(dr: Drop, t: Float, w: Float, h: Float, d: Float): Pair<Float, Float> {
        val dist = dr.speed * d * t / h // fall distance, in height fractions
        val travelled = dr.seedY + dist
        var y = travelled % 1.1f
        if (y > 1.0f) y -= 1.1f // the part of the cycle above the top edge
        val wraps = (travelled / 1.1f).toInt()
        val x = (dr.seedX + dropSlant(dr) * dist * h / w + wraps * 0.37f) % 1f
        return Pair(x, y)
    }

    /** A flake's height fraction at time [t] (1.06-tall wrap cycle). */
    private fun flakeY(f: Flake, t: Float, h: Float, d: Float): Float {
        val dist = f.speed * d * t / h
        var y = (f.seedY + dist) % 1.06f
        if (y > 1.0f) y -= 1.06f
        return y
    }

    /**
     * The sun/moon glow's breathing (v0.1.39): a 7 s swell between
     * 1.0 and 0.90. It only ever *dims* the glow from its scanned
     * static value (and is exactly 1.0 at t = 0), so the contrast
     * scan's worst case still bounds every animated frame.
     */
    private fun breathFactor(t: Float): Float =
        1f - 0.05f * (1f - kotlin.math.cos(t * (2.0 * PI / 7.0)).toFloat())

    /**
     * A star's twinkle (v0.1.39): a slow cosine swell whose depth
     * and period come from the star's own seed values (period
     * ~5.7-12.6 s). Like the breathing it only dims from the
     * scanned static alpha and is exactly 1.0 at t = 0 — a swell,
     * never a blink.
     */
    private fun twinkleFactor(s: Star, t: Float): Float {
        val depth = 0.30f + 0.30f * ((s.alpha - 50) / 90f)
        val speed = 0.5f + (s.rDp - 0.7f) / 0.7f * 0.6f
        return 1f - depth * 0.5f * (1f - kotlin.math.cos(speed * t).toFloat())
    }

    // Particle paints live in fields (reused every frame; onDraw
    // allocates nothing for the particle layers).
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
                val sp = starPaint
                for (s in stars) {
                    sp.alpha = (s.alpha * starScale * twinkleFactor(s, animTimeSec))
                        .toInt().coerceIn(0, 255)
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
            // v0.1.39: the glow breathes (see breathFactor) — alpha
            // and radius swell together so the light feels alive
            // without ever exceeding the scanned static glow.
            val breath = breathFactor(animTimeSec)
            val glowR = 96f * d * (1f - 0.5f * (1f - breath))
            canvas.drawCircle(
                cx, cy, glowR,
                radialPaint(cx, cy, glowR, withAlpha(glowCol, (glowA * sunVis * breath).toInt()))
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
            val breath = breathFactor(animTimeSec)
            if (st.dark) {
                canvas.drawCircle(
                    cx, cy, 85f * d,
                    radialPaint(cx, cy, 85f * d, withAlpha(moonCol, (20 * moonVis * breath).toInt()))
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
                    radialPaint(cx, cy, 70f * d, withAlpha(Color.WHITE, (60 * moonVis * breath).toInt()))
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
            val frac = ((cl.seedX + animTimeSec * cl.speedDp * d / w) % 1.3f + 1.3f) % 1.3f
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
                val fx = w * (0.30f + 0.40f * ((i * 0.37f + animTimeSec * 0.004f) % 1f))
                val rp = radialPaint(fx, fy, w * 0.62f, withAlpha(fogCol, fogA))
                canvas.save()
                canvas.scale(1f, 0.16f, fx, fy)
                canvas.drawCircle(fx, fy, w * 0.62f, rp)
                canvas.restore()
            }
        }

        // 7) Rain streaks (front). Thin lines — they cross glyphs
        // without changing their measured contrast (see ART_DIRECTION).
        // v0.1.39: positions are the pure dropPos(t); each streak
        // fades in just under the top edge and out near the ground
        // (smoothstep over its height fraction), so the wrap cycle
        // never pops. The lean is per-drop now (dropSlant).
        if (st.rainLevel > 0) {
            val rp = rainPaint
            rp.strokeWidth = 1.4f * d
            // v0.1.10: thinner presence over the glass (was 115 / 100).
            rp.color = if (st.dark) Color.rgb(0xCF, 0xE4, 0xF2) else Color.rgb(0x5B, 0x7A, 0x94)
            val baseA = if (st.dark) 82 else 92
            val active = st.rainLevel * 30
            drops.forEachIndexed { i, dr ->
                if (i >= active) return@forEachIndexed
                val (fx, fy) = dropPos(dr, animTimeSec, w, h, d)
                val fade = smoothstep(-0.05f, 0.05f, fy) * (1f - smoothstep(0.90f, 1.02f, fy))
                if (fade <= 0.01f) return@forEachIndexed
                rp.alpha = (baseA * fade).toInt().coerceIn(0, 255)
                val x = fx * w
                val y = fy * h
                val len = dr.lenDp * d
                canvas.drawLine(x - len * dropSlant(dr), y - len, x, y, rp)
            }
        }

        // 8) Snowflakes (front), slow with a gentle sway. Height is
        // the pure flakeY(t); the sway was already a function of the
        // animation clock, so a flake's whole path is reproducible.
        if (st.snow) {
            val fp = snowPaint
            fp.color = withAlpha(if (st.dark) Color.WHITE else Color.rgb(0xC7, 0xD6, 0xE2), 190)
            for (f in flakes) {
                val fx = f.seedX * w + sin(animTimeSec * 0.7f + f.phase) * 8f * d
                val fy = flakeY(f, animTimeSec, h, d) * h
                canvas.drawCircle(fx, fy, f.rDp * d, fp)
            }
        }
    }
}
