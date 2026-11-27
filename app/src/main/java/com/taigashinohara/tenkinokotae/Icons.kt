package com.taigashinohara.tenkinokotae

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * アイコン体系 (v0.1.9): every icon is drawn in code as a vector shape —
 * no external assets, no emoji, no kanji glyphs (the v0.1.6 lesson:
 * a character that repeats the label reads as a stutter; a *picture*
 * does not). One line style for the whole app: stroke = 2.1 units of
 * a 24-unit box, round caps and joins, single color per icon (the
 * caller passes ink or sub). Meanings are the ones fixed by general
 * use (umbrella, bottle, pin, thermometer…); where a shape alone
 * would be ambiguous, the existing short label stays next to it —
 * per minimal_information_design, icon + label beats icon alone when
 * the icon's meaning is not fixed.
 *
 * Registered in docs/UI_CATALOG.md (アイコン体系).
 *
 * v0.1.15 (docs/FIX_v0.1.15.md): chip-size legibility pass from the
 * real-device screenshot — UMBRELLA_NONE redrawn (smooth wide dome
 * under the slash; it read as a flower), DOWN simplified and widened
 * (its scarf tail read as bag straps), the bottle family widened.
 */
enum class IconKind {
    TEE, SHIRT_MID, SHIRT_LONG, SWEATER, COAT, COAT_SCARF, DOWN,
    UMBRELLA_OPEN, UMBRELLA_FOLDED, UMBRELLA_NONE,
    BOTTLE, BOTTLE_SLASH, BOTTLE_SUN, BOTTLE_SHADE,
    PRESSURE_STEEP, PRESSURE_FALL, PRESSURE_SLIGHT,
    MOON, SUN, PIN, THERMOMETER, ARROW_UP, ARROW_DOWN,
    // v0.1.37: the floating tab bar's three marks (the concept-A
    // mock leads each tab with a picture, like the rest of the app).
    HOME, CHART, SLIDERS
}

object Icons {

    fun draw(canvas: Canvas, kind: IconKind, cx: Float, cy: Float, sizePx: Float, color: Int) {
        val u = sizePx / 24f
        val left = cx - sizePx / 2f
        val top = cy - sizePx / 2f
        fun x(v: Float) = left + v * u
        fun y(v: Float) = top + v * u

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.1f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val p = Path()
        fun moveTo(a: Float, b: Float) = p.moveTo(x(a), y(b))
        fun lineTo(a: Float, b: Float) = p.lineTo(x(a), y(b))
        fun quadTo(a: Float, b: Float, c: Float, d: Float) = p.quadTo(x(a), y(b), x(c), y(d))
        fun line(a: Float, b: Float, c: Float, d: Float) {
            canvas.drawLine(x(a), y(b), x(c), y(d), stroke)
        }
        fun circle(a: Float, b: Float, r: Float, paint: Paint = stroke) {
            canvas.drawCircle(x(a), y(b), r * u, paint)
        }

        /** Shirt-family outline; sleeve hem at [sleeveY], sleeve tip x at [sleeveX]. */
        fun shirt(sleeveX: Float, sleeveY: Float, bodyBottom: Float, bodyX: Float = 8.6f) {
            val rX = 24f - sleeveX
            val rBody = 24f - bodyX
            p.reset()
            moveTo(9.3f, 4.2f)
            lineTo(sleeveX, 7.6f)
            lineTo(sleeveX + 1.4f, sleeveY)
            lineTo(bodyX, sleeveY - 1.2f)
            lineTo(bodyX, bodyBottom)
            lineTo(rBody, bodyBottom)
            lineTo(rBody, sleeveY - 1.2f)
            lineTo(rX - 1.4f, sleeveY)
            lineTo(rX, 7.6f)
            lineTo(14.7f, 4.2f)
            quadTo(12f, 7.0f, 9.3f, 4.2f)
            canvas.drawPath(p, stroke)
        }

        fun umbrellaCanopy(scaleDown: Float = 1f) {
            // Dome with a scalloped lower edge — the umbrella silhouette.
            p.reset()
            moveTo(2.5f, 13.5f)
            quadTo(12f, 3.5f, 21.5f, 13.5f)
            quadTo(18.3f, 11.3f, 15.2f, 13.5f)
            quadTo(12f, 11.3f, 8.8f, 13.5f)
            quadTo(5.7f, 11.3f, 2.5f, 13.5f)
            canvas.drawPath(p, stroke)
        }

        fun bottle(bx: Float, by: Float, bw: Float, bh: Float) {
            // Sunscreen bottle: cap + shoulders + rounded body.
            val capW = bw * 0.42f
            val capX = bx + (bw - capW) / 2f
            val capH = bh * 0.16f
            val bodyTop = by + capH + bh * 0.10f
            p.reset()
            p.addRoundRect(
                RectF(x(capX), y(by), x(capX + capW), y(by + capH)),
                0.8f * u, 0.8f * u, Path.Direction.CW
            )
            canvas.drawPath(p, stroke)
            p.reset()
            moveTo(capX, by + capH)
            lineTo(bx, bodyTop)
            lineTo(bx, by + bh - 1.2f)
            quadTo(bx, by + bh, bx + 1.2f, by + bh)
            lineTo(bx + bw - 1.2f, by + bh)
            quadTo(bx + bw, by + bh, bx + bw, by + bh - 1.2f)
            lineTo(bx + bw, bodyTop)
            lineTo(capX + capW, by + capH)
            canvas.drawPath(p, stroke)
        }

        fun sun(cxv: Float, cyv: Float, r: Float, rayInner: Float, rayOuter: Float) {
            circle(cxv, cyv, r)
            for (k in 0 until 8) {
                val a = Math.toRadians((k * 45).toDouble())
                val ca = kotlin.math.cos(a).toFloat()
                val sa = kotlin.math.sin(a).toFloat()
                line(
                    cxv + ca * rayInner, cyv + sa * rayInner,
                    cxv + ca * rayOuter, cyv + sa * rayOuter
                )
            }
        }

        fun pressureArrow(x1: Float, y1: Float, x2: Float, y2: Float) {
            line(x1, y1, x2, y2)
            // Arrowhead: two short strokes back along the shaft direction.
            val dx = x2 - x1
            val dy = y2 - y1
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val ux = dx / len
            val uy = dy / len
            val hx = 3.6f
            line(x2, y2, x2 - ux * hx + uy * hx * 0.62f, y2 - uy * hx - ux * hx * 0.62f)
            line(x2, y2, x2 - ux * hx - uy * hx * 0.62f, y2 - uy * hx + ux * hx * 0.62f)
        }

        fun pressureWave() {
            p.reset()
            moveTo(2.5f, 17.5f)
            quadTo(5.5f, 14.5f, 8.5f, 17.5f)
            quadTo(11.5f, 20.5f, 14.5f, 17.5f)
            quadTo(17.5f, 14.5f, 21.5f, 17.5f)
            canvas.drawPath(p, stroke)
        }

        when (kind) {
            IconKind.TEE -> shirt(4.2f, 11.6f, 19.8f)
            IconKind.SHIRT_MID -> shirt(4.6f, 14.2f, 19.8f)
            IconKind.SHIRT_LONG -> {
                shirt(4.9f, 18.6f, 19.8f)
                // Open cardigan over the shirt: the V and the front line.
                line(9.8f, 5.2f, 12f, 10.5f)
                line(14.2f, 5.2f, 12f, 10.5f)
                line(12f, 10.5f, 12f, 19.8f)
            }
            IconKind.SWEATER -> {
                shirt(4.9f, 18.6f, 19.8f, bodyX = 8.2f)
                // Second neck line (thick crew neck), cuff ticks, hem band.
                p.reset()
                moveTo(9.8f, 5.6f)
                quadTo(12f, 7.8f, 14.2f, 5.6f)
                canvas.drawPath(p, stroke)
                line(6.2f, 16.6f, 8.4f, 16.3f)
                line(15.6f, 16.3f, 17.8f, 16.6f)
                line(8.2f, 17.9f, 15.8f, 17.9f)
            }
            IconKind.COAT, IconKind.COAT_SCARF, IconKind.DOWN -> {
                val isDown = kind == IconKind.DOWN
                // v0.1.15: the down body widens (7.9 -> 7.3) so the
                // bulkiest rank is also the widest shape at chip size.
                val sleeveX = if (isDown) 4.4f else 5.4f
                val bodyX = if (isDown) 7.3f else 7.9f
                // Long body (to 21), sleeves to the wrist.
                p.reset()
                moveTo(9.2f, 4.0f)
                lineTo(sleeveX, 6.8f)
                lineTo(sleeveX + 1.0f, 18.8f)
                lineTo(bodyX + 0.2f, 18.5f)
                lineTo(bodyX, 21.0f)
                lineTo(24f - bodyX, 21.0f)
                lineTo(24f - bodyX - 0.2f, 18.5f)
                lineTo(24f - sleeveX - 1.0f, 18.8f)
                lineTo(24f - sleeveX, 6.8f)
                lineTo(14.8f, 4.0f)
                quadTo(12f, 6.2f, 9.2f, 4.0f)
                canvas.drawPath(p, stroke)
                when {
                    isDown -> {
                        // Raised collar arc + scarf wrap band + two
                        // quilt lines. v0.1.15: the v0.1.14 icon's two
                        // long scarf-tail lines read as *bag straps*
                        // at chip size, so the tail is dropped here
                        // (the wrap band still says scarf; the full
                        // tail lives on 今日の人, drawn large) and the
                        // quilts go from three lines to two, wider
                        // apart — puffer first, details second.
                        p.reset()
                        moveTo(9.0f, 4.8f)
                        quadTo(12f, 1.9f, 15.0f, 4.8f)
                        canvas.drawPath(p, stroke)
                        p.reset()
                        moveTo(9.4f, 6.6f)
                        quadTo(12f, 9.2f, 14.6f, 6.6f)
                        canvas.drawPath(p, stroke)
                        for (qy in floatArrayOf(11.8f, 16.2f)) {
                            p.reset()
                            moveTo(bodyX + 0.1f, qy)
                            quadTo(12f, qy + 0.9f, 24f - bodyX - 0.1f, qy)
                            canvas.drawPath(p, stroke)
                        }
                    }
                    kind == IconKind.COAT_SCARF -> {
                        // Scarf: wrap band under the neck + hanging tail.
                        p.reset()
                        moveTo(8.8f, 5.4f)
                        quadTo(12f, 8.8f, 15.2f, 5.4f)
                        canvas.drawPath(p, stroke)
                        line(10.2f, 8.6f, 10.2f, 15.2f)
                        line(12.2f, 8.6f, 12.2f, 15.2f)
                        line(10.2f, 15.2f, 10.2f, 16.4f)
                        line(11.2f, 15.2f, 11.2f, 16.4f)
                        line(12.2f, 15.2f, 12.2f, 16.4f)
                        line(12f, 17.6f, 12f, 21.0f)
                    }
                    else -> {
                        // Lapels, front line, buttons.
                        line(10.0f, 4.6f, 12f, 9.5f)
                        line(14.0f, 4.6f, 12f, 9.5f)
                        line(12f, 9.5f, 12f, 21.0f)
                        for (byy in floatArrayOf(12.6f, 15.6f, 18.6f)) {
                            circle(12f, byy, 0.75f, fill)
                        }
                    }
                }
            }
            IconKind.UMBRELLA_OPEN -> {
                umbrellaCanopy()
                line(12f, 5.4f, 12f, 2.8f)
                line(12f, 13.5f, 12f, 19.3f)
                p.reset()
                moveTo(12f, 19.3f)
                quadTo(12f, 22f, 14.5f, 22f)
                quadTo(16.8f, 22f, 16.8f, 19.6f)
                canvas.drawPath(p, stroke)
            }
            IconKind.UMBRELLA_FOLDED -> {
                p.reset()
                moveTo(12f, 2.5f)
                quadTo(16f, 9f, 15.6f, 16f)
                lineTo(8.4f, 16f)
                quadTo(8f, 9f, 12f, 2.5f)
                canvas.drawPath(p, stroke)
                p.reset()
                moveTo(8.7f, 13.0f)
                quadTo(12f, 13.9f, 15.3f, 13.0f)
                canvas.drawPath(p, stroke)
                line(12f, 16f, 12f, 19.4f)
                p.reset()
                moveTo(12f, 19.4f)
                quadTo(12f, 21.6f, 14.1f, 21.6f)
                canvas.drawPath(p, stroke)
            }
            IconKind.UMBRELLA_NONE -> {
                // v0.1.15 redraw: the scalloped canopy + the full
                // slash + the ferrule spike read as a *flower* at
                // chip size on the real device. The dome is now wide
                // and almost smooth (two shallow scallops, no spike)
                // and the shaft + hook stay long, so the silhouette
                // still says "umbrella" underneath the slash — the
                // classic prohibition pictogram instead of a sparkle.
                p.reset()
                moveTo(2.2f, 13.8f)
                quadTo(12f, 3.0f, 21.8f, 13.8f)
                quadTo(17f, 11.7f, 12f, 13.8f)
                quadTo(7f, 11.7f, 2.2f, 13.8f)
                canvas.drawPath(p, stroke)
                line(12f, 13.8f, 12f, 19.2f)
                p.reset()
                moveTo(12f, 19.2f)
                quadTo(12f, 22f, 14.6f, 22f)
                quadTo(17f, 22f, 17f, 19.5f)
                canvas.drawPath(p, stroke)
                // The slash that turns it into "no umbrella needed".
                line(4.2f, 20.8f, 19.8f, 4.2f)
            }
            IconKind.BOTTLE -> {
                // v0.1.15: the bottle body widens (6.8 -> 8.2 units)
                // across the whole bottle family — at chip size the
                // narrow bottle was the faintest of the three chips.
                bottle(7.9f, 3.4f, 8.2f, 17.2f)
                circle(12f, 14.2f, 1.8f)
            }
            IconKind.BOTTLE_SLASH -> {
                bottle(7.9f, 3.4f, 8.2f, 17.2f)
                line(4.6f, 19.8f, 19.4f, 4.2f)
            }
            IconKind.BOTTLE_SUN -> {
                bottle(4.6f, 6.8f, 7.2f, 13.8f)
                sun(17.8f, 6.4f, 2.3f, 3.6f, 5.2f)
            }
            IconKind.BOTTLE_SHADE -> {
                // Bottle sheltered under a parasol canopy.
                p.reset()
                moveTo(3f, 10.5f)
                quadTo(12f, 3f, 21f, 10.5f)
                quadTo(18f, 8.6f, 15f, 10.5f)
                quadTo(12f, 8.6f, 9f, 10.5f)
                quadTo(6f, 8.6f, 3f, 10.5f)
                canvas.drawPath(p, stroke)
                bottle(8.3f, 12.0f, 7.4f, 9.0f)
            }
            IconKind.PRESSURE_STEEP -> {
                pressureArrow(6f, 3.5f, 15.5f, 13f)
                pressureWave()
            }
            IconKind.PRESSURE_FALL -> {
                pressureArrow(6f, 5f, 15.5f, 12.5f)
                pressureWave()
            }
            IconKind.PRESSURE_SLIGHT -> {
                pressureArrow(6f, 6.5f, 15.5f, 11f)
                pressureWave()
            }
            IconKind.MOON -> {
                // Crescent: outer arc the long way round (258° -> 22°),
                // inner arc back (59.7° -> 219.4°). The two circles are
                // computed to meet at both tips (10.2, 3.8) and
                // (19.9, 15.2), so the stroke has no gaps.
                p.reset()
                p.moveTo(x(10.2f), y(3.8f))
                p.arcTo(RectF(x(3.5f), y(3.5f), x(20.5f), y(20.5f)), 258f, -236f)
                p.arcTo(RectF(x(8.47f), y(1.03f), x(23.67f), y(16.23f)), 59.7f, 159.7f)
                canvas.drawPath(p, stroke)
            }
            IconKind.SUN -> sun(12f, 12f, 4.2f, 6.2f, 8.6f)
            IconKind.PIN -> {
                p.reset()
                moveTo(12f, 21.3f)
                quadTo(5.2f, 14.5f, 5.2f, 9.8f)
                p.arcTo(RectF(x(5.2f), y(3.2f), x(18.8f), y(16.4f)), 180f, 180f)
                quadTo(18.8f, 14.5f, 12f, 21.3f)
                canvas.drawPath(p, stroke)
                circle(12f, 9.8f, 2.6f)
            }
            IconKind.THERMOMETER -> {
                line(10.6f, 4.2f, 10.6f, 14.4f)
                line(13.4f, 4.2f, 13.4f, 14.4f)
                p.reset()
                moveTo(10.6f, 4.2f)
                quadTo(12f, 2.6f, 13.4f, 4.2f)
                canvas.drawPath(p, stroke)
                circle(12f, 17.2f, 3.5f)
                line(12f, 8.5f, 12f, 15.6f)
                circle(12f, 17.2f, 1.5f, fill)
            }
            IconKind.ARROW_UP -> {
                line(12f, 19.5f, 12f, 5f)
                line(6.8f, 10.4f, 12f, 5f)
                line(17.2f, 10.4f, 12f, 5f)
            }
            IconKind.ARROW_DOWN -> {
                line(12f, 4.5f, 12f, 19f)
                line(6.8f, 13.6f, 12f, 19f)
                line(17.2f, 13.6f, 12f, 19f)
            }
            IconKind.HOME -> {
                // Roof, walls, and a door — the fixed "home" shape.
                p.reset()
                moveTo(3.8f, 11.2f)
                lineTo(12f, 4f)
                lineTo(20.2f, 11.2f)
                canvas.drawPath(p, stroke)
                line(6.4f, 9.6f, 6.4f, 20f)
                line(17.6f, 9.6f, 17.6f, 20f)
                line(6.4f, 20f, 17.6f, 20f)
                line(10.2f, 20f, 10.2f, 15.4f)
                line(13.8f, 20f, 13.8f, 15.4f)
                line(10.2f, 15.4f, 13.8f, 15.4f)
            }
            IconKind.CHART -> {
                // Axes + the day's line: the 数値で見る screen's own
                // shape (its hero is a line chart).
                line(4.5f, 4f, 4.5f, 19.5f)
                line(4.5f, 19.5f, 20f, 19.5f)
                p.reset()
                moveTo(6.5f, 15.5f)
                lineTo(10.5f, 9.5f)
                lineTo(14f, 12.5f)
                lineTo(18.5f, 6.5f)
                canvas.drawPath(p, stroke)
            }
            IconKind.SLIDERS -> {
                // Three adjustment sliders — the fixed "settings"
                // shape, distinct from the sun (rays) and the gear
                // (teeth) it could be confused with at tab size.
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
                circle(9f, 7f, 1.7f, fill)
                circle(15f, 12f, 1.7f, fill)
                circle(7f, 17f, 1.7f, fill)
            }
        }
    }
}

/** A square view that draws one [IconKind]. Set a contentDescription at the call site. */
class IconView(
    ctx: Context,
    private val kind: IconKind,
    iconColor: Int
) : View(ctx) {
    /**
     * The drawing color, re-settable: the tab bar recolors its marks
     * when the selection moves (v0.1.37), where every other call site
     * still passes the color once at construction.
     */
    var iconColor: Int = iconColor
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        Icons.draw(canvas, kind, width / 2f, height / 2f, s, iconColor)
    }
}
