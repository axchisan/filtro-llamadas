package com.axchisan.filtro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Registro(
    val id: Long,
    val numero: String,
    val tipo: TipoNumero,
    val fecha: Long,
    val veredicto: Veredicto,
    val motivo: String,
)

enum class Lista { BLOQUEADOS, CONFIABLES }

/** Todo se guarda en el teléfono: historial de llamadas filtradas, listas y reglas. */
class Almacen private constructor(private val context: Context) :
    SQLiteOpenHelper(context, "filtro.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE registro (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                numero TEXT NOT NULL,
                tipo TEXT NOT NULL,
                fecha INTEGER NOT NULL,
                veredicto TEXT NOT NULL,
                motivo TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX registro_numero ON registro(numero, fecha)")
        db.execSQL("CREATE TABLE lista (numero TEXT PRIMARY KEY, lista TEXT NOT NULL, fecha INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun registrar(numero: Numero, decision: Decision, fecha: Long = System.currentTimeMillis()) {
        writableDatabase.insert("registro", null, ContentValues().apply {
            put("numero", numero.canonico)
            put("tipo", numero.tipo.name)
            put("fecha", fecha)
            put("veredicto", decision.veredicto.name)
            put("motivo", decision.motivo)
        })
        // Se conservan 90 días de historial.
        writableDatabase.delete("registro", "fecha < ?", arrayOf((fecha - 90L * DIA_MS).toString()))
    }

    fun llamadasDesde(numero: String, desde: Long): Int {
        if (numero.isEmpty()) return 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM registro WHERE numero = ? AND fecha >= ?",
            arrayOf(numero, desde.toString()),
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun recientes(limite: Int = 50): List<Registro> =
        readableDatabase.rawQuery(
            "SELECT id, numero, tipo, fecha, veredicto, motivo FROM registro ORDER BY fecha DESC LIMIT ?",
            arrayOf(limite.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Registro(
                        c.getLong(0), c.getString(1), TipoNumero.valueOf(c.getString(2)), c.getLong(3),
                        Veredicto.valueOf(c.getString(4)), c.getString(5),
                    )
                )
            }
        }

    fun conteoDesde(desde: Long): Map<Veredicto, Int> =
        readableDatabase.rawQuery(
            "SELECT veredicto, COUNT(*) FROM registro WHERE fecha >= ? GROUP BY veredicto",
            arrayOf(desde.toString()),
        ).use { c ->
            buildMap { while (c.moveToNext()) put(Veredicto.valueOf(c.getString(0)), c.getInt(1)) }
        }

    fun listaDe(numero: String): Lista? {
        if (numero.isEmpty()) return null
        readableDatabase.rawQuery("SELECT lista FROM lista WHERE numero = ?", arrayOf(numero)).use { c ->
            return if (c.moveToFirst()) Lista.valueOf(c.getString(0)) else null
        }
    }

    fun agregar(numero: String, lista: Lista) {
        if (numero.isEmpty()) return
        writableDatabase.insertWithOnConflict("lista", null, ContentValues().apply {
            put("numero", numero)
            put("lista", lista.name)
            put("fecha", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun quitar(numero: String) {
        writableDatabase.delete("lista", "numero = ?", arrayOf(numero))
    }

    fun numerosEn(lista: Lista): List<String> =
        readableDatabase.rawQuery(
            "SELECT numero FROM lista WHERE lista = ? ORDER BY fecha DESC", arrayOf(lista.name),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private val prefs get() = context.getSharedPreferences("reglas", Context.MODE_PRIVATE)

    var reglas: Reglas
        get() = Reglas(
            rechazarInternacionales = prefs.getBoolean("internacionales", true),
            rechazarFijos = prefs.getBoolean("fijos", true),
            dejarPasarInsistentes = prefs.getBoolean("insistentes", true),
        )
        set(valor) {
            prefs.edit()
                .putBoolean("internacionales", valor.rechazarInternacionales)
                .putBoolean("fijos", valor.rechazarFijos)
                .putBoolean("insistentes", valor.dejarPasarInsistentes)
                .apply()
        }

    companion object {
        const val DIA_MS = 24 * 60 * 60 * 1000L

        @Volatile private var instancia: Almacen? = null

        fun de(context: Context): Almacen =
            instancia ?: synchronized(this) {
                instancia ?: Almacen(context.applicationContext).also { instancia = it }
            }
    }
}
