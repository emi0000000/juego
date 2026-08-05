# MAPA DE ARCHIVOS — ÉXODO: LA VENGANZA DE NOX

> Qué es cada archivo y de qué se encarga. Una línea para encontrar rápido dónde tocar algo.

---

## ÁRBOL DE CARPETAS

```
juego/
│
├── ProjectExodusAdvanced.java     ← ARRANQUE. Ventana, menú principal y el main
├── LabyrinthPanel.java            ← EL MOTOR. Todo lo que pasa mientras juegas
│
├── MapGenerator.java              ← Construye el mapa de cada barrio
├── Tiles.java                     ← Qué significa cada número del mapa
├── LevelInfo.java                 ← Ficha de los 6 barrios (nombre, jefe, colores)
│
├── Sprites.java                   ← Dibuja todo: escenario, Nox, perros, objetos
├── Particles.java                 ← Chispas, humo, explosiones, números de daño
├── Jumpscares.java                ← Los 10 sustos
├── Cinematics.java                ← Prólogo y los dos finales
│
├── AudioEngine.java               ← Música y sonido (se calculan, no son archivos)
├── Gamepad.java                   ← Mando de Xbox
│
├── Achievements.java              ← Los 25 logros
├── Settings.java                  ← Opciones del jugador
├── Memories.java                  ← Los 18 textos de las fotos
├── SaveManager.java               ← Guardar y cargar la partida
│
├── SmokeTest.java                 ← Pruebas automáticas
├── server.py                      ← Servidor opcional (lo único que no es Java)
│
├── flatlaf-3.4.1.jar              ← Librería del tema oscuro (única externa)
│
├── JUGAR.bat                      ← Doble clic para jugar
├── CREAR-EJECUTABLE.bat           ← Genera el .jar y el .exe
├── .gitignore                     ← Qué NO se sube a GitHub
│
├── .vscode/
│   └── settings.json              ← Le dice a VS Code dónde está la librería
│
├── HISTORIA.md                    ← La historia, jefes, armas, finales
├── GUIA.md                        ← Logros, finales y secretos explicados
├── COMO_EJECUTAR.md               ← Instalar y ejecutar en una computadora nueva
├── MOTORES.md                     ← Cómo funcionan los motores por encima
├── CODIGO.md                      ← Cómo está programado, archivo por archivo
└── ARCHIVOS.md                    ← Este documento
```

---

## LOS ARCHIVOS DEL JUEGO

### Arranque

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`ProjectExodusAdvanced.java`** | 630 | Crea la ventana, aplica el tema oscuro y cambia entre menú y partida. Contiene el **`main`**, o sea el punto por donde arranca todo. Dentro tiene también el **menú principal**: la lluvia de fondo, los botones de dificultad, la pantalla de logros, la de opciones, la pantalla completa y el código secreto. |

### El motor

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`LabyrinthPanel.java`** | 2.850 | **El archivo más grande y el corazón del juego.** El bucle que corre 60 veces por segundo, el movimiento de Nox, las colisiones, la cámara, los perros y sus jefes, las armas, la tienda, la cordura, las alucinaciones, los sustos, el HUD y todas las pantallas de dentro de la partida. Si algo pasa mientras juegas, pasa aquí. |

### El mundo

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`MapGenerator.java`** | 375 | Construye el terreno de cada barrio con un algoritmo propio: montañas de basura, puestos de mercado, túneles de metro, contenedores, azoteas sobre el vacío y salones de mansión. Además **garantiza que el barrio se pueda terminar**: excava un camino a la salida y conecta las zonas que queden aisladas. |
| **`Tiles.java`** | 40 | El mapa es una tabla de números. Este archivo dice qué significa cada uno (0 suelo, 3 muro, 6 agua...) y responde a tres preguntas: ¿bloquea el paso?, ¿se puede pisar?, ¿se puede romper? |
| **`LevelInfo.java`** | 62 | La ficha de los seis barrios: cómo se llama cada uno, quién es su jefe, de qué raza es, qué dice al aparecer, a quién se rescata allí y su paleta de colores. Está separado para poder cambiar un color o una frase sin tocar el motor. |

### Lo que se ve

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`Sprites.java`** | 975 | **Dibuja absolutamente todo**, con figuras geométricas. Los materiales de los seis barrios (que se guardan en memoria para no redibujarlos 60 veces por segundo), a Nox con su cola y sus bigotes, a los perros de todas las razas, las monedas, la tienda, la salida, las fotos, el callejón quemado y los focos de luz. |
| **`Particles.java`** | 215 | Las chispas al golpear, el humo, las brasas, las explosiones, las salpicaduras de lluvia, el polvo de los pasos y los números que salen flotando al hacer daño. Es lo que hace que un golpe **se sienta** como un golpe. |
| **`Jumpscares.java`** | 400 | Los **diez sustos**, cada uno con su dibujo y su sonido: la embestida, los ojos, la estática, la jauría, tu propio reflejo, las garras, las fauces, el susurro silencioso, Colmillo y la sombra. También decide cuál toca sin repetir el anterior. |
| **`Cinematics.java`** | 640 | Las escenas del **prólogo** (6 planos) y de los **dos finales** (4 planos cada uno). No son vídeo: son dibujos encadenados con fundidos y texto que aparece a su tiempo. |

### Lo que se oye y se toca

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`AudioEngine.java`** | 590 | **Fabrica el sonido en vez de reproducirlo.** Los 42 efectos son fórmulas matemáticas y la música es un generador continuo que cambia según el barrio y según lo mal que esté Nox. Los mezcla en estéreo, así que un gruñido a tu izquierda se oye a tu izquierda. |
| **`Gamepad.java`** | 250 | El mando de Xbox, llamando a Windows directamente desde Java, sin librerías. Lee los botones y los sticks, controla la zona muerta y hace vibrar el mando. Si no hay mando o el sistema no lo soporta, se apaga solo y se juega con teclado. |

### Los datos del jugador

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`Achievements.java`** | 175 | Los **25 logros**: sus nombres, sus condiciones, cuáles son secretos, guardarlos entre partidas y mostrar el aviso deslizante cuando consigues uno. |
| **`Settings.java`** | 75 | Las opciones: música, efectos, pantalla completa y las de accesibilidad (apagar sustos, destellos, temblor, números de daño y vibración). Las guarda en disco. |
| **`Memories.java`** | 60 | Los **18 textos** de las fotos escondidas, tres por barrio. Están aparte para poder reescribir la historia sin tocar el código del juego. |
| **`SaveManager.java`** | 235 | Guarda y carga la partida: barrio, dinero, armas, armadura, familia rescatada, fotos encontradas, muertes y puntuación. Si hay servidor, lo manda también por red, pero **en un hilo aparte** para que el juego no se congele si está apagado. |

### Herramientas

| Archivo | Líneas | De qué se encarga |
|---|---|---|
| **`SmokeTest.java`** | 900 | **Las pruebas automáticas.** Arranca una partida sin abrir ventana, simula miles de fotogramas y comprueba 48 cosas: que los mapas se puedan terminar, que los jefes no se atasquen, que las alucinaciones no hagan daño, que las pantallas no bloqueen el juego, que el mando se lea una sola vez por fotograma... |
| **`server.py`** | 147 | Servidor **opcional** en Python con FastAPI, para guardar el progreso y una tabla de récords. El juego funciona perfectamente sin él. |

---

## LOS ARCHIVOS QUE NO SON CÓDIGO

| Archivo | Para qué sirve |
|---|---|
| **`flatlaf-3.4.1.jar`** | La **única librería externa** del proyecto. Le da a los botones y ventanas el aspecto oscuro moderno; sin ella se verían con el gris de Java de los años 90. |
| **`JUGAR.bat`** | Doble clic y se juega. Comprueba que haya Java, compila si hace falta y arranca. |
| **`CREAR-EJECUTABLE.bat`** | Genera lo distribuible: **`Exodo.jar`** (un solo archivo, necesita Java) y **`ejecutable\Exodo.exe`** (lleva Java dentro, no necesita nada). |
| **`.gitignore`** | Evita subir a GitHub lo que se genera solo: clases compiladas, el `.jar`, el `.exe`, los `.zip` y las partidas guardadas. |
| **`.vscode/settings.json`** | Le dice a VS Code que la librería FlatLaf está ahí, para que no marque los imports en rojo. |

---

## LOS ARCHIVOS QUE CREA EL JUEGO AL JUGAR

Estos **no** están en el repositorio: aparecen solos y son de cada jugador.

| Archivo | Qué guarda |
|---|---|
| `exodo_save.properties` | La partida en curso |
| `exodo_logros.properties` | Los logros conseguidos |
| `exodo_config.properties` | Las opciones elegidas |
| `server_data.json` | Datos del servidor, solo si lo usas |
| `*.class` | Las clases compiladas |
| `build/`, `ejecutable/`, `Exodo.jar` | Lo que genera `CREAR-EJECUTABLE.bat` |

---

## LOS DOCUMENTOS

| Archivo | Qué explica | Para quién |
|---|---|---|
| **`HISTORIA.md`** | La historia de Nox, los seis barrios, los jefes, las armas, los dos finales | Quien quiera saber de qué va el juego |
| **`GUIA.md`** | Cómo se sacan los 25 logros, cómo conseguir cada final y dónde están los secretos | Quien esté jugando y quiera el 100% |
| **`COMO_EJECUTAR.md`** | Instalar Java y VS Code, clonar el proyecto y ejecutarlo paso a paso | Quien lo abra en una computadora nueva |
| **`MOTORES.md`** | Cómo funcionan los motores por encima: el bucle, el dibujo, el audio, el mando | Quien quiera entender el proyecto sin leer el código |
| **`CODIGO.md`** | El detalle técnico archivo por archivo, con las funciones y por qué están así | Quien vaya a tocar el código |
| **`ARCHIVOS.md`** | Este documento: qué es cada archivo | Para orientarse rápido |

---

## SI QUIERO CAMBIAR ALGO, ¿DÓNDE TOCO?

| Quiero... | Archivo |
|---|---|
| Cambiar el nombre de un barrio o de un jefe | `LevelInfo.java` |
| Cambiar los colores de un barrio | `LevelInfo.java` (niebla y jefe) y `Sprites.java` (materiales) |
| Cambiar la forma de un barrio | `MapGenerator.java` |
| Cambiar precios de la tienda | `LabyrinthPanel.java`, en `buySelectedItem()` y `shopItems()` |
| Cambiar la vida o la velocidad de un enemigo | `LabyrinthPanel.java`, constructor de `Enemy` |
| Cambiar el ataque de un jefe | `LabyrinthPanel.java`, los métodos `rockyLanzaBidones()`, `spikeVuelcaPuestos()`... |
| Cambiar el umbral de los finales | `LabyrinthPanel.java`, en `triggerVictory()` |
| Añadir o cambiar un logro | `Achievements.java` (definirlo) y donde toque llamar a `unlock()` |
| Cambiar los textos de las fotos | `Memories.java` |
| Cambiar el texto de las cinemáticas | `Cinematics.java`, el array `SHOT_TEXTS` |
| Añadir un sonido | `AudioEngine.java`, en `buildSfxBank()` |
| Cambiar cómo se dibuja Nox | `Sprites.java`, en `drawNox()` |
| Añadir un susto | `Jumpscares.java` |
| Cambiar los controles | `LabyrinthPanel.java` (`keyPressed` y `handleGamepad`) |
