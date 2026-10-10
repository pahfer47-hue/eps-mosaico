package com.epsonmosaico

import kotlin.math.ceil
import kotlin.math.floor

/** Papel disponible. Medidas en milésimas de pulgada (mils), como usa Android. */
enum class Paper(val label: String, val widthMils: Int, val heightMils: Int) {
    CARTA("Carta", 8500, 11000),
    OFICIO("Oficio", 8500, 13000),
    A4("A4", 8268, 11692);

    fun widthPt(landscape: Boolean): Float = pts(if (landscape) heightMils else widthMils)
    fun heightPt(landscape: Boolean): Float = pts(if (landscape) widthMils else heightMils)

    private fun pts(mils: Int): Float = mils / 1000f * 72f
}

enum class FitMode(val label: String) {
    /** Llena todo el cuadro recortando lo que sobre (centrado). */
    FILL("Llenar (recorta)"),

    /** Muestra la imagen completa, con bordes blancos si hace falta. */
    FIT("Ajustar (completa)")
}

/** Ajustes del usuario, en centímetros. */
data class MosaicSettings(
    val tileWidthCm: Float = 5f,
    val tileHeightCm: Float = 5f,
    val marginCm: Float = 0.4f,
    val gapCm: Float = 0.1f,
    val copies: Int = 1,
    val fit: FitMode = FitMode.FILL,
    val cutGuides: Boolean = true
)

data class Slot(val left: Float, val top: Float, val width: Float, val height: Float)

/** Rejilla calculada para una hoja. Todo en puntos (1/72 de pulgada). */
data class Grid(
    val cols: Int,
    val rows: Int,
    val originX: Float,
    val originY: Float,
    val tileW: Float,
    val tileH: Float,
    val gap: Float
) {
    val perPage: Int get() = cols * rows

    fun slot(indexInPage: Int): Slot {
        val c = indexInPage % cols
        val r = indexInPage / cols
        return Slot(
            left = originX + c * (tileW + gap),
            top = originY + r * (tileH + gap),
            width = tileW,
            height = tileH
        )
    }
}

object MosaicLayout {
    const val PT_PER_CM = 72f / 2.54f
    private const val EPS = 1e-3f

    fun cmToPt(cm: Float): Float = cm * PT_PER_CM

    /** Calcula cuántas fotos caben por hoja y dónde empieza la rejilla (centrada). */
    fun grid(pageWidthPt: Float, pageHeightPt: Float, s: MosaicSettings): Grid {
        val tileW = cmToPt(s.tileWidthCm)
        val tileH = cmToPt(s.tileHeightCm)
        val margin = cmToPt(s.marginCm)
        val gap = cmToPt(s.gapCm)

        val usableW = pageWidthPt - 2 * margin
        val usableH = pageHeightPt - 2 * margin

        val cols = if (tileW <= 0f) 0 else floor((usableW + gap + EPS) / (tileW + gap)).toInt().coerceAtLeast(0)
        val rows = if (tileH <= 0f) 0 else floor((usableH + gap + EPS) / (tileH + gap)).toInt().coerceAtLeast(0)

        val gridW = if (cols > 0) cols * tileW + (cols - 1) * gap else 0f
        val gridH = if (rows > 0) rows * tileH + (rows - 1) * gap else 0f

        return Grid(
            cols = cols,
            rows = rows,
            originX = (pageWidthPt - gridW) / 2f,
            originY = (pageHeightPt - gridH) / 2f,
            tileW = tileW,
            tileH = tileH,
            gap = gap
        )
    }

    fun pageCount(totalItems: Int, perPage: Int): Int =
        if (perPage <= 0 || totalItems <= 0) 0 else ceil(totalItems / perPage.toDouble()).toInt()
}
