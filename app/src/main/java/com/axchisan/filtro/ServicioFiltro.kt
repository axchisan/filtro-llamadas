package com.axchisan.filtro

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log

/**
 * Android lo invoca antes de que el teléfono timbre. Hay 5 segundos para responder, así que todo
 * se resuelve en local: contactos, listas propias y reglas. No se usa internet.
 */
class ServicioFiltro : CallScreeningService() {

    override fun onScreenCall(detalles: Call.Details) {
        if (detalles.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(detalles, CallResponse.Builder().build())
            return
        }

        val decision = runCatching { evaluar(detalles) }.getOrElse { e ->
            Log.e(TAG, "Fallo al evaluar la llamada; se deja pasar", e)
            null
        }
        respondToCall(detalles, respuestaPara(decision?.second?.veredicto ?: Veredicto.PERMITIR))

        if (decision != null) {
            val (numero, d) = decision
            runCatching { Avisos.notificar(this, numero, d) }
                .onFailure { Log.e(TAG, "No se pudo mostrar el aviso", it) }
        }
    }

    private fun evaluar(detalles: Call.Details): Pair<Numero, Decision> {
        val oculto = detalles.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val crudo = if (oculto) null else detalles.handle?.schemeSpecificPart
        val numero = Clasificador.normalizar(crudo)
        val almacen = Almacen.de(this)
        val ahora = System.currentTimeMillis()

        val lista = almacen.listaDe(numero.canonico)
        val ctx = Contexto(
            enContactos = crudo != null && estaEnContactos(crudo),
            confiable = lista == Lista.CONFIABLES,
            bloqueado = lista == Lista.BLOQUEADOS,
            llamadasRecientes = almacen.llamadasDesde(numero.canonico, ahora - Clasificador.VENTANA_INSISTENCIA_MS),
        )
        val decision = Clasificador.decidir(numero, ctx, almacen.reglas)
        // Las llamadas de contactos no se guardan: el historial solo cuenta desconocidos.
        if (!ctx.enContactos) almacen.registrar(numero, decision, ahora)
        Log.i(TAG, "${numero.tipo} → ${decision.veredicto} (${decision.motivo})")
        return numero to decision
    }

    private fun estaEnContactos(numero: String): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero))
        return contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }

    private fun respuestaPara(veredicto: Veredicto): CallResponse = when (veredicto) {
        Veredicto.PERMITIR -> CallResponse.Builder().build()
        // La llamada entra en silencio: aparece en pantalla y se puede contestar si se quiere.
        Veredicto.SILENCIAR -> CallResponse.Builder().setSilenceCall(true).build()
        // Se rechaza sin notificación de llamada perdida, pero queda en el registro de llamadas.
        Veredicto.RECHAZAR -> CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .build()
    }

    private companion object {
        const val TAG = "FiltroLlamadas"
    }
}
