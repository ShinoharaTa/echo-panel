# Echo Panel

LineageOS 18.1 を入れた Amazon Echo Show 5 第2世代 (cronos) を、机の上の情報パネルとして
使うためのアプリ。古い Android 端末を横向きに置く用途でも動く。

## なぜアプリにしたか

Fully Kiosk + Web ページの構成でも見た目は作れるが、次の3つがどうしても解決できなかった。

1. **無操作時のスクリーンセーバーがタイマーを覆う。** ポモドーロ動作中に画面が時計に
   切り替わると残り時間が見えなくなる。Web からは自分がスクリーンセーバーになれない。
2. **鳴らせない。** 覆われた WebView から確実に音を出す手段がない。
3. **`navigator.wakeLock` が使えない。** セキュアコンテキスト必須のため
   `http://10.1.111.145:8123` のような平文 HTTP 配信では利用できない。

アプリなら `DreamService` を実装して**自分がスクリーンセーバーになれる**ので、
1 は「時計の下に残り時間を出す」だけで解決する。2 は `AlarmManager` +
フォアグラウンドサービス。3 は不要になる。

さらに `category.HOME` を宣言できるので、ホームアプリにすれば
Fully Kiosk の Launch on Boot と `SYSTEM_ALERT_WINDOW` の付与も不要になる。

## 設計

- **タイマーは残り時間を持たない。** 保存するのは終了時刻 (`endAt`) だけで、
  残りは常に `endAt - now` で計算する。プロセスが死んでも画面が覆われても
  復帰した瞬間に正しい値になる。`Pomodoro.kt` を参照。
- **配色はモノクロのみ。** 値は `ui/Tokens.kt` に集約。直値を書かない。
- **HA は WebView で埋め込む。** SwitchBot 操作も温湿度も HA 側が持っているので
  ここで作り直さない。端末に認証情報を置かない方針とも整合する
  (この ROM は SELinux Permissive のため)。

## 画面

横スワイプで 3 面。

| # | 面 | 内容 |
|---|---|---|
| 0 | 時計 | 大時計。5分ごとに描画位置を動かして焼き付きを避ける |
| 1 | ポモドーロ | 25/5。動作中はスクリーンセーバーにも残り時間が出る |
| 2 | Home Assistant | ダッシュボードをそのまま表示 |

## 対象環境

- Echo Show 5 2nd gen (cronos) / LineageOS 18.1 (Android 11, API 30)
- 実効サイズ **787 x 394 dp** (960x480px / 195dpi、システムバー非表示時)
- minSdk 26 なので手持ちの古い Android 端末でも動く

## ビルド

`java` が PATH にないので Android Studio 同梱の JBR を使う。

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

## 端末側の設定

```bash
D=<serial>

# スクリーンセーバーをこのアプリにする
adb -s $D shell settings put secure screensaver_components \
  "dev.shino3.echopanel/dev.shino3.echopanel.dream.PanelDream"

# ホームアプリにする (任意)
adb -s $D shell cmd package set-home-activity dev.shino3.echopanel/.MainActivity

# 元に戻す
adb -s $D shell settings put secure screensaver_components \
  "com.android.deskclock/com.android.deskclock.Screensaver"
adb -s $D shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher
```

`stay_on_while_plugged_in` を 1 以上にすると画面消灯タイマーが走らず
スクリーンセーバーに入らない。0 のままにすること。

## 未実装

- HA の URL がソース直書き (`MainActivity.kt` の `HA_URL`)
- 作業/休憩の長さが固定 (25/5)
- パネルの追加・並べ替え
