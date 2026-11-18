package mx.gestamercado.api

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate

/** Cliente REST contra un servidor HTTP local que imita los endpoints de la especificación. */
class GestaApiTest {
    private lateinit var server: HttpServer
    private lateinit var api: GestaApi
    private val recibidos = mutableListOf<Triple<String, String, String?>>() // método, ruta, Authorization
    private val cuerpos = mutableListOf<Map<String, Any?>>()
    private val pagosVistos = mutableSetOf<String>()

    @Before fun arrancar() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val ruta = ex.requestURI.toString()
            recibidos += Triple(ex.requestMethod, ruta, ex.requestHeaders.getFirst("Authorization"))
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val cuerpo = if (body.isBlank()) emptyMap() else Json.parse(body).asMap()
            if (body.isNotBlank()) cuerpos += cuerpo
            fun responder(code: Int, json: Any?, tipo: String = "application/json") {
                val b = if (json is ByteArray) json else Json.encode(json).toByteArray()
                ex.responseHeaders.add("Content-Type", tipo)
                ex.sendResponseHeaders(code, b.size.toLong()); ex.responseBody.use { it.write(b) }
            }
            when {
                ruta == Rutas.LOGIN && cuerpo["password"] == "malo" -> responder(401, mapOf("detail" to "Credenciales inválidas"))
                ruta == Rutas.LOGIN -> responder(200, mapOf("token" to "jwt.abc", "usuarioId" to "u-1", "username" to cuerpo["username"], "roles" to listOf("INSPECTOR"), "debeCambiarPass" to false, "mercadoId" to 3))
                ruta.startsWith(Rutas.PUEDE_ABRIR_JORNADA) -> responder(200, mapOf("puedeAbrir" to true))
                ruta == Rutas.COBRO_CAMPO -> {
                    val id = cuerpo["pagoId"] as String
                    if (!pagosVistos.add(id)) responder(409, mapOf("detail" to "pagoId duplicado")) else responder(201, mapOf("id" to id, "estado" to "APLICADO"))
                }
                ruta == Rutas.CERRAR_CORTE -> responder(201, mapOf("id" to "CC-1", "montoEsperado" to 1000.0, "estado" to "CERRADO"))
                ruta.endsWith("/pdf") -> responder(200, "%PDF-1.4 prueba".toByteArray(), "application/pdf")
                else -> responder(404, mapOf("message" to "no existe"))
            }
        }
        server.start()
        api = GestaApi("http://127.0.0.1:${server.address.port}/")
    }

    @After fun detener() = server.stop(0)

    @Test fun loginGuardaTokenYLoEnviaDespues() {
        val r = api.login(LoginRequest("insp.jramirez", "secreto123"))
        assertEquals(Perfil.INSPECTOR, r.perfil)
        assertEquals(3L, r.mercadoId)
        assertTrue(api.puedeAbrirJornada("u-1", 3).puedeAbrir)
        assertEquals("Bearer jwt.abc", recibidos.last().third)
        assertEquals("${Rutas.PUEDE_ABRIR_JORNADA}?cobradorId=u-1&mercadoId=3", recibidos.last().second)
    }

    @Test fun loginFallidoDaErrorConMensaje() {
        val e = runCatching { api.login(LoginRequest("x", "malo")) }.exceptionOrNull() as ApiError
        assertEquals(401, e.status)
        assertEquals("Credenciales inválidas", e.message)
    }

    @Test fun reenviarElMismoPagoEsIdempotente() {
        val r = CobroCampoRequest(Firma.nuevoUuid(), "A-08", "u-1", Firma.nuevoUuid(), "M01A9F-000001", dinero(20.0), Instant.now(), FormaPago.EFECTIVO).firmado("clave-de-prueba-123")
        api.enviar(Rutas.COBRO_CAMPO, r.toMap())
        val e = runCatching { api.enviar(Rutas.COBRO_CAMPO, r.toMap()) }.exceptionOrNull() as ApiError
        assertTrue(e.duplicado)
        assertEquals(r.hashOffline, cuerpos.first()["hashOffline"])
    }

    @Test fun corteCalculaDiferencia() {
        val c = api.cerrarCorte(CerrarCorteRequest("u-1", 3, LocalDate.of(2026, 11, 16), dinero(950.0), null))
        assertEquals(-50.0, c.diferencia!!, 0.001)
    }

    @Test fun descargaPdf() {
        assertArrayEquals("%PDF-1.4 prueba".toByteArray(), api.reciboPdf("p-1"))
    }

    @Test fun sinServidorEsErrorDeRed() {
        val e = runCatching { GestaApi("http://127.0.0.1:1").login(LoginRequest("a", "b")) }.exceptionOrNull() as ApiError
        assertTrue(e.sinRed)
    }
}
