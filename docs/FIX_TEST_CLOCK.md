# FIX — 画面テストの実時刻依存を解消（2026-11-28）

## 問題

画面テスト `home_nightShowsTomorrow`（19時以降はホームが明日表示に
なる検証）が実行実時刻に依存し、19:00〜24:00 の実行でしか通らなかった
（FEATURE_v0.1.38 の残課題）。同じ理由で home.png ゴールデンは
「夕方に記録された夜間モード画面」になっており、詳細画面の
「いまから」窓も実時計の「時」境界で揺れていた。

## 実測した原因（使い捨てプローブで確認）

この VM の直接 JUnitCore 実行では、Robolectric の時計制御が
アプリの読む時計に届かない：

- `SystemClock.setCurrentTimeMillis` は true を返し
  `SystemClock.uptimeMillis` はピンに従う。
- しかしアプリが読む `System.currentTimeMillis()` と
  `LocalDate.now()` は実時刻のまま（ShadowSystem.currentTimeMillis
  は uptimeMillis を返す実装だが、この実行形態ではアプリ側の
  呼び出しがそこへルーティングされない）。
- スケジューラの advanceTo でも動かない。

つまり従来の SystemClock ピンは、アプリの夜間切替
（MainActivity.nowHourTokyo → System.currentTimeMillis）と
日付（todayStr → LocalDate.now）に対して無力だった。

## 採用した固定方式

**アプリ側のテストシーム＋固定日付ピン**（テスト専用 Clock 抽象）：

- MainActivity に SkyView.clockMillis と同じ型のシーム
  `internal var nowMillis: () -> Long`（companion）を追加。
  既定は `System::currentTimeMillis` で、実挙動は不変。
  画面ロジックの「いま」読み取り口である4関数
  （nowHourTokyo / nowMinutesFloat / todayStr / shiftedStr）を
  このシーム経由に集約した。キャッシュの鮮度判定・保存時刻は
  実時計同士で比較される値なので、意図的にシームの対象外
  （従来どおり実時計を直接読む）。
- テスト側（ScreenshotSupport）は固定日付 **2026-11-25** を
  基準に、昼 10:00／夕方 17:50／夜 20:00 のピンをシームへ流す
  （repinClock / repinDuskClock / repinNightClock）。固定「日付」
  にしたことで、キャッシュフィクスチャ・アプリの今日/明日選択・
  夜ゴールデンの日付ラベル（「明日 11/26（木）」）まで全て
  実行日に依らなくなった。
- シードする cache_at は実時計のまま（鮮度判定が実時計で
  行われるため。凍結すると 24h 窓を過ぎてキャッシュが
  弾かれる）。
- SystemClock ピンはスケジューラ向けに従来どおり併記するが、
  断言の根拠はシーム側に移った。

## 検証（実時刻はいずれも従来落ちていた 19時より前の帯）

- 修正前（実時刻 03:06）： MainActivityScreenshotTest は
  5件全滅（夜テストはラベル断言で失敗）。
- ゴールデン再記録（フルスイート文脈、実時刻 03:1x 台）：
  **全144件合格**。home.png は本来の昼（今日表示）画面に、
  home_night.png のラベルは固定日付の「明日 11/26（木）」に
  なったことを目視確認。
- フルスイート verify（実時刻 03:19〜03:23）： **全144件合格**。
- 対象クラス単独 verify（実時刻 03:23〜03:27）： **5件全合格**
  （従来は単独実行でも ±1 の全面揺らぎで落ちていたが、
  空の焼き込みがシーム由来になったため解消）。

## 変更ファイル

- `app/src/main/java/.../MainActivity.kt`（シーム追加＋4関数の
  経由化のみ。版上げなし・APK 再交付なし）
- `app/src/test/java/.../ScreenshotSupport.kt`、
  `MainActivityScreenshotTest.kt`、`MainActivitySkyVariationsTest.kt`、
  `MainActivitySkyDuskTest.kt`、`MainActivityNavigationTest.kt`
- ゴールデン再記録： `app/src/test/roborazzi/` の画面系9枚
  （フィギュア系は不変）
