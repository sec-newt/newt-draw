package io.github.secnewt.draw.model

/** Brush widths in dp. */
enum class BrushSize(val label: String, val widthDp: Float) {
    Small("Small", 3f),
    Medium("Medium", 6f),
    Large("Large", 12f),
    ExtraLarge("Extra large", 24f),
}

enum class Tool(val label: String) {
    Pen("Pen"),
    Eraser("Eraser"),
}
