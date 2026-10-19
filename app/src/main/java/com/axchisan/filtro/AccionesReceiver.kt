package com.axchisan.filtro

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Botones "Bloquear" / "Es confiable" de las notificaciones. */
class AccionesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val numero = intent.getStringExtra(EXTRA_NUMERO) ?: return
        val lista = when (intent.action) {
            BLOQUEAR -> Lista.BLOQUEADOS
            CONFIAR -> Lista.CONFIABLES
            else -> return
        }
        Almacen.de(context).agregar(numero, lista)
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_AVISO, 0))
        val texto = if (lista == Lista.BLOQUEADOS) "Bloqueado: no volverá a entrar" else "Marcado como confiable"
        Toast.makeText(context, texto, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val BLOQUEAR = "com.axchisan.filtro.BLOQUEAR"
        const val CONFIAR = "com.axchisan.filtro.CONFIAR"
        const val EXTRA_NUMERO = "numero"
        const val EXTRA_AVISO = "aviso"
    }
}
