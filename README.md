# Gesta Mercado · App Android (Kotlin + Jetpack Compose)

Prototipo **nativo, funcional y navegable** de los dos módulos móviles de la Especificación de Flujos V7.3, con **datos simulados** en memoria. Mismo diseño que la web: tinta `#121517`, magenta `#C80766`, teal `#1C9988`, tipografías **Archivo + DM Mono** (incluidas en `res/font`, funcionan sin internet) y el logotipo «Padrón».

- Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09.03) · Material 3
- Android Gradle Plugin 8.5.2 · Gradle 8.9 (wrapper incluido) · JDK 17
- minSdk 26 (Android 8.0) · targetSdk 34
- Dos modos: **simulado** (por defecto, datos en memoria) o **backend real** Spring Boot con JWT (`-PgestaApiUrl=…`)
- Contratos de la *Especificación Técnica de Vistas, Campos y Contratos* (Parte 2 · App Móvil): M-L1 a M-L4 y M-I1 a M-I5

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

## Modo simulado o backend real

- **Simulado** (por defecto): no se configura nada. Las cuentas de prueba aparecen en el inicio de sesión y aceptan cualquier contraseña.
- **Backend real**: compila con la URL de la API. En el emulador, `10.0.2.2` es el `localhost` de la PC.

```bash
./gradlew installDebug -PgestaApiUrl=http://10.0.2.2:8080
```

En *debug* se permite `http://`; en *release* solo `https://`.

## Cómo recorrer la app

Un solo **inicio de sesión** (`POST /api/v1/auth/login`). El perfil se decide por `roles`: `LOCATARIO` abre el perfil A; `INSPECTOR` o `ADMINISTRADOR`, el perfil B.

**Perfil A · Locatario** (`loc.guadalupe.hernandez`)
- **M-L1** La cuenta nace con `debeCambiarPass = true` (la crea el administrador en la web, W4): primero pide cambiar la contraseña (`POST /api/v1/auth/cambiar-password`).
- **M-L2** Mis locales y mi concesión: es titular de A-08 y suplente de A-09 (selector de local). Tarjetón QR, datos de la concesión y **cédula de concesión PDF con QR** para abrir o compartir.
- **M-L3** Estado de cuenta: adeudo, saldo a favor y cargos con filtros.
- **M-L4** Recibos: lista de pagos y **recibo oficial PDF con QR**.

**Perfil B · Inspector** (`insp.jramirez`; `insp.lmorales` muestra la jornada bloqueada; `c.najera` entra como ADMINISTRADOR)
- **M-I1** Terminal POS enrolada (`dispositivoId`, prefijo de folios y secreto HMAC capturados del panel web W5; toca la franja inferior del login) y `GET /api/v1/cortes-caja/puede-abrir-jornada`. Si devuelve `false` se muestra el bloqueo con las jornadas pendientes.
- **M-I2** Pase de lista desde la tarjeta del local: `PRESENTE`, `AUSENTE_CERRADO` o `NEGATIVA_PAGO`, con notas, fecha automática y **GPS obligatorio** (`POST /api/v1/asistencias`).
- **M-I3** Cobro offline-first (`POST /api/v1/cobranza/campo`):
  - `pagoId` UUID v4 y folio `SERIE-000001`.
  - `hashOffline` firmado con HMAC-SHA256.
  - GPS, `unidades` y, opcionalmente, el cargo que se cubre primero (`fechaInicioCargo`).
  - Cada cobro queda en cola y se sube solo al haber red.
  - Si el servidor responde 409 al reenviar un `pagoId`, el cobro se marca como sincronizado (reintento idempotente).
  - Los cobros con más de `diasMaxAntiguedadCobro` días pasan a revisión.
- **M-I4** **+ Feria** en la ronda, o en un espacio libre de la zona de feria (F-01 a F-04). Se elige comerciante, giro, fecha de inicio, de 1 a 30 días y monto; el permiso se firma y se encola (`POST /api/v1/concesiones/feria-express`).
- **M-I5** Pestaña **Corte**: **corte ciego**. El inspector cuenta billetes y monedas y declara el efectivo sin ver el total del sistema (`POST /api/v1/cortes-caja/cerrar`). Al cerrar se muestra el sobrante o faltante y la jornada queda cerrada.
- En la barra superior, **OFFLINE/EN LÍNEA** simula la red y **ESC/POS** enlaza la impresora.

### Firma `hashOffline`

La especificación pide firmar «la cadena canónica del cobro», pero no define su formato. La app usa el siguiente, que el backend debe replicar:

```
cobro: pagoId|localId|cobradorId|dispositivoId|folio|montoCobrado|fecha|formaPago
feria: pagoId|localId|titularPersonaId|giroId|fechaInicio|dias|dispositivoId|folio|montoCobrado|formaPago
```

- `montoCobrado` va con 2 decimales (`150.00`) y `fecha` en ISO-8601 UTC con segundos.
- Si el secreto tiene 64 caracteres hexadecimales se usan sus 32 bytes; si no, sus bytes UTF-8.
- La implementación está en `api/Firma.kt` y el vector de prueba en `ContratosTest`.

### Supuestos sobre la API (ajustables en `api/` y `data/Remoto.kt`)

- **Cambio de contraseña:** se envía `{ passwordActual, passwordNueva }`; la especificación solo da el endpoint.
- **Login:** la respuesta trae `token`, `usuarioId`, `roles` y `debeCambiarPass`, y de forma opcional `mercadoId`, `nombre` y `personaId`.
- **`puede-abrir-jornada`:** responde un booleano o un objeto `{ puedeAbrir, motivo, pendientes[] }`.
- **Feria express:** el buscador usa `GET /api/v1/personas?q=` y los giros salen de `GET /api/v1/giros`.
- **Respuestas de lectura** (locales, concesiones, cargos, pagos): se mapean aceptando varios nombres de campo.

## Estructura

```
app/src/main/java/mx/gestamercado/
  MainActivity.kt            rutas, backend (simulado/REST), auto-sincronización y snackbar
  api/                       Kotlin puro (sin Android): contratos JSON exactos, firma HMAC,
                             cliente REST (HttpURLConnection + JWT) e interfaz Backend
  data/AppState.kt           estado: sesión, terminal, padrón, cola offline, ferias y cortes
  data/Simulado.kt           backend en memoria con cuentas de prueba
  data/Sincronizador.kt      envío de la cola (idempotente, antigüedad máxima, revisión)
  data/Remoto.kt             mapeo de respuestas del backend real
  data/Gps.kt                ubicación (LocationManager, sin Play Services)
  data/Documentos.kt         cédula y recibo PDF con QR (descarga o generación local) + FileProvider
  data/Prefs.kt              terminal y consecutivo de folios persistentes
  ui/                        tema y componentes
  screens/Acceso.kt          M-L1/M-I1: login, cambio de contraseña, terminal POS
  screens/inspector/         Acceso (bloqueo de jornada) · Ronda (mapa, tarjeta) · Campo (pase de lista, alta) ·
                             Cobro (cobro firmado, comprobante) · Feria (feria express) · Turno (sincronización, corte ciego)
  screens/locatario/         Locatario (mis locales y concesión, estado de cuenta, simulador, recibos)
app/src/test/java/…/api/     pruebas JVM: contratos, HMAC (RFC 4231) y cliente REST contra servidor local
```

Pruebas: `./gradlew testDebugUnitTest`

## Siguientes pasos para producción

- Persistir la cola offline en Room/SQLite (hoy vive en memoria y se pierde si se cierra la app con registros sin subir).
- Guardar el secreto HMAC en Android Keystore / EncryptedSharedPreferences.
- MapLibre GL Android (mapa offline), CameraX + ML Kit (QR) y socket Bluetooth ESC/POS.
- Firma de release propia y `isMinifyEnabled = true` antes de publicar en Play Store.
