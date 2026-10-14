# YpsoPump Lab — APK de diagnóstico independiente

Esta aplicación Android se compila en el módulo `:ypsolab`. **No es la aplicación AAPS** y utiliza el identificador propio `app.aaps.ypsopump.lab`. Se instala sin sustituir AndroidAPS.

## Funciones incluidas

- Busca dispositivos YpsoPump visibles mediante Bluetooth LE.
- Permite elegir manualmente la bomba encontrada.
- Abre una conexión GATT y descubre los servicios disponibles.
- Intenta leer, sin enviar escrituras, las características de identificación y versiones publicadas por la bomba.
- Presenta los resultados en una interfaz sencilla con etiquetas para TalkBack.

**No incluye** control de bolos, basales, emparejamiento criptográfico ni funciones de tratamiento. Algunas lecturas pueden requerir autenticación y fallar. El funcionamiento no está comprobado en una bomba real.

## Compilar desde GitHub Actions

1. Abrir `Actions > AAPS CI` en el repositorio `tecnologiaaccesible/AndroidAPS_3.4`.
2. Pulsar **Run workflow** y elegir la rama `ypsopump-lab-3.4.2.6`.
3. Seleccionar `3.4.2.6` y `fullDebug`.
4. Cuando el workflow termine correctamente, abrir esa ejecución y descargar el artefacto `ypsopump-lab-readonly-debug-apk`.
5. Extraer el ZIP: contendrá un archivo APK de la aplicación independiente, no un APK completo de AAPS.

El archivo APK de depuración utiliza la firma debug generada por el runner de GitHub. Esa firma puede cambiar entre ejecuciones, por lo que para instalar otra versión puede ser necesario desinstalar la anterior. La aplicación no almacena configuraciones clínicas.

## Condiciones

- Uso exclusivamente con bomba de pruebas sin conexión a ningún cuerpo.
- No compartir claves criptográficas, PIN ni seriales completos en incidencias públicas.
- No conectar esta aplicación a bombas que estén administrando insulina.
- No interpretar una compilación correcta como validación del protocolo de la bomba.
