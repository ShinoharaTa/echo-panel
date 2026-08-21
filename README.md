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

- **画面構成は JSON で外に出す。** 5.5インチのタッチでレイアウトを編集するのは
  現実的でないので、Mac から `adb push` した `panels.json` を端末が読む。
  ファイルの更新時刻を3秒ごとに見て自動で再読み込みするので、push した瞬間に画面が変わる。
- **レイアウトは weight 付きの木ひとつ。** 節点はタイルかグループのどちらかで、
  グループは `dir` (row/column) と `children` を持つ。2分割も3分割も入れ子も
  同じ経路で出るので、タイルを追加するたびにレイアウト側を触らなくて済む。
- **タイマーは残り時間を持たない。** 保存するのは終了時刻 (`endAt`) だけで、
  残りは常に `endAt - now` で計算する。プロセスが死んでも画面が覆われても
  復帰した瞬間に正しい値になる。`Pomodoro.kt` を参照。
- **色は下地がモノクロ、意味だけ有彩色。** 値は `ui/Tokens.kt` に集約して直値を書かない。
  有彩色は閾値を超えたときにだけ出る (30°以上=琥珀 / 10°以下=青 / 降水50%以上=青 /
  予定15分前=琥珀 / 5分前=赤)。装飾には使わない。
- **HA が持っているものは作り直さない。** 天気も週間予報もカレンダーも外気温の履歴も
  Home Assistant が既に持っているので、気象 API やカレンダー API とは個別に繋がず
  HA の REST を1本だけ叩く。認証もトークン1つで済む。依存を増やさないため
  `HttpURLConnection` と `org.json` だけで書いてある (`data/HaClient.kt`)。
  SwitchBot 操作のような双方向の UI は `ha.web` タイルで HA のダッシュボードを
  そのまま埋める。この ROM は SELinux Permissive なので、端末に置く認証情報は少ないほどよい。

### 背景と、ガラスカード

背景 (`ui/Backdrop.kt`) はドット方眼に太陽/月の弧を重ねた 1枚の Bitmap で、
太陽は 6-18時、月は 18-6時 に弧の上を進む。月は月齢の形まで描く (`astro/Celestial.kt`)。
晴れた夜は固定シードの星、雨と雪は Compose の無限アニメで雨脚/雪片を重ねる (`PrecipLayer`)。
太陽と月が動く分、1分ごとに描き直す。天気は `weatherEntity` から10分ごとに取る。

API 30 には backdrop blur が無い。そこで背景の生成時に縮小→拡大の2パスで
**ブラー版も同時に作っておき**、各タイルが `drawBehind` で自分の位置の切片を敷いてから
60% 黒と 1px の縁を重ねる。これで「カード越しに方眼と天体が透ける」見た目になる
(`ui/tiles/TileFrame.kt`)。ブラー版がまだ無い間は不透明のフォールバックで描く。

タイル同士の見切りは余白だけで、枠の外は塗らない。ギャップから背景が覗くのが意図した絵。

## panels.json

`/sdcard/Android/data/dev.shino3.echopanel/files/panels.json`。root なしで書き込める。
初回起動時に既定の構成が書き出される (`config/PanelConfig.kt` の `DEFAULT_JSON`)。

```bash
adb -s <serial> push panels.json \
  /sdcard/Android/data/dev.shino3.echopanel/files/panels.json
```

### トップレベル

| キー | 既定 | 内容 |
|---|---|---|
| `haUrl` | `""` | HA のベース URL |
| `haToken` | `""` | 長期アクセストークン |
| `weatherEntity` | `""` | 背景レイヤーが参照する weather エンティティ |
| `pomodoroWorkMinutes` | 25 | |
| `pomodoroBreakMinutes` | 5 | |
| `mock` | `false` | true で HA を呼ばず作り物のデータを流す。レイアウト調整用 |
| `mockCondition` | `partlycloudy` | mock 時の天気。背景の雨/雪/星の確認に使う |
| `pages` | | `{ name, layout }` の配列。横スワイプで送る |

### レイアウト木

`children` があればグループ、無ければタイル。`weight` が比率になる。

```json
{ "dir": "row", "children": [
  { "weight": 2, "dir": "column", "children": [
    { "weight": 2.4, "type": "clock", "showDate": true },
    { "weight": 1, "type": "sensor.trend", "entity": "sensor.outdoor_temp", "hours": 24 }
  ]},
  { "weight": 1, "type": "weather.now", "entity": "weather.home" }
]}
```

### タイル

| type | 固有のプロパティ |
|---|---|
| `clock` | `seconds` (false), `showDate` (true) |
| `pomodoro` | — |
| `weather.now` | `entity` |
| `weather.forecast` | `entity`, `forecastType` (`daily`/`hourly`), `count` (5, 1-8)。daily は行のリスト (広いタイルでは気温レンジバー付き)、hourly は横並びの短冊 |
| `sensor.trend` | `entity`, `hours` (24, 1-168) |
| `calendar` | `entity`, `count` (3, 1-8), `days` (7, 1-60) |
| `ha.web` | HA のダッシュボードを WebView で表示 (`haUrl`) |
| `blank` | 何も描かない。余白の確保に使う |

全タイル共通: `scale` (文字倍率 0.5-3.0)、`filled` (ガラスカードを敷くか)、
`showLabel`、`label`。

`entity` が空だったり HA に届かなかったりしても落とさず、タイルの中に理由を出す。

## 対象環境

- Echo Show 5 2nd gen (cronos) / LineageOS 18.1 (Android 11, API 30)
- 実効サイズ **787 x 394 dp** (960x480px / 195dpi、システムバー非表示時)
- minSdk 26 なので手持ちの古い Android 端末でも動く

Android 11 で `policy_control` が消えたため、システムバーはアプリ側で隠すしかない。
`WindowInsetsController` だけでは実機で剥がれることがあったので、旧 `systemUiVisibility` の
`IMMERSIVE_STICKY` と併用し、さらに `onResume` から 300ms 後に掛け直している
(`MainActivity.goImmersive`)。

WebView (`ha.web`) は横スワイプを全て食ってしまい、そのページから出られなくなる。
画面の左右端 24dp を透明なスワイプ受けにして、ページ送りだけこちらが先に取る。

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

# ホームアプリにする。起動時に自動で前面に出るようになる
adb -s $D shell cmd package set-home-activity dev.shino3.echopanel/.MainActivity

# 元に戻す
adb -s $D shell settings put secure screensaver_components \
  "com.android.deskclock/com.android.deskclock.Screensaver"
adb -s $D shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher
```

`stay_on_while_plugged_in` を 1 以上にすると画面消灯タイマーが走らず
スクリーンセーバーに入らない。0 のままにすること。
無操作から時計に切り替わるまでの時間は `system screen_off_timeout` (ミリ秒)。

### 前任の Fully Kiosk を止める

ホームアプリに設定しても、**Fully Kiosk が入ったままだと再起動後に上から被さる。**
Launch on Boot で起動したうえに `SYSTEM_ALERT_WINDOW` を持っているため、
ホームより後に前面を取る。Echo Panel 自体はホームタスクとして起動しているが、
その裏に隠れるので気付きにくい。

```bash
adb -s $D shell pm disable-user --user 0 de.ozerov.fully
# 戻すとき
adb -s $D shell pm enable de.ozerov.fully
```

`disable-user` なら設定もライセンスも消えない。完全に消すなら `pm uninstall`。

### 動作の確認

```bash
# ホームがこのアプリに解決されるか
adb -s $D shell cmd package resolve-activity \
  -a android.intent.action.MAIN -c android.intent.category.HOME

# スクリーンセーバーを即座に呼ぶ (10分待たずに確認できる)
adb -s $D shell am start -n com.android.systemui/.Somnambulator

# いま前面にいるもの
adb -s $D shell dumpsys activity activities | grep -m1 ResumedActivity
```

## 未実装

- パネルの追加・並べ替えを端末上からできない (`panels.json` を push するしかない)
- HA が落ちているときの再試行が素朴 (次の定期取得まで待つ)
