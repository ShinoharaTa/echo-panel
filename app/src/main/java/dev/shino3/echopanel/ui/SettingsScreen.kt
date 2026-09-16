package dev.shino3.echopanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.shino3.echopanel.config.HaSettings
import dev.shino3.echopanel.config.UiSettings
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.data.HaStatus
import dev.shino3.echopanel.data.describeHaError
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home Assistant の接続設定。画面右下の歯車から開く。
 *
 * panels.json を adb push しなくても、端末のタッチだけで
 * URL・長期アクセストークン・天気エンティティを入れられる。
 * 保存先はアプリ内部 (SharedPreferences)。/sdcard には出ない。
 *
 * 長いトークンをソフトキーボードで打つのは辛いので、
 * 欄をタップしてから Mac 側で `adb shell input text '<token>'` を流す手もある。
 */
@Composable
fun SettingsScreen(
    initial: HaSettings,
    /** panels.json 側の値。設定が空欄のときの実効値としてヒントに出す */
    fallback: HaSettings,
    initialUi: UiSettings,
    /** panels.json 側の theme。「JSON に従う」を選んだときの実効値 */
    fallbackTheme: String,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(initial.haUrl) }
    var token by remember { mutableStateOf(initial.haToken) }
    var weather by remember { mutableStateOf(initial.weatherEntity) }
    var showToken by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(initialUi.theme) }

    // 実効値: 空欄なら panels.json の値が使われる
    val effUrl = url.ifBlank { fallback.haUrl }
    val effToken = token.ifBlank { fallback.haToken }
    val effWeather = weather.ifBlank { fallback.weatherEntity }

    fun test() {
        if (effUrl.isBlank() || effToken.isBlank()) {
            status = "URL とトークンを入れてから試す"
            return
        }
        busy = true
        status = "接続中…"
        scope.launch {
            val client = HaClient(effUrl, effToken)
            val r = client.ping()
            status = r.fold(
                onSuccess = { msg ->
                    if (effWeather.isBlank()) "接続OK: $msg"
                    else client.state(effWeather).fold(
                        onSuccess = { st ->
                            if (st == null) "接続OK だが $effWeather が見つからない"
                            else "接続OK: $effWeather = ${st.state}"
                        },
                        onFailure = { "接続OK だが $effWeather を取れない: ${describeHaError(it)}" }
                    )
                },
                onFailure = { "失敗: ${describeHaError(it)}" }
            )
            busy = false
        }
    }

    fun save() {
        HaSettings.save(ctx, HaSettings(url, token, weather))
        onSaved()
        onClose()
    }

    /**
     * 配色だけは選んだ瞬間に確定させる。
     * 部屋の明るさに合うかどうかは当ててみないと分からないので、
     * 「保存」を挟むと選び直すたびに画面を閉じる羽目になる。
     */
    fun pickTheme(id: String) {
        theme = id
        UiSettings.save(ctx, UiSettings(id))
        onSaved()
    }

    // 背面のタップがパネルに抜けないよう、全面で受ける
    Box(
        Modifier
            .fillMaxSize()
            .background(T.bg.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = T.edge, vertical = T.gapM)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Home Assistant 接続", color = T.fg, fontSize = T.valueSize)
                Spacer(Modifier.width(T.gapM))
                Text(
                    "空欄は panels.json の値を使う。保存先は端末内部",
                    color = T.fgFaint, fontSize = T.labelSize
                )
            }
            Spacer(Modifier.height(T.gapXs))
            HaStatusLine()
            Spacer(Modifier.height(T.gapS))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(T.gapM)) {
                Field(
                    value = url, onChange = { url = it },
                    label = "URL",
                    hint = fallback.haUrl.ifBlank { "http://homeassistant.local:8123" },
                    keyboard = KeyboardType.Uri,
                    modifier = Modifier.weight(1.2f)
                )
                Field(
                    value = weather, onChange = { weather = it },
                    label = "天気エンティティ (背景用)",
                    hint = fallback.weatherEntity.ifBlank { "weather.home" },
                    keyboard = KeyboardType.Ascii,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(T.gapS))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value = token, onChange = { token = it },
                    label = "長期アクセストークン",
                    hint = if (fallback.haToken.isNotBlank()) "(panels.json に設定あり)" else "",
                    keyboard = KeyboardType.Ascii,
                    hide = !showToken,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(T.gapS))
                SmallButton(if (showToken) "隠す" else "表示") { showToken = !showToken }
            }
            Spacer(Modifier.height(T.gapS))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(T.gapS)
            ) {
                SmallButton("接続テスト", enabled = !busy) { test() }
                SmallButton("保存", primary = true) { save() }
                SmallButton("閉じる") { onClose() }
                if (!initial.isEmpty || !initialUi.isEmpty) {
                    SmallButton("消去") {
                        HaSettings.clear(ctx)
                        UiSettings.clear(ctx)
                        url = ""; token = ""; weather = ""; theme = ""
                        status = "端末側の設定を消した。panels.json の値に戻る"
                        onSaved()
                    }
                }
            }
            Spacer(Modifier.height(T.gapM))
            ThemeRow(
                selected = theme,
                fallbackTheme = fallbackTheme,
                onPick = ::pickTheme
            )

            status?.let {
                Spacer(Modifier.height(T.gapS))
                Text(
                    it,
                    color = if (it.startsWith("失敗")) T.accAlert else T.fgMuted,
                    fontSize = T.labelSize,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 配色の切り替え。
 *
 * 選んだ瞬間に当たるので、実際の部屋で見比べて決められる。
 * 「JSON に従う」は端末側の指定を外して panels.json の値へ戻す。
 */
@Composable
private fun ThemeRow(selected: String, fallbackTheme: String, onPick: (String) -> Unit) {
    val effective = selected.ifBlank { fallbackTheme }
    val note = when (effective) {
        Themes.AUTO -> "時刻で昼と夜を入れ替える"
        else -> Themes.all.firstOrNull { it.id == effective }?.note ?: ""
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("配色", color = T.fg, fontSize = T.valueSize)
        Spacer(Modifier.width(T.gapM))
        Text(note, color = T.fgFaint, fontSize = T.labelSize)
    }
    Spacer(Modifier.height(T.gapS))

    Row(horizontalArrangement = Arrangement.spacedBy(T.gapS)) {
        Chip("JSON に従う", selected.isBlank()) { onPick("") }
        Themes.all.forEach { p ->
            Chip(p.label, selected == p.id) { onPick(p.id) }
        }
        Chip("自動", selected == Themes.AUTO) { onPick(Themes.AUTO) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) T.bg else T.fg,
        fontSize = T.bodySize,
        modifier = Modifier
            .clip(RoundedCornerShape(T.radius))
            .background(if (selected) T.fg else T.surface)
            .border(1.dp, if (selected) T.fg else T.line, RoundedCornerShape(T.radius))
            .clickable(onClick = onClick)
            .padding(horizontal = T.gapM, vertical = T.gapS)
    )
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)

/**
 * いまパネルが HA と繋がっているかを1行で出す。
 *
 * タイル側は通信が切れても最後に取れた値を出し続けるので、
 * 画面を見ているだけでは「止まっている」ことに気付けない。
 * 気付いたときに原因まで辿れる場所がここ。
 */
@Composable
private fun HaStatusLine() {
    val failures = HaStatus.consecutiveFailures
    val last = HaStatus.lastSuccessAt
    val text = when {
        failures > 0 ->
            "${failures}回連続で失敗" + (HaStatus.lastError?.let { " — $it" } ?: "") +
                (if (last > 0L) "  (最終取得 ${CLOCK.format(atLocal(last))})" else "")
        last > 0L -> "最終取得 ${CLOCK.format(atLocal(last))}"
        else -> "まだ取得していない"
    }
    Text(
        "疎通: $text",
        color = if (HaStatus.down) T.accHot else T.fgFaint,
        fontSize = T.labelSize
    )
}

private fun atLocal(epochMs: Long): ZonedDateTime =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    hide: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = T.labelSize) },
        placeholder = { Text(hint, color = T.fgFaint, fontSize = T.bodySize) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrect = false),
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = T.fg,
            unfocusedTextColor = T.fg,
            focusedBorderColor = T.fgMuted,
            unfocusedBorderColor = T.line,
            focusedLabelColor = T.fgMuted,
            unfocusedLabelColor = T.fgFaint,
            cursorColor = T.fg,
            focusedContainerColor = T.surface,
            unfocusedContainerColor = T.surface,
        ),
        modifier = modifier
    )
}

@Composable
private fun SmallButton(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = when {
            !enabled -> T.fgFaint
            primary -> T.bg
            else -> T.fg
        },
        fontSize = T.bodySize,
        modifier = Modifier
            .clip(RoundedCornerShape(T.radius))
            .background(if (primary && enabled) T.fg else T.surface)
            .border(1.dp, if (primary && enabled) T.fg else T.line, RoundedCornerShape(T.radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = T.gapM, vertical = T.gapS)
    )
}
