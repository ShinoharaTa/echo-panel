package dev.shino3.echopanel.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shino3.echopanel.ui.T

/**
 * タイルの外枠。
 * 面の分割数が変わっても見た目が揃うよう、余白と見出しはここだけで決める。
 *
 * label に null を渡すと見出し行ごと消える。JSON 側の "showLabel": false と
 * "filled": false で、枠なし・見出しなしの素の表示にできる。
 */
@Composable
fun TileFrame(
    label: String? = null,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(T.radius))
            .background(if (filled) T.surface else T.bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
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
        Text(text, color = T.fgFaint, fontSize = 11.sp)
    }
}
