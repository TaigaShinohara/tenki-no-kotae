package com.taigashinohara.tenkinokotae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * FETCH_REVIEW #1/#4/#5 で切り出した純粋関数のテスト：
 * サイズ上限つき読み取り、タイムアウト分割、状態コード分類、
 * Retry-After 解釈、リトライ待ち時間、そして fetchWithBudget の
 * ループ挙動（フェイクの attempt で検証する）。
 */
class FetchSupportTest {

    // ---------- readLimitedText (#1) ----------

    private fun streamOf(s: String) = ByteArrayInputStream(s.toByteArray(Charsets.UTF_8))

    @Test fun readLimited_readsUnderLimit() {
        assertEquals("hello", readLimitedText(streamOf("hello"), 100))
        assertEquals("", readLimitedText(streamOf(""), 100))
    }

    @Test fun readLimited_exactLimitIsOk() {
        assertEquals("abcde", readLimitedText(streamOf("abcde"), 5))
    }

    @Test fun readLimited_overLimitThrows() {
        try {
            readLimitedText(streamOf("abcdef"), 5)
            fail("上限超過で例外になるはず")
        } catch (e: ResponseTooLargeException) {
            assertEquals(5, e.limitBytes)
        }
    }

    @Test fun readLimited_countsBytesNotChars() {
        val s = "天気のこたえ" // 6文字 × 3バイト = 18バイト
        val bytes = s.toByteArray(Charsets.UTF_8).size
        assertEquals(s, readLimitedText(streamOf(s), bytes))
        try {
            readLimitedText(streamOf(s), bytes - 1)
            fail("バイト単位の上限で例外になるはず")
        } catch (e: ResponseTooLargeException) {
            // expected
        }
    }

    private fun gzipped(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    @Test fun readLimited_gzipBombIsStoppedAfterDecompression() {
        // 2MB のゼロは gzip では数KB。上限は「解凍後」に効くこと。
        val gz = gzipped(ByteArray(2 * 1024 * 1024))
        assertTrue(gz.size < 100_000)
        try {
            readLimitedText(GZIPInputStream(ByteArrayInputStream(gz)), 1 * 1024 * 1024)
            fail("解凍後の上限超過で例外になるはず")
        } catch (e: ResponseTooLargeException) {
            // expected
        }
    }

    @Test fun readLimited_gzipUnderLimitReadsFine() {
        val gz = gzipped("forecast".toByteArray(Charsets.UTF_8))
        assertEquals(
            "forecast",
            readLimitedText(GZIPInputStream(ByteArrayInputStream(gz)), 1024)
        )
    }

    // ---------- splitTimeouts (#4) ----------

    @Test fun splitTimeouts_knownValues() {
        assertEquals(10_000 to 25_000, splitTimeouts(35_000))
        assertEquals(10_000 to 10_000, splitTimeouts(20_000))
        assertEquals(10_000 to 1_000, splitTimeouts(11_000))
        assertEquals(4_000 to 1_000, splitTimeouts(5_000))
        assertEquals(1_000 to 1_000, splitTimeouts(2_000))
        // 残りが小さすぎる場合は 2 秒に切り上げて最低限を確保する
        assertEquals(1_000 to 1_000, splitTimeouts(0))
        // 残りが大きくても各フェーズの上限（10秒／25秒）は超えない
        assertEquals(10_000 to 25_000, splitTimeouts(100_000))
    }

    @Test fun splitTimeouts_sumNeverExceedsRemaining() {
        for (rem in listOf(2_000L, 3_000L, 7_500L, 12_345L, 20_000L, 34_999L, 35_000L)) {
            val (c, r) = splitTimeouts(rem)
            assertTrue("sum ${c + r} <= $rem", c + r <= rem)
            assertTrue("connect >= 1000", c >= 1_000)
            assertTrue("read >= 1000", r >= 1_000)
            assertTrue("connect <= 10000", c <= 10_000)
            assertTrue("read <= 25000", r <= 25_000)
        }
    }

    // ---------- isDeterministicFailure (#5) ----------

    @Test fun statusClassification() {
        assertTrue(isDeterministicFailure(400))
        assertTrue(isDeterministicFailure(403))
        assertTrue(isDeterministicFailure(404))
        assertTrue(isDeterministicFailure(499))
        // 一時的な 4xx はリトライ対象
        assertFalse(isDeterministicFailure(408))
        assertFalse(isDeterministicFailure(429))
        // 4xx 以外もリトライ対象
        assertFalse(isDeterministicFailure(500))
        assertFalse(isDeterministicFailure(503))
        assertFalse(isDeterministicFailure(302))
        assertFalse(isDeterministicFailure(200))
    }

    // ---------- parseRetryAfterMs (#5) ----------

    @Test fun retryAfter_secondsForm() {
        assertEquals(5_000L, parseRetryAfterMs("5", 1_000_000L))
        assertEquals(0L, parseRetryAfterMs("0", 1_000_000L))
        assertEquals(120_000L, parseRetryAfterMs(" 120 ", 1_000_000L))
    }

    @Test fun retryAfter_noHint() {
        assertNull(parseRetryAfterMs(null, 0L))
        assertNull(parseRetryAfterMs("", 0L))
        assertNull(parseRetryAfterMs("   ", 0L))
        assertNull(parseRetryAfterMs("abc", 0L))
        assertNull(parseRetryAfterMs("-3", 0L))
    }

    @Test fun retryAfter_httpDateForm() {
        val now = 1_763_000_000_000L
        val future = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now + 30_000), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val parsed = parseRetryAfterMs(future, now)
        assertTrue("date form lands near +30s: $parsed", parsed != null && parsed in 25_000..30_000)
        val past = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now - 60_000), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(0L, parseRetryAfterMs(past, now))
    }

    // ---------- retryWaitMs (#5) ----------

    @Test fun retryWait_values() {
        // ヒントなし → 既定バックオフ（残りに余裕がある場合のみ）
        assertEquals(1_000L, retryWaitMs(null, 5_000))
        assertNull(retryWaitMs(null, 1_000))
        assertNull(retryWaitMs(null, 500))
        // ヒントあり → そのまま（上限キャップ 10 秒）
        assertEquals(3_000L, retryWaitMs(3_000, 5_000))
        assertEquals(0L, retryWaitMs(0, 5_000))
        assertEquals(10_000L, retryWaitMs(60_000, 50_000))
        // 待ちが残り予算を食い尽くすなら待たずに打ち切る
        assertNull(retryWaitMs(30_000, 5_000))
        assertNull(retryWaitMs(10_000, 10_000))
    }

    // ---------- fetchWithBudget のループ挙動 (#4/#5/#2) ----------

    @Test fun budget_deterministic403StopsAtOnce() {
        var attempts = 0
        try {
            fetchWithBudget(budgetMs = 10_000, maxAttempts = 3) { _, _, _ ->
                attempts++
                throw HttpStatusException(403, "HTTP 403")
            }
            fail("403 が伝播するはず")
        } catch (e: HttpStatusException) {
            assertEquals(403, e.code)
        }
        assertEquals(1, attempts)
    }

    @Test fun budget_429IsRetried() {
        var attempts = 0
        val result = fetchWithBudget(budgetMs = 10_000, maxAttempts = 3) { _, _, _ ->
            attempts++
            if (attempts == 1) throw HttpStatusException(429, "HTTP 429", retryAfterMs = 0)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test fun budget_408IsRetried() {
        var attempts = 0
        val result = fetchWithBudget(budgetMs = 10_000, maxAttempts = 3) { _, _, _ ->
            attempts++
            if (attempts == 1) throw HttpStatusException(408, "HTTP 408", retryAfterMs = 0)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test fun budget_retryAfterBeyondBudgetStops() {
        var attempts = 0
        try {
            fetchWithBudget(budgetMs = 5_000, maxAttempts = 3) { _, _, _ ->
                attempts++
                throw HttpStatusException(429, "HTTP 429", retryAfterMs = 60_000)
            }
            fail("429 が伝播するはず")
        } catch (e: HttpStatusException) {
            assertEquals(429, e.code)
        }
        // 60 秒のヒントはキャップ後の 10 秒でも残り予算を超える → 打切り
        assertEquals(1, attempts)
    }

    @Test fun budget_transientIoFailureIsRetried() {
        var attempts = 0
        val result = fetchWithBudget(budgetMs = 10_000, maxAttempts = 3) { _, _, _ ->
            attempts++
            if (attempts == 1) throw IOException("boom")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test fun budget_cancelledBeforeStart() {
        var attempts = 0
        try {
            fetchWithBudget(
                budgetMs = 10_000, maxAttempts = 3,
                isCancelled = { true }
            ) { _, _, _ ->
                attempts++
                "ok"
            }
            fail("キャンセル例外になるはず")
        } catch (e: FetchCancelledException) {
            // expected
        }
        assertEquals(0, attempts)
    }

    @Test fun budget_cancelledBetweenAttempts() {
        var attempts = 0
        try {
            fetchWithBudget(
                budgetMs = 10_000, maxAttempts = 3,
                isCancelled = { attempts >= 1 }
            ) { _, _, _ ->
                attempts++
                throw IOException("boom")
            }
            fail("キャンセル例外になるはず")
        } catch (e: FetchCancelledException) {
            // expected: 失敗をデータ源の失敗として扱わず、世代交代として捨てる
        }
        assertEquals(1, attempts)
    }

    @Test fun budget_attemptTimeoutsComeFromSplit() {
        val seen = mutableListOf<Pair<Int, Int>>()
        fetchWithBudget(budgetMs = 20_000, maxAttempts = 1) { c, r, _ ->
            seen.add(c to r)
            "ok"
        }
        assertEquals(listOf(10_000 to 10_000), seen)
        seen.clear()
        fetchWithBudget(budgetMs = 35_000, maxAttempts = 1) { c, r, _ ->
            seen.add(c to r)
            "ok"
        }
        assertEquals(listOf(10_000 to 25_000), seen)
    }
}
