package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.data.MockData
import dev.shino3.echopanel.ui.T
import dev.shino3.echopanel.ui.WeatherIcon
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 温度の意味色。30°以上=琥珀 / 10°以下=青 / 間はモノクロ。
 * 「普段は白黒、極端なときだけ灯る」閾値制。
 */
fun tempColor(t: Double?) = when {
    t == null -> T.fg
    t >= 30.0 -> T.accHot
    t <= 10.0 -> T.accCold
    else -> T.fg
}

/** HA の condition を短い日本語にする。記号や色は使わない。 */
fun conditionLabel(c: String?): String = when (c) {
    "clear-night" -> "快晴"
    "cloudy" -> "曇"
    "fog" -> "霧"
    "hail" -> "雹"
    "lightning" -> "雷"
    "lightning-rainy" -> "雷雨"
    "partlycloudy" -> "晴時々曇"
    "pouring" -> "大雨"
    "rainy" -> "雨"
    "snowy" -> "雪"
    "snowy-rainy" -> "みぞれ"
    "sunny" -> "晴"
    "windy", "windy-variant" -> "風強"
    "exceptional" -> "荒天"
    null, "" -> "—"
    else -> c
}

@Composable
private fun rememberClient(cfg: PanelConfig): HaClient? =
    remember(cfg.haUrl, cfg.haToken) {
        if (cfg.haConfigured) HaClient(cfg.haUrl, cfg.haToken) else null
    }

/**
 * 現在の天気。アイコン + 気温をひとかたまりの主役にして、
 * 天気の言葉と湿度は1行の従属に畳む (iOS の小ウィジェットの解剖)。
 */
@Composable
fun WeatherNowTile(tile: Node.Tile, cfg: PanelConfig) {
    val entity = tile.str("entity")
    val client = rememberClient(cfg)
    var st by remember { mutableStateOf<HaClient.EntityState?>(null) }

    LaunchedEffect(entity, client, cfg.mock) {
        if (cfg.mock) { st = MockData.weatherNow(cfg.mockCondition); return@LaunchedEffect }
        if (client == null || entity.isBlank()) return@LaunchedEffect
        while (true) {
            st = client.state(entity)
            delay(10 * 60_000L)
        }
    }

    TileFrame(label = tile.labelOrNull("現在"), filled = tile.filled) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() && !cfg.mock -> TileNotice("entity 未設定")
            st == null -> TileNotice("取得中")
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Column に入ると DSL マーカーで maxHeight が見えなくなるので先に取り出す
                val boxH = maxHeight.value
                // アイコンと数字が横に並ぶので、高さだけでなく幅からも上限を掛ける。
                // 縦長の枠に置いたとき「18.4°」が折り返すのを防ぐ
                val big = (minOf(boxH * 0.42f, maxWidth.value / 3.4f) * tile.scale)
                    .coerceIn(18f, 120f)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val temp = st!!.attrDouble("temperature")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((big * 0.16f).dp)
                    ) {
                        WeatherIcon(st!!.state, (big * 0.60f).dp)
                        Text(
                            text = temp?.let { "%.1f°".format(it) } ?: "—",
                            color = tempColor(temp),
                            fontSize = big.sp,
                            fontWeight = T.displayWeight
                        )
                    }
                    val hum = st!!.attrDouble("humidity")
                    Text(
                        text = conditionLabel(st!!.state) +
                            (hum?.takeIf { boxH > 80f }?.let { "  湿度 ${it.toInt()}%" } ?: ""),
                        color = T.fgMuted,
                        fontSize = (big * 0.28f).coerceIn(T.labelSize.value, T.valueSize.value).sp
                    )
                }
            }
        }
    }
}

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)
private val HOUR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H時", Locale.JAPAN)

/** 予報。daily は行のリスト、hourly は横並びの短冊。 */
@Composable
fun ForecastTile(tile: Node.Tile, cfg: PanelConfig) {
    val entity = tile.str("entity")
    val type = tile.str("forecastType", "daily")
    val count = tile.int("count", 5).coerceIn(1, 8)
    val client = rememberClient(cfg)
    var list by remember { mutableStateOf<List<HaClient.ForecastEntry>>(emptyList()) }

    LaunchedEffect(entity, type, client, cfg.mock) {
        if (cfg.mock) { list = MockData.forecast(count); return@LaunchedEffect }
        if (client == null || entity.isBlank()) return@LaunchedEffect
        while (true) {
            list = client.forecast(entity, type)
            delay(30 * 60_000L)
        }
    }

    TileFrame(
        label = tile.labelOrNull(if (type == "hourly") "時間予報" else "週間予報"),
        filled = tile.filled
    ) {
        when {
            !cfg.haConfigured -> TileNotice("haToken 未設定")
            entity.isBlank() && !cfg.mock -> TileNotice("entity 未設定")
            list.isEmpty() -> TileNotice("取得中")
            else ->
                if (type == "hourly") HourlyStrip(list.take(count), tile.scale)
                else DailyRows(list.take(count), tile.scale)
        }
    }
}

/**
 * 週間予報。Apple 天気の週間欄の解剖に合わせる:
 * 曜日 | アイコン | 降水% | 最低 —レンジ帯— 最高。
 * 最低と最高は同じサイズにして、従属は色 (muted) だけで表す。
 * 数値は右寄せの固定幅で桁を揃える。
 */
@Composable
private fun DailyRows(list: List<HaClient.ForecastEntry>, s: Float) {
    // 週全体の最低〜最高を1本の物差しにして、日ごとの帯の位置で寒暖の推移を見せる。
    // 狭いタイルでは帯を省き、全幅に間延びしないよう行の幅も絞って中央に置く
    val weekMin = list.mapNotNull { it.tempLow }.minOrNull()
    val weekMax = list.mapNotNull { it.tempHigh }.maxOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showBar = maxWidth > 340.dp &&
            weekMin != null && weekMax != null && weekMax - weekMin > 0.1
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy((2 * s).dp, Alignment.CenterVertically)
        ) {
            list.forEach { f ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8 * s).dp)
                ) {
                    Text(
                        text = f.time?.format(DAY_FMT) ?: "—",
                        color = T.fgMuted,
                        fontSize = (T.labelSize.value * s).sp,
                        modifier = Modifier.width((20 * s).dp)
                    )
                    WeatherIcon(f.condition, (20 * s).dp)
                    val pp = f.precipProbability
                    Text(
                        text = if (pp != null && pp >= 10) "${pp.toInt()}%" else "",
                        color = if (pp != null && pp >= 50) T.accCold else T.fgMuted,
                        fontSize = (T.labelSize.value * s).sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width((32 * s).dp)
                    )
                    if (!showBar) Spacer(Modifier.weight(1f))
                    Text(
                        text = f.tempLow?.let { "%.0f°".format(it) } ?: "",
                        color = if (f.tempLow != null && f.tempLow <= 10.0) T.accCold else T.fgMuted,
                        fontSize = (T.valueSize.value * s).sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width((40 * s).dp)
                    )
                    if (showBar) {
                        RangeBar(
                            low = f.tempLow, high = f.tempHigh,
                            min = weekMin!!, max = weekMax!!,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = f.tempHigh?.let { "%.0f°".format(it) } ?: "—",
                        color = tempColor(f.tempHigh),
                        fontSize = (T.valueSize.value * s).sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width((44 * s).dp)
                    )
                }
            }
        }
    }
}

/** その日の最低〜最高を、週全体のレンジを物差しにした帯で描く */
@Composable
private fun RangeBar(low: Double?, high: Double?, min: Double, max: Double, modifier: Modifier) {
    Canvas(modifier.height(4.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(color = T.line, cornerRadius = r)
        if (low != null && high != null && max > min) {
            val span = (max - min).toFloat()
            val x0 = ((low - min).toFloat() / span) * size.width
            val x1 = ((high - min).toFloat() / span) * size.width
            drawRoundRect(
                color = T.fgMuted,
                topLeft = Offset(x0, 0f),
                size = Size((x1 - x0).coerceAtLeast(size.height), size.height),
                cornerRadius = r
            )
        }
    }
}

/**
 * 時間予報は横並びの短冊: 時刻 → アイコン → 気温 (iOS の時間別と同型)。
 * 1列3項目に絞る。読むものではなく眺めるものなので、これ以上詰めない。
 */
@Composable
private fun HourlyStrip(list: List<HaClient.ForecastEntry>, s: Float) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        list.forEach { f ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((4 * s).dp)
            ) {
                Text(
                    text = f.time?.format(HOUR_FMT) ?: "—",
                    color = T.fgMuted,
                    fontSize = (T.labelSize.value * s).sp
                )
                WeatherIcon(f.condition, (22 * s).dp)
                Text(
                    text = f.tempHigh?.let { "%.0f°".format(it) } ?: "—",
                    color = tempColor(f.tempHigh),
                    fontSize = (T.valueSize.value * s).sp
                )
            }
        }
    }
}
