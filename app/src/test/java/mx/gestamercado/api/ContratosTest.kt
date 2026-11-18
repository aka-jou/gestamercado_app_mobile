package mx.gestamercado.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ContratosTest {

    private val secreto = "4f1c2a9be07d35c86a1e9f04b2d7c3e5a8f6b1d0c9e2a7f4b3d8e1c6a5f09b72"

    private fun cobro() = CobroCampoRequest(
        pagoId = "7d0f3f4e-5b2a-4c1e-9a3d-2f6b8c1e0a94",
        localId = "A-08",
        cobradorId = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        dispositivoId = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        folio = Firma.folio("M01A9F", 452),
        montoCobrado = dinero(150.0),
        fecha = Instant.parse("2026-11-16T15:30:00.123Z"),
        formaPago = FormaPago.EFECTIVO,
        ubicacion = GeoPoint(-98.20634, 19.04141),
        unidades = BigDecimal.ONE,
    )

    @Test fun hmacCoincideConVectorRfc4231() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", Firma.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
    }

    @Test fun cadenaCanonicaDelCobro() {
        assertEquals(
            "7d0f3f4e-5b2a-4c1e-9a3d-2f6b8c1e0a94|A-08|3fa85f64-5717-4562-b3fc-2c963f66afa6|9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d|M01A9F-000452|150.00|2026-11-16T15:30:00Z|EFECTIVO",
            cobro().canonica(),
        )
    }

    @Test fun cobroFirmadoEsValidoYVerificable() {
        val r = cobro().firmado(secreto)
        assertTrue(Firma.RE_HASH.matches(r.hashOffline))
        assertEquals(emptyList<String>(), r.validar())
        assertTrue(Firma.verificar(secreto, r.canonica(), r.hashOffline))
        assertFalse(Firma.verificar(secreto, r.copy(montoCobrado = dinero(151.0)).canonica(), r.hashOffline))
    }

    @Test fun cobroSinFirmaNoValida() {
        assertTrue(cobro().validar().any { "HMAC" in it })
    }

    @Test fun jsonDelCobroUsaLosCamposDeLaEspecificacion() {
        val m = Json.parse(Json.encode(cobro().firmado(secreto).toMap())).asMap()
        assertEquals(
            listOf("pagoId", "localId", "cobradorId", "dispositivoId", "folio", "montoCobrado", "fecha", "hashOffline", "formaPago", "ubicacion", "unidades", "fechaInicioCargo"),
            m.keys.toList(),
        )
        assertEquals(150.0, m["montoCobrado"])
        assertTrue(Json.encode(cobro().toMap()).contains("\"montoCobrado\":150.00"))
        assertEquals("2026-11-16T15:30:00Z", m["fecha"])
        assertEquals(mapOf("type" to "Point", "coordinates" to listOf(-98.20634, 19.04141)), m["ubicacion"])
    }

    @Test fun feriaValidaDuracion() {
        val f = FeriaExpressRequest("F-01", "p1", 3, LocalDate.of(2026, 11, 20), 31, Firma.nuevoUuid(), "d", "MC01-000001", dinero(80.0))
        assertTrue(f.validar().any { "1 a 30" in it })
        assertEquals(LocalDate.of(2026, 12, 20), f.fechaFin)
    }

    @Test fun perfilSegunRoles() {
        assertEquals(Perfil.LOCATARIO, LoginResponse.from(mapOf("token" to "t", "roles" to listOf("LOCATARIO"))).perfil)
        assertEquals(Perfil.INSPECTOR, LoginResponse.from(mapOf("token" to "t", "roles" to listOf("ADMINISTRADOR"))).perfil)
        assertEquals(null, LoginResponse.from(mapOf("token" to "t", "roles" to listOf("TESORERIA"))).perfil)
    }

    @Test fun jornadaAceptaBooleanoUObjeto() {
        assertTrue(Jornada.from(true).puedeAbrir)
        val j = Jornada.from(mapOf("puedeAbrir" to false, "pendientes" to listOf(mapOf("fecha" to "2026-10-05", "pagos" to 14, "efectivo" to 1260.0))))
        assertFalse(j.puedeAbrir)
        assertEquals(1260.0, j.pendientes.single().efectivo, 0.0)
    }

    @Test fun jsonEscapaYParsea() {
        val s = Json.encode(mapOf("a" to "línea\n\"x\"", "b" to listOf(1, 2.5, true, null)))
        assertEquals(mapOf("a" to "línea\n\"x\"", "b" to listOf(1L, 2.5, true, null)), Json.parse(s))
    }
}
