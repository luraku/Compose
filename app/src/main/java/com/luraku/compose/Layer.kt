package com.luraku.compose

import android.graphics.Bitmap

/**
 * Une image posée sur la page. Les coordonnées sont exprimées dans le repère
 * logique de la page (voir [Aspect]), indépendant de la taille à l'écran.
 */
data class Layer(
    val id: Long,
    val bitmap: Bitmap,
    val cx: Float,
    val cy: Float,
    val scale: Float = 1f,
    /** Rotation centrale, dans le plan de l'écran, en degrés. */
    val rotation: Float = 0f,
    /** Rotation axiale autour de l'axe horizontal de l'image, en degrés. */
    val tiltX: Float = 0f,
    /** Rotation axiale autour de l'axe vertical de l'image, en degrés. */
    val tiltY: Float = 0f,
    val alpha: Float = 1f,
    val flipped: Boolean = false,
)

enum class Aspect(val label: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    LANDSCAPE_16_9("16:9", 16f / 9f),
    PORTRAIT_9_16("9:16", 9f / 16f),
}

enum class Background(val label: String, val color: Int?) {
    TRANSPARENT("Transparent", null),
    WHITE("Blanc", 0xFFFFFFFF.toInt()),
    BLACK("Noir", 0xFF000000.toInt()),
    GRAY("Gris", 0xFF808080.toInt()),
}
