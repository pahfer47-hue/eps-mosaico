package com.epsonmosaico

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Genera las hojas del mosaico y las entrega al servicio de impresión de Android.
 * Funciona con cualquier impresora Epson (o de otra marca) que tenga servicio de
 * impresión instalado: Epson Print Enabler o Mopria.
 */
class MosaicPrintAdapter(
    private val context: Context,
    private val uris: List<Uri>,
    private val settings: MosaicSettings
) : PrintDocumentAdapter() {

    private var pdf: PrintedPdfDocument? = null
    private val executor = Executors.newSingleThreadExecutor()

    // Cada imagen se repite "copies" veces seguidas.
    private val items: List<Uri> = uris.flatMap { u -> List(settings.copies.coerceAtLeast(1)) { u } }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val guidePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.3f
        color = Color.rgb(170, 170, 170)
    }

    private var lastUri: Uri? = null
    private var lastBitmap: Bitmap? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        pdf?.close()
        pdf = PrintedPdfDocument(context, newAttributes)

        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        val (w, h) = pageSizePt(newAttributes)
        val grid = MosaicLayout.grid(w, h, settings)
        val pages = MosaicLayout.pageCount(items.size, grid.perPage)

        if (pages <= 0) {
            callback.onLayoutFailed("El tamaño elegido no cabe en la hoja")
            return
        }

        val info = PrintDocumentInfo.Builder("mosaico.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(pages)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        executor.execute {
            try {
                val doc = pdf ?: throw IllegalStateException("Documento no preparado")
                var total = -1

                var pageIndex = 0
                while (total < 0 || pageIndex < total) {
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return@execute
                    }
                    val page = doc.startPage(pageIndex)
                    val pw = page.info.pageWidth.toFloat()
                    val ph = page.info.pageHeight.toFloat()
                    val grid = MosaicLayout.grid(pw, ph, settings)
                    if (total < 0) total = MosaicLayout.pageCount(items.size, grid.perPage)

                    drawPage(page.canvas, grid, pageIndex)
                    doc.finishPage(page)
                    pageIndex++
                }

                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    /** Guarda el mosaico como PDF (tamaño de hoja en puntos). Llamar fuera del hilo principal. */
    fun exportPdf(out: java.io.OutputStream, pageW: Int, pageH: Int) {
        val doc = android.graphics.pdf.PdfDocument()
        try {
            val grid = MosaicLayout.grid(pageW.toFloat(), pageH.toFloat(), settings)
            val pages = MosaicLayout.pageCount(items.size, grid.perPage)
            for (p in 0 until pages) {
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, p + 1).create()
                val page = doc.startPage(info)
                drawPage(page.canvas, grid, p)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
            lastBitmap = null
            lastUri = null
        }
    }

    override fun onFinish() {
        pdf?.close()
        pdf = null
        lastBitmap = null
        lastUri = null
        executor.shutdown()
    }

    private fun pageSizePt(attrs: PrintAttributes): Pair<Float, Float> {
        val media = attrs.mediaSize ?: return Pair(612f, 792f)
        // Android ya entrega el tamaño con la orientación elegida (vertical u horizontal).
        return Pair(media.widthMils / 1000f * 72f, media.heightMils / 1000f * 72f)
    }

    private fun drawPage(canvas: Canvas, grid: Grid, pageIndex: Int) {
        val start = pageIndex * grid.perPage
        val end = min(start + grid.perPage, items.size)
        val targetPx = (max(grid.tileW, grid.tileH) / 72f * 300f).toInt().coerceAtLeast(64)

        for (i in start until end) {
            val slot = grid.slot(i - start)
            val bmp = bitmapFor(items[i], targetPx) ?: continue
            val dst = RectF(slot.left, slot.top, slot.left + slot.width, slot.top + slot.height)
            drawBitmapInto(canvas, bmp, dst)
            if (settings.cutGuides) canvas.drawRect(dst, guidePaint)
        }
    }

    private fun drawBitmapInto(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val dstRatio = dst.width() / dst.height()
        val srcRatio = bw / bh

        when (settings.fit) {
            FitMode.FILL -> {
                val src: Rect = if (srcRatio > dstRatio) {
                    val w = (bh * dstRatio).toInt()
                    val x = ((bw - w) / 2).toInt()
                    Rect(x, 0, x + w, bmp.height)
                } else {
                    val h = (bw / dstRatio).toInt()
                    val y = ((bh - h) / 2).toInt()
                    Rect(0, y, bmp.width, y + h)
                }
                canvas.drawBitmap(bmp, src, dst, bitmapPaint)
            }
            FitMode.FIT -> {
                val scale = min(dst.width() / bw, dst.height() / bh)
                val w = bw * scale
                val h = bh * scale
                val x = dst.left + (dst.width() - w) / 2
                val y = dst.top + (dst.height() - h) / 2
                canvas.drawBitmap(bmp, null, RectF(x, y, x + w, y + h), bitmapPaint)
            }
        }
    }

    private fun bitmapFor(uri: Uri, targetPx: Int): Bitmap? {
        if (uri == lastUri) return lastBitmap
        val bmp = loadBitmap(uri, targetPx)
        lastUri = uri
        lastBitmap = bmp
        return bmp
    }

    private fun loadBitmap(uri: Uri, targetPx: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
    }
}
