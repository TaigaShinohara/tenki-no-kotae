# 修正記録 — 天気のこたえ v0.1.11

実施： 2026-11-19。対象は `docs/BUG_REVIEW_v0.1.10.md` の重大1件＋中・確実7件（計8件）。
小・疑い分、Rules の閾値・文言、デザインには触れていない。
versionName 0.1.11 / versionCode 12。署名は従来と同一
（SHA-256: FC:F7:43:C2:38:8C:8A:E9:F5:9F:69:2B:49:75:5F:47:9A:05:06:20:8D:BF:B7:A5:6E:E2:D3:6D:80:FE:F8:3F、上書きインストール可）。

検証： 単体テスト 38件 全合格（従来方式＝コンパイル＋JUnit 手動実行）。
ビルドは build.sh（Gradle 8.9・in-process）で assembleDebug／assembleRelease とも成功。
納品APKは release ビルド（前版までの納品慣行どおり）。

---

## 1.予備源の「今日」は部分日なのに、判定は完全な24時間前提 → 解消

**設計判断（部分日の扱い）**： 「無い時間帯をごまかさず、実在する時間だけで判定し、
ごまかせないものは名乗りを変えるか、前日比を作らない」。

- `Rules.daytimeAvg`： 9〜17時にデータがあれば実在分だけの平均。1件も無い日は
日中の平均のふりをせず、残っている時間全部の平均へ明示的に切り替える
（呼び出し側は `Rules.hasDaytimeHours` で区別できる）。
- `Rules.matchedDaytimeAvgs`（新設）： 前日比は両日に共通して実在する 9〜17時の
時間だけで平均して比べる。共通時間が無ければ null を返し、`Rules.conclusion` は
前日比を作らず絶対評価（`conclusionNoYesterday`）へ切り替える。`tomorrowLine` も同じ物差し。
- `Rules.umbrella`： 窓（7〜18時）が空の日は「雨の心配なし」と決めつけず、
残っている時間で判定し直す。
- `Rules.isPartialDay`（新設）で部分日を判定し、詳細画面では
「最高・最低は、取得できた時間の範囲での値です」と明示。ヒーローの見出しも
日中データが無い日は「体感温度（いま以降の平均）」に変わる。
- 根拠コード： Rules.kt（`hasDaytimeHours` / `isPartialDay` / `matchedDaytimeAvgs` /
`conclusion` / `umbrella`）、MainActivity.kt `showDetail` の hero ラベルと部分日注記、
`showHome` の結論は `Rules.conclusion` 経由。

## 2.気圧の「6時間低下」がインデックス＝時刻前提／未測定を 0.0 と表示 → 解消

- `Rules.pressure` を実時刻ベースに変更： 時刻 h と h+6 の両方が実在するペアだけで測る。
- `PressureAdvice.dropMeasured` を追加。6時間幅が取れない日は「未測定」として区別し、
詳細画面は「6時間の変化は未測定」「未測定（6時間分のデータがありません）」と表示する
（測れた 0.0 とは表示が違う）。時間帯の文言（timingNote）も実時刻の終了時刻から決まる。
- 根拠コード： Rules.kt `pressure`（`byHour[h + 6]` のペア走査と `dropMeasured`）、
MainActivity.kt `showDetail` の気圧カード／折りたたみ行／読み上げ文。

## 3.loadWeather の多重起動・世代管理なし → 解消

- `@Volatile loadGeneration` を導入し、`loadWeather` の呼び出しごとに世代を進める。
取得スレッドは自分の世代が最新でなくなったら、キャッシュ書き込みも UI 更新もせず破棄する
（主源・予備源・キャッシュ・エラー表示の全経路に `current()` チェック）。
- 根拠コード： MainActivity.kt `loadWeather`（`val gen = ++loadGeneration` と各経路の `if (!current())`）。

## 4.署名のサイレントフォールバック → 解消

- `app/build.gradle.kts`： `.local/signing.properties` または鍵ファイルが無ければ
`GradleException` でビルド自体を失敗させる。既定デバッグ鍵で黙って署名される経路は廃止。
- 根拠コード： build.gradle.kts 冒頭の `if (!hasPersistentSigning) throw GradleException(...)`。

## 5.テストの穴 → 解消

- WeatherApi.parse のテストを新設（配列の対応付け・時刻文字列の解析・daily 値の由来）。
降水確率に JSON null を混ぜたフィクスチャで null と数値の混在も固定。
- 部分日フィクスチャを新設（`PartialDayTest` 10件）： 実在時間だけの平均、日中なしの日の
絶対評価、同じ時間同士の前日比、傘の窓なし判定、気圧の実時刻・未測定を固定。
- MetApiTest の実サンプル検証は `assumeTrue` の無言スキップをやめ、サンプルを
`src/test/resources/met_complete_sample.json` へ移して常時実行＋ `assertNotNull` に変更。
- テスト総数： 26件 → **38件**（RulesTest 16／FallbackRulesTest 4／WeatherApiTest 4（+2）／
MetApiTest 4／PartialDayTest 10（新規））。既存テストの変更は MetApiTest の
サンプル読み込み方式のみ（前提が変わったため）で、他は無変更で合格。

## 6.回転・ダークモード切替で状態が消える → 解消

- **方式**： AndroidManifest の MainActivity に
`android:configChanges="orientation|screenSize|uiMode"` を宣言し、再生成自体を止める。
取得済み予報・現在のタブ・折りたたみ状態はフィールドに保持されるためそのまま残る。
- ダークモード切替は `onConfigurationChanged` で受け、色だけが古くなる問題には
現在の画面をその場で再構築して対処（詳細のスクロール位置は `detailScrollY` で引き継ぐ）。
純粋な回転では階層に触らないのでスクロールもそのまま。
- 根拠コード： AndroidManifest.xml、MainActivity.kt `onConfigurationChanged` / `lastNightMode`。

## 7.全滅時のエラーまで最悪約230秒 → 解消

- 共有の `fetchWithBudget`（WeatherApi.kt）を新設： データ源ごとに壁時計の予算を持ち、
各試行のタイムアウトは残り予算で頭打ちにする。主源＝予算35秒・最大3回、
予備源＝予算20秒・最大2回。合計の目安は約55秒。
- 決定的な失敗（HTTP 4xx）は `HttpStatusException` で区別し、その場でリトライを打ち切って
次のデータ源へ進む（実際に踏んだ 403 は即フォールバックになる）。
- 根拠コード： WeatherApi.kt `fetchWithBudget` / `HttpStatusException` / `fetchText`、
MetApi.kt `fetchText`。
- 残る注記： 1回の試行は「接続＋読み取り」の合計が残り予算を最大で約10秒超え得るため、
全タイムアウトを使い切る理論上の最悪は合計65〜70秒程度。従来の約230秒からは大幅に短縮。
4xx の決定的失敗では即座に切り替わる。

## 8.詳細の折りたたみでスクロールが先頭に戻る → 解消

- 折りたたみの開閉で画面を再構築する前に `detailScrollY` へ現在位置を保存し、
再構築後に `sv.post { sv.scrollTo(0, y)}` で復元する。他タブ表示時と新規読み込み時はリセット。
- 根拠コード： MainActivity.kt の折りたたみヘッダーの `setOnClickListener` と
`showDetail` 末尾の復元処理。

---

## 残った制約・実機でしか分からない点

- 回転・ダーク切替の保持はコードとマニフェストで担保したが、実機での動作確認は未実施
（回転時に空のアニメーションやスクロールが実際にどう見えるかは実機待ち）。
- 全滅時の待ち時間短縮は予算ロジックの設計値であり、実回線での実測は未実施。
- 予備源モードの夕方表示（部分日の注記や「いま以降の平均」の見え方）は実機未確認。
- BUG_REVIEW の小・疑い分（13〜25）は本版の対象外で未着手のまま。
