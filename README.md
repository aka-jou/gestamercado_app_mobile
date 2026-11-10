# Gesta Mercado · App Android (Kotlin + Jetpack Compose)

Prototipo **nativo, funcional y navegable** de los dos módulos móviles de la Especificación de Flujos V7.3, con **datos simulados** en memoria. Mismo diseño que la web: tinta `#121517`, magenta `#C80766`, teal `#1C9988`, tipografías **Archivo + DM Mono** (incluidas en `res/font`, funcionan sin internet) y el logotipo «Padrón».

- Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09.03) · Material 3
- Android Gradle Plugin 8.5.2 · Gradle 8.9 (wrapper incluido) · JDK 17
- minSdk 26 (Android 8.0) · targetSdk 34
- Sin servidor: todo vive en `AppState` (se reinicia al cerrar la app)

## Opción A · Android Studio (recomendada)

1. Instala **Android Studio** (Koala o más reciente).
2. *File → Open…* y elige la carpeta `gesta_mercado_kotlin` (la que contiene `settings.gradle.kts`).
3. Espera a que termine *Gradle Sync* (la primera vez descarga Gradle y dependencias).
   Si Studio sugiere actualizar AGP, puedes aceptar o posponerlo; el proyecto compila tal cual.
4. En tu **Samsung**: *Ajustes → Acerca del teléfono → Información de software* → toca **Número de compilación** 7 veces → *Opciones de desarrollador* → activa **Depuración USB**. Conéctalo por cable y acepta el aviso.
5. Selecciona tu equipo en la barra superior y pulsa **Run ▶**.

## Opción B · Línea de comandos (APK para instalar)

Necesitas JDK 17+ y el Android SDK (con `ANDROID_HOME` configurado o un `local.properties` con `sdk.dir=...`).

```bash
# Windows
gradlew.bat assembleRelease
# macOS / Linux
chmod +x gradlew && ./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (firmado con la clave de depuración para poder instalarlo directo). Pásalo al teléfono y permite «Instalar apps desconocidas».

Para instalar con el teléfono conectado: `gradlew installDebug`.

## Cómo recorrer la app

Pantalla inicial: **Inspector** o **Locatario**.

**Inspector** (`insp.jramirez`, cualquier contraseña)
- `insp.lmorales` → 1.1.B Bloqueo por corte ciego pendiente.
- Ronda (1.2.A): mapa con colores por estatus, filtros, buscador y botón **QR** (escaneo simulado → A-08).
- Toca un local → tarjeta resumen (1.2.B) → **Realizar cobro** (1.3.A): FIFO en vivo, unidades, forma de pago y quién paga → **Comprobante** (1.3.B) con QR real (ZXing) y ticket ESC/POS simulado.
- **Registrar incidencia** (1.4.A): acta con foto simulada y contador de actas (prueba con B-04: llega a 3 de 3).
- **+ Alta** (1.5.A): toca el mapa para agregar vértices; calcula área, perímetro y centroide, y bloquea por traslape, primer cuadro o < 100 m.
- Pestañas **Sincronización** (1.6.A) y **Corte** (1.6.B).
- En la barra superior toca **OFFLINE/EN LÍNEA** para simular la red y **ESC/POS** para enlazar o desenlazar la impresora.

**Locatario**
- 2.1.A Activación (CURP precargada válida). Cambia un carácter para ver el flujo alterno «no empadronado».
- 2.2.A Mi local / tarjetón QR · 2.3.A Estado de cuenta · 2.4.A Simulador UMA · 2.5.A Recibos y detalle.

Un cobro hecho como inspector en **A-08** se refleja en el estado de cuenta y en los recibos del locatario mientras la app siga abierta.

## Estructura

```
app/src/main/java/mx/gestamercado/
  MainActivity.kt            actividad, rutas (back stack) y snackbar
  data/AppState.kt           modelos, datos simulados y lógica (FIFO, sync, corte)
  util/Format.kt             dinero, fechas y fórmulas de cuota en UMA
  ui/Theme.kt                colores y tipografías
  ui/Components.kt           logo, barras, botones, campos, tarjetas, QR
  screens/RoleChooser.kt
  screens/inspector/         Acceso (login, bloqueo) · Ronda (mapa, tarjeta) ·
                             Cobro (cobro, comprobante, historial) ·
                             Campo (acta, alta de local) · Turno (sync, corte)
  screens/locatario/         Acceso (activación, no empadronado) ·
                             Locatario (mi local, cuenta, simulador, recibos)
app/src/main/res/font/       Archivo (variable) y DM Mono
```

## Siguientes pasos para producción

- Room/SQLite para la cola offline y Retrofit hacia el backend Spring Boot.
- MapLibre GL Android (mapa offline), CameraX + ML Kit (QR), Fused Location (GPS) y socket Bluetooth ESC/POS.
- Firma de release propia y `isMinifyEnabled = true` antes de publicar en Play Store.
