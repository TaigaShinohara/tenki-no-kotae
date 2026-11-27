# 検証基盤（実機なし検証の標準化＋ビルド直列化）

2026-11-23、本人の決定による全プロジェクト共通標準2件を組み込んだ記録。
バージョン番号は上げない（検証基盤の追加のみ）。

## 1. ビルドの直列化（AGENTS.md「Build serialization rule」）

- `build.sh` の実ビルドを共通ラッパー `~/workspace/tools/with-build-lock.sh`
  （flock 2枠＝`.build.lock.slot1` / `.slot2`、待ち上限3600秒・タイムアウト時 exit 75）
  経由に変更した。ミラーの起動確認はロックの外（ビルドではないため）。
- これで本プロジェクトのビルドは、他プロジェクトのビルドと同時には走らない。

## 2. 実機なし検証（AGENTS.md「Pre-delivery UI verification standard」）

このVMにはKVMが無くエミュレータは実用不可のため、交付前にJVM上で画面を検証する。
本アプリは素のView製・単一Activityなので、相性の良い **Robolectric 4.14.1 +
Roborazzi 1.29.0**（ネイティブグラフィックス）を採用した。

### 構成の変更

- ルート `build.gradle.kts`：Roborazziプラグイン 1.29.0 を宣言。
- `app/build.gradle.kts`：プラグイン適用、`testOptions.unitTests.isIncludeAndroidResources = true`、
  testImplementation に robolectric / roborazzi / roborazzi-junit-rule を追加。
- ゴールデン画像：`app/src/test/roborazzi/`（リポジトリにコミット）。

### テストの内容

| クラス | 種別 | カバー範囲 |
|---|---|---|
| `MainActivityScreenshotTest` | スクリーンショット（ゴールデン比較※） | 起動直後の画面（ホーム）、数値で見る（詳細）、設定、見た目テスト |
| `PersonFigureScreenshotTest` | スクリーンショット（Roborazzi完全一致） | 「今日の人」の傘まわり：日傘ランク2（v0.1.33で作り直した組み合わせ）、日傘ランク5全身、雨傘ランク1、レインコート ランク3、うちわ ランク0（v0.1.31） |
| `MainActivityNavigationTest` | Robolectric遷移テスト | タブ遷移（ホーム⇔詳細⇔設定）で内容が入れ替わること、各画面で高さ0崩壊がないこと、設定⇔見た目テストの往復 |

※ MainActivity 全画面のゴールデン比較は `ScreenshotSupport.captureGolden`
（空の揺らぎだけを許容する比較）。理由と閾値は「実行結果」の所見に記す。
フィギュア単体は Roborazzi の完全一致照合のまま。

- 本アプリにオンボーディングは無いため、「オンボーディング入口」に相当する
  起動直後の画面はホーム（キャッシュ表示）でカバーする。
- 決定的な描画のため、時計を 10:00（Asia/Tokyo）にピン留めし、
  SharedPreferences `tenki_prefs` に新鮮な Open-Meteo キャッシュ
  （晴れ・UV 9.2・傘なし＝日傘日）をシードしてから起動する。
  起動時のバックグラウンド取得が完了するまで待ってから操作する
  （取得失敗時の再描画がテスト中の画面を上書きしないため）。
  ネットワーク・読み込みスピナー・導入演出に依存しない。
- ゴールデン比較の前に、ビットマップの「真っ白・単色でないこと」
  （色数と支配色の割合）を全画面で assert する（高さ0で真っ白になる
  崩壊をゴールデン無しでも捕まえるため）。

### このVMでの実行方法

Gradle の `testDebugUnitTest` はこのVMでは実行プロセスが起動直後に
異常終了する（Gradleプロセスとのループバック通信が傍受されるため。
従来からのプロジェクト慣行どおり）。そのため実行は:

```bash
tools/run-unit-tests.sh          # 全テスト（ゴールデン照合モード）
tools/run-unit-tests.sh record   # ゴールデンの再記録
tools/run-unit-tests.sh verify com.taigashinohara.tenkinokotae.MainActivityScreenshotTest
```

- ランナーは build.sh（ロック経由）でコンパイルし、
  `tools/test-classpath.init.gradle` で Gradle が使うはずのテスト
  クラスパスをそのまま取り出して JUnitCore を直接実行する。
- Robolectric の android-all は Java のネットワークが傍受される
  このVMでは自動ダウンロードできないため、curl で
  `.local/robolectric-deps/`（gitignore対象）に取得し、
  `-Drobolectric.offline=true -Drobolectric.dependency.dir=...` で渡す。
  ランナーが不在時に自動取得する。
- 通常のマシンでは `./gradlew testDebugUnitTest` /
  `verifyRoborazziDebug` がそのまま使える。

### 実行結果（2026-11-23）

- **全127件合格**（`tools/run-unit-tests.sh verify`、RUNNER_EXIT=0）。
  内訳：既存単体テスト116件＋新規11件（スクリーンショット9＝全画面4＋
  フィギュア5、遷移2）。スクリーンショットクラス単独の verify でも
  別途合格しており、全件実行と単独実行の両方で再現する。
- ゴールデン9枚は全て目視確認済み（ホーム＝結論文＋日傘の人＋回答
  チップ＋タブ、詳細＝体感温度・UV最大9.2・グラフ、設定＝地点チップ等、
  フィギュア＝v0.1.34の幅広浅ドーム日傘ほか）。真っ白・崩壊なし。
- build.sh のロック経由ビルドも同ランナー内で毎回通っている。
  横断展開の最中は他プロジェクトのビルドと重なり、ロック待ちが
  数分〜十数分になることがあった（直列化が効いている証拠でもある）。

#### 所見（このアプリ×このVMで分かったこと）

1. **Robolectric の一時停止クロックは後ろに戻せない。**
   `ShadowPausedSystemClock.setCurrentTimeMillis` は現在値より小さい
   値を渡すと false を返して無視する（4.14.1 のバイトコードで確認）。
   夕方に当日10:00へ固定する指定は黙って効いていなかった。対策として
   ピンは「今日の10:00、過ぎていれば翌日の10:00」へ前方に取り、
   キャッシュの保存時刻（`cache_at_大阪`）もピン値に合わせた。
   **（2026-11-28 追記）この対策は superseded。** 実測の結果、
   アプリの `System.currentTimeMillis` / `LocalDate.now` には
   SystemClock ピンがそもそも届いておらず（夜間テストの
   実時刻依存として顕在化）、現在は MainActivity.nowMillis
   シーム＋固定日付（2026-11-25）ピンが正本。
   docs/FIX_TEST_CLOCK.md を参照。cache_at は実時計で打つ。
2. **それでも全画面のピクセル完全一致は成立しない。** SkyView は
   太陽・月の位置を「描画時点の壁時計（秒精度）」から決める
   （`SkyView.nowMinutes()`）。ピンはできてもクロックはスケジューラを
   回すたびに秒単位で前へ drift するため、太陽のグロー（大きな
   RadialGradient）が毎回 ±1レベル（最大Δ≈13）で再サンプルされる。
   実測では差分は空の帯（上部30%）の中だけ・全体の約1.2%の画素に
   限られた。そこで全画面4枚だけ `ScreenshotSupport.captureGolden`
   の許容比較にした：単画素Δ>40は不合格／Δ>4の画素は全体の3%以下
   かつ空の帯の中だけ／Δあり画素は5%以下。空より下（本文・図形・
   チップ・タブ）は実質完全一致で、崩壊・見切れ・文言変化は
   Δ100超の広域差分になるため確実に捕まる。フィギュア5枚は
   時計に依存しないため Roborazzi 完全一致のまま全て合格している。
3. **JUnitCore 直接実行には `--add-opens` が要る。** Gradle の
   テストワーカーが自動で付けるモジュール開放が無いと、ゴールデン
   PNG の読み書きで Robolectric の FileDescriptor インターセプタが
   `InaccessibleObjectException` を投げる。ランナーの java 起動に
   推奨一式（java.io / java.lang / java.nio / java.util 等）を追加済み。
4. 統括注意の lint 件（`lintVitalAnalyzeRelease` の "37.2"）は、
   本作業ではリリースビルドを実行していないため未遭遇。
