package com.axchisan.filtro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Notificaciones discretas: sin sonido, sin ventanas flotantes. */
object Avisos {

    private const val CANAL_SILENCIADAS = "silenciadas"
    private const val CANAL_BLOQUEADAS = "bloqueadas"

    fun crearCanales(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CANAL_SILENCIADAS, "Llamadas silenciadas", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Desconocidos que entraron sin timbrar"
            },
            NotificationChannel(CANAL_BLOQUEADAS, "Llamadas bloqueadas", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Números falsos, internacionales o de tu lista de bloqueo"
            },
        ))
    }

    fun notificar(context: Context, numero: Numero, decision: Decision) {
        if (decision.veredicto == Veredicto.PERMITIR) return
        crearCanales(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val almacen = Almacen.de(context)
        val semana = almacen.llamadasDesde(numero.canonico, System.currentTimeMillis() - 7 * Almacen.DIA_MS)
        val detalle = buildString {
            append(decision.motivo)
            if (semana > 1) append(" · $semana.ª vez esta semana")
        }
        val silenciada = decision.veredicto == Veredicto.SILENCIAR
        val titulo = (if (silenciada) "Silenciada: " else "Bloqueada: ") + Clasificador.formatear(numero)
        val id = numero.canonico.hashCode()

        val abrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, if (silenciada) CANAL_SILENCIADAS else CANAL_BLOQUEADAS)
            .setSmallIcon(R.drawable.ic_escudo)
            .setContentTitle(titulo)
            .setContentText(detalle)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)

        if (numero.canonico.isNotEmpty()) {
            if (silenciada) {
                builder.addAction(accion(context, AccionesReceiver.BLOQUEAR, numero.canonico, id, "Bloquear"))
            }
            builder.addAction(accion(context, AccionesReceiver.CONFIAR, numero.canonico, id, "Es confiable"))
        }
        nm.notify(id, builder.build())
    }

    private fun accion(context: Context, accion: String, numero: String, idAviso: Int, texto: String): Notification.Action {
        val intent = Intent(context, AccionesReceiver::class.java)
            .setAction(accion)
            .putExtra(AccionesReceiver.EXTRA_NUMERO, numero)
            .putExtra(AccionesReceiver.EXTRA_AVISO, idAviso)
        val pi = PendingIntent.getBroadcast(
            context, (accion + numero).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, texto, pi).build()
    }
}
