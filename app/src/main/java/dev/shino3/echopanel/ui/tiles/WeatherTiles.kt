package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.data.HaClient
import dev.shino3.echopanel.data.MockData
import dev.shino3.echopanel.ui.T
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

/** 現在の天気。気温・湿度・体感を1枠に収める。 */
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
                val big = (boxH * 0.42f * tile.scale).coerceIn(18f, 120f)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val temp = st!!.attrDouble("temperature")
                    Text(
                        text = temp?.let { "%.1f°".format(it) } ?: "—",
                        color = tempColor(temp),
                        fontSize = big.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = conditionLabel(st!!.state),
                        color = T.fgMuted,
                        fontSize = (big * 0.32f).coerceIn(11f, 18f).sp
                    )
                    val hum = st!!.attrDouble("humidity")
                    if (hum != null && boxH > 96f) {
                        Text(
                            text = "湿度 ${hum.toInt()}%",
                            color = T.fgFaint,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)
private val HOUR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H時", Locale.JAPAN)

/** 予報。横に並べてひと目で見る。daily / hourly を切り替えられる。 */
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
            else -> {
                val s = tile.scale
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    list.take(count).forEach { f ->
                        // 値が無い行も透明で置いて高さを揃える。
                        // 降水 0% の列だけ行が減ると、列ごとに天地がずれて
                        // 錯視レベルではなく実際に高さが揃わなくなる。
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = f.time?.format(if (type == "hourly") HOUR_FMT else DAY_FMT) ?: "—",
                                color = T.fgFaint,
                                fontSize = (11 * s).sp
                            )
                            Text(
                                text = conditionLabel(f.condition),
                                color = T.fgMuted,
                                fontSize = (12 * s).sp
                            )
                            Text(
                                text = f.tempHigh?.let { "%.0f°".format(it) } ?: "—",
                                color = tempColor(f.tempHigh),
                                fontSize = (18 * s).sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = f.tempLow?.let { "%.0f°".format(it) } ?: " ",
                                color = T.fgFaint,
                                fontSize = (11 * s).sp,
                                modifier = Modifier.alpha(if (f.tempLow != null) 1f else 0f)
                            )
                            val pp = f.precipProbability
                            Text(
                                text = pp?.let { "${it.toInt()}%" } ?: " ",
                                color = if (pp != null && pp >= 50) T.accCold else T.fgFaint,
                                fontSize = (10 * s).sp,
                                modifier = Modifier.alpha(if (pp != null && pp > 0) 1f else 0f)
                            )
                        }
                    }
                }
            }
        }
    }
}
