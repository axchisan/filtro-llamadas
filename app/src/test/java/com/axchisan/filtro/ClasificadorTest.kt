package com.axchisan.filtro

import org.junit.Assert.assertEquals
import org.junit.Test

class ClasificadorTest {

    private fun tipo(n: String?) = Clasificador.normalizar(n).tipo
    private val reglas = Reglas()

    @Test
    fun numerosColombianosValidos() {
        assertEquals(Numero(TipoNumero.MOVIL, "3001234567"), Clasificador.normalizar("+57 300 123 4567"))
        assertEquals(Numero(TipoNumero.MOVIL, "3001234567"), Clasificador.normalizar("3001234567"))
        assertEquals(Numero(TipoNumero.MOVIL, "3001234567"), Clasificador.normalizar("573001234567"))
        assertEquals(Numero(TipoNumero.FIJO, "6017654321"), Clasificador.normalizar("601-765-4321"))
        assertEquals(TipoNumero.LINEA_01800, tipo("018000912345"))
        assertEquals(TipoNumero.CORTO, tipo("611"))
    }

    @Test
    fun formatosFalsosVistosEnElHistorial() {
        assertEquals(TipoNumero.FALSO, tipo("00573001234567"))   // número colombiano por ruta internacional
        assertEquals(TipoNumero.FALSO, tipo("315212345678"))     // 12 dígitos sin prefijo de país
        assertEquals(TipoNumero.FALSO, tipo("+5730012345"))      // +57 con 8 dígitos
        assertEquals(TipoNumero.FALSO, tipo("5001234567"))       // 10 dígitos fuera del plan
        assertEquals(TipoNumero.FALSO, tipo("6091234567"))       // indicativo 609 no existe
    }

    @Test
    fun internacionalesYOcultos() {
        assertEquals(TipoNumero.INTERNACIONAL, tipo("+15731234567"))
        assertEquals(TipoNumero.INTERNACIONAL, tipo("+2349012345678"))
        assertEquals(Numero(TipoNumero.INTERNACIONAL, "+34612345678"), Clasificador.normalizar("0034612345678"))
        assertEquals(TipoNumero.OCULTO, tipo(null))
        assertEquals(TipoNumero.OCULTO, tipo(""))
    }

    @Test
    fun contactosYConfiablesSiemprePasan() {
        val falso = Clasificador.normalizar("00573001234567")
        assertEquals(Veredicto.PERMITIR, Clasificador.decidir(falso, Contexto(enContactos = true), reglas).veredicto)
        assertEquals(Veredicto.PERMITIR, Clasificador.decidir(falso, Contexto(confiable = true), reglas).veredicto)
    }

    @Test
    fun reglasPorTipo() {
        fun v(n: String, ctx: Contexto = Contexto(), r: Reglas = reglas) =
            Clasificador.decidir(Clasificador.normalizar(n), ctx, r).veredicto

        assertEquals(Veredicto.SILENCIAR, v("3001234567"))
        assertEquals(Veredicto.RECHAZAR, v("3001234567", Contexto(bloqueado = true)))
        assertEquals(Veredicto.RECHAZAR, v("00573001234567"))
        assertEquals(Veredicto.RECHAZAR, v("+15731234567"))
        assertEquals(Veredicto.SILENCIAR, v("+15731234567", r = reglas.copy(rechazarInternacionales = false)))
        assertEquals(Veredicto.RECHAZAR, v("6017654321"))
        assertEquals(Veredicto.SILENCIAR, v("6017654321", r = reglas.copy(rechazarFijos = false)))
        assertEquals(Veredicto.SILENCIAR, v(""))
    }

    @Test
    fun quienInsistePasaSalvoFalsosEInternacionales() {
        fun v(n: String, r: Reglas = reglas) =
            Clasificador.decidir(Clasificador.normalizar(n), Contexto(llamadasRecientes = 1), r).veredicto

        assertEquals(Veredicto.PERMITIR, v("3001234567"))
        assertEquals(Veredicto.PERMITIR, v("6017654321"))
        assertEquals(Veredicto.RECHAZAR, v("00573001234567"))
        assertEquals(Veredicto.RECHAZAR, v("+15731234567"))
        assertEquals(Veredicto.SILENCIAR, v("3001234567", reglas.copy(dejarPasarInsistentes = false)))
    }

    @Test
    fun formato() {
        assertEquals("300 123 4567", Clasificador.formatear(Clasificador.normalizar("3001234567")))
        assertEquals("01 8000 912 345", Clasificador.formatear(Clasificador.normalizar("018000912345")))
    }
}
