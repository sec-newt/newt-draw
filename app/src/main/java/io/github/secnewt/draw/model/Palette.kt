package io.github.secnewt.draw.model

/**
 * The colors to draw and fill with. Each has a plain name, so TalkBack can say it and the
 * Color button can show it in words, not just as a patch of color.
 */
enum class Palette(val label: String, val argb: Int) {
    Black("Black", 0xFF000000.toInt()),
    Gray("Gray", 0xFF808080.toInt()),
    White("White", 0xFFFFFFFF.toInt()),
    Red("Red", 0xFFE53935.toInt()),
    Orange("Orange", 0xFFFB8C00.toInt()),
    Yellow("Yellow", 0xFFFDD835.toInt()),
    Green("Green", 0xFF43A047.toInt()),
    Blue("Blue", 0xFF1E88E5.toInt()),
    Navy("Navy", 0xFF1A237E.toInt()),
    Purple("Purple", 0xFF8E24AA.toInt()),
    Pink("Pink", 0xFFF48FB1.toInt()),
    Brown("Brown", 0xFF795548.toInt()),
    Tan("Tan", 0xFFD2B48C.toInt()),
    Peach("Peach", 0xFFFFCCAA.toInt()),
    ;

    companion object {
        /** The palette color with exactly this value, if there is one. */
        fun of(argb: Int) = entries.firstOrNull { it.argb == argb }
    }
}
