# Epson Mosaico (Android, Kotlin + Compose)

Elige muchas imágenes de tu galería, define el tamaño de cada una (ej. 5 × 5 cm) y la app las
acomoda en hojas Carta, Oficio o A4. Puedes imprimirlas o guardarlas como PDF.
Usa el sistema de impresión de Android, así que sirve para la Epson L5590 y cualquier otra Epson.

## Cómo compilarlo desde cero (Windows)
1. Descarga e instala **Android Studio** desde https://developer.android.com/studio
   (ya trae Java y el SDK de Android; acepta todo con "Next" y deja la instalación estándar).
   Necesita internet y unos 8–10 GB libres.
2. Descomprime `EpsonMosaico.zip` en una carpeta sencilla (ej. `C:\EpsonMosaico`).
3. Abre Android Studio → **Open** → elige la carpeta `EpsonMosaico`.
4. Espera a que termine "Gradle sync" (la primera vez descarga mucho, puede tardar 10–30 min).
   Si pide instalar un SDK o "Update Gradle/AGP", acepta.

### Opción A: instalar en el celular con cable
1. En el celular: Ajustes → Acerca del teléfono → toca 7 veces "Número de compilación".
2. Ajustes → Opciones de desarrollador → activa **Depuración USB**.
3. Conecta el celular por USB y acepta el aviso "¿Permitir depuración?".
4. En Android Studio elige tu celular arriba y pulsa el botón verde ▶ (Run).

### Opción B: generar un APK y pasarlo
1. Menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Al terminar, pulsa "locate": el archivo es `app-debug.apk`
   (en `app/build/outputs/apk/debug/`).
3. Pásalo al celular (cable, Drive, WhatsApp) y ábrelo. Permite "instalar apps desconocidas" si lo pide.

## Requisito en el celular
Un servicio de impresión: **Epson Print Enabler** (Play Store) o **Mopria Print Service**
(viene en la mayoría de teléfonos). Celular e impresora en la misma red WiFi.

## Uso
- **Elegir imágenes**: se abre el selector de fotos, marca varias y confirma. Puedes repetir para agregar más.
- Papel, orientación, tamaño, margen y separación en cm.
- Copias por imagen, modo Llenar/Ajustar, líneas guía para cortar.
- La vista previa muestra cuántas caben por hoja y cuántas hojas saldrán.
- **Imprimir**: elige la L5590 en el diálogo. **Guardar como PDF**: elige dónde guardarlo.

Con 5 × 5 cm: Carta = 20 por hoja (4×5), Oficio = 24 por hoja (4×6).

## Estructura
- `MosaicLayout.kt`: cálculo de la rejilla (lógica pura, con tests).
- `MosaicPrintAdapter.kt`: dibuja las hojas, las imprime o las exporta a PDF.
- `MainActivity.kt`: pantalla y vista previa.
