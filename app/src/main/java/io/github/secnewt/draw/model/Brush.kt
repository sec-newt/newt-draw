package io.github.secnewt.draw.model

/**
 * Brush widths in paper pixels (the paper is 3000 wide). Like a real pen on real paper,
 * the line looks thicker on screen when you zoom in.
 */
enum class BrushSize(val label: String, val width: Float) {
    Small("Small", 8f),
    Medium("Medium", 16f),
    Large("Large", 32f),
    ExtraLarge("Extra large", 64f),
}

enum class Tool(val label: String) {
    Pen("Pen"),
    Eraser("Eraser"),
}
