package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.shino3.echopanel.ui.LocalBackdrop
import dev.shino3.echopanel.ui.T

/**
 * タイルの外枠 = ガラスカード。
 *
 * API 30 には backdrop blur が無いので、LocalBackdrop が持つ
 * 「ブラー済み背景」から自分の位置の切片を敷き、その上に半透明の面を重ねる。
 * カード越しに方眼と天体がぼんやり透けて見える。
 * ブラー画像がまだ無い間は不透明フォールバックで描く。
 */
@Composable
fun TileFrame(
    label: String? = null,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(T.radius)
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }

    var m = Modifier
        .fillMaxSize()
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)

    if (filled) {
        m = m
            .drawBehind {
                val blurred = backdrop?.blurred
                if (blurred != null) {
                    val sx = pos.x.toInt().coerceIn(0, (blurred.width - 1).coerceAtLeast(0))
                    val sy = pos.y.toInt().coerceIn(0, (blurred.height - 1).coerceAtLeast(0))
                    val sw = size.width.toInt().coerceAtMost(blurred.width - sx)
                    val sh = size.height.toInt().coerceAtMost(blurred.height - sy)
                    if (sw > 0 && sh > 0) {
                        drawImage(
                            image = blurred,
                            srcOffset = IntOffset(sx, sy),
                            srcSize = IntSize(sw, sh),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                    drawRect(T.glass)
                } else {
                    drawRect(T.glassSolid)
                }
            }
            .border(1.dp, T.glassEdge, shape)
    }

    Column(m.padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (label != null) {
            Text(
                text = label,
                color = T.fgFaint,
                fontSize = T.labelSize,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** データが無い / 設定が足りないときの表示。落とさずに理由を出す。 */
@Composable
fun TileNotice(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = T.fgFaint, fontSize = T.labelSize)
    }
}
