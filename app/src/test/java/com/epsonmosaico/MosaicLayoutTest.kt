package com.epsonmosaico

import org.junit.Assert.assertEquals
import org.junit.Test

class MosaicLayoutTest {

    @Test
    fun carta_5x5_cabe_4x5() {
        val s = MosaicSettings(tileWidthCm = 5f, tileHeightCm = 5f)
        val g = MosaicLayout.grid(Paper.CARTA.widthPt(false), Paper.CARTA.heightPt(false), s)
        assertEquals(4, g.cols)
        assertEquals(5, g.rows)
    }

    @Test
    fun oficio_5x5_cabe_4x6() {
        val s = MosaicSettings(tileWidthCm = 5f, tileHeightCm = 5f)
        val g = MosaicLayout.grid(Paper.OFICIO.widthPt(false), Paper.OFICIO.heightPt(false), s)
        assertEquals(4, g.cols)
        assertEquals(6, g.rows)
    }

    @Test
    fun oficio_da_mas_filas_que_carta() {
        val s = MosaicSettings(tileWidthCm = 5f, tileHeightCm = 5f, marginCm = 0.5f, gapCm = 0f)
        val carta = MosaicLayout.grid(Paper.CARTA.widthPt(false), Paper.CARTA.heightPt(false), s)
        val oficio = MosaicLayout.grid(Paper.OFICIO.widthPt(false), Paper.OFICIO.heightPt(false), s)
        assertEquals(carta.cols, oficio.cols)
        assertEquals(true, oficio.rows > carta.rows)
    }

    @Test
    fun paginas() {
        assertEquals(0, MosaicLayout.pageCount(0, 12))
        assertEquals(3, MosaicLayout.pageCount(30, 12))
        assertEquals(1, MosaicLayout.pageCount(12, 12))
    }
}
