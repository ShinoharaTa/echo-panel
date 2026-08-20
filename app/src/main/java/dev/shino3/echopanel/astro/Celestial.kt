package dev.shino3.echopanel.astro

import java.time.Instant
import java.time.LocalTime

/**
 * 背景に描く天体の計算。
 * 観賞用なので分単位の精度で十分。天文学的な厳密さは追わない。
 */
object Celestial {

    /** 2000-01-06 18:14 UTC の新月を基準にした朔望月 (29.530589日) での月齢。 */
    private const val NEW_MOON_EPOCH_MS = 947_182_440_000L
    private const val SYNODIC_MS = (29.530588853 * 86_400_000L).toLong()

    /** 月相 [0,1)。0=新月, 0.25=上弦, 0.5=満月, 0.75=下弦 */
    fun moonPhase(now: Instant = Instant.now()): Float {
        val d = Math.floorMod(now.toEpochMilli() - NEW_MOON_EPOCH_MS, SYNODIC_MS)
        return d.toFloat() / SYNODIC_MS.toFloat()
    }

    /**
     * 弧上の位置。
     * 昼(6-18時)は太陽が 0→1、夜(18-6時)は月が 0→1 を進む。
     * 日の出入り時刻は固定運用 — 表示装置の演出であって暦計算ではない。
     */
    data class SkyPos(val t: Float, val isDay: Boolean)

    fun skyPos(time: LocalTime): SkyPos {
        val h = time.hour + time.minute / 60f
        return if (h in 6f..18f) {
            SkyPos((h - 6f) / 12f, true)
        } else {
            val nh = if (h > 18f) h - 18f else h + 6f
            SkyPos(nh / 12f, false)
        }
    }
}
