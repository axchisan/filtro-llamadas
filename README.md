# Filtro de llamadas

App Android mínima para frenar las llamadas spam **sin anuncios, sin cuentas, sin internet y sin
cambiar la pantalla de llamada**. Pensada para Colombia, pero las reglas se adaptan fácil a otro país.

Nació como alternativa a Truecaller y similares, que llenan el teléfono de anuncios, ventanas
flotantes y suscripciones, y que además suben tu agenda a sus servidores.

## Cómo funciona

Usa la API oficial de Android [`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService):
el sistema le pregunta a la app antes de que el teléfono timbre y ella decide en local, en milisegundos.
No reemplaza tu app de teléfono.

| Llamada | Qué pasa |
|---|---|
| Contacto o número marcado "confiable" | Timbra normal |
| Formato falso (`0057…`, 12 dígitos sin `+57`, indicativos que no existen) | Se rechaza |
| Internacional *(regla)* | Se rechaza |
| Fijo `60x` desconocido, típico de call center *(regla)* | Se rechaza |
| El mismo número insiste en menos de 3 min *(regla)* | Timbra |
| Celular, 01 8000, número corto u oculto desconocido | Entra **en silencio** + aviso discreto |
| Número en tu lista de bloqueo | Se rechaza |

- Las llamadas rechazadas quedan en el registro de llamadas del teléfono, sin aviso de llamada perdida.
- Las silenciadas aparecen en pantalla por si quieres contestar, y dejan una notificación sin sonido con
  botones **Bloquear** / **Es confiable**.
- Las reglas marcadas se pueden apagar desde la app.

¿Por qué reglas y no una base de datos de spam? Porque no existe una gratuita y abierta con buena
cobertura de números colombianos (Truecaller no ofrece API de consulta; Hiya y similares son solo
para empresas). Y en la práctica el spam casi nunca repite número, pero sí repite patrones: números
mal formados, rutas internacionales, fijos de call center y llamadas que cuelgan en segundos.

## Instalar

Requiere Android 10 o superior.

1. Descarga el APK desde [Releases](../../releases) e instálalo (hay que permitir "instalar apps de
   fuentes desconocidas").
2. Abre **Filtro** → **Activar filtro** → elígela como app de identificación de llamadas y spam.
3. **Dar permisos**: contactos (para dejarlos pasar) y notificaciones (para avisarte).

## Compilar

```sh
./gradlew testDebugUnitTest assembleRelease   # JDK 17+
adb install -r app/build/outputs/apk/release/app-release.apk
```

Toda la lógica de decisión está en [`Clasificador.kt`](app/src/main/java/com/axchisan/filtro/Clasificador.kt)
y tiene pruebas en [`ClasificadorTest.kt`](app/src/test/java/com/axchisan/filtro/ClasificadorTest.kt).
Para otro país basta con ajustar `normalizar()` a su plan de numeración.

## Privacidad

Sin permiso de internet: la app no puede enviar nada fuera del teléfono. Guarda 90 días de historial
de llamadas filtradas (solo desconocidos) en una base de datos local.

## Licencia

[MIT](LICENSE): úsala, cámbiala y compártela libremente.
