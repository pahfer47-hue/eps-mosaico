package com.epsonmosaico

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) { MosaicScreen() }
            }
        }
    }
}

private fun parseCm(text: String, default: Float): Float =
    text.replace(',', '.').toFloatOrNull() ?: default

@Composable
fun MosaicScreen() {
    val context = LocalContext.current

    var uris by remember { mutableStateOf(listOf<Uri>()) }
    var paper by remember { mutableStateOf(Paper.CARTA) }
    var landscape by remember { mutableStateOf(false) }
    var wText by remember { mutableStateOf("5") }
    var hText by remember { mutableStateOf("5") }
    var marginText by remember { mutableStateOf("0.4") }
    var gapText by remember { mutableStateOf("0.1") }
    var copies by remember { mutableIntStateOf(1) }
    var fit by remember { mutableStateOf(FitMode.FILL) }
    var guides by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { picked ->
        if (picked.isNotEmpty()) uris = uris + picked.filter { it !in uris }
    }

    val settings = MosaicSettings(
        tileWidthCm = parseCm(wText, 5f),
        tileHeightCm = parseCm(hText, 5f),
        marginCm = parseCm(marginText, 0.4f),
        gapCm = parseCm(gapText, 0.1f),
        copies = copies,
        fit = fit,
        cutGuides = guides
    )
    val pageW = paper.widthPt(landscape)
    val pageH = paper.heightPt(landscape)

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { dest ->
        if (dest != null) savePdf(context, dest, uris, settings, pageW.toInt(), pageH.toInt())
    }
    val grid = MosaicLayout.grid(pageW, pageH, settings)
    val totalItems = uris.size * copies
    val pages = MosaicLayout.pageCount(totalItems, grid.perPage)

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Epson Mosaico", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Elegir imágenes") }
            if (uris.isNotEmpty()) {
                OutlinedButton(onClick = { uris = emptyList() }) { Text("Limpiar") }
            }
            Text("${uris.size} seleccionadas")
        }

        Text("Papel", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper.values().forEach { p ->
                FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(p.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = landscape, onCheckedChange = { landscape = it })
            Text("Horizontal")
        }

        Text("Tamaño de cada imagen (cm)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Ancho", wText, { wText = it }, Modifier.weight(1f))
            NumberField("Alto", hText, { hText = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Margen", marginText, { marginText = it }, Modifier.weight(1f))
            NumberField("Separación", gapText, { gapText = it }, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Copias de cada una:")
            OutlinedButton(onClick = { if (copies > 1) copies-- }) { Text("−") }
            Text("$copies")
            OutlinedButton(onClick = { if (copies < 99) copies++ }) { Text("+") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitMode.values().forEach { m ->
                FilterChip(selected = fit == m, onClick = { fit = m }, label = { Text(m.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = guides, onCheckedChange = { guides = it })
            Text("Líneas guía para cortar")
        }

        if (grid.perPage > 0) {
            Text("${grid.cols} × ${grid.rows} = ${grid.perPage} por hoja · $pages hoja(s)")
        } else {
            Text("Ese tamaño no cabe en la hoja", color = MaterialTheme.colorScheme.error)
        }

        PagePreview(pageW, pageH, grid, totalItems)

        Button(
            enabled = uris.isNotEmpty() && grid.perPage > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = { startPrint(context, uris, settings, paper, landscape) }
        ) { Text("Imprimir") }

        OutlinedButton(
            enabled = uris.isNotEmpty() && grid.perPage > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = { pdfSaver.launch("mosaico.pdf") }
        ) { Text("Guardar como PDF") }
    }
}

private fun savePdf(context: Context, dest: Uri, uris: List<Uri>, settings: MosaicSettings, w: Int, h: Int) {
    val appContext = context.applicationContext
    val main = Handler(Looper.getMainLooper())
    Toast.makeText(appContext, "Generando PDF...", Toast.LENGTH_SHORT).show()
    Thread {
        val msg = try {
            appContext.contentResolver.openOutputStream(dest)?.use { out ->
                MosaicPrintAdapter(appContext, uris, settings).exportPdf(out, w, h)
            }
            "PDF guardado"
        } catch (e: Exception) {
            "No se pudo guardar: ${e.message}"
        }
        main.post { Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show() }
    }.start()
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun PagePreview(pageW: Float, pageH: Float, grid: Grid, totalItems: Int) {
    val line = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val scale = min(size.width / pageW, size.height / pageH)
        val ox = (size.width - pageW * scale) / 2f
        val oy = (size.height - pageH * scale) / 2f
        drawRect(Color.White, Offset(ox, oy), Size(pageW * scale, pageH * scale))
        drawRect(line, Offset(ox, oy), Size(pageW * scale, pageH * scale), style = Stroke(2f))
        if (grid.perPage > 0) {
            val onFirstPage = min(grid.perPage, totalItems)
            for (i in 0 until grid.perPage) {
                val s = grid.slot(i)
                val topLeft = Offset(ox + s.left * scale, oy + s.top * scale)
                val sz = Size(s.width * scale, s.height * scale)
                if (i < onFirstPage) drawRect(fill.copy(alpha = 0.25f), topLeft, sz)
                drawRect(line, topLeft, sz, style = Stroke(1f))
            }
        }
    }
}

private fun startPrint(
    context: Context,
    uris: List<Uri>,
    settings: MosaicSettings,
    paper: Paper,
    landscape: Boolean
) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val base = PrintAttributes.MediaSize("mosaico_${paper.name}", paper.label, paper.widthMils, paper.heightMils)
    val attrs = PrintAttributes.Builder()
        .setMediaSize(if (landscape) base.asLandscape() else base)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()
    pm.print("Mosaico", MosaicPrintAdapter(context.applicationContext, uris, settings), attrs)
}
