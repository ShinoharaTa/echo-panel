package dev.shino3.echopanel.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay

/**
 * HA の定期取得を、通信断に耐える形にまとめる。
 *
 * 机の上で常時点いている画面なので、Wi-Fi が数十秒切れただけで
 * 天気も予定も「取得中」に巻き戻るのは、無い方がましな挙動だった。
 * ここでの方針は3つ。
 *
 * - **失敗しても前の値を捨てない。** 最後に取れた内容を出し続ける。
 * - **黙って古い値を出さない。** 一定以上古くなったらタイルに印を出す
 *   (`isStale`)。嘘の鮮度で出すくらいなら、古いと分かる方がよい。
 * - **失敗中は間隔を詰めてから広げる。** 復帰を早く拾いつつ、
 *   HA が長く落ちているときに叩き続けない。
 */
@Stable
class FeedState<T>(initial: T) {
    var value by mutableStateOf(initial)
        private set

    /** 最後に取得できた時刻。0 なら一度も取れていない */
    var fetchedAt by mutableLongStateOf(0L)
        private set

    /** 直近の取得が失敗したか */
    var failing by mutableStateOf(false)
        private set

    /**
     * 最後に取得を試みた時刻。
     * 鮮度の判定に System.currentTimeMillis() を直接使うと、時間が経っても
     * 再コンポーズの契機が無く印が出ない。試行のたびに動くこの値を
     * 物差しにして、状態の変化だけで判定が進むようにする。
     */
    var lastAttemptAt by mutableLongStateOf(0L)
        private set

    val hasValue: Boolean get() = fetchedAt > 0L

    /**
     * 出している値が古すぎるか。
     * 「失敗している」だけでは印を出さない。次の定期取得までの間に1回
     * 落ちただけなら、値はまだ十分新しいため。
     */
    fun isStale(intervalMs: Long): Boolean =
        failing && hasValue && lastAttemptAt - fetchedAt > intervalMs * STALE_FACTOR

    /** mock など、取得ループを回さずに値を入れる経路 */
    fun set(v: T) {
        value = v
        fetchedAt = System.currentTimeMillis()
        lastAttemptAt = fetchedAt
        failing = false
    }

    /**
     * 成功するまで指数バックオフで詰め、成功したら interval へ戻す取得ループ。
     * 呼び出し側の LaunchedEffect が消えるまで回り続ける。
     */
    suspend fun poll(intervalMs: Long, fetch: suspend () -> Result<T>) {
        var backoffMs = RETRY_MIN_MS
        while (true) {
            var wait = intervalMs
            lastAttemptAt = System.currentTimeMillis()
            fetch()
                .onSuccess {
                    value = it
                    fetchedAt = System.currentTimeMillis()
                    lastAttemptAt = fetchedAt
                    failing = false
                    HaStatus.onSuccess()
                    backoffMs = RETRY_MIN_MS
                }
                .onFailure { e ->
                    failing = true
                    HaStatus.onFailure(e)
                    wait = backoffMs
                    backoffMs = (backoffMs * 2).coerceAtMost(RETRY_MAX_MS)
                }
            delay(wait)
        }
    }

    private companion object {
        /** 定期取得の間隔の何倍で「古い」とするか */
        const val STALE_FACTOR = 3

        const val RETRY_MIN_MS = 20_000L
        const val RETRY_MAX_MS = 5 * 60_000L
    }
}

/**
 * 取得済みの値をプロセスに残しておく置き場。
 *
 * HorizontalPager は表示していないページを捨てるので、値をタイルの中だけに
 * 持つと、ページを送って戻るたびに「取得中」からやり直しになる。
 * 10分間隔で取っているものが、ページを跨ぐたびに消えるのでは意味がない。
 * 同じ entity を複数のタイルが見ているときも、ここで1つに合流する。
 *
 * コンポジションからしか触らないので同期は要らない。
 */
private val feeds = mutableMapOf<String, FeedState<*>>()

/**
 * @param kind タイルの種類。同じ entity でも取っている中身が違うので鍵を分ける
 * @param keys entity や hours など、中身が変われば別物になる値
 */
@Composable
fun <T> rememberFeed(kind: String, initial: T, vararg keys: Any?): FeedState<T> {
    val id = keys.joinToString("|", prefix = "$kind|")
    @Suppress("UNCHECKED_CAST")
    return remember(id) { feeds.getOrPut(id) { FeedState(initial) } as FeedState<T> }
}

/**
 * HA との疎通の要約。タイルをまたいで1つ。
 *
 * どのタイルも同じ HA を見ているので、1枚でも取れていれば繋がっている。
 * 歯車の色と設定画面の表示に使い、「画面が更新されないが原因が分からない」
 * 状態を作らないためにある。
 */
object HaStatus {
    var lastSuccessAt by mutableLongStateOf(0L)
        private set
    var consecutiveFailures by mutableIntStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** 連続で落ち続けているか。歯車に色を出す閾値 */
    val down: Boolean get() = consecutiveFailures >= DOWN_AFTER

    fun onSuccess() {
        lastSuccessAt = System.currentTimeMillis()
        consecutiveFailures = 0
        lastError = null
    }

    fun onFailure(e: Throwable?) {
        consecutiveFailures++
        lastError = e?.let { describeHaError(it) } ?: "原因不明"
    }

    private const val DOWN_AFTER = 3
}
