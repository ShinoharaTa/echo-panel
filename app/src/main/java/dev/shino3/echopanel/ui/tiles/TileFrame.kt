package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    /** 出している値が古い。右上に小さな印を出す (閾値を超えたときだけ灯る有彩色) */
    stale: Boolean = false,
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
                // 背景の切り替え中は前の絵も敷いておく。
                // カードの中だけ絵が飛ぶのを防ぐ
                backdrop?.prevBlurred?.let { prev -> drawBackdropSlice(prev, pos, 1f) }
                val blurred = backdrop?.blurred
                if (blurred != null) {
                    drawBackdropSlice(blurred, pos, backdrop.fade.value)
                    drawRect(T.glass)
                } else {
                    drawRect(T.glassSolid)
                }
            }
            .border(1.dp, T.glassEdge, shape)
    }

    Box(m) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (label != null) {
                Text(
                    text = label,
                    color = T.fgFaint,
                    fontSize = T.labelSize,
                    fontWeight = T.labelWeight,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content()
            }
        }

        // 古い値を黙って出さない。文字を足すと狭い画面では邪魔なので点ひとつ。
        if (stale) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(T.accHot)
            )
        }
    }
}

/** ブラー済み背景から自分の位置の切片を敷く */
private fun DrawScope.drawBackdropSlice(image: ImageBitmap, pos: Offset, alpha: Float) {
    val sx = pos.x.toInt().coerceIn(0, (image.width - 1).coerceAtLeast(0))
    val sy = pos.y.toInt().coerceIn(0, (image.height - 1).coerceAtLeast(0))
    val sw = size.width.toInt().coerceAtMost(image.width - sx)
    val sh = size.height.toInt().coerceAtMost(image.height - sy)
    if (sw <= 0 || sh <= 0) return
    drawImage(
        image = image,
        srcOffset = IntOffset(sx, sy),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = alpha,
    )
}

/** データが無い / 設定が足りないときの表示。落とさずに理由を出す。 */
@Composable
fun TileNotice(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = T.fgMuted, fontSize = T.labelSize)
    }
}
