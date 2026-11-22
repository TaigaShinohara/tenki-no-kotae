package com.taigashinohara.tenkinokotae

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面ウィジェット (v0.1.14): the day's answer on the launcher —
 * city, the conclusion's first sentence, the clothing / umbrella
 * answers, and 「今日の人」 wearing them.
 *
 * Data flow is deliberately one-way and offline: MainActivity saves a
 * [WidgetSnapshot] every time Home is shown, and the widget only ever
 * reads that snapshot (no network from the widget process, ever).
 * With no snapshot yet, the widget shows a placeholder that invites
 * opening the app. Tapping anywhere opens MainActivity.
 *
 * The widget's face is fixed dark (deep slate glass) in both system
 * modes: a launcher widget sits on arbitrary wallpapers, and the dark
 * face keeps its text above the contrast gates on all of them — the
 * same reason the app's dark palette is defined independently
 * (ART_DIRECTION §10). The accent stored in the snapshot is therefore
 * the *dark* variant of the day's color. Registered in
 * docs/UI_CATALOG.md (ホーム画面ウィジェット).
 */
object WidgetSnapshot {

    private const val FILE = "tenki_widget"

    data class Data(
        val city: String,
        val headline: String,
        val clothing: String,
        val umbrella: String,
        val sunscreen: String,
        val rank: Int,
        val umbrellaLevel: String,
        val uvMax: Double,
        // v0.1.19: the layered person's extra inputs, so the widget
        // figure wears the same knit cap / towel / mask as the app's.
        // The averages are null when the day had no 9–18時 hours.
        val snow: Boolean = false,
        val daytimeFeelAvg: Double? = null,
        val daytimeHumidityAvg: Double? = null,
        val accentDark: Int,
        val updatedAt: Long
    )

    fun save(ctx: Context, data: Data) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("city", data.city)
            .putString("headline", data.headline)
            .putString("clothing", data.clothing)
            .putString("umbrella", data.umbrella)
            .putString("sunscreen", data.sunscreen)
            .putInt("rank", data.rank)
            .putString("umbLevel", data.umbrellaLevel)
            .putFloat("uvMax", data.uvMax.toFloat())
            .putBoolean("snow", data.snow)
            .putBoolean("feelHas", data.daytimeFeelAvg != null)
            .putFloat("feelAvg", (data.daytimeFeelAvg ?: 0.0).toFloat())
            .putBoolean("humHas", data.daytimeHumidityAvg != null)
            .putFloat("humAvg", (data.daytimeHumidityAvg ?: 0.0).toFloat())
            .putInt("accentDark", data.accentDark)
            .putLong("updatedAt", data.updatedAt)
            .apply()
    }

    fun load(ctx: Context): Data? {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val headline = p.getString("headline", null) ?: return null
        return Data(
            city = p.getString("city", "") ?: "",
            headline = headline,
            clothing = p.getString("clothing", "") ?: "",
            umbrella = p.getString("umbrella", "") ?: "",
            sunscreen = p.getString("sunscreen", "") ?: "",
            rank = p.getInt("rank", 3),
            umbrellaLevel = p.getString("umbLevel", "NONE") ?: "NONE",
            uvMax = p.getFloat("uvMax", 0f).toDouble(),
            snow = p.getBoolean("snow", false),
            daytimeFeelAvg = if (p.getBoolean("feelHas", false)) {
                p.getFloat("feelAvg", 0f).toDouble()
            } else {
                null
            },
            daytimeHumidityAvg = if (p.getBoolean("humHas", false)) {
                p.getFloat("humAvg", 0f).toDouble()
            } else {
                null
            },
            accentDark = p.getInt("accentDark", Color.rgb(0x7C, 0xC7, 0xF0)),
            updatedAt = p.getLong("updatedAt", 0L)
        )
    }
}

class TenkiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
    }

    companion object {

        private val WIDGET_INK = Color.rgb(0xF5, 0xF1, 0xEA)

        /** Called by MainActivity right after a snapshot is saved. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, TenkiWidgetProvider::class.java)
            )
            for (id in ids) updateOne(context, mgr, id)
        }

        private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_tenki)
            val data = WidgetSnapshot.load(context)
            if (data == null) {
                views.setTextViewText(R.id.widget_city, "")
                views.setTextViewText(R.id.widget_headline, "アプリを開いて天気を確認")
                views.setTextViewText(R.id.widget_answers, "")
                views.setTextViewText(R.id.widget_updated, "")
                views.setTextColor(R.id.widget_headline, WIDGET_INK)
                // A neutral person (rank 3, nothing in hand) keeps the
                // placeholder from looking broken.
                views.setImageViewBitmap(
                    R.id.widget_person,
                    personBitmap(context, PersonFigure.spec(3, Rules.UmbrellaLevel.NONE, 0.0))
                )
            } else {
                val level = runCatching {
                    Rules.UmbrellaLevel.valueOf(data.umbrellaLevel)
                }.getOrDefault(Rules.UmbrellaLevel.NONE)
                val spec = PersonFigure.spec(
                    data.rank, level, data.uvMax,
                    snow = data.snow,
                    daytimeFeelAvg = data.daytimeFeelAvg,
                    daytimeHumidityAvg = data.daytimeHumidityAvg
                )
                views.setTextViewText(R.id.widget_city, data.city)
                views.setTextViewText(R.id.widget_headline, data.headline)
                views.setTextColor(R.id.widget_headline, data.accentDark)
                views.setTextViewText(
                    R.id.widget_answers,
                    "服装：${data.clothing}\n傘：${data.umbrella}\n日焼け止め：${data.sunscreen}"
                )
                val time = SimpleDateFormat("H:mm", Locale.JAPAN)
                    .format(Date(data.updatedAt))
                views.setTextViewText(R.id.widget_updated, "$time 更新")
                views.setImageViewBitmap(R.id.widget_person, personBitmap(context, spec))
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            mgr.updateAppWidget(id, views)
        }

        /**
         * RemoteViews cannot host the custom PersonView, so the same
         * [PersonComposer] routine renders into a Bitmap instead —
         * one composition, two hosts, never a second implementation.
         */
        private fun personBitmap(context: Context, spec: PersonSpec): Bitmap {
            val bmp = Bitmap.createBitmap(192, 384, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PersonComposer.draw(canvas, context, spec, 0f, 0f, 192f, 384f)
            return bmp
        }
    }
}
