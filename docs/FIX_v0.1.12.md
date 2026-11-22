# 修正記録 — 天気のこたえ v0.1.12

実施： 2026-11-20。対象は `docs/FETCH_REVIEW_v0.1.11.md` の「中・確実」6件（1〜6）。
小・疑い分、Rules の閾値・文言、デザインには触れていない。
versionName 0.1.12 / versionCode 13。署名は従来と同一
（SHA-256: FC:F7:43:C2:38:8C:8A:E9:F5:9F:69:2B:49:75:5F:47:9A:05:06:20:8D:BF:B7:A5:6E:E2:D3:6D:80:FE:F8:3F、
apksigner で v0.1.11 の納品APKと一致を確認済み、上書きインストール可）。

検証： 単体テスト **59件 全合格**（従来方式＝コンパイル＋JUnit 手動実行）。
内訳は従来 38件（無変更で合格）＋新規 `FetchSupportTest` 21件。
ビルドは build.sh（Gradle 8.9・in-process）で assembleDebug／assembleRelease とも成功。
納品APKは release ビルド（従来の納品慣行どおり）、747,452 バイト。

---

## 1.応答の読み取りと gzip 解凍にサイズ上限がない → 解消

- 純粋ヘルパー `readLimitedText(InputStream, maxBytes)` を新設（WeatherApi.kt）。
  バイト単位で数え、上限を超えた時点で `ResponseTooLargeException`（IOException）を投げる。
- `httpGetText` の成功経路は解凍後ボディ上限 **10MB**、エラー本文は **8KB** まで読みに変更。
  gzip はラッパーの後段で数えるため、解凍爆弾（小さい圧縮体→巨大な展開）も解凍後の量で止まる。
  上限超過は取得失敗として扱われ、従来どおり次の手段（予備源→キャッシュ→エラー）へ進む。
- 根拠コード： WeatherApi.kt `readLimitedText` / `MAX_BODY_BYTES` / `MAX_ERROR_BODY_BYTES` /
  `httpGetText`。テスト： FetchSupportTest の readLimited 系 6件（上限ちょうど／超過／
  バイト単位の数え方／gzip 爆弾／gzip 正常系）。

## 2.旧世代の取得スレッドが止まらない・連打で積み上がる → 解消

- 世代ごとの接続レジストリ `liveConnections`（MainActivity）を新設。各試行は開始前に
  自分の HttpURLConnection を登録し（httpGetText の `onConnection` フック経由）、
  `loadWeather` は新世代の開始時に旧世代の接続をすべて `disconnect()` する。
  切断された読み取りは即座に例外で終わるため、旧世代は予算を使い切らずに止まる。
  登録時点で既に旧世代だった接続はその場で切断する。
- 試行間・パース前の世代チェック： `fetchWithBudget` が `isCancelled` を試行間と
  各 catch で確認し、世代交代を `FetchCancelledException` として区別（データ源の失敗と
  混同しない）。MainActivity 側も各段階とパース直前に `current()` を確認する。
- 連打ガード： `loadInFlight` フラグを追加。取得中の再試行ボタン等からの loadWeather は
  無視される。地点変更は `force = true` で新世代として旧世代を止めてから開始する。
- 根拠コード： MainActivity.kt `liveConnections` / `registerConnection` / `stopGeneration` /
  `loadWeather(force)`、WeatherApi.kt `fetchWithBudget` の `isCancelled` /
  `registerConnection` 伝播、WeatherApi.kt・MetApi.kt の `fetchText` 引数追加。
  テスト： FetchSupportTest の budget_cancelled 系 2件。

## 3.キャッシュは失敗時の代役専用で、開くたびに全量再取得 → 解消

- `loadWeather` が現在の都市のキャッシュ（24時間以内、Open-Meteo キャッシュ→MET
  キャッシュの順、従来の代役と同じ鮮度・同じキー）を先に表示するようにした。
  キャッシュがある場合はスピナーを出さず即表示し、裏で通常の取得を行い、成功したら
  差し替える。キャッシュ表示時も差し替え時もイントロの段階表示は再生しない。
- 裏の取得が全滅した場合はキャッシュ表示を維持し、その時だけホームに
  「前回のデータを表示しています」の控えめな注記を出す（`staleCache` フラグ。
  取得中は出ない。新データが届けば消える）。都市が違うキャッシュはキー構造上
  読まれない（従来どおり都市名キー）。
- 根拠コード： MainActivity.kt `loadWeather` / `showCachedForecast` /
  `runFetch` の `finishWithFailure`、`showHome` の `staleCache` 注記。

## 4.予算の数え方（1試行が残り予算を最大約2倍まで伸びる） → 解消

- 純粋関数 `splitTimeouts(remainingMs)` を新設： connect = min(10秒, 残り−1秒)、
  read = min(25秒, 残り−connect)。**接続＋読み取りの合計が残り予算を超えない**
  （残り2秒未満は2秒に切り上げて最低1秒ずつを確保、各フェーズの上限10秒／25秒は維持）。
- `fetchWithBudget` は全試行でこの分割を使い、待ち時間（バックオフ／Retry-After）も
  同じ壁時計予算から引く。主源予算35秒・予備源20秒は不変。
- **新しい理論最悪値**： 全滅時は主源35秒＋予備源20秒＝約55秒の通信時間＋パース分
  （数百ミリ秒〜数秒）でエラー画面またはキャッシュ注記に到達する。旧版の
  「試行ごとの食い破りで最大約65〜80秒」は構造的に解消した。なおヘッジ（6）が
  効く日は予備源が主源と並行に走るため、体感の上限はさらに短い（主源の予算が上限）。
- 根拠コード： WeatherApi.kt `splitTimeouts` / `fetchWithBudget`。
  テスト： FetchSupportTest の splitTimeouts 系 2件（既知の値＋合計≤残りの性質）と
  budget_attemptTimeoutsComeFromSplit。

## 5.429／408 も即打ち切り、Retry-After を読まない → 解消

- 純粋関数 `isDeterministicFailure(code)` を新設： 決定的な 4xx から **408 と 429 を
  除外**し、それ以外（5xx・3xx 含む）も含めて「リトライ可能／決定的」を分類する。
  `fetchWithBudget` は決定的な失敗でのみ即打ち切りし、408／429 は予算内なら再試行する。
- `Retry-After` ヘッダを読むようにした（`httpGetText` が
  `HttpStatusException.retryAfterMs` に載せる）。解釈は純粋関数
  `parseRetryAfterMs`（秒形式と HTTP-date 形式の両対応、過去日付は 0）。
  待ち時間の決定は純粋関数 `retryWaitMs`： ヒントを尊重しつつ **上限10秒** に
  キャップし、待ちが残り予算を食い尽くす場合は待たずに打ち切って次のデータ源へ進む。
  ヒントなしの既定バックオフ（1秒）も同じ規則で予算に収める。
- 根拠コード： WeatherApi.kt `isDeterministicFailure` / `parseRetryAfterMs` /
  `retryWaitMs` / `RETRY_AFTER_CAP_MS`、`httpGetText` の Retry-After 取得、
  `HttpStatusException.retryAfterMs`。
  テスト： FetchSupportTest の statusClassification、retryAfter 系 3件、retryWait、
  budget_429IsRetried、budget_408IsRetried、budget_retryAfterBeyondBudgetStops、
  budget_deterministic403StopsAtOnce。

## 6.フォールバックが完全直列で、主源が遅いと予備源が始まらない → 解消

- `loadWeather` の取得部を `runFetch`（編成スレッド）に分離し、主源は専用スレッドで
  走らせる。主源が **8秒**（`hedgeDelayMs`）経っても終わらない場合、予備源を並行開始
  する（ヘッジ）。先に成功した方を表示する。
- 予備が先に勝った場合も主源は止めない： 主源が成功したら表示を主源データに差し替え
  （イントロ再生なし。「予備のデータで表示しています」注記は backupMode の切替で
  消える）＋主源キャッシュを保存する。主源が先に勝った場合は予備を裏で最後まで
  流し（上限25秒）、成功応答は予備キャッシュとして保存する（画面には触れない）。
- 表示の競合防止： 表示更新はすべて編成スレッドからの runOnUiThread に一本化し、
  世代チェック＋「表示済み（shown）」フラグで二重表示・古い世代の上書きを防ぐ。
  Toast はこの経路では使わない（キャッシュ全滅時は注記のみ）ため二重表示は起きない。
  全滅時は従来どおりキャッシュ表示の維持（3）→エラー画面の順。
- 根拠コード： MainActivity.kt `runFetch`（primaryDone / eitherDone / backupDone の
  ラッチ編成、showPrimary / showBackup / finishOk / finishWithFailure）。

---

## 残った制約・実機でしか分からない点

- ヘッジとキャッシュ先行表示の実際の挙動（8秒での予備開始、予備→主源の差し替えの
  見え方、注記の出入り）はコード上の編成で担保したが、実機確認は未実施。
- 待ち時間の新しい理論最悪値（約55秒＋パース分）は予算ロジックからの計算値であり、
  実回線での実測は未実施。DNS 解決のぶら下がり（レビュー #18）など、予算の外側の
  要因は従来どおり残る。
- disconnect による旧世代の中断は HttpURLConnection の仕様上の効果（読み取り中の
  スレッドが例外で起きる）に依拠する。実機の回線・端末での効き方は未確認。
- FETCH_REVIEW の小・疑い分（7〜20）は本版の対象外で未着手のまま。
