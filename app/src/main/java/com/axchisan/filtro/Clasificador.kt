package com.axchisan.filtro

/** Tipos de número según el plan de numeración colombiano. */
enum class TipoNumero(val etiqueta: String) {
    MOVIL("Celular"),
    FIJO("Fijo"),
    LINEA_01800("Línea 01 8000"),
    CORTO("Número corto"),
    INTERNACIONAL("Internacional"),
    OCULTO("Número oculto"),
    FALSO("Formato falso"),
}

enum class Veredicto { PERMITIR, SILENCIAR, RECHAZAR }

data class Numero(val tipo: TipoNumero, val canonico: String)

data class Decision(val veredicto: Veredicto, val motivo: String)

data class Reglas(
    val rechazarInternacionales: Boolean = true,
    val rechazarFijos: Boolean = true,
    val dejarPasarInsistentes: Boolean = true,
)

/** Contexto de la llamada que el servicio arma antes de decidir. */
data class Contexto(
    val enContactos: Boolean = false,
    val confiable: Boolean = false,
    val bloqueado: Boolean = false,
    /** Llamadas previas del mismo número en los últimos minutos (ver [Clasificador.VENTANA_INSISTENCIA_MS]). */
    val llamadasRecientes: Int = 0,
)

object Clasificador {

    const val VENTANA_INSISTENCIA_MS = 3 * 60 * 1000L

    /**
     * Normaliza el número tal como lo entrega la red. Los números colombianos quedan en 10 dígitos
     * (3xx… celular, 60x… fijo); los que no encajan en el plan se marcan como [TipoNumero.FALSO],
     * que es como llega casi todo el identificador falsificado (00573…, 3152… de 12 dígitos, etc.).
     */
    fun normalizar(crudo: String?): Numero {
        val limpio = crudo.orEmpty().filter { it.isDigit() || it == '+' }
        if (limpio.isEmpty() || limpio.all { it == '+' }) return Numero(TipoNumero.OCULTO, "")

        if (limpio.startsWith("+57")) return nacional(limpio.removePrefix("+57"), limpio)
        if (limpio.startsWith("+")) return Numero(TipoNumero.INTERNACIONAL, limpio)
        // 00 es el prefijo de salida internacional: un número colombiano que llega como 0057… viene
        // de una ruta internacional que suplanta un número local.
        if (limpio.startsWith("0057")) return Numero(TipoNumero.FALSO, limpio)
        if (limpio.startsWith("00")) return Numero(TipoNumero.INTERNACIONAL, "+" + limpio.drop(2))
        if (limpio.length == 12 && limpio.startsWith("57")) return nacional(limpio.drop(2), limpio)
        if (limpio.length == 10) return nacional(limpio, limpio)
        if (limpio.length == 12 && limpio.startsWith("018000")) return Numero(TipoNumero.LINEA_01800, limpio)
        if (limpio.length <= 6) return Numero(TipoNumero.CORTO, limpio)
        return Numero(TipoNumero.FALSO, limpio)
    }

    private fun nacional(diez: String, original: String): Numero = when {
        diez.length != 10 -> Numero(TipoNumero.FALSO, original)
        diez.startsWith("3") -> Numero(TipoNumero.MOVIL, diez)
        diez.startsWith("60") && diez[2] in '1'..'8' -> Numero(TipoNumero.FIJO, diez)
        else -> Numero(TipoNumero.FALSO, original)
    }

    fun decidir(numero: Numero, ctx: Contexto, reglas: Reglas): Decision {
        if (ctx.enContactos) return Decision(Veredicto.PERMITIR, "Contacto")
        if (ctx.confiable) return Decision(Veredicto.PERMITIR, "Marcado como confiable")
        if (ctx.bloqueado) return Decision(Veredicto.RECHAZAR, "En tu lista de bloqueo")
        if (numero.tipo == TipoNumero.FALSO) return Decision(Veredicto.RECHAZAR, "Número con formato falso")
        if (numero.tipo == TipoNumero.INTERNACIONAL && reglas.rechazarInternacionales) {
            return Decision(Veredicto.RECHAZAR, "Llamada internacional")
        }
        // Quien insiste en pocos minutos suele ser real (domicilios, emergencias); los marcadores
        // automáticos rara vez repiten tan rápido.
        if (reglas.dejarPasarInsistentes && ctx.llamadasRecientes > 0 && numero.tipo != TipoNumero.OCULTO) {
            return Decision(Veredicto.PERMITIR, "Insistió en menos de 3 minutos")
        }
        if (numero.tipo == TipoNumero.FIJO && reglas.rechazarFijos) {
            return Decision(Veredicto.RECHAZAR, "Fijo desconocido (posible call center)")
        }
        return Decision(Veredicto.SILENCIAR, "${numero.tipo.etiqueta} desconocido")
    }

    /** 3001234567 → 300 123 4567; 6011234567 → 601 123 4567. */
    fun formatear(numero: Numero): String {
        val c = numero.canonico
        return when (numero.tipo) {
            TipoNumero.MOVIL, TipoNumero.FIJO -> "${c.take(3)} ${c.substring(3, 6)} ${c.drop(6)}"
            TipoNumero.LINEA_01800 -> "01 8000 ${c.substring(6, 9)} ${c.drop(9)}"
            TipoNumero.OCULTO -> "Número oculto"
            else -> c
        }
    }
}
