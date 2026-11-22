package com.taigashinohara.tenkinokotae

/**
 * 数値 → 行動への変換ルール（設計 v1.3 §3 が正本）。
 * すべて体感温度（apparent temperature）を物差しにする。純粋関数のみ（単体テスト対象）。
 */
object Rules {

    // ---------- 前日比 ----------

    /** level: -3 別世界級に寒い … 0 ほぼ同じ … +3 別世界級に暖かい */
    data class DiffResult(val phrase: String, val level: Int)

    fun diffWord(diff: Double, base: String = "昨日"): DiffResult = when {
        diff <= -10.0 -> DiffResult("${base}とは別世界なくらい寒い", -3)
        diff <= -6.0 -> DiffResult("${base}よりぐっと寒い", -2)
        diff <= -3.0 -> DiffResult("${base}より少し寒い", -1)
        diff < 3.0 -> DiffResult("${base}とほぼ同じ体感", 0)
        diff < 6.0 -> DiffResult("${base}より少し暖かい", 1)
        diff < 10.0 -> DiffResult("${base}よりぐっと暖かい", 2)
        else -> DiffResult("${base}とは別世界なくらい暖かい", 3)
    }

    /** その日に 9〜17時台のデータが1件でもあるか。 */
    fun hasDaytimeHours(day: DayForecast): Boolean = day.hours.any { it.hour in 9..17 }

    /**
     * 0〜23時がそろっていない「部分日」か。予備源（MET Norway）の
     * 「今日」は現在時刻以降しかないため、夕方取得では朝の時間が
     * 欠けた部分日になる。完全日のふりをしないための判定に使う。
     */
    fun isPartialDay(day: DayForecast): Boolean {
        if (day.hours.isEmpty()) return false
        val hs = day.hours.map { it.hour }
        return hs.size < 24 || hs.minOrNull()!! > 0 || hs.maxOrNull()!! < 23
    }

    /**
     * 評価に使う体感温度の平均。原則は「自分が名乗る時間帯に実在する
     * 時間だけを使う」：
     * - 9〜17時（日中）にデータがあれば、その実在分だけの平均。
     * - 9〜17時が1件も無い日（予備源を夕方に取得した「今日」など）は、
     *   日中の平均のふりをせず、その日に残っている時間（実在分全部）の
     *   平均へ明示的に切り替える。呼び出し側は [hasDaytimeHours] で
     *   どちらの評価か区別できる（結論の前日比は切り替え先を日中と
     *   して比べない — [matchedDaytimeAvgs] を使う）。
     * - データ自体が無い日は 0.0。
     */
    fun daytimeAvg(day: DayForecast): Double {
        val hs = day.hours.filter { it.hour in 9..17 }
        val src = if (hs.isEmpty()) day.hours else hs
        if (src.isEmpty()) return 0.0
        return src.map { it.apparent }.average()
    }

    /**
     * 前日比のための「同じ時間同士」の日中平均ペア（today, yesterday）。
     * 両日に共通して実在する 9〜17時の時間だけで平均するので、片方が
     * 部分日でも比べる時間帯がずれない。共通する日中の時間が1件も
     * 無い場合は比べようがないので null（呼び出し側は前日比を作らず、
     * 絶対評価に切り替える）。
     */
    fun matchedDaytimeAvgs(
        today: DayForecast, yesterday: DayForecast
    ): Pair<Double, Double>? {
        val tHours = today.hours.filter { it.hour in 9..17 }.map { it.hour }.toSet()
        val yHours = yesterday.hours.filter { it.hour in 9..17 }.map { it.hour }.toSet()
        val common = tHours intersect yHours
        if (common.isEmpty()) return null
        fun avg(day: DayForecast): Double =
            day.hours.filter { it.hour in common }.map { it.apparent }.average()
        return avg(today) to avg(yesterday)
    }

    /** 日中 9〜18時の最大風速（m/s）。 */
    fun daytimeMaxWind(day: DayForecast): Double =
        day.hours.filter { it.hour in 9..17 }.maxOfOrNull { it.wind } ?: 0.0

    /**
     * 日中（9〜18時）に実在する時間の平均体感温度（v0.1.19、「今日の人」
     * の首タオル判定の入力）。服装の日中（9〜17時台）とは窓が違う —
     * こちらは仕様の「9〜18時」をそのまま使う。窓の時間が1件も無い
     * 日は null（判定は発動しない）。
     */
    fun daytimeFeelAvg(day: DayForecast): Double? {
        val hs = day.hours.filter { it.hour in 9..18 }
        if (hs.isEmpty()) return null
        return hs.map { it.apparent }.average()
    }

    /**
     * 日中（9〜18時）に実在する時間の平均湿度（v0.1.19、マスク判定の
     * 入力）。窓の時間が1件も無い日は null。湿度を持たないデータ源は
     * 既定値50で埋まるため、その場合は40%未満にならず発動しない。
     */
    fun daytimeHumidityAvg(day: DayForecast): Double? {
        val hs = day.hours.filter { it.hour in 9..18 }
        if (hs.isEmpty()) return null
        return hs.map { it.humidity }.average()
    }

    /** 1日の体感温度の寒暖差。 */
    fun dayNightGap(day: DayForecast): Double {
        if (day.hours.isEmpty()) return 0.0
        val apps = day.hours.map { it.apparent }
        return (apps.maxOrNull() ?: 0.0) - (apps.minOrNull() ?: 0.0)
    }

    // ---------- 服装 ----------

    data class Clothing(val rank: Int, val title: String, val sub: String, val lead: String)

    private val clothingTable = listOf(
        Clothing(0, "半袖Tシャツ", "薄着でOK", "半袖でOK。"),
        Clothing(1, "半袖 ／ 薄い長袖シャツ", "どちらでも快適", "半袖か薄い長袖で。"),
        Clothing(2, "長袖シャツ ＋ 薄手カーディガン", "羽織りが1枚あると安心", "長袖＋羽織りで。"),
        Clothing(3, "トレーナー／薄手ニット ＋ ライトアウター", "朝晩は羽織って", "トレーナー＋ライトアウターで。"),
        Clothing(4, "ニット ＋ コート", "しっかり防寒を", "ニット＋コートで正解。"),
        Clothing(5, "厚手ニット ＋ 冬コート ＋ マフラー", "首元まで暖かく", "冬コート＋マフラーで。"),
        Clothing(6, "ダウン ＋ 手袋・マフラー", "一番暖かい装備で", "ダウンで完全防備を。")
    )

    fun baseRank(daytimeTemp: Double): Int = when {
        daytimeTemp >= 28.0 -> 0
        daytimeTemp >= 23.0 -> 1
        daytimeTemp >= 18.0 -> 2
        daytimeTemp >= 13.0 -> 3
        daytimeTemp >= 8.0 -> 4
        daytimeTemp >= 3.0 -> 5
        else -> 6
    }

    /**
     * feelOffset: 寒がり +1 / ふつう 0 / 暑がり -1（ランクが大きいほど厚着）。
     * 強風（日中最大風速10m/s以上）の日は1ランク厚く。
     */
    fun clothing(day: DayForecast, feelOffset: Int): Clothing {
        var rank = baseRank(daytimeAvg(day)) + feelOffset
        if (daytimeMaxWind(day) >= 10.0) rank += 1
        return clothingTable[rank.coerceIn(0, 6)]
    }

    // ---------- 傘 ----------

    enum class UmbrellaLevel { NEED, FOLDING, NONE }

    data class Umbrella(val level: UmbrellaLevel, val title: String, val note: String)

    fun umbrella(day: DayForecast): Umbrella {
        // 窓（7〜18時）は実在する時間だけを使う。窓が1件も無い日
        // （予備源を夜に取得した「今日」など）では「雨の心配なし」と
        // 決めつけず、その日に残っている時間で判定し直す。
        var window = day.hours.filter { it.hour in 7..18 }
        if (window.isEmpty()) window = day.hours
        // 「雨が降る時間」は降水量ベースで数える（確率だけで2時間以上と数えない）
        val rainy = window.filter { it.precip >= 0.1 }
        val probMax = window.maxOfOrNull { it.precipProb ?: 0 } ?: 0
        val precipMax = window.maxOfOrNull { it.precip } ?: 0.0
        // 降水確率が1件も提供されないデータ源（予備源 MET Norway）では、
        // 確率を使わず降水量だけで判定する。確率がある場合は従来どおり。
        val hasProb = window.any { it.precipProb != null }
        val level = if (hasProb) {
            when {
                probMax >= 50 || precipMax >= 1.0 || rainy.size >= 2 -> UmbrellaLevel.NEED
                probMax >= 30 || precipMax >= 0.1 || rainy.size == 1 -> UmbrellaLevel.FOLDING
                else -> UmbrellaLevel.NONE
            }
        } else {
            when {
                precipMax >= 1.0 -> UmbrellaLevel.NEED
                precipMax >= 0.1 -> UmbrellaLevel.FOLDING
                else -> UmbrellaLevel.NONE
            }
        }
        val noteHours = if (rainy.isNotEmpty()) rainy else window.filter { (it.precipProb ?: 0) >= 30 }
        val note = when {
            noteHours.isEmpty() -> "雨の心配なし"
            noteHours.all { it.hour >= 15 } -> "夕方だけ降りそう"
            noteHours.all { it.hour <= 10 } -> "朝だけ降りそう"
            noteHours.size == 1 -> "${noteHours.first().hour}時ごろに通り雨"
            else -> "${noteHours.first().hour}〜${noteHours.last().hour}時ごろ降りそう"
        }
        val title = when (level) {
            UmbrellaLevel.NEED -> "傘がいる"
            UmbrellaLevel.FOLDING -> "折りたたみでOK"
            UmbrellaLevel.NONE -> "傘はいらない"
        }
        return Umbrella(level, title, note)
    }

    // ---------- 日焼け止め ----------

    data class Sunscreen(val title: String, val sub: String)

    fun sunscreen(uvMax: Double): Sunscreen = when {
        uvMax < 3.0 -> Sunscreen("日焼け止めは不要", "日差しは弱め")
        uvMax < 6.0 -> Sunscreen("日焼け止めは塗ると安心", "出かける前に薄く")
        uvMax < 8.0 -> Sunscreen("日焼け止め必須", "しっかり塗って")
        else -> Sunscreen("日焼け止め必須 ＋ 帽子か日傘も", "日差しが強い日")
    }

    // ---------- 気圧（α枠） ----------

    /**
     * level: 2 急降下 / 1 下がり気味 / 0 少し下がり気味 / -1 出さない。
     * sensitivity: 0 弱い（level2のみ）/ 1 ふつう（level1以上）/ 2 敏感（level0以上）。
     */
    data class PressureAdvice(
        val level: Int,
        val visible: Boolean,
        val title: String,
        val action: String,
        val timingNote: String,
        val maxDrop6h: Double,
        val maxRise6h: Double,
        val minPressure: Double,
        /** 6時間幅のペアが実在し、低下量を実際に測れたか。false の時 maxDrop6h は「未測定」であって 0.0 の実測ではない。 */
        val dropMeasured: Boolean
    )

    fun pressure(day: DayForecast, sensitivity: Int): PressureAdvice {
        // 6時間低下は「リストの並び」ではなく実時刻で測る： 時刻 h と
        // h+6 の両方が実在するペアだけを使う（部分日では開始位置が
        // 0時とは限らず、インデックス＝時刻の前提は崩れる）。
        val byHour = day.hours.associate { it.hour to it.pressure }
        var maxDrop = 0.0
        var dropEndHour = -1
        var maxRise = 0.0
        var measured = false
        for ((h, p0) in byHour) {
            val p6 = byHour[h + 6] ?: continue
            measured = true
            val d = p0 - p6
            if (d > maxDrop) { maxDrop = d; dropEndHour = h + 6 }
            if (-d > maxRise) maxRise = -d
        }
        val minP = byHour.values.minOrNull() ?: 1013.0
        val level = when {
            maxDrop >= 6.0 || minP < 1003.0 -> 2
            maxDrop >= 3.0 || minP < 1007.0 -> 1
            maxDrop >= 1.5 -> 0
            else -> -1
        }
        val visible = when (sensitivity) {
            0 -> level >= 2
            2 -> level >= 0
            else -> level >= 1
        }
        val (title, action) = when (level) {
            2 -> "気圧が急降下。頭痛・だるさが出やすいかも" to "予定は軽めに。早めの休憩を先に確保して"
            1 -> "気圧が下がり気味。頭が重くなりやすいかも" to "水分をこまめに。予定は詰めすぎないで"
            0 -> "気圧は少し下がり気味" to "敏感な人は無理のない範囲で"
            else -> "" to ""
        }
        val timing = when {
            dropEndHour < 0 -> ""
            dropEndHour <= 11 -> "朝から下がっていく"
            dropEndHour <= 14 -> "昼ごろから下がっていく"
            dropEndHour <= 17 -> "午後から下がっていく"
            else -> "夜にかけて下がっていく"
        }
        return PressureAdvice(
            level, visible && level >= 0, title, action, timing,
            maxDrop, maxRise, minP, measured
        )
    }

    // ---------- 帰りの1行 ----------

    fun eveningLine(day: DayForecast): String {
        val evening = day.hours.filter { it.hour in 17..22 }
        val rain = evening.any { it.precip >= 0.1 || (it.precipProb ?: 0) >= 50 }
        val eveAvg = if (evening.isEmpty()) daytimeAvg(day)
            else evening.map { it.apparent }.average()
        val cold = eveAvg <= daytimeAvg(day) - 5.0
        return when {
            rain && cold -> "帰り： 雨も冷えもあり。傘＋羽織りを"
            rain -> "帰り： 雨が降りそう。傘を忘れずに"
            cold -> "帰り： ぐっと冷える。羽織りを足して"
            else -> "帰りもそのままでOK"
        }
    }

    // ---------- 明日の1行 ----------

    fun tomorrowLine(today: DayForecast, tomorrow: DayForecast): String {
        // 今日が部分日でも、比べる時間帯がずれないよう共通の実在時間で比べる。
        val matched = matchedDaytimeAvgs(tomorrow, today)
        val diff = if (matched != null) {
            matched.first - matched.second
        } else {
            daytimeAvg(tomorrow) - daytimeAvg(today)
        }
        val word = diffWord(diff, "今日")
        val umb = when (umbrella(tomorrow).level) {
            UmbrellaLevel.NEED -> "傘がいる"
            UmbrellaLevel.FOLDING -> "折りたたみ傘を"
            UmbrellaLevel.NONE -> "傘はいらない"
        }
        return "明日： ${word.phrase}。$umb"
    }

    // ---------- 結論 ----------

    fun conclusion(today: DayForecast, yesterday: DayForecast, feelOffset: Int): String {
        // 前日比は「同じ時間同士」で比べる。共通の日中時間が無い日
        // （予備源を夕方以降に取得し、今日に日中データが無い場合）は、
        // 夜の平均と昨日の日中を引いて「昨日より寒い」を作らず、
        // 昨日の無い時と同じ絶対評価に切り替える。
        val matched = matchedDaytimeAvgs(today, yesterday)
            ?: return conclusionNoYesterday(today, feelOffset)
        val diff = diffWord(matched.first - matched.second)
        val lead = clothing(today, feelOffset).lead
        return "${diff.phrase}。$lead"
    }

    /**
     * 昨日のデータが無い時（予備源のみで表示する時）の結論。
     * 前日比の代わりに、今日の日中体感温度そのものの評価（服装帯と連動）＋服装の一文。
     */
    fun conclusionNoYesterday(today: DayForecast, feelOffset: Int): String {
        val avg = daytimeAvg(today)
        val feel = when {
            avg >= 28.0 -> "今日はかなり暑い"
            avg >= 23.0 -> "今日は暖かい"
            avg >= 18.0 -> "今日は過ごしやすい暖かさ"
            avg >= 13.0 -> "今日は少し肌寒い"
            avg >= 8.0 -> "今日は寒い"
            avg >= 3.0 -> "今日はしっかり寒い"
            else -> "今日は凍える寒さ"
        }
        return "$feel。${clothing(today, feelOffset).lead}"
    }

    /** 朝晩の寒暖差が大きい日の追加の一言（なければ null）。 */
    fun gapNote(day: DayForecast): String? =
        if (dayNightGap(day) >= 7.0) "夜は冷える。羽織りを足して" else null
}
