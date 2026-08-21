package dev.shino3.echopanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shino3.echopanel.config.Dir
import dev.shino3.echopanel.config.Node
import dev.shino3.echopanel.config.PanelConfig
import dev.shino3.echopanel.ui.tiles.RenderTile

/**
 * タイルのタップでページを切り替えるナビゲーション。PanelRoot が提供する。
 * ホームは要約、詳細は別ページに置き、タップを入口にする (progressive disclosure)。
 */
val LocalPageNav = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * JSON のレイアウト木をそのまま Row/Column の入れ子に落とす。
 * weight をそのまま Compose の weight に渡すので、2:1 も 3分割も同じ経路で出る。
 */
@Composable
fun RenderNode(node: Node, cfg: PanelConfig, modifier: Modifier = Modifier) {
    when (node) {
        is Node.Group -> {
            if (node.dir == Dir.ROW) {
                Row(modifier.fillMaxSize()) {
                    node.children.forEach { child ->
                        Box(Modifier.weight(child.weight).fillMaxSize()) {
                            RenderNode(child, cfg)
                        }
                    }
                }
            } else {
                Column(modifier.fillMaxSize()) {
                    node.children.forEach { child ->
                        Box(Modifier.weight(child.weight).fillMaxSize()) {
                            RenderNode(child, cfg)
                        }
                    }
                }
            }
        }

        is Node.Tile -> {
            // タイル間の見切りは余白だけ。背景は塗らない —
            // ギャップから Backdrop(方眼と天体)が覗くのが意図した見た目。
            val nav = LocalPageNav.current
            val target = node.str("tapPage")
            var m = modifier
                .fillMaxSize()
                .padding(T.gapTile)
            if (target.isNotBlank() && nav != null) {
                m = m.clickable { nav(target) }
            }
            Box(m) {
                RenderTile(node, cfg)
            }
        }
    }
}
