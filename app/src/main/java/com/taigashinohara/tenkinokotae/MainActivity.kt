package com.taigashinohara.tenkinokotae

import android.app.Activity
import android.content.res.Configuration
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Presentation follows docs/ART_DIRECTION.md (確定 2026-11-18) and the
 * Simple Glass skill (v0.1.4 で正本どおりに実装、v0.1.5 で再照合):
 * - Accent is "その日の色" — one color per day, chosen by today's daytime
 *   average apparent temperature. Used only for the conclusion, card chips /
 *   labels, the selected tab and the primary button. (Color roles are
 *   ART_DIRECTION's domain and are NOT changed by the glass pass.)
 * - Base = 標準の土台: 3-point same-hue gradient + 3 lights of the day's
 *   tint drawn as radial gradients whose edges fully dissolve (plain Views
 *   have no backdrop blur; the lights' blur is substituted this way, and
 *   the reading glass keeps the density that separates it from the
 *   background color alone, per the skill's no-backdrop-filter rule).
 * - Surfaces = 色付きガラス (v0.1.6): the reading glass carries the day's
 *   tint itself instead of white alpha — on a dark base, white 19→13%
 *   rendered as colorless grey slabs on the real device (user feedback),
 *   so the face is now the day's tint at high alpha (light: tint lightened
 *   30% @82% → tint @73%; dark: tint lightened 10% @69% → tint @59%),
 *   with the same specular edge: a 1dp ring whose gradient runs top white
 *   75%→bottom 15% (dark 38%→6%). Small text sits on solid tint only.
 *   The skill sanctions colored faces ("色付きの面はブランド色の淡色を
 *   そのまま使い、透過で馴染ませる"); the contrast gates were re-scanned
 *   for the colored faces (see ART_DIRECTION.md §3 v0.1.6 追記).
 * - Type scale: 34 (conclusion) / 20 (heading) / 16 (body) / 14 (card sub) /
 *   13 (aux). Spacing scale: 4/8/12/16/24/32. Touch targets >= 48dp.
 * - v0.1.7 (minimal-information pass, docs/INFO_REVIEW_v0.1.7.md): only
 *   the amount and placement of information changed — constant texts
 *   (dates, "今日") removed, duplicated screen titles removed, hourly
 *   columns cut to the ones the answers use, error text down to one
 *   sentence, and Home stages its elements in on fresh data. Colors,
 *   glass surfaces and all Rules output are unchanged.
 * - v0.1.8 (minimal-information pass 2, docs/INFO_REVIEW_v0.1.8.md):
 *   the user asked for bold changes ("がんがんかえていいよ") after
 *   seeing v0.1.7 on a real device. Detail stops *listing* numbers:
 *   the always-visible layer is now figures (hero 体感 + 最高/最低/UV)
 *   and shapes (a 体感温度 line + rain bars chart, a pressure
 *   sparkline), and the full numeric record — summary rows and the
 *   hourly table, now with aligned columns — folds away under
 *   "くわしい数値". Settings drops radio buttons: 体感/感度 become
 *   segmented controls (the tab-bar pattern) and 地点 becomes a chip
 *   grid; descriptions shrink to one short line each. Home is
 *   unchanged (already at its floor; see the review doc).
 * - v0.1.9 (生きている空 + アイコン体系, docs/REVIEW_v0.1.9.md): the
 *   user's instruction — "背景で天気と時間が分かるようにアニメー
 *   ションさせて。アイコンや図形をふんだんに使って". The static
 *   BaseBackground is replaced by SkyView: the sky of the selected
 *   place right now (phase gradient from sunrise/sunset, sun/moon
 *   arc, clouds, rain, snow, fog, stars), driven only by data the
 *   app already fetched. The day's tint keeps its roles (glass,
 *   chips, conclusion, ambient glow). Every sky state was re-scanned
 *   for the text contrast gates before a single color was chosen.
 *   Line icons (Icons.kt, one stroke style, drawn in code) now lead
 *   each action card, the evening/tomorrow lines, and the Detail /
 *   Settings headings — pictures first, the Rules sentences stay.
 * - v0.1.10 (visual-calm pass, docs/INFO_REVIEW_v0.1.10.md): the same
 *   minimal-information skill, aimed at *visual* readability rather
 *   than text count. The sky stays but competes less (SkyView), the
 *   card icons align to the top of the main line so the left column
 *   keeps one rhythm down the stack, the fold header returns to the
 *   radius / spacing tokens, the Detail stat icons grow to the small-
 *   icon family size, and the Detail pressure heading follows the
 *   same level as Home's pressure card. Logic and Rules text are
 *   again untouched.
 * - v0.1.11 (bug-fix pass, docs/FIX_v0.1.11.md): the strict review's
 *   重大1件＋中・確実7件. Partial days stop pretending to be full
 *   days (Rules judges only the hours that exist; the 前日比 compares
 *   matched hours; Detail labels a remaining-hours average and a
 *   partial-day 最高/最低 as what they are), pressure drops are
 *   measured between real hours and can be *unmeasured*, loads are
 *   generation-guarded, rotation / theme flips no longer recreate
 *   the activity, the all-failed path is time-budgeted, and the
 *   Detail fold keeps the scroll position.
 * - v0.1.12 (fetch-hardening pass, docs/FIX_v0.1.12.md): the fetch
 *   review's six 中・確実 items. Response bodies are size-capped,
 *   superseded generations have their live connections disconnected
 *   (and retry hammering is guarded), a fresh cache shows instantly
 *   and refreshes behind itself, one attempt's connect+read timeouts
 *   now sum inside the remaining budget, 408/429 retry with
 *   Retry-After honored, and a slow primary is hedged by the backup
 *   in parallel (first success shows; the primary still wins last).
 * - v0.1.13 (editorial Home, docs/LAYOUT_v0.1.13.md): Home drops its
 *   cards for the B5 pattern the user picked from generated mockups.
 *   The conclusion is set in display type (one block per sentence,
 *   auto-stepped size, a soft halo for contrast) directly on the
 *   living sky; the three answers become a row of bare chips at the
 *   bottom (icon + label + short answer); pressure / evening /
 *   tomorrow and the backup / stale notes are small lines under the
 *   chips. The root is fixed-height with a weighted spacer, so Home
 *   always fits one screen without scrolling. The full answer
 *   sentences (main + sub) moved to a 今日の答え card at the top of
 *   Detail. Rules / fetch are untouched.
 * - v0.1.14 (今日の人 + widget, docs/FEATURE_v0.1.14.md): the user
 *   asked for a human figure so the way to go out reads at a glance
 *   ("寒そうだったり、暖かそうだったり") and for a home-screen
 *   widget. A line-drawn person (PersonFigure.kt) now stands on Home
 *   wearing the day's clothing rank, holding the day's umbrella and
 *   a hat on strong-UV days; the variation is a pure function with
 *   its own tests. Home also saves a widget snapshot on every
 *   display, and TenkiWidgetProvider renders it (the person drawn
 *   into a Bitmap by the same routine) — the widget never fetches.
 * - v0.1.15 (real-device fix pass, docs/FIX_v0.1.15.md): the user's
 *   screenshot of v0.1.14 showed the person as a stick figure at the
 *   bottom right, the no-umbrella chip icon reading as a flower, and
 *   the down chip icon as a bag. PersonDrawer is redrawn around
 *   per-rank silhouettes with thicker strokes and a wrapped scarf;
 *   the person grows (148/128/112dp) and moves up to stand directly
 *   under the conclusion, as one cluster with it; the umbrella-none
 *   and down icons are redrawn and the bottle family widened in
 *   Icons.kt. The one-screen budget is re-added and still fits.
 * - v0.1.16 (person as generated assets, docs/FEATURE_v0.1.16.md):
 *   the user asked for the person as generated imagery (「人は画像
 *   にしよう。組み合わせて使えるように」). PersonDrawer (the line
 *   drawing) is deleted; PersonComposer lays a generated rank
 *   portrait under the day's props (open / folded umbrella, hat) at
 *   fixed anchors, in Home and the widget alike. PersonFigure.spec
 *   and its thresholds are unchanged; the shiver / sweat signs are
 *   baked into the portraits.
 * - v0.1.17 (real-device layout fixes, docs/FIX_v0.1.17.md): the
 *   user's v0.1.16 screenshot (night, rank 6) asked for two fixes.
 *   The person moves from the right end to the horizontal center of
 *   the screen. And the conclusion's display size is no longer
 *   guessed from character counts — it is fitted by measuring the
 *   sentences (fitConclusion), so the conclusion lands on two lines
 *   (one sentence per line, one shared size) instead of wrapping
 *   mid-word into four.
 * - v0.1.18 (vertical balance, docs/FIX_v0.1.18.md): the user's
 *   v0.1.17 screenshot showed the conclusion + person hugging the
 *   top of the screen with a quarter of it empty below the person
 *   ("調整して"). The person is no longer a fixed step by screen
 *   height: PersonFigure.heightDp sizes it from the room the day's
 *   fixed parts leave in the column (60% of the leftover, clamped
 *   to 150..230dp), and the single weighted spacer under the person
 *   becomes two — one above, one below — so the leftover splits
 *   evenly and the figure floats at the center of the gap between
 *   the conclusion and the chips.
 * - v0.1.36 (night mode, docs/FEATURE_v0.1.36.md): the user asked
 *   that opening the app at night show tomorrow ("夜見たときは明日
 *   の天気を見れるように"). From Rules.NIGHT_SWITCH_HOUR (19:00)
 *   Home's main display — conclusion, the three answers, the α
 *   line, the person — is generated from tomorrow, compared
 *   against today; a dated 「明日 M/d（曜）」 label and a one-tap
 *   「今日を見る」 in the header keep the days unmistakable, and
 *   tonight survives as one small line. With no tomorrow data,
 *   Home stays on today and says why. Detail gains a dated
 *   明日の要点 card. The sky and the widget stay on today.
 * - v0.1.37 (concept-A redesign, docs/FEATURE_v0.1.37.md): the user
 *   picked design concept A （そらグラデーション) from a board of
 *   twelve and the whole app was re-skinned to it. The sky is now a
 *   fixed sky palette (pale blue -> warm peach horizon in light
 *   mode, deep sky navy in dark) instead of the day's tint; text is
 *   deep sky navy (light) / pale sky (dark) set directly on the
 *   sky; the glass is the skill's white reading glass again (light
 *   62->54% / dark 19->13%) — the v0.1.6 colored glass retires
 *   together with the tinted sky it was tuned for. Home's three
 *   answers are frosted-glass cards again (the B5 stack itself is
 *   unchanged), and the tab bar floats as a rounded glass pill
 *   with icon-led tabs, the selected tab a solid navy pill. The
 *   day's color survives only as the widget headline's color.
 *   Every text pair was re-scanned against the new grounds
 *   (weakest: sub on the light night sky top, 4.58:1).
 * - v0.1.38 (the sky follows the weather, docs/FEATURE_v0.1.38.md):
 *   at the user's request — "keep this mood, but let it change
 *   with the weather and the time" — the fixed palette now takes
 *   a weather cast on top of the phase mixes: seven groups
 *   (clear / partly / cloudy / rain / snow / thunder / fog, from
 *   the current hour's weather code, shared with the widget via
 *   SkyView's skyGroupForCode) pull each stop toward a scanned
 *   anchor, and the conclusion's glow thins as the cast deepens.
 *   A clear sky renders exactly as v0.1.37. The widget's face
 *   follows too (day / wet-day / night / wet-night variants).
 *   Re-scanned: no group falls below the clear-sky baseline on
 *   any text pair (docs/FEATURE_v0.1.38.md).
 * - v0.1.39 (the sky breathes, docs/FEATURE_v0.1.39.md): the user
 *   asked for the weather itself to animate — "リアルでヌルヌル
 *   動く雨や、日差し". The frame loop now runs at the display's
 *   vsync (the ~30 fps cap is gone), every particle position is a
 *   pure function of SkyView's animation clock, the sun/moon glow
 *   breathes, stars twinkle on slow swells, rain fades at the top
 *   and the ground — and Settings gains 空の動き， the user's own
 *   off switch next to the system's reduced-motion fallback. All
 *   animation modulations only dim from the scanned static values,
 *   so the v0.1.38 contrast guarantees hold for every frame.
 * Logic (Rules / WeatherApi / MetApi) is untouched by the design pass.
 */
class MainActivity : Activity() {

    // ---------- design tokens (ART_DIRECTION.md) ----------

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // v0.1.37 (concept A): the text colors join the sky world —
    // deep sky navy on the light sky, pale sky white on the night
    // sky. Both were re-scanned against every sky stop and glass
    // ground (docs/FEATURE_v0.1.37.md); sub's weakest ground is the
    // light sky at night (4.58:1), ink's is 5.82:1.
    private val ink: Int get() = if (isDark) rgb(0xED, 0xF3, 0xFB) else rgb(0x1E, 0x2C, 0x5F)
    private val sub: Int get() = if (isDark) rgb(0xC9, 0xD9, 0xEC) else rgb(0x2E, 0x3C, 0x72)
    private val paper: Int get() = if (isDark) rgb(0x12, 0x15, 0x1A) else rgb(0xFB, 0xF8, 0xF3)

    /**
     * そらアクセント (v0.1.37): the one solid color of the concept-A
     * world — deep sky navy in light mode, pale sky blue in dark.
     * It fills the selected tab / segment / chip and the primary
     * button (the skill's 役割色ベタ slots) and leads the answer
     * cards' icons. The day's color （その日の色） retired from
     * these roles with the redesign — the mock's world is navy on
     * sky throughout — and survives only as the widget headline's
     * color (the snapshot's accentDark, via [toneFor]).
     */
    private val accent: Int
        get() = if (isDark) rgb(0xA9, 0xC8, 0xF5) else rgb(0x1E, 0x2C, 0x5F)

    /** Fixed sky blue for the Detail chart's area fill (was the day's tint). */
    private val skyTint: Int get() = rgb(0x7F, 0xA8, 0xE0)

    private data class Tone(val accent: Int, val tint: Int)

    /**
     * その日の色 — bands match the clothing table in the design doc.
     * Since v0.1.37 the table has exactly one caller left: the widget
     * snapshot asks for the dark variant directly, because the
     * widget's face is fixed dark (v0.1.14, see TenkiWidget.kt), and
     * the day's color survives in the app only as that headline's
     * color. The in-app roles it used to fill (sky base, glass tint,
     * text accents) moved to the fixed concept-A sky palette and
     * [accent]. The values are ART_DIRECTION §2, unchanged.
     */
    private fun toneFor(daytimeAvg: Double, dark: Boolean): Tone = if (dark) {
        when {
            daytimeAvg >= 28 -> Tone(rgb(0xFF, 0xA1, 0x83), rgb(0x3A, 0x23, 0x1B))
            daytimeAvg >= 23 -> Tone(rgb(0xFF, 0xAB, 0x7A), rgb(0x40, 0x2A, 0x1C))
            daytimeAvg >= 18 -> Tone(rgb(0xDE, 0xC9, 0x78), rgb(0x37, 0x32, 0x1B))
            daytimeAvg >= 13 -> Tone(rgb(0x93, 0xD1, 0x8D), rgb(0x1E, 0x33, 0x20))
            daytimeAvg >= 8 -> Tone(rgb(0x63, 0xC7, 0xD4), rgb(0x16, 0x33, 0x3A))
            daytimeAvg >= 3 -> Tone(rgb(0x7C, 0xC7, 0xF0), rgb(0x1D, 0x32, 0x42))
            else -> Tone(rgb(0x8F, 0xB9, 0xF5), rgb(0x18, 0x2A, 0x47))
        }
    } else {
        when {
            daytimeAvg >= 28 -> Tone(rgb(0xA6, 0x2B, 0x00), rgb(0xFB, 0xE7, 0xDB))
            daytimeAvg >= 23 -> Tone(rgb(0xC1, 0x44, 0x00), rgb(0xFF, 0xED, 0xD9))
            daytimeAvg >= 18 -> Tone(rgb(0x8A, 0x6D, 0x00), rgb(0xF7, 0xF0, 0xDA))
            daytimeAvg >= 13 -> Tone(rgb(0x2E, 0x7D, 0x32), rgb(0xE7, 0xF3, 0xE4))
            daytimeAvg >= 8 -> Tone(rgb(0x00, 0x69, 0x78), rgb(0xE0, 0xF3, 0xF5))
            daytimeAvg >= 3 -> Tone(rgb(0x02, 0x77, 0xBD), rgb(0xE0, 0xF2, 0xFB))
            else -> Tone(rgb(0x0D, 0x47, 0xA1), rgb(0xE2, 0xED, 0xFA))
        }
    }

    /** Text color on an accent fill: dark accents take white, light (dark-mode) accents take ink. */
    private fun onAccent(accent: Int): Int {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val lum = 0.2126 * lin(Color.red(accent)) + 0.7152 * lin(Color.green(accent)) +
            0.0722 * lin(Color.blue(accent))
        return if (lum > 0.35) rgb(0x1E, 0x2C, 0x5F) else Color.WHITE
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = Color.argb(a, r, g, b)

    private fun mix(c: Int, other: Int, ratio: Float): Int {
        val r = (Color.red(c) + (Color.red(other) - Color.red(c)) * ratio).toInt()
        val g = (Color.green(c) + (Color.green(other) - Color.green(c)) * ratio).toInt()
        val b = (Color.blue(c) + (Color.blue(other) - Color.blue(c)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun withAlpha(c: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))

    /**
     * 読み物ガラス (v0.1.37): the glass is white again — the Simple
     * Glass skill's reading-glass values verbatim (light: white
     * 62% -> 54%; dark: white 19% -> 13%), under the same specular
     * ring as always (1dp gradient edge, top white 75% -> bottom
     * 15%, dark 38% -> 6%). The v0.1.6 colored glass (the day's tint
     * at high alpha) existed because white over the old dark-tinted
     * sky read as grey concrete; on concept A's fixed sky the white
     * glass is the mock's frosted face, and its effective color over
     * every sky stop was re-scanned with the new ink / sub
     * (docs/FEATURE_v0.1.37.md — weakest pair: dark sub on glass
     * over the day sky, 5.00:1). Built as two layers: the outer
     * layer paints the edge gradient, the inner layer (inset 1dp)
     * paints the glass fill, so only the 1dp ring stays visible.
     * Plain Views have no backdrop blur, so the fill alone carries
     * the separation, exactly as the skill prescribes.
     */
    private fun glassBg(radiusDp: Int = 24): Drawable {
        val edgeColors = if (isDark) {
            intArrayOf(argb(97, 255, 255, 255), argb(15, 255, 255, 255))
        } else {
            intArrayOf(argb(191, 255, 255, 255), argb(38, 255, 255, 255))
        }
        val edge = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, edgeColors).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }
        val fillColors = if (isDark) {
            intArrayOf(argb(48, 255, 255, 255), argb(33, 255, 255, 255))
        } else {
            intArrayOf(argb(158, 255, 255, 255), argb(138, 255, 255, 255))
        }
        val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, fillColors).apply {
            cornerRadius = dp((radiusDp - 1).coerceAtLeast(0)).toFloat()
        }
        return LayerDrawable(arrayOf(edge, fill)).apply {
            val e = dp(1)
            setLayerInset(1, e, e, e, e)
        }
    }

    /**
     * 役割色ベタのやんわりグラデーション (white +3% -> black -9%).
     * v0.1.5: the skill's +8%/-4% window put white text at 4.20:1 on the
     * top stop for the worst day band (寒い). The window is shifted down
     * (same 12pt step) so even the top stop clears 4.5:1 (scan: 4.56).
     * The contrast gate is the final judge per the skill; see
     * ART_DIRECTION.md §3 (v0.1.5 追記).
     */
    private fun solidBg(accent: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(mix(accent, Color.WHITE, 0.03f), mix(accent, Color.BLACK, 0.09f))
        ).apply { cornerRadius = dp(radiusDp).toFloat() }

    /**
     * Interactive 役割色ベタ with the states quality.md §3 requires:
     * pressed = deeper gradient, focused = normal gradient + darker edge.
     */
    private fun solidBgStates(accent: Int, radiusDp: Int): StateListDrawable {
        val pressed = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(mix(accent, Color.WHITE, 0.02f), mix(accent, Color.BLACK, 0.10f))
        ).apply { cornerRadius = dp(radiusDp).toFloat() }
        val focused = solidBg(accent, radiusDp).apply {
            setStroke(dp(2), mix(accent, Color.BLACK, 0.25f))
        }
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        states.addState(intArrayOf(android.R.attr.state_focused), focused)
        states.addState(intArrayOf(), solidBg(accent, radiusDp))
        return states
    }

    /** Transparent face for unselected tabs / rows, with a pressed state. */
    private fun ghostBg(radiusDp: Int): StateListDrawable {
        val pressedFill = if (isDark) argb(31, 255, 255, 255) else argb(64, 255, 255, 255)
        val pressed = GradientDrawable().apply {
            setColor(pressedFill)
            cornerRadius = dp(radiusDp).toFloat()
        }
        val empty = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(radiusDp).toFloat()
        }
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        states.addState(intArrayOf(), empty)
        return states
    }

    // ---------- 生きている空 (v0.1.9) ----------
    //
    // The background is SkyView; here we only compute its State from
    // the forecast model + the clock. Since v0.1.37 the gradient
    // stops are the fixed concept-A sky palette (light: pale blue
    // #8FB4E6 -> #C2DCF5 -> warm peach horizon #FDEBCB; dark: deep
    // sky navy #132238 / #1B3350 / #243D59), pulled toward the phase
    // colors exactly as before: warm (#FFC99A light / #8A5A38 dark)
    // around sunrise and sunset, night slate (light) or day-bright
    // (dark) by the dayness factor. Every coefficient below is a
    // scanned constant — the grid (light/dark x day / dawn / night x
    // sky and glass grounds) keeps ink / sub above their gates in
    // every sky state (docs/FEATURE_v0.1.37.md). Do not retune
    // without re-running the scan.
    //
    // v0.1.38: the palette also takes a weather cast — after the
    // phase mixes, each stop is pulled toward the weather group's
    // anchor by (per-stop ratio x shade), so a rainy sky sinks to
    // grey-blue, a snowy sky whitens, a thunder sky deepens, while
    // a clear sky keeps the exact v0.1.37 colors (shade 0). The
    // anchors are deliberately luminance-safe: in light mode the
    // wet anchors are desaturated rather than dark (an overcast
    // daytime sky is bright grey), because the night top stop is
    // the scanned weakest ground for sub (4.58:1) and any real
    // darkening there breaks the 4.5 gate. Re-scanned for all
    // 7 groups x phases (docs/FEATURE_v0.1.38.md): under a strict
    // 5-minute sampling of the reachable (dayness, warm) states,
    // no group falls below the clear-sky baseline on any pair.
    // Do not retune without re-running that scan.

    private val warmSky: Int get() = if (isDark) rgb(0x8A, 0x5A, 0x38) else rgb(0xFF, 0xC9, 0x9A)
    private val nightSlate: Int = rgb(0x8E, 0xA6, 0xBE)
    private val dayBright: Int = rgb(0x35, 0x52, 0x6B)

    /** One weather group's palette cast: an anchor + per-stop pull. */
    private data class SkyCast(val anchor: Int, val rTop: Float, val rMid: Float, val rBot: Float)

    private fun skyCast(group: SkyGroup): SkyCast? {
        return if (isDark) when (group) {
            SkyGroup.CLEAR -> null
            SkyGroup.PARTLY -> SkyCast(rgb(0x0F, 0x1B, 0x2A), 0.20f, 0.18f, 0.16f)
            SkyGroup.CLOUDY -> SkyCast(rgb(0x0E, 0x19, 0x26), 0.32f, 0.30f, 0.27f)
            SkyGroup.RAIN -> SkyCast(rgb(0x0B, 0x14, 0x1F), 0.46f, 0.43f, 0.40f)
            SkyGroup.THUNDER -> SkyCast(rgb(0x06, 0x0C, 0x14), 0.52f, 0.50f, 0.46f)
            SkyGroup.SNOW -> SkyCast(rgb(0x2B, 0x3D, 0x54), 0.26f, 0.26f, 0.24f)
            SkyGroup.FOG -> SkyCast(rgb(0x1A, 0x26, 0x34), 0.42f, 0.40f, 0.38f)
        } else when (group) {
            SkyGroup.CLEAR -> null
            SkyGroup.PARTLY -> SkyCast(rgb(0xB0, 0xC2, 0xD4), 0.26f, 0.24f, 0.28f)
            SkyGroup.CLOUDY -> SkyCast(rgb(0xA9, 0xBA, 0xCA), 0.30f, 0.34f, 0.44f)
            SkyGroup.RAIN -> SkyCast(rgb(0xA5, 0xB6, 0xC6), 0.30f, 0.36f, 0.55f)
            SkyGroup.THUNDER -> SkyCast(rgb(0x9F, 0xB2, 0xC4), 0.34f, 0.38f, 0.52f)
            SkyGroup.SNOW -> SkyCast(rgb(0xE8, 0xEE, 0xF4), 0.36f, 0.40f, 0.34f)
            SkyGroup.FOG -> SkyCast(rgb(0xB2, 0xC1, 0xCC), 0.46f, 0.48f, 0.50f)
        }
    }

    /** Phase- and weather-adjusted gradient stops (scanned formulas). */
    private fun skyStops(
        dayness: Float, warm: Float, group: SkyGroup, shade: Float
    ): Triple<Int, Int, Int> {
        var top: Int
        var mid: Int
        var bot: Int
        if (isDark) {
            val top0 = rgb(0x13, 0x22, 0x38)
            val mid0 = rgb(0x1B, 0x33, 0x50)
            val bot0 = rgb(0x24, 0x3D, 0x59)
            top = mix(mix(top0, dayBright, 0.42f * dayness), warmSky, 0.18f * warm)
            mid = mix(mix(mid0, dayBright, 0.34f * dayness), warmSky, 0.16f * warm)
            bot = mix(mix(bot0, dayBright, 0.12f * dayness), warmSky, 0.08f * warm)
        } else {
            val top0 = rgb(0x8F, 0xB4, 0xE6)
            val mid0 = rgb(0xC2, 0xDC, 0xF5)
            val bot0 = rgb(0xFD, 0xEB, 0xCB)
            top = mix(mix(top0, warmSky, 0.30f * warm), nightSlate, 0.40f * (1f - dayness))
            val midT = mix(nightSlate, bot0, 0.35f)
            mid = mix(mix(mid0, warmSky, 0.24f * warm), midT, 0.32f * (1f - dayness))
            bot = mix(bot0, warmSky, 0.12f * warm)
        }
        val cast = skyCast(group)
        if (cast != null && shade > 0f) {
            top = mix(top, cast.anchor, cast.rTop * shade)
            mid = mix(mid, cast.anchor, cast.rMid * shade)
            bot = mix(bot, cast.anchor, cast.rBot * shade)
        }
        return Triple(top, mid, bot)
    }

    /**
     * The ambient glow behind the conclusion (SkyView.State.glow):
     * warm sunlight in light mode, a pale moonlit blue in dark —
     * concept A's sun pool, at the scanned strengths. v0.1.38: the
     * glow thins as the weather cast deepens (shade), so an overcast
     * or rainy sky has no sunny pool behind the conclusion; a clear
     * sky keeps the v0.1.37 strength exactly.
     */
    private fun skyGlow(shade: Float): Int {
        val f = 1f - 0.5f * shade
        return if (isDark) {
            withAlpha(rgb(0x9D, 0xB8, 0xD9), (110 * f).toInt())
        } else {
            withAlpha(rgb(0xFF, 0xEF, 0xD0), (170 * f).toInt())
        }
    }

    private fun smoothstepF(a: Float, b: Float, x: Float): Float {
        val u = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    /** 0 at night, 1 in full day; ramps across sunrise / sunset. */
    private fun daynessAt(nowMin: Float, sunriseMin: Int, sunsetMin: Int): Float {
        val up = smoothstepF(sunriseMin - 40f, sunriseMin + 50f, nowMin)
        val down = 1f - smoothstepF(sunsetMin - 50f, sunsetMin + 40f, nowMin)
        return up * down
    }

    /** Dawn / dusk warmth: gaussian bumps (width 60 min) at sunrise+15 and sunset-15. */
    private fun warmAt(nowMin: Float, sunriseMin: Int, sunsetMin: Int): Float {
        fun g(c: Float): Float {
            val dd = (nowMin - c) / 60f
            return kotlin.math.exp(-(dd * dd).toDouble()).toFloat()
        }
        return maxOf(g(sunriseMin + 15f), g(sunsetMin - 15f)).coerceIn(0f, 1f)
    }

    /** "2026-11-19T06:35" -> minutes of day; fallback when the source has none. */
    private fun minutesOf(iso: String, fallback: Int): Int {
        if (iso.length < 16) return fallback
        val hh = iso.substring(11, 13).toIntOrNull() ?: return fallback
        val mm = iso.substring(14, 16).toIntOrNull() ?: return fallback
        return hh * 60 + mm
    }

    companion object {
        /**
         * Test seam, the same pattern as SkyView.clockMillis: the
         * funnel through which the screen logic reads "now" (hour,
         * minutes, date). Production always runs with the default —
         * the real clock — so behaviour is unchanged; the screen
         * tests pin it because in this environment Robolectric's
         * clock controls (SystemClock / scheduler) do not reach
         * System.currentTimeMillis or LocalDate.now (measured; see
         * docs/FEATURE_v0.1.38.md and docs/FIX_TEST_CLOCK.md).
         * Cache stamps and freshness checks keep reading the real
         * clock directly: they are compared against real timestamps
         * and never displayed.
         */
        internal var nowMillis: () -> Long = System::currentTimeMillis

        /** Today in Asia/Tokyo, as read through the [nowMillis] seam. */
        internal fun todayTokyo(): LocalDate =
            LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis()), ZoneId.of("Asia/Tokyo"))
    }

    private fun nowMinutesFloat(): Float {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            timeInMillis = nowMillis()
        }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60f +
            cal.get(java.util.Calendar.MINUTE) + cal.get(java.util.Calendar.SECOND) / 60f
    }

    /** Current hour of day in Asia/Tokyo — the night-mode switch reads it. */
    private fun nowHourTokyo(): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            timeInMillis = nowMillis()
        }
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    private data class SkyWeather(
        val cloudiness: Float, val cloudDark: Float,
        val rainLevel: Int, val snow: Boolean, val fog: Boolean,
        // v0.1.38: the palette cast — which mood group the sky is in
        // and how strongly the cast pulls (0 = the clear v0.1.37 sky).
        val group: SkyGroup = SkyGroup.CLEAR, val shade: Float = 0f
    )

    /**
     * Weather -> sky elements, from the hour entry nearest to now.
     * cloudiness drives how many clouds are awake, cloudDark how grey
     * they are, rainLevel 0..3 the streak density; snow / fog get
     * their own layers. WMO weather codes (Open-Meteo); the backup
     * source maps its symbols onto the same codes (MetApi), so one
     * table drives both. With no data the sky is time-only.
     * v0.1.38: the same row also names the palette group (via
     * [skyGroupForCode], shared with the widget) and the cast shade.
     */
    private fun skyWeather(today: DayForecast?): SkyWeather {
        if (today == null || today.hours.isEmpty()) {
            return SkyWeather(0f, 0f, 0, snow = false, fog = false)
        }
        val nowH = (nowMinutesFloat() / 60).toInt()
        val e = today.hour(nowH) ?: today.hours.minByOrNull { kotlin.math.abs(it.hour - nowH) }
            ?: return SkyWeather(0f, 0f, 0, snow = false, fog = false)
        var cloud = 0.4f
        var dark = 0.15f
        var rain = 0
        var snow = false
        var fog = false
        var shade = 0f
        when (e.weatherCode) {
            0 -> { cloud = 0.06f; dark = 0.0f }
            1 -> { cloud = 0.25f; dark = 0.05f; shade = 0.45f }
            2 -> { cloud = 0.5f; dark = 0.15f; shade = 0.7f }
            3 -> { cloud = 0.85f; dark = 0.35f; shade = 0.85f }
            45, 48 -> { cloud = 0.7f; dark = 0.3f; fog = true; shade = 0.75f }
            51, 53, 55, 56, 57 -> { cloud = 0.8f; dark = 0.45f; rain = 1; shade = 0.5f }
            61 -> { cloud = 0.85f; dark = 0.5f; rain = 1; shade = 0.65f }
            63 -> { cloud = 0.9f; dark = 0.6f; rain = 2; shade = 0.8f }
            65 -> { cloud = 0.95f; dark = 0.7f; rain = 3; shade = 1.0f }
            66, 67 -> { cloud = 0.9f; dark = 0.6f; rain = 2; shade = 0.8f }
            71, 73, 75, 77 -> { cloud = 0.85f; dark = 0.4f; snow = true; shade = 0.8f }
            80 -> { cloud = 0.7f; dark = 0.4f; rain = 1; shade = 0.5f }
            81 -> { cloud = 0.8f; dark = 0.5f; rain = 2; shade = 0.7f }
            82 -> { cloud = 0.9f; dark = 0.65f; rain = 3; shade = 0.95f }
            85, 86 -> { cloud = 0.8f; dark = 0.4f; snow = true; shade = 0.65f }
            95 -> { cloud = 0.95f; dark = 0.7f; rain = 2; shade = 0.85f }
            96, 99 -> { cloud = 1.0f; dark = 0.75f; rain = 3; shade = 1.0f }
        }
        if (rain == 0 && e.precip >= 0.1) rain = 1
        if (rain in 1..2 && e.precip >= 2.0) rain = 3
        return SkyWeather(cloud, dark, rain, snow, fog, skyGroupForCode(e.weatherCode), shade)
    }

    private fun applyBackground() {
        val today = forecast?.day(todayStr())
        val sunriseMin = today?.let { minutesOf(it.sunrise, 360) } ?: 360
        val sunsetMin = today?.let { minutesOf(it.sunset, 1080) } ?: 1080
        val now = nowMinutesFloat()
        val dayness = daynessAt(now, sunriseMin, sunsetMin)
        val warm = warmAt(now, sunriseMin, sunsetMin)
        val w = skyWeather(today)
        val (top, mid, bot) = skyStops(dayness, warm, w.group, w.shade)
        val glow = skyGlow(w.shade)
        skyView.skyState = SkyView.State(
            top, mid, bot, isDark, glow,
            sunriseMin, sunsetMin, dayness,
            w.cloudiness, w.cloudDark, w.rainLevel, w.snow, w.fog
        )
        window.statusBarColor = top
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (isDark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    // ---------- state ----------

    private lateinit var rootLayout: LinearLayout
    private lateinit var skyView: SkyView
    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    /** One tab of the floating bar: its clickable row, mark, and label. */
    private class TabButton(
        val root: LinearLayout, val icon: IconView, val label: TextView
    )

    private lateinit var tabButtons: List<TabButton>
    private val prefs by lazy { getSharedPreferences("tenki_prefs", MODE_PRIVATE) }

    private var forecast: Forecast? = null
    private var loading = false
    private var currentTab = 0
    /**
     * Set when fresh data is on its way in; the next Home build stages
     * its elements in (conclusion -> chips -> small lines). Tab
     * switches rebuild Home with this false, so returning to the tab
     * never replays the intro (v0.1.7, minimal-information 原則6).
     */
    private var introAnimate = true
    /**
     * Detail screen's "くわしい数値" fold (v0.1.8). The full numeric
     * record is B-rank (設計 v1.3: 数値は畳む), so it starts closed;
     * the state survives rebuilds within the session.
     */
    private var detailExpanded = false
    /**
     * Night mode (v0.1.36): the user tapped 「今日を見る」 on the
     * auto-tomorrow Home and pinned the display back to today. Kept
     * for as long as this display session lasts (never persisted);
     * [Rules.resolveHomeDay] reads it on every Home build.
     */
    private var homeManualToday = false

    /**
     * 見た目テスト (v0.1.19) slot overrides: -1 is 自動 (the day's own
     * spec), anything else pins that slot by hand. Plain fields, never
     * persisted — leaving the screen discards them, and they never
     * reach Home or the widget.
     */
    private var testOutfit = -1 // 0..6 = rank, 7 = raincoat
    private var testHead = -1 // 0 = なし, 1 = 帽子, 2 = ニット帽
    private var testMask = -1 // 0 = なし, 1 = あり
    private var testSung = -1 // 0 = なし, 1 = あり
    private var testHeld = -1 // 0 = なし, 1 = 傘, 2 = 折りたたみ, 3 = 日傘, 4 = うちわ
    private var testNeck = -1 // 0 = なし, 1 = マフラー, 2 = タオル
    private var testSky = 0 // 0 = 現在, 1 = 昼, 2 = 夕方, 3 = 夜
    /** True while [forecast] comes from the backup source (MET Norway). */
    private var backupMode = false
    /** Yesterday borrowed from a cached Open-Meteo response (backup mode only). */
    private var backupYesterday: DayForecast? = null
    /**
     * True while the on-screen forecast is a cache entry whose
     * background refresh has failed (FETCH_REVIEW #3): Home then shows
     * a quiet "前回のデータを表示しています" note. Set only when the
     * refresh actually fails — while it is still running, the cache
     * shows without the note.
     */
    private var staleCache = false
    /**
     * A load generation is still fetching (FETCH_REVIEW #2): plain
     * loadWeather calls (the error screen's retry button) are ignored
     * while one is in flight, so hammering retry cannot stack parallel
     * downloads onto the free APIs. A city change passes force and
     * supersedes the in-flight load instead. Set on the UI thread,
     * released inside the settling UI update; @Volatile because the
     * fetch threads read the generation state around it.
     */
    @Volatile private var loadInFlight = false
    /**
     * Detail scroll position, saved when the "くわしい数値" fold is
     * toggled (the toggle rebuilds the screen) and restored after the
     * rebuild, so opening the numbers no longer throws the user back
     * to the top (BUG_REVIEW #8). Also carries the position across the
     * in-place rebuild on a dark-mode flip (#6). Reset on new loads;
     * showScreen resets it whenever another tab is shown.
     */
    private var detailScrollY = 0
    /** The Detail screen's ScrollView, while it is on screen. */
    private var detailScrollView: ScrollView? = null
    /**
     * Last seen night-mode flag. The activity declares configChanges
     * for uiMode (BUG_REVIEW #6), so a dark-mode flip arrives via
     * onConfigurationChanged instead of a recreation; comparing against
     * this tells a theme flip (rebuild the screen with fresh colors)
     * from a pure rotation (leave the hierarchy — and its scroll — be).
     */
    private var lastNightMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED

    private fun city(): City = Cities.byName(prefs.getString("city", "大阪") ?: "大阪")
    private fun feelOffset(): Int = prefs.getInt("feel", 0)
    private fun sensitivity(): Int = prefs.getInt("sens", 1)

    /** 空の動き (v0.1.39): the app's own motion escape hatch, on unless the user turns it off. */
    private fun skyMotion(): Boolean = prefs.getBoolean("skymotion", true)

    /** Applies the 空の動き setting to the live sky at once (the settings toggle calls this). */
    private fun applySkyMotion() {
        if (!::skyView.isInitialized) return
        skyView.motionEnabled = skyMotion()
        if (skyMotion()) skyView.startAnimation() else skyView.stopAnimation()
        skyView.invalidate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = FrameLayout(this)
        rootLayout.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Floating pill tab bar (v0.1.37, concept A): the bar no
        // longer spans the screen edge to edge as a band — it floats
        // as one rounded glass pill with side margins, and each tab
        // leads with its picture (HOME / CHART / SLIDERS), the
        // selected tab a solid navy pill inside it (see showScreen).
        val tabWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = glassBg(32)
        }
        val names = listOf("ホーム", "数値で見る", "設定")
        val tabIcons = listOf(IconKind.HOME, IconKind.CHART, IconKind.SLIDERS)
        tabButtons = names.mapIndexed { i, name ->
            val label = TextView(this).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, 0, 0)
            }
            val icon = IconView(this, tabIcons[i], sub)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener { showScreen(i) }
            }
            root.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            root.addView(label)
            TabButton(root, icon, label)
        }
        tabButtons.forEach {
            // ART_DIRECTION §5: tab touch target is 56dp (was 52 in v0.1.4).
            tabBar.addView(it.root, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        tabWrap.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootLayout.addView(tabWrap)
        // The living sky sits behind everything, full-bleed (it also
        // paints behind the system bars); the UI column floats on it.
        skyView = SkyView(this)
        val frame = FrameLayout(this)
        frame.addView(
            skyView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        frame.addView(
            rootLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(frame)
        // Edge-to-edge (enforced at targetSdk 35): keep content clear of
        // the system bars. The sky behind the root still draws
        // full-bleed behind the bars; only the children are inset. Without this the
        // header (city name) slid under the status bar and collided with
        // the clock on the real device (v0.1.6 fix).
        rootLayout.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets
        }
        rootLayout.requestApplyInsets()
        applyBackground()
        lastNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        loadWeather()
    }

    /**
     * Rotation / dark-mode flips no longer recreate this activity
     * (android:configChanges in the manifest, BUG_REVIEW #6): the
     * fetched forecast, the current tab and the fold state live in
     * fields, so they simply survive. A pure size change needs nothing
     * — the hierarchy stays, scroll positions stay, and SkyView
     * redraws itself at the new size. A night-mode flip is different:
     * every color and glass face was baked with the old mode, so the
     * current screen is rebuilt in place with the new palette (the
     * Detail scroll position is carried across via detailScrollY).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != lastNightMode) {
            lastNightMode = night
            if (currentTab == 1) {
                detailScrollView?.let { detailScrollY = it.scrollY }
            }
            if (::rootLayout.isInitialized) showScreen(currentTab)
        }
    }

    override fun onResume() {
        super.onResume()
        // The sky animates only while the app is in front of the user
        // (battery discipline, ART_DIRECTION §6); SkyView itself also
        // honors reduced-motion / battery-saver by staying a still
        // picture. Re-applying the background refreshes the phase for
        // the current time after a long background stay.
        if (::skyView.isInitialized) {
            applyBackground()
            skyView.motionEnabled = skyMotion()
            if (skyMotion()) skyView.startAnimation() else skyView.stopAnimation()
        }
    }

    override fun onPause() {
        if (::skyView.isInitialized) skyView.stopAnimation()
        super.onPause()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- data loading ----------

    /**
     * Monotonic load generation (BUG_REVIEW #3): every loadWeather call
     * bumps it, and only the newest generation may write caches or touch
     * the UI. A slow fetch that finishes after the user has already
     * switched city (or hammered retry) discards its result instead of
     * overwriting the newer screen with a stale city.
     */
    @Volatile private var loadGeneration = 0

    /**
     * Live connections per load generation (FETCH_REVIEW #2): every
     * fetch attempt registers its HttpURLConnection here, and starting
     * a new generation disconnects the previous generations'
     * connections. That is what actually *stops* a superseded fetch
     * mid-flight — the generation check alone only discarded its
     * result after it had run to completion.
     */
    private val liveConnections =
        ConcurrentHashMap<Int, MutableSet<HttpURLConnection>>()

    private fun registerConnection(gen: Int, conn: HttpURLConnection) {
        if (gen != loadGeneration) {
            // Registered after its generation was already superseded:
            // stop this connection at once instead of tracking it.
            try { conn.disconnect() } catch (ignored: Exception) {}
            return
        }
        liveConnections.getOrPut(gen) { ConcurrentHashMap.newKeySet() }.add(conn)
    }

    /** Disconnects and forgets every live connection of [gen]. */
    private fun stopGeneration(gen: Int) {
        liveConnections.remove(gen)?.forEach { conn ->
            try { conn.disconnect() } catch (ignored: Exception) {}
        }
    }

    /** How long the primary source gets before the backup hedges in (FETCH_REVIEW #6). */
    private val hedgeDelayMs = 8_000L

    /**
     * Upper bound for letting a hedged backup finish in the background
     * after the primary already won, so its response can still be kept
     * as the backup cache. The backup's own 20 s budget ends it sooner
     * in practice.
     */
    private val backupDrainMs = 25_000L

    /**
     * Loads the current city's forecast.
     *
     * Display order (FETCH_REVIEW #3): a fresh (< 24 h) cache for the
     * current city goes on screen *immediately* — no spinner — and the
     * network fetch replaces it when it lands (without replaying the
     * intro staging). Only with nothing cached does the spinner show.
     *
     * Fetch order: Open-Meteo -> MET Norway (backup) -> error screen.
     * Some networks are rejected by the free Open-Meteo endpoint
     * (HTTP 403) while MET Norway answers fine, so a single source is
     * not enough. If Open-Meteo is still running after [hedgeDelayMs],
     * MET starts in parallel and the first success takes the screen
     * (FETCH_REVIEW #6); a backup that wins is still replaced by the
     * primary when the primary lands, because only the primary
     * carries yesterday and precipitation probability.
     *
     * [force] (a city change) supersedes an in-flight load: the old
     * generation's live connections are disconnected (FETCH_REVIEW
     * #2). Without [force], a call while a load is in flight is
     * ignored — the retry-hammering guard.
     */
    private fun loadWeather(force: Boolean = false) {
        if (loadInFlight && !force) return
        val gen = ++loadGeneration
        // Actually stop the superseded generations' in-flight fetches:
        // disconnecting their connections aborts the reads instead of
        // letting them run out their budgets in the background.
        for (old in liveConnections.keys.toList()) {
            if (old != gen) stopGeneration(old)
        }
        loadInFlight = true
        loading = true
        staleCache = false
        detailScrollY = 0
        val target = city()

        // Cache-first display (FETCH_REVIEW #3). The intro staging is
        // not replayed for a cache display, nor for its replacement.
        val cachedShown = showCachedForecast(target)
        introAnimate = !cachedShown
        if (cachedShown) loading = false
        showScreen(currentTab)

        thread { runFetch(gen, target, cachedShown) }
    }

    /**
     * Puts the freshest usable cache for [target] on screen — the
     * Open-Meteo cache first, then the MET cache, each only if under
     * 24 h old (the same freshness the old failure fallback used).
     * Only this city's own entries are ever read: the keys are
     * per-city by construction. Returns true when something was shown.
     */
    private fun showCachedForecast(target: City): Boolean {
        val cached = prefs.getString("cache_${target.name}", null)
        val cachedAt = prefs.getLong("cache_at_${target.name}", 0L)
        if (cached != null &&
            System.currentTimeMillis() - cachedAt < 24 * 60 * 60 * 1000L
        ) {
            try {
                forecast = WeatherApi.parse(org.json.JSONObject(cached))
                backupMode = false
                backupYesterday = null
                return true
            } catch (ignored: Exception) {
                // fall through to the MET cache
            }
        }
        val metCached = prefs.getString("metcache_${target.name}", null)
        val metCachedAt = prefs.getLong("metcache_at_${target.name}", 0L)
        if (metCached != null &&
            System.currentTimeMillis() - metCachedAt < 24 * 60 * 60 * 1000L
        ) {
            try {
                forecast = MetApi.parse(org.json.JSONObject(metCached))
                backupMode = true
                backupYesterday = yesterdayFromOpenMeteoCache(target)
                return true
            } catch (ignored: Exception) {
                // nothing usable is cached
            }
        }
        return false
    }

    /**
     * The fetch choreography for one load generation, on a background
     * thread. The primary source runs on its own thread so the backup
     * can hedge in parallel when the primary is slow (FETCH_REVIEW
     * #6); this thread only orchestrates and applies results. Every
     * stage re-checks the generation before parsing and before
     * touching the UI, so a superseded generation can neither write a
     * cache nor paint the screen.
     */
    private fun runFetch(gen: Int, target: City, cachedShown: Boolean) {
        fun current() = gen == loadGeneration
        val isCancelled = { !current() }
        val register: (HttpURLConnection) -> Unit = { conn ->
            registerConnection(gen, conn)
        }
        var shown = cachedShown
        var primaryError: Exception? = null

        // Applies a fetched forecast on the UI thread. [animate] is
        // true only for the first thing this generation shows when no
        // cache preceded it — replacements never replay the intro.
        // [settle] releases the load-in-flight guard in the same UI
        // pass, so a retry tap can never slip in between the flag and
        // the screen update.
        fun applyFetched(
            f: Forecast, backup: Boolean, yday: DayForecast?,
            animate: Boolean, settle: Boolean
        ) {
            runOnUiThread {
                if (!current()) return@runOnUiThread
                forecast = f
                backupMode = backup
                backupYesterday = yday
                staleCache = false
                loading = false
                if (settle) loadInFlight = false
                introAnimate = animate
                showScreen(currentTab)
            }
        }

        // Parses + caches + shows a primary-source response. False
        // when the generation moved on or parsing failed — a parse
        // failure is treated as a primary failure, as before, so the
        // backup still gets its turn.
        fun showPrimary(text: String, settle: Boolean): Boolean {
            if (!current()) return false
            val f = try {
                WeatherApi.parse(org.json.JSONObject(text))
            } catch (e: Exception) {
                primaryError = e
                return false
            }
            if (!current()) return false
            // Keep the last good response so a later network failure
            // can still show something instead of an error screen.
            prefs.edit()
                .putString("cache_${target.name}", text)
                .putLong("cache_at_${target.name}", System.currentTimeMillis())
                .apply()
            applyFetched(f, backup = false, yday = null, animate = !shown, settle = settle)
            shown = true
            return true
        }

        fun showBackup(text: String, settle: Boolean): Boolean {
            if (!current()) return false
            val f = try {
                MetApi.parse(org.json.JSONObject(text))
            } catch (ignored: Exception) {
                return false
            }
            if (!current()) return false
            prefs.edit()
                .putString("metcache_${target.name}", text)
                .putLong("metcache_at_${target.name}", System.currentTimeMillis())
                .apply()
            val yday = yesterdayFromOpenMeteoCache(target)
            applyFetched(f, backup = true, yday = yday, animate = !shown, settle = settle)
            shown = true
            return true
        }

        // This generation is fully settled with data on screen.
        fun finishOk() {
            stopGeneration(gen)
        }

        // Both sources failed (or their bodies did not parse). A cache
        // display, if one is up, stays — now with the stale note. With
        // nothing shown, the error screen carries the primary's reason.
        fun finishWithFailure() {
            stopGeneration(gen)
            runOnUiThread {
                if (!current()) return@runOnUiThread
                loading = false
                loadInFlight = false
                if (cachedShown) {
                    staleCache = true
                    introAnimate = false
                    showScreen(currentTab)
                } else {
                    showError(primaryError?.message ?: "不明なエラー")
                }
            }
        }

        // 1) Primary source, on its own thread so the backup can be
        // started in parallel if the primary drags (the hedge below).
        val primaryText = AtomicReference<String?>(null)
        val primaryErr = AtomicReference<Exception?>(null)
        val primaryDone = CountDownLatch(1)
        val eitherDone = CountDownLatch(1)
        thread {
            try {
                primaryText.set(WeatherApi.fetchText(target, isCancelled, register))
            } catch (e: Exception) {
                primaryErr.set(e)
            } finally {
                primaryDone.countDown()
                eitherDone.countDown()
            }
        }

        if (primaryDone.await(hedgeDelayMs, TimeUnit.MILLISECONDS)) {
            // The primary answered (or failed) quickly: no hedge.
            if (!current()) { stopGeneration(gen); return }
            val text = primaryText.get()
            if (text != null && showPrimary(text, settle = true)) { finishOk(); return }
            if (text == null) primaryError = primaryErr.get()
            // Serial backup, as before.
            val bText = try {
                MetApi.fetchText(target, isCancelled, register)
            } catch (ignored: Exception) {
                null
            }
            if (!current()) { stopGeneration(gen); return }
            if (bText != null && showBackup(bText, settle = true)) { finishOk(); return }
            finishWithFailure()
            return
        }

        // 2) Hedge (FETCH_REVIEW #6): the primary is still running
        // after hedgeDelayMs, so the backup starts in parallel and the
        // first success takes the screen.
        if (!current()) { stopGeneration(gen); return }
        val backupText = AtomicReference<String?>(null)
        val backupDone = CountDownLatch(1)
        thread {
            try {
                backupText.set(MetApi.fetchText(target, isCancelled, register))
            } catch (ignored: Exception) {
                // the orchestrator reads the (missing) result below
            } finally {
                backupDone.countDown()
                eitherDone.countDown()
            }
        }

        eitherDone.await()
        if (!current()) { stopGeneration(gen); return }

        // The primary is in (it may have finished first, or during the
        // same instant as the backup): it wins the screen outright.
        primaryText.get()?.let { text ->
            if (showPrimary(text, settle = true)) {
                // The backup may still be running. Let it drain in the
                // background and keep a successful response as the
                // backup cache — it never touches the screen.
                if (backupDone.await(backupDrainMs, TimeUnit.MILLISECONDS) && current()) {
                    backupText.get()?.let { bText ->
                        try {
                            MetApi.parse(org.json.JSONObject(bText))
                            prefs.edit()
                                .putString("metcache_${target.name}", bText)
                                .putLong(
                                    "metcache_at_${target.name}",
                                    System.currentTimeMillis()
                                )
                                .apply()
                        } catch (ignored: Exception) {
                            // an unparsable backup response is not cached
                        }
                    }
                }
                finishOk()
                return
            }
        }

        // The backup got in first: it takes the screen now, and the
        // primary — still running — replaces it when it lands (its
        // data carries yesterday and precipitation probability, and
        // the swap clears the "予備のデータ" note via backupMode).
        backupText.get()?.let { text ->
            if (showBackup(text, settle = false)) {
                primaryDone.await()
                if (!current()) { stopGeneration(gen); return }
                val pText = primaryText.get()
                val replaced = pText != null && showPrimary(pText, settle = true)
                if (!replaced) {
                    // The primary failed (or its body did not parse):
                    // the backup display stays as this generation's
                    // result; just release the guard.
                    if (primaryError == null) primaryError = primaryErr.get()
                    runOnUiThread { if (current()) loadInFlight = false }
                }
                finishOk()
                return
            }
        }

        // The first finisher failed and the other was still running:
        // wait for the other one, then take whichever succeeded.
        primaryDone.await()
        backupDone.await()
        if (!current()) { stopGeneration(gen); return }
        primaryText.get()?.let { text ->
            if (showPrimary(text, settle = true)) { finishOk(); return }
        }
        backupText.get()?.let { text ->
            if (showBackup(text, settle = true)) { finishOk(); return }
        }
        if (primaryError == null) primaryError = primaryErr.get()
        finishWithFailure()
    }

    /** Yesterday's forecast from the cached Open-Meteo response, if still usable. */
    private fun yesterdayFromOpenMeteoCache(target: City): DayForecast? {
        val cached = prefs.getString("cache_${target.name}", null) ?: return null
        val cachedAt = prefs.getLong("cache_at_${target.name}", 0L)
        if (System.currentTimeMillis() - cachedAt >= 72 * 60 * 60 * 1000L) return null
        return try {
            WeatherApi.parse(org.json.JSONObject(cached)).day(shiftedStr(-1))
        } catch (e: Exception) {
            null
        }
    }

    private fun todayStr(): String =
        todayTokyo().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun shiftedStr(days: Long): String =
        todayTokyo().plusDays(days)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    // ---------- state screens ----------

    private fun showError(msg: String, network: Boolean = true) {
        content.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "答えを取りにいけませんでした"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            // One sentence (v0.1.7): the second sentence only repeated
            // the button below ("もう一度試す"). What stays is the fact
            // that creates trust — the app already retried by itself
            // (ART_DIRECTION §8). No attempt count (v0.1.11): the retry
            // shape is now two sources under time budgets, so a fixed
            // number would be a claim the code no longer makes true.
            text = if (network) {
                "何度か自動で試しましたが、つながりませんでした。"
            } else {
                "今日のデータが届いていません。"
            }
            textSize = 16f
            setTextColor(sub)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            lineHeight = dp(26)
        })
        col.addView(TextView(this).apply {
            text = "（$msg）"
            textSize = 13f
            setTextColor(sub)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })
        col.addView(primaryButton("もう一度試す") { loadWeather() })
        content.addView(col)
    }

    private fun showLoading() {
        content.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val pb = ProgressBar(this)
        pb.indeterminateDrawable?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        col.addView(pb)
        col.addView(TextView(this).apply {
            text = "今日の答えを取りにいっています"
            textSize = 16f
            setTextColor(sub)
            setPadding(0, dp(16), 0, 0)
        })
        content.addView(col)
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(onAccent(accent))
            background = solidBgStates(accent, 26)
            minHeight = dp(52)
            setPadding(dp(32), 0, dp(32), 0)
            setOnClickListener { onClick() }
        }

    // ---------- screens ----------

    private fun showScreen(idx: Int) {
        currentTab = idx
        // The saved Detail scroll position belongs to the Detail visit
        // that produced it; leaving the tab ends that visit. (The fold
        // toggle re-enters via showScreen(1), which keeps it.)
        if (idx != 1) detailScrollY = 0
        applyBackground()
        // The floating pill's glass is rebuilt alongside the sky
        // (v0.1.37): white reading glass, radius 32; the selected
        // tab is the solid sky-accent pill (radius 28, concentric
        // with the bar's 5dp padding), icon and label recolored
        // together.
        tabBar.background = glassBg(32)
        tabButtons.forEachIndexed { i, b ->
            if (i == idx) {
                b.root.background = solidBgStates(accent, 28)
                b.label.setTextColor(onAccent(accent))
                b.label.setTypeface(null, Typeface.BOLD)
                b.icon.iconColor = onAccent(accent)
            } else {
                b.root.background = ghostBg(28)
                b.label.setTextColor(sub)
                b.label.setTypeface(null, Typeface.NORMAL)
                b.icon.iconColor = sub
            }
        }
        if (loading) { showLoading(); return }
        val f = forecast
        if (f == null) { showError("まだ取得していません", network = false); return }
        when (idx) {
            0 -> showHome(f)
            1 -> showDetail(f)
            else -> showSettings()
        }
    }

    private fun scrollOf(inner: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.addView(inner)
        return sv
    }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(24))
    }

    /**
     * A soft halo behind text that sits directly on the sky (the
     * editorial conclusion and its gap note): white in light mode,
     * near-black in dark mode, so the glyph's immediate ground is
     * pushed away from the accent in luminance. The v0.1.13 contrast
     * scan models it as a 0.45 mix toward the halo color (shadow
     * alpha 0.90 diluted by the blur) and measures the conclusion's
     * worst case improving from 3.01 to 3.75 (light) and 3.18 to
     * 5.79 (dark) — see docs/LAYOUT_v0.1.13.md. This is a text
     * underlay, not a face shadow (faces still take no shadow,
     * ART_DIRECTION §3).
     */
    private fun skyHalo(tv: TextView) {
        val c = if (isDark) argb(230, 8, 11, 16) else argb(230, 255, 255, 255)
        tv.setShadowLayer(dp(16).toFloat(), 0f, 0f, c)
    }

    /**
     * 答えカード (v0.1.37, concept A): one of the three answers as a
     * frosted-glass card floating on the sky — white reading glass
     * (Simple Glass 正本 values, see [glassBg]), the state's line
     * icon in the sky accent, the kind label, and the short answer.
     * v0.1.13–0.1.36 the chips were bare columns on the sky (the B5
     * editorial form); the concept the user picked puts the glass
     * back under the three answers only — the conclusion and the
     * small lines still sit directly on the sky, and Home's stack
     * (conclusion -> person -> cards -> small lines, fixed height,
     * no scrolling) is unchanged. The full sentences (main + sub)
     * live on Detail's 今日の答え card (answerRow).
     */
    private fun answerChip(
        icon: IconKind, label: String, answer: String
    ): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(14), dp(6), dp(14))
            background = glassBg(24)
        }
        val iv = IconView(this, icon, accent)
        iv.contentDescription = label
        c.addView(iv, LinearLayout.LayoutParams(dp(28), dp(28)))
        c.addView(TextView(this).apply {
            text = label
            textSize = 13f; setTextColor(sub)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        c.addView(TextView(this).apply {
            text = answer
            textSize = 15f; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 3
            lineHeight = dp(20)
            setPadding(0, dp(2), 0, 0)
        })
        return c
    }

    /**
     * One answer on Detail's 今日の答え card (v0.1.13): the icon +
     * label + main + sub the Home action cards used to carry, as a
     * row inside the shared card. Home's editorial layout shows only
     * the short answer; the full sentences moved here.
     */
    private fun answerRow(
        icon: IconKind, label: String, main: String, subText: String,
        first: Boolean = false
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(if (first) 12 else 16), 0, 0)
        }
        val iv = IconView(this, icon, ink)
        iv.contentDescription = label
        row.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        texts.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(sub)
            setTypeface(null, Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = main; textSize = 16f; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
            lineHeight = dp(22)
        })
        if (subText.isNotEmpty()) texts.addView(TextView(this).apply {
            text = subText; textSize = 14f; setTextColor(sub)
            setPadding(0, dp(2), 0, 0)
            lineHeight = dp(22)
        })
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    /** Clothing rank -> the garment's shape (Icons.kt). */
    private fun clothingIcon(rank: Int): IconKind = when (rank.coerceIn(0, 6)) {
        0 -> IconKind.TEE
        1 -> IconKind.SHIRT_MID
        2 -> IconKind.SHIRT_LONG
        3 -> IconKind.SWEATER
        4 -> IconKind.COAT
        5 -> IconKind.COAT_SCARF
        else -> IconKind.DOWN
    }

    private fun umbrellaIcon(level: Rules.UmbrellaLevel): IconKind = when (level) {
        Rules.UmbrellaLevel.NEED -> IconKind.UMBRELLA_OPEN
        Rules.UmbrellaLevel.FOLDING -> IconKind.UMBRELLA_FOLDED
        Rules.UmbrellaLevel.NONE -> IconKind.UMBRELLA_NONE
    }

    /** UV strength -> bottle picture; the strongest sun adds the parasol. */
    private fun sunscreenIcon(uvMax: Double): IconKind = when {
        uvMax < 3.0 -> IconKind.BOTTLE_SLASH
        uvMax < 6.0 -> IconKind.BOTTLE
        uvMax < 8.0 -> IconKind.BOTTLE_SUN
        else -> IconKind.BOTTLE_SHADE
    }

    private fun pressureIcon(level: Int): IconKind = when {
        level >= 2 -> IconKind.PRESSURE_STEEP
        level == 1 -> IconKind.PRESSURE_FALL
        else -> IconKind.PRESSURE_SLIGHT
    }

    /**
     * A small icon-led line on the sky (気圧 / 帰り / 明日 on Home,
     * v0.1.13): 18dp mark + 14sp text, no face. The celestial marks
     * still arrive before the words (v0.1.9): the moon says "tonight"
     * and the sun says "tomorrow" before the line is read.
     */
    private fun smallLine(
        icon: IconKind, desc: String, text: String, color: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iv = IconView(this, icon, color)
        iv.contentDescription = desc
        row.addView(iv, LinearLayout.LayoutParams(dp(18), dp(18)))
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 14f; setTextColor(color)
            setPadding(dp(8), 0, 0, 0)
            lineHeight = dp(22)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /**
     * Splits a Rules conclusion into its sentences, keeping the
     * terminal 。 on each: the editorial layout sets every sentence
     * as its own block of display type.
     */
    private fun conclusionSentences(text: String): List<String> =
        text.split("。").map { it.trim() }.filter { it.isNotEmpty() }.map { "$it。" }

    /** A fitted conclusion: one shared text size, and the lines to
     * set (normally the sentences themselves, one per line). */
    private data class ConclusionFit(val sizeSp: Float, val lines: List<String>)

    /**
     * Fits the editorial conclusion to two lines — one sentence per
     * line — by *measuring* the sentences in the bold face the
     * TextViews render with (v0.1.17). The v0.1.13–16 sizing guessed
     * the size from the longest sentence's character count, and on
     * the real device the guess wrapped mid-word (「昨日とほぼ同じ
     * 体／感。」 at 42sp in the user's v0.1.16 screenshot), so the
     * conclusion sprawled over four lines. Now each sentence's
     * fitting size is measured (Paint.measureText at a reference
     * size; text width scales linearly with size) against the real
     * available width — the screen minus the column's side padding,
     * with a 2% safety margin for face differences — and the whole
     * conclusion takes the smallest of them, so both sentences share
     * one size and each fits on its own line.
     *
     * The size is capped at 46sp (the old top step) and floored at
     * 24sp. A sentence that cannot fit even at the floor is broken
     * once at a phrase boundary ([breakSentence]) — but only while
     * the conclusion stays within three lines; if *every* sentence
     * would need a break (the two longest patterns together on a
     * narrow screen), the two-line form wins and the size follows
     * the measured fit below the floor instead of breaking both.
     * The longest patterns Rules can emit are both 15.0em wide
     * (「昨日とは別世界なくらい暖かい。」/「トレーナー＋ライト
     * アウターで。」, measured in Noto Sans CJK Bold): they fit the
     * floor at 24.7sp on a 411dp-wide screen and take the
     * below-floor fit (21.3sp, still two lines) at 360dp wide.
     * The full table is in docs/FIX_v0.1.17.md.
     */
    private fun fitConclusion(sentences: List<String>): ConclusionFit {
        if (sentences.isEmpty()) return ConclusionFit(46f, sentences)
        val dm = resources.displayMetrics
        val availPx = (dm.widthPixels - dp(40)) * 0.98f
        if (availPx <= 0f) return ConclusionFit(46f, sentences)
        val paint = Paint().apply { typeface = Typeface.DEFAULT_BOLD }
        paint.textSize = 100f * dm.scaledDensity
        fun fitSp(s: String): Float {
            val w = paint.measureText(s)
            return if (w > 0f) 100f * availPx / w else 46f
        }
        val fits = sentences.map { fitSp(it) }
        val unified = minOf(46f, fits.min())
        if (unified >= 24f) return ConclusionFit(unified, sentences)
        val tooLong = sentences.indices.filter { fits[it] < 24f }
        // Breaking every too-long sentence adds one line each; keep
        // the floor only while the conclusion stays within 3 lines.
        if (sentences.size + tooLong.size <= 3) {
            val lines = sentences.mapIndexed { i, s ->
                if (i in tooLong) breakSentence(paint, s) else s
            }
            return ConclusionFit(24f, lines)
        }
        return ConclusionFit(unified, sentences)
    }

    /**
     * Breaks one sentence into two lines at a phrase boundary: the
     * split that best balances the two halves' measured widths,
     * preferring (within 15% of the best balance) a split right
     * after a bunsetsu-ending character (は／が／を／に／の／な／と／
     * も／＋／／), so the break never lands mid-word when a natural
     * boundary is nearly as balanced — e.g. 「トレーナー＋｜ライト
     * アウターで。」 and 「昨日とは別世界な｜くらい暖かい。」.
     */
    private fun breakSentence(paint: Paint, s: String): String {
        if (s.length < 4) return s
        val boundary = setOf('は', 'が', 'を', 'に', 'の', 'な', 'と', 'も', '＋', '／')
        var best = Float.MAX_VALUE
        val scores = FloatArray(s.length)
        for (i in 2..s.length - 2) {
            val l = paint.measureText(s.substring(0, i))
            val r = paint.measureText(s.substring(i))
            scores[i] = maxOf(l, r)
            if (scores[i] < best) best = scores[i]
        }
        val limit = best * 1.15f
        var pick = -1
        for (i in 2..s.length - 2) {
            if (scores[i] <= limit && s[i - 1] in boundary) { pick = i; break }
        }
        if (pick < 0) {
            for (i in 2..s.length - 2) if (scores[i] == best) { pick = i; break }
        }
        if (pick < 0) return s
        return s.substring(0, pick) + "\n" + s.substring(pick)
    }

    /**
     * The tab bar's total height (dp): tabWrap padding 10 + 16, the
     * floating bar's own padding 5 + 5 and the 56dp tabs (onCreate).
     * Only the estimate path of [homeColumnHeightDp] needs it; once
     * the content area has been laid out its real height is read
     * instead.
     */
    private val tabBarHeightDp = 10 + 16 + 5 + 5 + 56

    /**
     * Height (dp) of the Home column — the space the Home stack has
     * to fit, i.e. what [PersonFigure.heightDp] calls the column.
     * After the first layout the content area's measured height is
     * exact; before it (the first display, straight out of onCreate)
     * it is estimated as the screen minus the root's window-insets
     * padding (already applied by then in practice — the fetch that
     * leads here is asynchronous) minus the tab bar. An overestimate
     * only ever makes the person a little tall on that first frame;
     * the next display reads the measured height and self-corrects.
     */
    private fun homeColumnHeightDp(): Float {
        val density = resources.displayMetrics.density
        if (content.height > 0) return content.height / density
        val screenDp = resources.displayMetrics.heightPixels / density
        val insetsDp = (rootLayout.paddingTop + rootLayout.paddingBottom) / density
        return screenDp - insetsDp - tabBarHeightDp
    }

    /**
     * Summed height (dp) of Home's fixed parts for the day — every
     * element of the stack except the person and the free space
     * around it — from the same constants the views are built with,
     * so [PersonFigure.heightDp] sizes the person against the real
     * stack (v0.1.18):
     * - column padding 20 + 12 and the city header (13sp; the 21dp
     *   the budgets have used since v0.1.15, font padding included);
     * - the conclusion: 10dp lead-in plus one fitted line height per
     *   line of [fit] (the TextViews' own lineHeight values);
     * - the gap note when present: 8dp padding + its 26dp line;
     * - the answer cards (v0.1.37): 14dp card padding + 28dp icon +
     *   5dp label padding + 20dp label (13sp bold, padding
     *   included — the same accounting that made the old 116dp
     *   three-line budget) + 2dp answer padding +
     *   [chipAnswerLines] × the answers' 20dp line height + 14dp
     *   card padding;
     * - the small lines: 10dp top padding + [smallLineCount] × their
     *   22dp line height + 5dp gaps between them.
     */
    private fun homeFixedHeightDp(
        fit: ConclusionFit, hasGapNote: Boolean, chipAnswerLines: Int, smallLineCount: Int,
        headerExtraDp: Float = 0f
    ): Float {
        // headerExtraDp: night mode's header row grows by the toggle's
        // vertical padding (v0.1.36); today mode's header is the bare
        // 21dp city line as before.
        var h = 32f + 21f + headerExtraDp
        h += 10f + fit.lines.size * (fit.sizeSp * 1.22f).toInt()
        if (hasGapNote) h += 34f
        h += 14 + 28 + 5 + 20 + 2 + chipAnswerLines * 20 + 14
        h += 10 + smallLineCount * 22 + (smallLineCount - 1).coerceAtLeast(0) * 5
        return h
    }

    /**
     * How many lines the tallest of the three card answers wraps to
     * (1..3, the cards' maxLines), measured with the same bold face
     * the cards render in: each card gets a third of the column
     * width minus the two 8dp gaps between the cards (v0.1.37) and
     * its own 6+6dp padding. A non-positive width falls back to the
     * maximum — the conservative side for the budget.
     */
    private fun chipAnswerLines(answers: List<String>): Int {
        val dm = resources.displayMetrics
        val chipTextPx = (dm.widthPixels - dp(40) - dp(16)) / 3f - dp(12)
        if (chipTextPx <= 0f) return 3
        val paint = Paint().apply { typeface = Typeface.DEFAULT_BOLD }
        paint.textSize = 15f * dm.scaledDensity
        return answers.maxOf { s ->
            val w = paint.measureText(s)
            if (w <= 0f) 1 else ceil(w / chipTextPx).toInt().coerceIn(1, 3)
        }
    }

    private fun showHome(f: Forecast) {
        val today = f.day(todayStr())
        // The backup source has no yesterday; borrow it from the Open-Meteo
        // cache when possible, else use the no-yesterday conclusion.
        val yesterday = f.day(shiftedStr(-1)) ?: backupYesterday
        val tomorrow = f.day(shiftedStr(1))
        if (today == null) { showError("今日のデータがありません", network = false); return }

        // Night mode (v0.1.36, the user's request — 夜見たときは明日を):
        // from Rules.NIGHT_SWITCH_HOUR on, Home's main display becomes
        // tomorrow. Everything the screen is made of — the conclusion,
        // the three answers, the α (pressure) line, the gap note and
        // 今日の人 — is generated from the *displayed* day, and the
        // comparison base shifts with it: tomorrow is measured against
        // today with the same matched-hours rule (Rules.conclusion's
        // base / dayWord). Tonight itself is not erased: it stays as
        // one small line (Rules.tonightLine). When tomorrow's data is
        // not really there, Home stays on today and says why in the
        // small lines — a tomorrow display is never faked from absent
        // data. The sky and the widget snapshot keep describing today
        // (the sky is *now*; the widget is today's glance) — only
        // Home's main display turns.
        val hourNow = nowHourTokyo()
        val tomorrowUsable = tomorrow != null && tomorrow.hours.isNotEmpty()
        val tomorrowMode = Rules.resolveHomeDay(hourNow, homeManualToday, tomorrowUsable) ==
            Rules.HomeDay.TOMORROW
        val nightNoTomorrow = !tomorrowMode && !homeManualToday &&
            Rules.autoHomeDay(hourNow) == Rules.HomeDay.TOMORROW && !tomorrowUsable
        val disp = if (tomorrowMode) tomorrow!! else today
        val prev = if (tomorrowMode) today else yesterday
        val dispWord = if (tomorrowMode) "明日" else "今日"
        val prevWord = if (tomorrowMode) "今日" else "昨日"

        // The displayed day's three answers, computed once: the chips
        // and the person read the same values. (The widget snapshot
        // computes its own from today, further down.)
        val clothing = Rules.clothing(disp, feelOffset())
        val umb = Rules.umbrella(disp)
        val sun = Rules.sunscreen(disp.uvMax)
        // The pressure line and the gap note are also computed here,
        // ahead of the views: since v0.1.18 the person's height is
        // sized from the day's fixed parts (homeFixedHeightDp), and
        // whether these two exist is part of that sum. The small
        // lines further down read the same values.
        val pres = Rules.pressure(disp, sensitivity())
        val gapNoteText = Rules.gapNote(disp)
        // Editorial layout (v0.1.13, the B5 pattern; glass cards
        // restored under the three answers only in v0.1.37). The sky
        // itself is the surface — the conclusion is set in display
        // type directly on it, the three answers are a row of
        // frosted-glass cards at the bottom, and the rest are small
        // lines.
        // The root is fixed-height; since v0.1.18 two weighted
        // spacers — one above the person, one below — split whatever
        // room the fixed parts leave, so everything fits one screen
        // without scrolling (the v0.1.12 "明日" line was clipped by
        // the tab bar) and the person floats mid-gap instead of
        // hugging the conclusion. Full answer sentences are on Detail.
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // v0.1.15: top 24 -> 20, bottom 16 -> 12 — part of the
            // room that lets 今日の人 grow (docs/FIX_v0.1.15.md).
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        // Header carries the city only in today mode (v0.1.7,
        // minimal-information pass): the date was a constant — this
        // screen always showed today — so it carried no information,
        // only glyphs. The city can change (8 places in Settings), so
        // it stays. Night mode (v0.1.36) breaks that constant, so the
        // header grows exactly what keeps the two days unmistakable:
        // a dated, accented 「明日 11/27（金）」 label, and — only
        // while the auto-tomorrow display is up — the small one-tap
        // way back to today. Once tapped, today is pinned for this
        // display session (homeManualToday) and the toggle goes away.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = city().name
            textSize = 13f; setTextColor(sub)
        })
        if (tomorrowMode) {
            header.addView(TextView(this).apply {
                text = "明日 ${Rules.dateLabel(disp.date)}"
                textSize = 13f; setTextColor(accent)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), 0, 0, 0)
            })
            header.addView(
                View(this),
                LinearLayout.LayoutParams(0, 1, 1f)
            )
            header.addView(TextView(this).apply {
                text = "今日を見る"
                textSize = 13f; setTextColor(sub)
                setPadding(dp(10), dp(6), dp(2), dp(6))
                background = ghostBg(24)
                isClickable = true
                isFocusable = true
                contentDescription = "今日の天気を見る"
                setOnClickListener {
                    homeManualToday = true
                    showScreen(0)
                }
            })
        }
        col.addView(header)

        // Element groups are staged in, in importance order, when
        // fresh data has just arrived (stageIn below).
        val intro = mutableListOf<View>()

        // ---- conclusion: every sentence its own block of display type ----
        val conclusionGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val conclusionText = if (prev != null) {
            Rules.conclusion(disp, prev, feelOffset(), base = prevWord, dayWord = dispWord)
        } else {
            Rules.conclusionNoYesterday(disp, feelOffset(), dispWord)
        }
        val sentences = conclusionSentences(conclusionText)
        // v0.1.17: the size is fitted by measurement so the two
        // sentences land on two lines (fitConclusion); a fitted line
        // can carry one manual break, making three lines at most.
        val fit = fitConclusion(sentences)
        fit.lines.forEachIndexed { i, line ->
            conclusionGroup.addView(TextView(this).apply {
                text = line
                // v0.1.37: the conclusion is ink now — the mock sets
                // it in the same deep navy as everything else on the
                // sky (it was the day's accent). The halo stays: the
                // re-scan measures ink on the halo at 8.64+ (light) /
                // 14.25+ (dark) against the 3:1 large-text gate.
                textSize = fit.sizeSp; setTextColor(ink)
                setTypeface(null, Typeface.BOLD)
                if (i == 0) setPadding(0, dp(10), 0, 0)
                lineHeight = dp((fit.sizeSp * 1.22f).toInt())
                skyHalo(this)
            })
        }
        gapNoteText?.let { note ->
            // Part of the conclusion block (v0.1.5: at body size the
            // accent on the base broke the body gate; as 20sp bold it
            // is large text). It takes the same halo as the conclusion;
            // in the concept-A palette the accent is ink itself in
            // light mode and the pale sky blue in dark, both clear of
            // the 3:1 gate on the halo (docs/FEATURE_v0.1.37.md).
            conclusionGroup.addView(TextView(this).apply {
                text = note; textSize = 20f; setTextColor(accent)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
                lineHeight = dp(26)
                skyHalo(this)
            })
        }
        col.addView(conclusionGroup)
        intro.add(conclusionGroup)

        // ---- 今日の人 (v0.1.14; moved up + grown v0.1.15; generated
        //      assets v0.1.16; centered v0.1.17; sized from the
        //      leftover and floated mid-gap v0.1.18) ----
        // The user asked for a human figure so the way to go out is
        // visible at a glance — cold-looking or warm-looking before
        // any word is read. In v0.1.14 the person stood at the bottom
        // right, just above the chips, and the real-device screenshot
        // showed it isolated from the conclusion and small enough to
        // read as a stick figure. v0.1.15 moved it directly under the
        // conclusion block and taller; v0.1.17 centered it
        // horizontally at the user's word ("人は画面中央に"). The
        // v0.1.17 screenshot still showed the vertical balance off:
        // the conclusion and the person hugged the top of the screen
        // while a quarter of it sat empty below the person, because
        // the single weighted spacer under the person took all the
        // leftover room. v0.1.18 therefore sizes the person from the
        // leftover itself — PersonFigure.heightDp against the day's
        // fixed parts (homeFixedHeightDp) — and splits the spacer in
        // two, one above and one below, so what room remains divides
        // evenly and the figure floats at the center of the gap. The
        // B5 stack (conclusion -> person -> chips -> small lines, no
        // scrolling) and the stage-in order are unchanged. The
        // variation comes from the pure PersonFigure.spec; the
        // clothing answer is its description.
        // The chip answers are computed here, ahead of the chips:
        // their wrapped line count is one of the fixed parts the
        // person's sizing subtracts (chipAnswerLines). The chips
        // below read the same strings.
        val clothingChip = clothing.title.replace(" ＋ ", "＋").replace(" ／ ", "／")
        val umbChip = umb.title
        val sunChip = sun.title.removePrefix("日焼け止めは").removePrefix("日焼け止め")
            .replace(" ＋ ", "＋")
        // How many small lines the displayed day has — counted under
        // the same conditions the lines block below adds them. The
        // evening/tonight slot is always exactly one line; the 明日
        // line exists only in today mode (in night mode tomorrow *is*
        // the screen), and the no-tomorrow-data note only on the
        // night fallback.
        val smallLineCount = (if (backupMode) 1 else 0) + (if (staleCache) 1 else 0) +
            (if (nightNoTomorrow) 1 else 0) +
            (if (pres.visible) 1 else 0) + 1 +
            (if (!tomorrowMode && tomorrow != null) 1 else 0)
        // The person's full variation (v0.1.19): the layered spec
        // also reads the day's snow codes and the 9–18時 feel /
        // humidity averages (the knit cap, the neck towel, the
        // mask). Its top pad (a parasol day needs headroom) is part
        // of the height budget, so the spec comes before the sizing.
        // Night mode dresses the person for the displayed day —
        // tomorrow (v0.1.36).
        val personSpec = PersonFigure.spec(
            clothing.rank, umb.level, disp.uvMax,
            snow = disp.hours.any { it.weatherCode in PersonFigure.SNOW_CODES },
            daytimeFeelAvg = Rules.daytimeFeelAvg(disp),
            daytimeHumidityAvg = Rules.daytimeHumidityAvg(disp)
        )
        val personH = PersonFigure.heightDp(
            homeColumnHeightDp(),
            homeFixedHeightDp(
                fit, gapNoteText != null,
                chipAnswerLines(listOf(clothingChip, umbChip, sunChip)),
                smallLineCount,
                headerExtraDp = if (tomorrowMode) 12f else 0f
            ),
            PersonFigure.padTopRatio(personSpec)
        )
        // ---- the open sky, above the person ----
        col.addView(
            View(this),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        val personRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val person = PersonView(this, personSpec)
        person.contentDescription = buildString {
            append("${dispWord}の服装：").append(clothing.title)
            if (personSpec.base == PersonBase.RAINCOAT) append("、レインコートを着ている")
            when (personSpec.held) {
                PersonHeld.OPEN -> append("、傘を持っている")
                PersonHeld.FOLDED -> append("、折りたたみ傘を持っている")
                PersonHeld.PARASOL -> append("、日傘をさしている")
                PersonHeld.UCHIWA -> append("、うちわを持っている")
                PersonHeld.NONE -> Unit
            }
            when (personSpec.visibleHead) {
                PersonHead.CAP -> append("、帽子をかぶっている")
                PersonHead.KNITCAP -> append("、ニット帽をかぶっている")
                PersonHead.NONE -> Unit
            }
            if (personSpec.sunglasses) append("、サングラスをかけている")
            if (personSpec.mask) append("、マスクをしている")
            if (personSpec.neck == PersonNeck.TOWEL) append("、首にタオルを巻いている")
        }
        // The view is wider than the v0.1.16 half-height and taller
        // by the composition's top pad: the layered props (a parasol
        // canopy, an open umbrella) overflow the person's own rect,
        // and the composer centers the figure inside whatever it is
        // given — the visible size is still personH (v0.1.19).
        val personBlockH = (personH * (1f + PersonFigure.padTopRatio(personSpec))).roundToInt()
        personRow.addView(
            person,
            LinearLayout.LayoutParams(dp((personH * 0.66f).roundToInt()), dp(personBlockH))
        )
        col.addView(
            personRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        intro.add(personRow)

        // ---- the open sky, below the person ----
        col.addView(
            View(this),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // ---- the three answers as frosted-glass cards (v0.1.37) ----
        // Card answers are the short forms: clothing keeps its title
        // with the spaces around the symbols closed up (the mock's
        // "ダウン＋手袋・マフラー"), sunscreen drops its own name
        // (the label already says 日焼け止め, so the card answers
        // 塗ると安心 / 不要 / 必須). Full sentences are on Detail.
        // The cards are separated by 8dp gaps so each glass face
        // reads as its own pill on the sky.
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipLp = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        chips.addView(
            answerChip(clothingIcon(clothing.rank), "服装", clothingChip), chipLp()
        )
        chips.addView(
            answerChip(umbrellaIcon(umb.level), "傘", umbChip),
            chipLp().apply { leftMargin = dp(8) }
        )
        chips.addView(
            answerChip(sunscreenIcon(disp.uvMax), "日焼け止め", sunChip),
            chipLp().apply { leftMargin = dp(8) }
        )
        col.addView(chips)
        intro.add(chips)

        // ---- small lines: notes, pressure, evening, tomorrow ----
        val lines = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // v0.1.15: top padding 16 -> 10, line gaps 6 -> 5 — part
            // of the room that lets 今日の人 grow (docs/FIX_v0.1.15.md).
            setPadding(0, dp(10), 0, 0)
        }
        var lineCount = 0
        fun addLine(v: View) {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (lineCount > 0) lp.topMargin = dp(5)
            v.layoutParams = lp
            lines.addView(v)
            lineCount++
        }
        if (backupMode) {
            // The source notes live in the small-lines area now
            // (v0.1.13): same quiet voice as before, no longer
            // competing with the conclusion at the top.
            addLine(TextView(this).apply {
                text = "予備のデータで表示しています"
                textSize = 13f; setTextColor(sub)
            })
        }
        if (staleCache) {
            // The cache-first display's background refresh failed
            // (FETCH_REVIEW #3): say so quietly. Cleared the moment
            // fresh data replaces the cache.
            addLine(TextView(this).apply {
                text = "前回のデータを表示しています"
                textSize = 13f; setTextColor(sub)
            })
        }
        if (nightNoTomorrow) {
            // Night, but tomorrow's data never arrived: Home stayed
            // on today (Rules.resolveHomeDay) and says so — the user
            // asked for tomorrow at night, so the absence is stated,
            // not silently passed over (v0.1.36).
            addLine(TextView(this).apply {
                text = "明日のデータが届いていないため、今日を表示しています"
                textSize = 13f; setTextColor(sub)
            })
        }
        if (pres.visible) {
            // The α-frame advice joins the small lines at the same
            // rank (v0.1.13): title only — the timing and the action
            // are on Detail's 今日の答え card. (pres itself was
            // computed at the top of showHome, where the person's
            // sizing needs it.)
            addLine(smallLine(pressureIcon(pres.level), "気圧", pres.title, ink))
        }
        if (tomorrowMode) {
            // Tonight survives as one line (v0.1.36): the moon mark,
            // today's evening material in tonight's words. The 帰り /
            // 明日 lines belong to the today display and are not
            // repeated here.
            addLine(smallLine(IconKind.MOON, "今夜", Rules.tonightLine(today), ink))
        } else {
            addLine(smallLine(IconKind.MOON, "帰り", Rules.eveningLine(disp), ink))
            if (tomorrow != null) {
                addLine(smallLine(IconKind.SUN, "明日", Rules.tomorrowLine(today, tomorrow), sub))
            }
        }
        col.addView(lines)
        intro.add(lines)

        content.removeAllViews()
        content.addView(
            col,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (introAnimate) {
            introAnimate = false
            stageIn(intro)
        }

        // Widget snapshot (v0.1.14): every Home display refreshes what
        // the launcher widget shows. The widget never fetches — it
        // only reads this. Short forms match the chips, so the
        // launcher and the app never disagree. The accent is the dark
        // variant: the widget face is fixed dark. Night mode does not
        // turn the widget (v0.1.36): the snapshot is always *today's*
        // answers, computed from today here even while Home's main
        // display shows tomorrow.
        val snapClothing = Rules.clothing(today, feelOffset())
        val snapUmb = Rules.umbrella(today)
        val snapSun = Rules.sunscreen(today.uvMax)
        val snapConclusion = if (yesterday != null) {
            Rules.conclusion(today, yesterday, feelOffset())
        } else {
            Rules.conclusionNoYesterday(today, feelOffset())
        }
        WidgetSnapshot.save(
            this,
            WidgetSnapshot.Data(
                city = city().name,
                headline = conclusionSentences(snapConclusion).firstOrNull() ?: snapConclusion,
                clothing = snapClothing.title.replace(" ＋ ", "＋").replace(" ／ ", "／"),
                umbrella = snapUmb.title,
                sunscreen = snapSun.title.removePrefix("日焼け止めは")
                    .removePrefix("日焼け止め").replace(" ＋ ", "＋"),
                rank = snapClothing.rank,
                umbrellaLevel = snapUmb.level.name,
                uvMax = today.uvMax,
                snow = today.hours.any { it.weatherCode in PersonFigure.SNOW_CODES },
                daytimeFeelAvg = Rules.daytimeFeelAvg(today),
                daytimeHumidityAvg = Rules.daytimeHumidityAvg(today),
                accentDark = toneFor(Rules.daytimeAvg(today), dark = true).accent,
                // v0.1.38: the widget's face follows the sky — the
                // current hour's code (same pick as skyWeather) and
                // the day's sunrise/sunset.
                weatherCode = run {
                    val nowH = (nowMinutesFloat() / 60).toInt()
                    (today.hour(nowH) ?: today.hours.minByOrNull {
                        kotlin.math.abs(it.hour - nowH)
                    })?.weatherCode ?: -1
                },
                sunriseMin = minutesOf(today.sunrise, 360),
                sunsetMin = minutesOf(today.sunset, 1080),
                updatedAt = System.currentTimeMillis()
            )
        )
        TenkiWidgetProvider.requestUpdate(this)
    }

    /**
     * 段階表示 (minimal-information 原則6; ART_DIRECTION §6 が予告した
     * 「主役 → 主要アクション → 補助」の順): Home's elements fade and
     * rise in, in the order they matter — conclusion first, then
     * 今日の人 (v0.1.14), the answer chips, then the evening / tomorrow
     * lines. Fast and short
     * (<= ~0.9s total) so it guides the eye instead of making the user
     * wait. If the system animator is disabled (reduced motion), views
     * are simply shown at once; the final state alone carries all the
     * information either way. A delayed safeguard forces the final
     * state even if an animation is interrupted.
     */
    private fun stageIn(views: List<View>) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(8).toFloat()
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(50L + i * 70L)
                .setDuration(220L)
                .start()
        }
        views.lastOrNull()?.postDelayed({
            views.forEach { it.alpha = 1f; it.translationY = 0f }
        }, 50L + views.size * 70L + 320L)
    }

    private fun fmt(v: Double): String = String.format("%.1f", v)

    private fun detailRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(sub)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value; textSize = 16f; setTextColor(ink)
        })
        parent.addView(row)
    }

    /**
     * Stacked variant of [detailRow] for values too long to sit beside
     * their label. On the real device the 気圧の6時間最大低下 row's
     * label wrapped mid-word and collided with its value (v0.1.7
     * screenshot); stacking keeps both intact.
     */
    private fun detailRowStacked(parent: LinearLayout, label: String, value: String) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        block.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(sub)
        })
        block.addView(TextView(this).apply {
            text = value; textSize = 16f; setTextColor(ink)
            setPadding(0, dp(2), 0, 0)
            lineHeight = dp(26)
        })
        parent.addView(block)
    }

    /** One row of the folded hourly table: fixed-weight columns, units live in the header row. */
    private fun hourlyRow(cells: List<String>, header: Boolean): LinearLayout {
        val weights = floatArrayOf(0.62f, 1.0f, 1.0f, 0.88f, 1.0f, 1.28f, 0.95f)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        cells.forEachIndexed { i, s ->
            row.addView(TextView(this).apply {
                text = s
                textSize = 13f
                setTextColor(if (header) sub else ink)
                if (header) setTypeface(null, Typeface.BOLD)
                gravity = if (i == 0) Gravity.START else Gravity.END
                maxLines = 1
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weights[i]))
        }
        return row
    }

    private fun glassCard(topMarginDp: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = glassBg(24)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(topMarginDp)
        card.layoutParams = lp
        return card
    }

    private fun statBlock(label: String, value: String, icon: IconKind): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iv = IconView(this, icon, sub)
        iv.contentDescription = label
        // 16dp (v0.1.10, was 14): the stat icons join the small-icon
        // family (18/20dp) instead of sitting one size below it, where
        // the scaled stroke thinned to ~1.2dp and read fainter than
        // every other icon on the screen.
        labelRow.addView(iv, LinearLayout.LayoutParams(dp(16), dp(16)))
        labelRow.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(sub)
            setPadding(dp(4), 0, 0, 0)
        })
        b.addView(labelRow)
        b.addView(TextView(this).apply {
            text = value; textSize = 16f; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        return b
    }

    /**
     * Detail (数値で見る), v0.1.8: the screen stops listing numbers.
     * Always visible = figures and shapes only — the hero 体感 number
     * with 最高/最低/UV under it, a chart that draws the day's 体感温度
     * as a line and the rain as bars, and a pressure card reduced to
     * the current value, a sparkline and one line. Everything else —
     * the full summary rows and the hourly table — is B-rank per the
     * design doc (数値は畳む) and folds under "くわしい数値", where
     * the hourly values finally sit in aligned columns instead of
     * wrapped text lines. Detail stays monochrome (ART_DIRECTION §7):
     * the charts draw in ink/sub; the only hue is the fixed sky blue
     * of the chart's area fill (v0.1.37; it was the day's tint).
     */
    private fun showDetail(f: Forecast) {
        val today = f.day(todayStr()) ?: run { showError("今日のデータがありません", network = false); return }
        val col = column()
        // No screen title (v0.1.7): the always-visible tab bar already
        // names this screen. The city stays: it can change.
        col.addView(TextView(this).apply {
            text = city().name
            textSize = 13f; setTextColor(sub)
        })
        val nowHour = (nowMinutesFloat() / 60).toInt()
        val cur = today.hour(nowHour)
        val pres = Rules.pressure(today, sensitivity())
        val sortedHours = today.hours.sortedBy { it.hour }

        // ---- 要点カード: hero figures + the day's shape ----
        val heroCard = glassCard(16)
        val heroLabel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heroIv = IconView(this, IconKind.THERMOMETER, sub)
        heroIv.contentDescription = "体感温度"
        heroLabel.addView(heroIv, LinearLayout.LayoutParams(dp(18), dp(18)))
        heroLabel.addView(TextView(this).apply {
            // The hero is the daytime average — unless the day has no
            // daytime hours at all (backup source fetched in the
            // evening), in which case Rules evaluated the remaining
            // hours and the label says so instead of claiming 日中
            // (BUG_REVIEW #1).
            text = if (Rules.hasDaytimeHours(today)) {
                "体感温度（日中平均）"
            } else {
                "体感温度（いま以降の平均）"
            }
            textSize = 13f; setTextColor(sub)
            setPadding(dp(6), 0, 0, 0)
        })
        heroCard.addView(heroLabel)
        heroCard.addView(TextView(this).apply {
            text = "${fmt(Rules.daytimeAvg(today))}℃"
            textSize = 34f; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        listOf(
            Triple("最高", "${fmt(today.tempMax)}℃", IconKind.ARROW_UP),
            Triple("最低", "${fmt(today.tempMin)}℃", IconKind.ARROW_DOWN),
            Triple("UV最大", fmt(today.uvMax), IconKind.SUN)
        ).forEach { (label, value, icon) ->
            stats.addView(
                statBlock(label, value, icon),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        heroCard.addView(stats)
        if (Rules.isPartialDay(today)) {
            // Partial day (the backup source's "today" starts at the
            // fetch hour): 最高/最低 are the extremes of the hours that
            // exist, not of the whole day — say so where they stand
            // instead of presenting them as the day's values
            // (BUG_REVIEW #1).
            heroCard.addView(TextView(this).apply {
                text = "最高・最低は、取得できた時間の範囲での値です"
                textSize = 13f; setTextColor(sub)
                setPadding(0, dp(8), 0, 0)
                lineHeight = dp(21)
            })
        }
        // The backup source has no precipitation probability; there the
        // bars show the amount instead, and the legend says so.
        val hasProb = sortedHours.any { it.precipProb != null }
        heroCard.addView(TextView(this).apply {
            text = "線＝体感温度　棒＝${if (hasProb) "雨の確率" else "雨の量"}"
            textSize = 13f; setTextColor(sub)
            setPadding(0, dp(16), 0, dp(4))
        })
        val chart = TempChartView(
            this, sortedHours, hasProb, nowHour,
            lineColor = ink,
            fillColor = withAlpha(skyTint, if (isDark) 95 else 115),
            barColor = withAlpha(sub, 115),
            labelColor = sub,
            paperColor = paper
        )
        chart.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(132)
        )
        if (sortedHours.isNotEmpty()) {
            val maxE = sortedHours.maxByOrNull { it.apparent }!!
            val minE = sortedHours.minByOrNull { it.apparent }!!
            chart.contentDescription =
                "体感温度のグラフ。最高 ${fmt(maxE.apparent)}℃（${maxE.hour}時）、" +
                "最低 ${fmt(minE.apparent)}℃（${minE.hour}時）"
        }
        heroCard.addView(chart)
        col.addView(heroCard)

        // ---- 気圧カード: current value + sparkline + one line ----
        val presCard = glassCard(12)
        val presHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Same level-driven picture as Home's pressure card (v0.1.10;
        // it was fixed to PRESSURE_FALL, so a gently-falling or
        // near-level day still wore the mid-strength arrow here).
        val presIv = IconView(this, pressureIcon(pres.level), sub)
        presIv.contentDescription = "気圧"
        presHead.addView(presIv, LinearLayout.LayoutParams(dp(18), dp(18)))
        presHead.addView(TextView(this).apply {
            text = "気圧"; textSize = 13f
            setTypeface(null, Typeface.BOLD); setTextColor(sub)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (cur != null) {
            presHead.addView(TextView(this).apply {
                text = "${fmt(cur.pressure)} hPa"
                textSize = 20f; setTextColor(ink)
                setTypeface(null, Typeface.BOLD)
            })
        }
        presCard.addView(presHead)
        val spark = PressureSparkView(this, sortedHours.map { it.pressure }, lineColor = ink)
        spark.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { topMargin = dp(8) }
        // When no real 6-hour pair exists in the data (partial day),
        // the drop is *unmeasured*, not 0.0 — the card and the
        // accessibility text say 未測定 instead of printing a measured-
        // looking number (BUG_REVIEW #2).
        spark.contentDescription =
            "気圧のグラフ。今日の最低 ${fmt(pres.minPressure)} hPa、" +
            if (pres.dropMeasured) {
                "6時間で最大 ${fmt(pres.maxDrop6h)} hPa 下降"
            } else {
                "6時間の変化はデータが足りず未測定"
            }
        presCard.addView(spark)
        presCard.addView(TextView(this).apply {
            text = buildString {
                append("最低 ${fmt(pres.minPressure)} hPa ・ ")
                if (pres.dropMeasured) {
                    append("6時間で最大 ${fmt(pres.maxDrop6h)} hPa 下降")
                    if (pres.timingNote.isNotEmpty()) append("（${pres.timingNote}）")
                } else {
                    append("6時間の変化は未測定")
                }
            }
            textSize = 13f; setTextColor(sub)
            setPadding(0, dp(8), 0, 0)
            lineHeight = dp(21)
        })
        col.addView(presCard)

        // ---- 折りたたみ: the full numeric record ----
        val foldHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            // Radius 24 (v0.1.10, was 16): 16dp is outside the radius
            // tokens (24 / full-round only, ART_DIRECTION §4) — a
            // one-off shape the eye reads as "almost a card".
            background = ghostBg(24)
            isClickable = true
            isFocusable = true
            contentDescription = "くわしい数値"
            setOnClickListener {
                // The toggle rebuilds the screen; save where the user
                // is so the rebuild can put them back (BUG_REVIEW #8) —
                // opening the numbers used to mean scrolling all the
                // way down again to read them.
                detailScrollView?.let { detailScrollY = it.scrollY }
                detailExpanded = !detailExpanded
                showScreen(1)
            }
        }
        val foldLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Section gap 24 (v0.1.10, was 20): 20dp is the horizontal
        // inset value, not a step of the vertical spacing scale
        // (4/8/12/16/24/32, ART_DIRECTION §5).
        foldLp.topMargin = dp(24)
        foldHeader.layoutParams = foldLp
        foldHeader.addView(TextView(this).apply {
            text = "くわしい数値"; textSize = 16f
            setTypeface(null, Typeface.BOLD); setTextColor(ink)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        foldHeader.addView(TextView(this).apply {
            text = if (detailExpanded) "△" else "▽"
            textSize = 13f; setTextColor(sub)
        })
        col.addView(foldHeader)

        if (detailExpanded) {
            val foldCard = glassCard(8)
            foldCard.setPadding(dp(20), dp(8), dp(20), dp(8))
            detailRow(foldCard, "気温", "最高 ${fmt(today.tempMax)}℃ ／ 最低 ${fmt(today.tempMin)}℃")
            detailRow(
                foldCard,
                if (Rules.hasDaytimeHours(today)) "体感温度（日中平均）" else "体感温度（いま以降の平均）",
                "${fmt(Rules.daytimeAvg(today))}℃"
            )
            detailRow(foldCard, "UV最大", fmt(today.uvMax))
            if (cur != null) detailRow(foldCard, "現在の気圧", "${fmt(cur.pressure)} hPa")
            detailRow(foldCard, "今日の最低気圧", "${fmt(pres.minPressure)} hPa")
            val dropText = if (pres.dropMeasured) {
                buildString {
                    append("${fmt(pres.maxDrop6h)} hPa")
                    if (pres.maxRise6h >= 3.0) append("（上昇は最大 ${fmt(pres.maxRise6h)} hPa）")
                }
            } else {
                "未測定（6時間分のデータがありません）"
            }
            // Stacked, not side-by-side: label + long value collided on
            // the real device (v0.1.7 screenshot).
            detailRowStacked(foldCard, "気圧の6時間最大低下", dropText)
            detailRow(
                foldCard, "日の出 ／ 日の入り",
                if (today.sunrise.isEmpty() || today.sunset.isEmpty()) {
                    "予備データには含まれません"
                } else {
                    "${today.sunrise.substringAfter("T")} ／ ${today.sunset.substringAfter("T")}"
                }
            )
            // The pollen row stays — its absence would read as "pollen
            // is fine" — inside the fold with the other disclosures.
            foldCard.addView(TextView(this).apply {
                text = "花粉： 準備中"
                textSize = 13f; setTextColor(sub)
                setPadding(0, dp(8), 0, dp(8))
                lineHeight = dp(21)
            })
            foldCard.addView(TextView(this).apply {
                text = "時間別"
                textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(sub)
                setPadding(0, dp(16), 0, dp(4))
            })
            // Aligned columns replace the old free-text lines, which
            // wrapped mid-value on the real device. Units live in the
            // header row only, so the cells stay short enough to fit.
            foldCard.addView(hourlyRow(listOf("時", "気温℃", "体感℃", "確率%", "雨量mm", "気圧hPa", "風m/s"), header = true))
            for (h in sortedHours) {
                foldCard.addView(
                    hourlyRow(
                        listOf(
                            "${h.hour}時", fmt(h.temp), fmt(h.apparent),
                            h.precipProb?.toString() ?: "–", fmt(h.precip),
                            fmt(h.pressure), fmt(h.wind)
                        ),
                        header = false
                    )
                )
            }
            col.addView(foldCard)
        }

        // ---- 明日の要点 (v0.1.36): Home turns into tomorrow's screen
        // at night, so Detail keeps the matching minimum for tomorrow
        // — the three answers and the day's headline figures, dated.
        // No second chart and no hourly table: the full numeric
        // record stays today's, tomorrow gets its 要点 only.
        val tomorrow = f.day(shiftedStr(1))
        if (tomorrow != null && tomorrow.hours.isNotEmpty()) {
            val tCard = glassCard(12)
            tCard.addView(TextView(this).apply {
                text = "明日 ${Rules.dateLabel(tomorrow.date)}"
                textSize = 16f; setTextColor(ink)
                setTypeface(null, Typeface.BOLD)
            })
            val tClothing = Rules.clothing(tomorrow, feelOffset())
            val tUmb = Rules.umbrella(tomorrow)
            val tSun = Rules.sunscreen(tomorrow.uvMax)
            detailRow(tCard, "服装", tClothing.title)
            detailRow(tCard, "傘", "${tUmb.title}（${tUmb.note}）")
            detailRow(tCard, "日焼け止め", tSun.title)
            detailRow(
                tCard, "気温",
                "最高 ${fmt(tomorrow.tempMax)}℃ ／ 最低 ${fmt(tomorrow.tempMin)}℃"
            )
            detailRow(
                tCard,
                if (Rules.hasDaytimeHours(tomorrow)) "体感温度（日中平均）" else "体感温度（平均）",
                "${fmt(Rules.daytimeAvg(tomorrow))}℃"
            )
            col.addView(tCard)
        }

        // Attribution stays visible under the data it describes (CC BY
        // 4.0), as the single home for it since v0.1.7.
        col.addView(TextView(this).apply {
            text = if (backupMode) {
                "データ： MET Norway（CC BY 4.0）"
            } else {
                "データ： Open-Meteo（CC BY 4.0）"
            }
            textSize = 13f; setTextColor(sub)
            setPadding(0, dp(16), 0, 0)
        })
        content.removeAllViews()
        val sv = scrollOf(col)
        detailScrollView = sv
        content.addView(sv)
        // Restore the position saved by the fold toggle / a theme-flip
        // rebuild (BUG_REVIEW #8). Posted so the new content is laid
        // out first; scrollTo clamps to the real range.
        if (detailScrollY > 0) {
            val y = detailScrollY
            sv.post { sv.scrollTo(0, y) }
        }
    }

    /**
     * The day's shape (v0.1.8): 体感温度 as a line over a tint-filled
     * area, rain as bars along the bottom, max/min labelled at their
     * points, a ring marking the current hour. Drawn in ink/sub only —
     * Detail stays monochrome (ART_DIRECTION §7); the hue comes from
     * the day's tint in the area fill, the same tint the glass carries.
     * Text inside the canvas uses the existing 13sp aux step.
     */
    private inner class TempChartView(
        ctx: Context,
        private val hours: List<HourEntry>,
        private val probMode: Boolean,
        private val nowHour: Int,
        private val lineColor: Int,
        private val fillColor: Int,
        private val barColor: Int,
        private val labelColor: Int,
        private val paperColor: Int
    ) : View(ctx) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (hours.size < 2) return
            val d = resources.displayMetrics.density
            val sp = resources.displayMetrics.scaledDensity
            val padX = 6f * d
            val padTop = 18f * d
            val padBottom = 20f * d
            val plotW = width - padX * 2
            val plotH = height - padTop - padBottom
            if (plotW <= 0f || plotH <= 0f) return
            val apps = hours.map { it.apparent }
            var minA = apps.minOrNull() ?: 0.0
            var maxA = apps.maxOrNull() ?: 0.0
            if (maxA - minA < 2.0) { maxA += 1.0; minA -= 1.0 } else { maxA += 0.7; minA -= 0.7 }
            val firstH = hours.first().hour
            val lastH = hours.last().hour
            val span = (lastH - firstH).coerceAtLeast(1)
            fun x(h: Int): Float = padX + (h - firstH) / span.toFloat() * plotW
            fun y(v: Double): Float =
                padTop + ((maxA - v) / (maxA - minA)).toFloat() * plotH
            val baseY = padTop + plotH

            // Rain bars (bottom 45% of the plot).
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = barColor; style = Paint.Style.FILL
            }
            val maxAmount = (hours.maxOfOrNull { it.precip } ?: 0.0).coerceAtLeast(0.1)
            val barW = plotW / (span + 1) * 0.55f
            for (e in hours) {
                val frac = if (probMode) {
                    (e.precipProb ?: 0) / 100.0
                } else {
                    e.precip / maxAmount
                }
                if (frac <= 0.0) continue
                val bh = (frac * plotH * 0.45).toFloat()
                val cx = x(e.hour)
                canvas.drawRect(cx - barW / 2, baseY - bh, cx + barW / 2, baseY, barPaint)
            }

            // Area fill + line.
            val linePath = Path()
            hours.forEachIndexed { i, e ->
                if (i == 0) linePath.moveTo(x(e.hour), y(e.apparent))
                else linePath.lineTo(x(e.hour), y(e.apparent))
            }
            val fillPath = Path(linePath).apply {
                lineTo(x(lastH), baseY)
                lineTo(x(firstH), baseY)
                close()
            }
            canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor; style = Paint.Style.FILL
            })
            canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor; style = Paint.Style.STROKE
                strokeWidth = 2f * d
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            })

            // Max / min points with value labels.
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor; textSize = 13f * sp
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
            fun pointLabel(e: HourEntry, above: Boolean) {
                canvas.drawCircle(x(e.hour), y(e.apparent), 3f * d, dotPaint)
                val label = "${fmt(e.apparent)}℃"
                textPaint.textAlign = when {
                    e.hour - firstH <= 2 -> Paint.Align.LEFT
                    lastH - e.hour <= 2 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                val ly = if (above) {
                    y(e.apparent) - 7f * d
                } else {
                    y(e.apparent) + 13f * sp + 5f * d
                }
                canvas.drawText(label, x(e.hour), ly, textPaint)
            }
            val maxE = hours.maxByOrNull { it.apparent }!!
            val minE = hours.minByOrNull { it.apparent }!!
            pointLabel(maxE, above = true)
            if (minE.hour != maxE.hour) {
                // A low minimum sits on the plot's floor, where a label
                // below the point would collide with the hour labels;
                // there the label goes above the point instead.
                val minAbove = y(minE.apparent) > padTop + plotH * 0.7f
                pointLabel(minE, above = minAbove)
            }

            // Current-hour ring on the line.
            hours.firstOrNull { it.hour == nowHour }?.let { e ->
                canvas.drawCircle(x(e.hour), y(e.apparent), 4.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = paperColor
                })
                canvas.drawCircle(x(e.hour), y(e.apparent), 2.4f * d, dotPaint)
            }

            // Hour labels.
            textPaint.textAlign = Paint.Align.CENTER
            for (h in listOf(0, 6, 12, 18)) {
                if (h in firstH..lastH) {
                    val lx = when (h) {
                        firstH -> x(h) + 8f * d
                        else -> x(h)
                    }
                    canvas.drawText("${h}時", lx, height - 5f * d, textPaint)
                }
            }
        }
    }

    /** Pressure sparkline (v0.1.8): the day's pressure as one quiet line, dot at the lowest point. */
    private inner class PressureSparkView(
        ctx: Context,
        private val values: List<Double>,
        private val lineColor: Int
    ) : View(ctx) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (values.size < 2) return
            val d = resources.displayMetrics.density
            val padX = 4f * d
            val padY = 6f * d
            val plotW = width - padX * 2
            val plotH = height - padY * 2
            if (plotW <= 0f || plotH <= 0f) return
            var minV = values.minOrNull() ?: 0.0
            var maxV = values.maxOrNull() ?: 0.0
            if (maxV - minV < 1.0) { maxV += 0.5; minV -= 0.5 } else { maxV += 0.3; minV -= 0.3 }
            fun x(i: Int): Float = padX + i / (values.size - 1).toFloat() * plotW
            fun y(v: Double): Float =
                padY + ((maxV - v) / (maxV - minV)).toFloat() * plotH
            val path = Path()
            values.forEachIndexed { i, v ->
                if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor; style = Paint.Style.STROKE
                strokeWidth = 1.6f * d
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            })
            val minIdx = values.indices.minByOrNull { values[it] } ?: 0
            canvas.drawCircle(
                x(minIdx), y(values[minIdx]), 3f * d,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
            )
        }
    }

    /**
     * Settings (v0.1.8): radio buttons are gone. The selected radio
     * rendered as an unexplained black disc on the real device, and a
     * list of circles + labels is a *reading* UI, not an operating one.
     * 体感/感度 are now segmented controls — the tab bar's own pattern
     * (solid pill = selected) — and 地点 is a chip grid, which turns
     * eight 48dp rows into two 48dp rows. Descriptions shrink to one
     * short line that names only what changes at the extremes; the
     * middle option is inferable. The screen still stays put when a
     * value changes (v0.1.5): its job is 申告, and the control itself
     * shows the new state.
     */
    private fun showSettings() {
        val col = column()
        // No screen title (v0.1.7): the selected tab below already says
        // 設定. The 地点 section also has no description — "天気を見る
        // 場所です" only restated the label (v0.1.7).
        col.addView(sectionLabel("地点", IconKind.PIN))
        col.addView(sectionCard(cityChips()))

        col.addView(sectionLabel("体感の感じ方", IconKind.SHIRT_LONG))
        col.addView(sectionDesc("寒がり＝1枚厚く／暑がり＝1枚薄く"))
        val feelValues = listOf(1, 0, -1)
        col.addView(sectionCard(
            segmentedControl(
                listOf("寒がり", "ふつう", "暑がり"),
                feelValues.indexOf(feelOffset()).coerceAtLeast(0)
            ) { i ->
                prefs.edit().putInt("feel", feelValues[i]).apply()
                showScreen(2)
            }
        ))

        col.addView(sectionLabel("気圧の感度", IconKind.PRESSURE_FALL))
        col.addView(sectionDesc("敏感＝早めに／弱い＝大きな下がりだけ"))
        val sensValues = listOf(0, 1, 2)
        col.addView(sectionCard(
            segmentedControl(
                listOf("弱い", "ふつう", "敏感"),
                sensValues.indexOf(sensitivity()).coerceAtLeast(0)
            ) { i ->
                prefs.edit().putInt("sens", sensValues[i]).apply()
                showScreen(2)
            }
        ))

        // 空の動き (v0.1.39): the user's own off switch for the
        // living sky, next to the system's reduced-motion setting
        // SkyView already honors. Off freezes the sky into the
        // still picture of the same state — weather and time still
        // read from the colors and the elements' places.
        col.addView(sectionLabel("空の動き", IconKind.SUN))
        col.addView(sectionDesc("オフ＝空は静止画になります"))
        val motionValues = listOf(true, false)
        col.addView(sectionCard(
            segmentedControl(
                listOf("オン", "オフ"),
                motionValues.indexOf(skyMotion()).coerceAtLeast(0)
            ) { i ->
                prefs.edit().putBoolean("skymotion", motionValues[i]).apply()
                applySkyMotion()
                showScreen(2)
            }
        ))

        // 見た目テスト (v0.1.19): the layered person's parts can be
        // tried out by hand — the user's ask when the parts system
        // landed (「テスト用に見た目を切り替えれる画面も作って
        // おいて」). What is chosen there never reaches Home.
        col.addView(sectionLabel("見た目テスト", IconKind.TEE))
        col.addView(sectionDesc("今日の人のパーツを切り替えて試せます（ホームには影響しません）"))
        val openTest = primaryButton("見た目テストを開く") { showPersonTest() }
        openTest.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        col.addView(sectionCard(openTest))

        // Footer carries the version only (v0.1.7): the two attribution
        // lines duplicated the Detail screen, which keeps the active
        // source's attribution directly under the data it describes —
        // one home for attribution instead of two.
        col.addView(TextView(this).apply {
            text = "天気のこたえ v0.1.39（プロトタイプ）"
            textSize = 13f; setTextColor(sub)
            setPadding(0, dp(24), 0, 0)
            lineHeight = dp(21)
        })
        content.removeAllViews()
        content.addView(scrollOf(col))
    }

    /**
     * 見た目テスト (v0.1.19): a sandbox for the layered person. The
     * top half is the day's sky (its time of day switchable) with the
     * person composited by the very same [PersonComposer] at Home's
     * size language; below, one segmented control per slot — 服装 /
     * 頭 / 顔 / 首 / 持ち物 — each with 自動 first, meaning "whatever
     * today's data says". Every tap rebuilds the screen (the app's
     * settings pattern), so the preview is always the current
     * selection. Nothing here is persisted and Home never reads it.
     */
    private fun showPersonTest() {
        val today = forecast?.day(todayStr())
        val autoRank = today?.let { Rules.clothing(it, feelOffset()).rank } ?: 3
        val auto = PersonFigure.spec(
            autoRank,
            today?.let { Rules.umbrella(it).level } ?: Rules.UmbrellaLevel.NONE,
            today?.uvMax ?: 0.0,
            snow = today?.hours?.any { it.weatherCode in PersonFigure.SNOW_CODES } ?: false,
            daytimeFeelAvg = today?.let { Rules.daytimeFeelAvg(it) },
            daytimeHumidityAvg = today?.let { Rules.daytimeHumidityAvg(it) }
        )
        val spec = testSpec(auto, autoRank)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- sky + person preview ----
        val preview = FrameLayout(this)
        val sky = SkyView(this)
        sky.skyState = testSkyState(today)
        preview.addView(
            sky,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val personH = 240
        val pv = PersonView(this, spec)
        pv.contentDescription = "見た目テストのプレビュー"
        val pLp = FrameLayout.LayoutParams(
            dp((personH * 0.66f).roundToInt()),
            dp((personH * (1f + PersonFigure.padTopRatio(spec))).roundToInt())
        )
        pLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        pLp.bottomMargin = dp(8)
        preview.addView(pv, pLp)
        root.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330))
        )

        // ---- slot controls ----
        val col = column()
        fun label(text: String) {
            col.addView(TextView(this).apply {
                this.text = text
                textSize = 16f; setTextColor(ink)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(24), 0, 0)
            })
        }
        fun control(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
            col.addView(sectionCard(segmentedControl(labels, selected, onSelect)))
        }

        label("空の時刻")
        control(listOf("現在", "昼", "夕方", "夜"), testSky) {
            testSky = it; showPersonTest()
        }

        label("服装")
        col.addView(sectionDesc("自動・ランク0（暑い）〜6（寒い）・レインコート"))
        control(
            listOf("自動", "0", "1", "2", "3", "4", "5", "6", "雨"),
            testOutfit + 1
        ) { testOutfit = it - 1; showPersonTest() }

        label("頭")
        if (spec.base == PersonBase.RAINCOAT) {
            col.addView(sectionDesc("レインコートのフードをかぶります"))
        } else {
            control(listOf("自動", "なし", "帽子", "ニット帽"), testHead + 1) {
                testHead = it - 1; showPersonTest()
            }
        }

        label("顔")
        control(listOf("自動", "なし", "マスクあり"), testMask + 1) {
            testMask = it - 1; showPersonTest()
        }
        control(listOf("自動", "なし", "サングラスあり"), testSung + 1) {
            testSung = it - 1; showPersonTest()
        }

        label("首")
        control(listOf("自動", "なし", "マフラー", "タオル"), testNeck + 1) {
            testNeck = it - 1; showPersonTest()
        }

        label("持ち物")
        control(
            listOf("自動", "なし", "傘", "折りたたみ", "日傘", "うちわ"),
            testHeld + 1
        ) { testHeld = it - 1; showPersonTest() }

        val back = primaryButton("設定にもどる") { showScreen(2) }
        val backLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        backLp.topMargin = dp(28)
        back.layoutParams = backLp
        col.addView(back)

        root.addView(
            scrollOf(col),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        content.removeAllViews()
        content.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** The day's auto spec with the test screen's pinned slots laid over it. */
    private fun testSpec(auto: PersonSpec, autoRank: Int): PersonSpec {
        var s = auto
        if (testOutfit >= 0) {
            val raincoat = testOutfit == 7
            val r = if (raincoat) autoRank else testOutfit
            val (base, top) = PersonFigure.outfitFor(r, raincoat)
            // The outfit-linked elements are re-derived from the
            // picked outfit (v0.1.20), not inherited from the day's
            // auto spec — a hand-picked summer outfit no longer
            // keeps the cold day's muffler, and the raincoat keeps
            // nothing. The neck lands here from the outfit; an
            // explicit 首 pin below still wins over it, and putting
            // 首 back to 自動 returns it to the outfit.
            val linked = PersonFigure.outfitLinked(r, raincoat)
            s = s.copy(
                garment = PersonFigure.garmentOf(r),
                base = base, top = top,
                neck = linked.neck,
                mittens = linked.mittens,
                shiver = linked.shiver,
                sweat = linked.sweat
            )
            // The fan is the held slot's outfit-linked member: on
            // 自動 it follows the picked outfit too (the umbrellas
            // and the parasol are the day's, and stay the day's).
            if (testHeld < 0 &&
                (s.held == PersonHeld.UCHIWA || s.held == PersonHeld.NONE)
            ) {
                s = s.copy(
                    held = if (!raincoat && r == 0) {
                        PersonHeld.UCHIWA
                    } else {
                        PersonHeld.NONE
                    }
                )
            }
        }
        if (testHead >= 0) {
            val h = when (testHead) {
                1 -> PersonHead.CAP
                2 -> PersonHead.KNITCAP
                else -> PersonHead.NONE
            }
            s = s.copy(head = h, hat = h == PersonHead.CAP)
        }
        if (testMask >= 0) s = s.copy(mask = testMask == 1)
        if (testSung >= 0) s = s.copy(sunglasses = testSung == 1)
        if (testNeck >= 0) {
            s = s.copy(
                neck = when (testNeck) {
                    1 -> PersonNeck.MUFFLER
                    2 -> PersonNeck.TOWEL
                    else -> PersonNeck.NONE
                }
            )
        }
        if (testHeld >= 0) {
            val held = when (testHeld) {
                1 -> PersonHeld.OPEN
                2 -> PersonHeld.FOLDED
                3 -> PersonHeld.PARASOL
                4 -> PersonHeld.UCHIWA
                else -> PersonHeld.NONE
            }
            s = s.copy(
                held = held,
                umbrella = when (held) {
                    PersonHeld.OPEN -> PersonUmbrella.OPEN
                    PersonHeld.FOLDED -> PersonUmbrella.FOLDED
                    else -> PersonUmbrella.NONE
                }
            )
        }
        return s
    }

    /** The test preview's sky: the day's weather at the chosen time of day. */
    private fun testSkyState(today: DayForecast?): SkyView.State {
        val sunriseMin = today?.let { minutesOf(it.sunrise, 360) } ?: 360
        val sunsetMin = today?.let { minutesOf(it.sunset, 1080) } ?: 1080
        val minutes = when (testSky) {
            1 -> 720f
            2 -> (sunsetMin - 30).toFloat()
            3 -> 1410f
            else -> nowMinutesFloat()
        }
        val dayness = daynessAt(minutes, sunriseMin, sunsetMin)
        val warm = warmAt(minutes, sunriseMin, sunsetMin)
        val w = skyWeather(today)
        val (top, mid, bot) = skyStops(dayness, warm, w.group, w.shade)
        val glow = skyGlow(w.shade)
        return SkyView.State(
            top, mid, bot, isDark, glow,
            sunriseMin, sunsetMin, dayness,
            w.cloudiness, w.cloudDark, w.rainLevel, w.snow, w.fog
        )
    }

    /**
     * Segmented control (v0.1.8): the tab bar's pattern reused — a
     * pale sky pill container, the selected segment a solid accent
     * pill, the rest quiet text. State reads from fill + bold +
     * label, never from color alone. Registered in UI_CATALOG.
     * (v0.1.37: the container was the day's tint; it is a fixed pale
     * sky grey-blue now, like everything else in the concept-A
     * world.)
     */
    private fun segmentedControl(
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val containerColors = if (isDark) {
            intArrayOf(rgb(0x27, 0x3D, 0x59), rgb(0x1E, 0x31, 0x4A))
        } else {
            intArrayOf(rgb(0xD9, 0xE5, 0xF3), rgb(0xC7, 0xD8, 0xEE))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, containerColors).apply {
                cornerRadius = dp(24).toFloat()
            }
        }
        labels.forEachIndexed { i, label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 16f
                gravity = Gravity.CENTER
                contentDescription = label
                isClickable = true
                isFocusable = true
                minHeight = dp(48)
                if (i == selected) {
                    background = solidBgStates(accent, 24)
                    setTextColor(onAccent(accent))
                    setTypeface(null, Typeface.BOLD)
                } else {
                    background = ghostBg(24)
                    setTextColor(sub)
                    setTypeface(null, Typeface.NORMAL)
                }
                setOnClickListener { onSelect(i) }
            }
            bar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    /** 地点 chips (v0.1.8): 4 per row; selected = solid accent pill, others = outlined quiet pills. */
    private fun cityChips(): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Cities.list.chunked(4).forEachIndexed { rowIdx, rowCities ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowCities.forEachIndexed { ci, c ->
                val selected = city().name == c.name
                val chip = TextView(this).apply {
                    text = c.name
                    textSize = 16f
                    gravity = Gravity.CENTER
                    contentDescription = c.name
                    isClickable = true
                    isFocusable = true
                    minHeight = dp(48)
                    if (selected) {
                        background = solidBgStates(accent, 24)
                        setTextColor(onAccent(accent))
                        setTypeface(null, Typeface.BOLD)
                    } else {
                        background = chipGhostBg()
                        setTextColor(ink)
                        setTypeface(null, Typeface.NORMAL)
                    }
                    setOnClickListener {
                        prefs.edit().putString("city", c.name).apply()
                        // A city change supersedes any in-flight load
                        // (its connections are disconnected, FETCH_REVIEW #2).
                        loadWeather(force = true)
                    }
                }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (ci > 0) lp.leftMargin = dp(8)
                row.addView(chip, lp)
            }
            val rlp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (rowIdx > 0) rlp.topMargin = dp(8)
            wrap.addView(row, rlp)
        }
        return wrap
    }

    /** Quiet outlined pill for unselected city chips, with a pressed state. */
    private fun chipGhostBg(): StateListDrawable {
        val stroke = withAlpha(sub, 140)
        fun face(fill: Int): GradientDrawable = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), stroke)
        }
        // v0.1.37: the quiet fill is a touch stronger than before —
        // the chips now sit on a glass card over the fixed sky, and
        // the old 19% white washed out against the pale horizon.
        val pressedFill = if (isDark) argb(64, 255, 255, 255) else argb(140, 255, 255, 255)
        val normalFill = if (isDark) argb(36, 255, 255, 255) else argb(90, 255, 255, 255)
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_pressed), face(pressedFill))
        states.addState(intArrayOf(), face(normalFill))
        return states
    }

    /** 設定の選択肢を包む読み物ガラス面。行自体は面を持たない (入れ子にしない)。 */
    private fun sectionCard(inner: View): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = glassBg(24)
        }
        card.addView(inner)
        // The 8dp gap above the card used to come from the description's
        // bottom padding; it lives on the card now so a section without
        // a description (地点, v0.1.7) keeps the same rhythm.
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        card.layoutParams = lp
        return card
    }

    private fun sectionLabel(text: String, icon: IconKind): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(24), 0, 0)
        }
        val iv = IconView(this, icon, ink)
        iv.contentDescription = text
        row.addView(iv, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 16f; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        })
        return row
    }

    private fun sectionDesc(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f; setTextColor(sub)
        setPadding(0, dp(4), 0, 0)
        lineHeight = dp(21)
    }
}
