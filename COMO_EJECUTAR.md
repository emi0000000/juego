# CÓMO EJECUTAR EL PROYECTO EN UNA COMPUTADORA NUEVA

> Guía pensada **exclusivamente para Visual Studio Code**. Sigue los pasos en orden y el juego correrá en cualquier computadora nueva (Windows).

---

## 1. Instalar los programas necesarios

### 1.1 Java (JDK)

1. Descarga el **JDK 17 o superior** desde: <https://adoptium.net/> (botón "Latest LTS Release").
2. Instálalo con todo por defecto (asegúrate de que la opción **"Add to PATH"** quede marcada).
3. Verifica que quedó instalado. Abre una terminal (PowerShell) y escribe:

```bash
java -version
```

Debe mostrar la versión (por ejemplo `openjdk version "17..."`). Si dice que no reconoce el comando, reinicia la computadora e intenta de nuevo.

### 1.2 Visual Studio Code

1. Descárgalo desde: <https://code.visualstudio.com/>
2. Instálalo con las opciones por defecto.

### 1.3 Extensiones de VS Code

1. Abre VS Code.
2. Ve a la pestaña de **Extensiones** (icono de cuadros en la barra izquierda, o `Ctrl+Shift+X`).
3. Busca e instala:
   - **Extension Pack for Java** (de Microsoft) — para compilar y correr el juego.
   - **Python** (de Microsoft) — solo si vas a usar el servidor `server.py`.

### 1.4 Git (para descargar el proyecto)

1. Descarga Git desde: <https://git-scm.com/downloads>
2. Instálalo con todo por defecto.

---

## 2. Descargar el proyecto

Abre una terminal (PowerShell) en la carpeta donde quieras guardar el proyecto y ejecuta:

```bash
git clone https://github.com/emi0000000/juego.git
```

Luego abre la carpeta en VS Code:

```bash
code juego
```

(O en VS Code: **File → Open Folder...** y selecciona la carpeta `juego`.)

> ⚠️ **Importante:** el archivo `flatlaf-3.4.1.jar` debe estar dentro de la carpeta del proyecto (viene incluido en el repositorio). Sin él, el juego no compila.

---

## 3. Archivos del proyecto

El juego está repartido en varios archivos `.java`. **Todos son necesarios** y deben estar en la misma carpeta:

| Archivo | Qué hace |
|---|---|
| `ProjectExodusAdvanced.java` | Ventana principal y menú del juego (aquí está el `main`) |
| `LabyrinthPanel.java` | Motor del juego: la ciudad, los perros, los jefes, la tienda |
| `MapGenerator.java` | Construye cada barrio con reglas propias (basurero, metro, puerto...) |
| `Sprites.java` | Dibuja a Nox, los perros y todos los materiales con Java 2D |
| `Particles.java` | Chispas, humo, explosiones y números de daño |
| `Tiles.java` | Tipos de casilla del mapa |
| `AudioEngine.java` | Música y efectos de sonido (se generan solos, sin archivos de audio) |
| `SaveManager.java` | Guarda y carga la partida |
| `LevelInfo.java` | Datos de los 6 barrios y sus jefes |
| `SmokeTest.java` | Prueba automática para verificar que todo funciona |
| `server.py` | Servidor opcional de progreso y récords |
| `flatlaf-3.4.1.jar` | Librería del tema oscuro |

No hay carpeta de imágenes ni de sonidos: **todo se dibuja y se sintetiza con código**, así que no se puede perder ningún recurso al copiar el proyecto.

---

## 4. Configuración de VS Code (ya incluida)

El proyecto ya trae la carpeta `.vscode` con esta configuración en `settings.json`:

```json
{
    "java.project.referencedLibraries": [
        "lib/**/*.jar",
        "flatlaf-3.4.1.jar"
    ]
}
```

Esto le dice a VS Code dónde está la librería **FlatLaf**. **No hay que hacer nada**, solo verificar que exista si algo falla.

Cuando abras el proyecto por primera vez, espera unos segundos a que la extensión de Java termine de cargar (abajo a la derecha aparece un ícono girando).

---

## 5. Ejecutar el juego

### Opción A — Desde VS Code (recomendada)

1. Abre el archivo `ProjectExodusAdvanced.java`.
2. Espera a que VS Code termine de analizar el proyecto.
3. Haz clic en el botón **▶ Run** que aparece arriba a la derecha (o sobre el texto `Run` encima del método `main`).

### Opción B — Desde la terminal de VS Code

Abre la terminal integrada (**Terminal → New Terminal** o `` Ctrl+ñ ``).

**Compilar** (el `*.java` compila todos los archivos de golpe):

```bash
javac -encoding UTF-8 -cp ".;flatlaf-3.4.1.jar" *.java
```

**Ejecutar** (en Windows el separador del classpath es punto y coma `;`):

```bash
java -cp ".;flatlaf-3.4.1.jar" ProjectExodusAdvanced
```

> 💡 En Linux o Mac el separador es dos puntos: `java -cp ".:flatlaf-3.4.1.jar" ProjectExodusAdvanced`

Si todo salió bien, se abre la ventana **"ÉXODO: LA VENGANZA DE NOX"** con el menú principal.

---

## 6. Controles

| Tecla | Acción |
|---|---|
| `W A S D` o flechas | Mover a Nox |
| `ESPACIO` | Atacar / disparar |
| `1` `2` `3` `4` | Cambiar de arma (garras, ballesta, plasma, molotov) |
| `Q` | Comerse una lata de atún (+50 de salud) |
| `F` | Silbato ultrasónico: aturde a los perros cercanos |
| `E` | Abrir el Mercado Negro (estando junto a la tienda rosa) |
| `M` | Mostrar u ocultar el mapa del barrio |
| `F11` | Pantalla completa (funciona en cualquier momento) |
| `P` o `ESC` | Pausa (desde ahí se sale al menú y se activa/desactiva el sonido) |

En el menú de pausa: `N` activa o desactiva la música, `B` los efectos de sonido.

### Con mando de Xbox

El juego detecta el mando solo. Conéctalo por cable o por Bluetooth (antes o durante la partida, da igual) y en el menú aparecerá **MANDO CONECTADO**.

| Botón | Acción |
|---|---|
| Stick izquierdo o cruceta | Mover a Nox (el stick es analógico: puedes caminar despacio) |
| **A** | Atacar / aceptar |
| **Gatillo derecho (RT)** | Atacar |
| **X** | Comer lata de atún |
| **Y** | Silbato ultrasónico |
| **B** | Abrir el Mercado Negro / cerrar / volver |
| **LB** y **RB** | Cambiar de arma |
| **BACK / VIEW** (o clic del stick derecho) | Ver el mapa del barrio |
| **START / MENU** | Pausa |

El mando **vibra** al recibir daño, en las explosiones, cuando aparece un jefe y en los sustos.

> El soporte de mando necesita **Java 22 o superior**. Con una versión anterior el juego funciona igual, solo que con teclado. El ejecutable de la carpeta `ejecutable\` ya lleva la versión correcta, así que ahí siempre funciona.

> ⚠️ **Vigila la barra de CORDURA.** Si llegas al jefe final con menos del 45%, verás el **final oscuro** en vez del bueno. El Amuleto de Cordura del Mercado Negro la sube 40 puntos.

---

## 7. Comprobar que todo funciona (prueba automática)

Si quieres verificar la instalación sin jugar, ejecuta la prueba incluida. Simula miles de fotogramas de juego, compra todas las armas, pelea contra el jefe y dibuja todas las pantallas:

```bash
java -cp ".;flatlaf-3.4.1.jar" SmokeTest
```

Debe terminar con `=== TODAS LAS PRUEBAS PASARON ===`.

---

## 8. (Opcional) Ejecutar el servidor `server.py`

El juego **guarda solo, en un archivo local** llamado `exodo_save.properties`. El servidor es un extra: guarda el progreso en la red y lleva una tabla de récords. Si no lo enciendes, no pasa nada.

1. Instala **Python 3** desde <https://www.python.org/downloads/> (marca **"Add Python to PATH"** al instalar).
2. En la terminal de VS Code, instala los requisitos:

```bash
pip install fastapi uvicorn
```

3. Inicia el servidor:

```bash
uvicorn server:app --reload
```

4. Queda corriendo en `http://127.0.0.1:8000`. Cosas que puedes abrir en el navegador:

| Dirección | Qué muestra |
|---|---|
| `http://127.0.0.1:8000/docs` | Documentación interactiva de todos los endpoints |
| `http://127.0.0.1:8000/api/status` | Estado del servidor |
| `http://127.0.0.1:8000/api/progress/nox` | Resumen del avance de la partida |
| `http://127.0.0.1:8000/api/scores` | Tabla de récords |

Con el servidor encendido, en la esquina inferior derecha del juego aparecerá **NUBE OK** en vez de **LOCAL**.

Para detener el servidor: `Ctrl+C` en la terminal.

---

## 9. Solución de problemas comunes

| Problema | Solución |
|---|---|
| `javac` no se reconoce como comando | El JDK no está en el PATH. Reinstala desde Adoptium y reinicia la PC. |
| `package com.formdev.flatlaf does not exist` | Falta `flatlaf-3.4.1.jar` en la carpeta, o falta el `-cp` en el comando. |
| `cannot find symbol: class LabyrinthPanel` | Compilaste un solo archivo. Usa `*.java` para compilar todos. |
| Se ven caracteres raros (`Ã©` en vez de `é`) | Falta `-encoding UTF-8` al compilar. |
| El botón ▶ Run no aparece | Falta la extensión **Extension Pack for Java**, o VS Code aún está cargando. |
| VS Code subraya los imports en rojo | `Ctrl+Shift+P` → **"Java: Clean Java Language Server Workspace"** → Restart. |
| El juego abre pero no responde al teclado | Haz clic dentro de la ventana para darle el foco. |
| No se escucha nada | Revisa que la música esté en **ON** en el menú. Si la computadora no tiene tarjeta de sonido, el juego avisa por consola y sigue funcionando en silencio. |
| Quiero empezar de cero | Borra el archivo `exodo_save.properties` de la carpeta del proyecto. |
