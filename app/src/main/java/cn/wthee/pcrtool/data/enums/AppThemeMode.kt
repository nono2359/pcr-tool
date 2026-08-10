package cn.wthee.pcrtool.data.enums

object AppThemeMode {
    const val SYSTEM = 0
    const val DAY = 1
    const val NIGHT = 2

    fun label(value: Int) = when (value) {
        DAY -> "デイ"
        NIGHT -> "ナイト"
        else -> "システム"
    }

    fun queryValue(value: Int) = when (value) {
        DAY -> "day"
        NIGHT -> "night"
        else -> "system"
    }
}
