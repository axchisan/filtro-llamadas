package com.axchisan.filtro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val almacen by lazy { Almacen.de(this) }
    private lateinit var contenido: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        Avisos.crearCanales(this)
        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            addView(contenido)
            setOnApplyWindowInsetsListener { v, insets ->
                val barras = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        dibujar()
    }

    private fun dibujar() {
        contenido.removeAllViews()
        texto("Filtro de llamadas", 26f, negrita = true)

        val rol = getSystemService(RoleManager::class.java)
        val activo = rol.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val sinContactos = !tienePermiso(Manifest.permission.READ_CONTACTS)
        val sinAvisos = !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)

        if (activo) {
            texto("● Activo. Tus contactos timbran normal; lo demás se filtra.", 15f, color = 0xFF2E7D32.toInt())
        } else {
            texto("● Inactivo. Falta elegir esta app como filtro de llamadas.", 15f, color = 0xFFC62828.toInt())
            boton("Activar filtro") {
                startActivityForResult(rol.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), PEDIR_ROL)
            }
        }
        if (sinContactos || sinAvisos) {
            texto(
                buildList {
                    if (sinContactos) add("acceso a contactos (para dejarlos pasar)")
                    if (sinAvisos) add("notificaciones (para avisarte qué se filtró)")
                }.joinToString(prefix = "Falta permiso de ", separator = " y "),
                14f, secundario = true,
            )
            boton("Dar permisos") {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS), PEDIR_PERMISOS,
                )
            }
        }

        seccion("Últimos 7 días")
        val conteo = almacen.conteoDesde(System.currentTimeMillis() - 7 * Almacen.DIA_MS)
        texto(
            "${conteo[Veredicto.SILENCIAR] ?: 0} silenciadas · ${conteo[Veredicto.RECHAZAR] ?: 0} bloqueadas · " +
                "${conteo[Veredicto.PERMITIR] ?: 0} desconocidos que pasaron",
            15f,
        )

        seccion("Reglas")
        texto("Siempre: los números con formato falso se bloquean y los desconocidos entran en silencio.", 13f, secundario = true)
        val r = almacen.reglas
        interruptor("Bloquear llamadas internacionales", r.rechazarInternacionales) {
            almacen.reglas = almacen.reglas.copy(rechazarInternacionales = it)
        }
        interruptor("Bloquear fijos desconocidos (call centers)", r.rechazarFijos) {
            almacen.reglas = almacen.reglas.copy(rechazarFijos = it)
        }
        interruptor("Dejar timbrar si insiste en menos de 3 min", r.dejarPasarInsistentes) {
            almacen.reglas = almacen.reglas.copy(dejarPasarInsistentes = it)
        }

        seccion("Mis listas")
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        contenido.addView(fila)
        for (lista in Lista.entries) {
            val numeros = almacen.numerosEn(lista)
            val nombre = if (lista == Lista.BLOQUEADOS) "Bloqueados" else "Confiables"
            fila.addView(Button(this).apply {
                text = "$nombre (${numeros.size})"
                isAllCaps = false
                setOnClickListener { mostrarLista(nombre, numeros) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        seccion("Llamadas filtradas")
        val registros = almacen.recientes()
        if (registros.isEmpty()) texto("Aún no ha llegado ninguna llamada de un desconocido.", 14f, secundario = true)
        for (reg in registros) filaRegistro(reg)
    }

    private fun filaRegistro(reg: Registro) {
        val numero = Numero(reg.tipo, reg.numero)
        val lista = almacen.listaDe(reg.numero)
        val estado = when (reg.veredicto) {
            Veredicto.PERMITIR -> "Pasó"
            Veredicto.SILENCIAR -> "Silenciada"
            Veredicto.RECHAZAR -> "Bloqueada"
        }
        val cuando = DateUtils.getRelativeTimeSpanString(reg.fecha, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        val bloque = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            background = getDrawable(fondoSeleccionable())
            setOnClickListener { opcionesNumero(numero) }
        }
        bloque.addView(TextView(this).apply {
            text = Clasificador.formatear(numero) + when (lista) {
                Lista.BLOQUEADOS -> "  ⛔"
                Lista.CONFIABLES -> "  ✓"
                null -> ""
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        })
        bloque.addView(TextView(this).apply {
            text = "$estado · ${reg.motivo} · $cuando"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(colorSecundario())
        })
        contenido.addView(bloque)
    }

    private fun opcionesNumero(numero: Numero) {
        if (numero.canonico.isEmpty()) return
        val opciones = arrayOf("Bloquear siempre", "Marcar como confiable", "Quitar de mis listas", "Copiar número")
        AlertDialog.Builder(this)
            .setTitle(Clasificador.formatear(numero))
            .setItems(opciones) { _, i ->
                when (i) {
                    0 -> almacen.agregar(numero.canonico, Lista.BLOQUEADOS)
                    1 -> almacen.agregar(numero.canonico, Lista.CONFIABLES)
                    2 -> almacen.quitar(numero.canonico)
                    3 -> {
                        getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("número", numero.canonico))
                        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
                    }
                }
                dibujar()
            }
            .show()
    }

    private fun mostrarLista(nombre: String, numeros: List<String>) {
        if (numeros.isEmpty()) {
            Toast.makeText(this, "La lista está vacía", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("$nombre · toca para quitar")
            .setItems(numeros.map { Clasificador.formatear(Clasificador.normalizar(it)) }.toTypedArray()) { _, i ->
                almacen.quitar(numeros[i])
                dibujar()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    @Deprecated("API de Activity sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        dibujar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        dibujar()
    }

    private fun tienePermiso(permiso: String) = checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED

    private fun seccion(titulo: String) {
        contenido.addView(View(this), LinearLayout.LayoutParams(1, dp(20)))
        texto(titulo.uppercase(), 13f, negrita = true, secundario = true)
    }

    private fun texto(valor: String, sp: Float, negrita: Boolean = false, secundario: Boolean = false, color: Int? = null) {
        contenido.addView(TextView(this).apply {
            text = valor
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
            when {
                color != null -> setTextColor(color)
                secundario -> setTextColor(colorSecundario())
            }
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun boton(valor: String, alPulsar: () -> Unit) {
        contenido.addView(Button(this).apply {
            text = valor
            isAllCaps = false
            setOnClickListener { alPulsar() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.START
        })
    }

    private fun interruptor(valor: String, marcado: Boolean, alCambiar: (Boolean) -> Unit) {
        contenido.addView(Switch(this).apply {
            text = valor
            isChecked = marcado
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, v -> alCambiar(v) }
        })
    }

    private fun colorSecundario(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return getColorStateList(tv.resourceId).defaultColor
    }

    private fun fondoSeleccionable(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val PEDIR_ROL = 1
        const val PEDIR_PERMISOS = 2
    }
}
