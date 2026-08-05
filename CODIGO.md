# CÓMO ESTÁ PROGRAMADO — ÉXODO: LA VENGANZA DE NOX

> Documento técnico. Explica **archivo por archivo** qué hace cada uno, cómo están escritas sus funciones y por qué se hizo así.
>
> Si buscas cómo funcionan los motores por encima, eso está en `MOTORES.md`. Este documento es el detalle.

---

## ÍNDICE

1. [Con qué está hecho](#1-con-qué-está-hecho)
2. [Cómo se compila y se empaqueta](#2-cómo-se-compila-y-se-empaqueta)
3. [Mapa de archivos](#3-mapa-de-archivos)
4. [`ProjectExodusAdvanced.java` — ventana y menú](#4-projectexodusadvancedjava)
5. [`LabyrinthPanel.java` — el motor del juego](#5-labyrinthpaneljava)
6. [`MapGenerator.java` — generación de barrios](#6-mapgeneratorjava)
7. [`Tiles.java` — tipos de casilla](#7-tilesjava)
8. [`LevelInfo.java` — datos de los barrios](#8-levelinfojava)
9. [`Sprites.java` — todo el dibujo](#9-spritesjava)
10. [`Particles.java` — efectos](#10-particlesjava)
11. [`Jumpscares.java` — los diez sustos](#11-jumpscaresjava)
12. [`Cinematics.java` — las cinemáticas](#12-cinematicsjava)
13. [`AudioEngine.java` — música y sonido](#13-audioenginejava)
14. [`Gamepad.java` — mando de Xbox](#14-gamepadjava)
15. [`Achievements.java` — logros](#15-achievementsjava)
16. [`Settings.java` — opciones](#16-settingsjava)
17. [`Memories.java` — los recuerdos](#17-memoriesjava)
18. [`SaveManager.java` — guardado](#18-savemanagerjava)
19. [`SmokeTest.java` — pruebas automáticas](#19-smoketestjava)
20. [`server.py` — servidor opcional](#20-serverpy)
21. [Reparto de lenguajes](#21-reparto-de-lenguajes)

---

## 1. CON QUÉ ESTÁ HECHO

### Herramientas

| Herramienta | Para qué | Versión |
|---|---|---|
| **JDK de Java** | Compilar y ejecutar todo | 25 (funciona desde la 17; el mando necesita la 22) |
| **Visual Studio Code** | Editor | Con *Extension Pack for Java* |
| **`javac`** | Compilador | Viene con el JDK |
| **`jar`** | Empaquetar el juego en un archivo | Viene con el JDK |
| **`jpackage`** | Crear el `.exe` con Java dentro | Viene con el JDK 14+ |
| **Python + FastAPI** | Servidor opcional | Solo para `server.py` |

### Librerías

**Externa, una sola:**

| Librería | Versión | Para qué | Licencia |
|---|---|---|---|
| **FlatLaf** | 3.4.1 | Tema oscuro de los botones y la ventana | Apache 2.0 |

**Del propio Java (no hay que instalar nada):**

| Paquete | Se usa para |
|---|---|
| `javax.swing` | Ventana, paneles, botones, `Timer` del bucle |
| `java.awt` | `Graphics2D`, colores, fuentes, polígonos |
| `java.awt.geom` | Curvas, elipses, transformaciones, distancias |
| `java.awt.image` | `BufferedImage` para la caché de casillas |
| `javax.sound.sampled` | Salida de audio en crudo |
| `java.lang.foreign` | Llamar a XInput para el mando (Java 22+) |
| `java.util` | Listas, mapas, `Random`, `Properties`, `ArrayDeque` |
| `java.io` / `java.net` | Guardado en disco y peticiones al servidor |

### La regla que manda sobre todo

> **No hay ni una imagen ni un archivo de sonido en el proyecto.**

Los gráficos se dibujan con figuras geométricas y el audio se calcula muestra a muestra. Por eso el proyecto se copia a una USB y funciona igual en cualquier ordenador: no hay carpeta de recursos que se pueda perder.

---

## 2. CÓMO SE COMPILA Y SE EMPAQUETA

### Compilar a mano

```bash
javac -encoding UTF-8 -cp ".;flatlaf-3.4.1.jar" *.java
```

`-encoding UTF-8` es obligatorio: sin él los acentos y las eñes del código salen rotos.

### Ejecutar

```bash
java --enable-native-access=ALL-UNNAMED -cp ".;flatlaf-3.4.1.jar" ProjectExodusAdvanced
```

`--enable-native-access` evita el aviso de Java al llamar a XInput para el mando.

### Los tres archivos de construcción

**`JUGAR.bat`** — Doble clic para jugar. Comprueba que exista Java, compila si hace falta y ejecuta.

**`CREAR-EJECUTABLE.bat`** — Genera el juego distribuible en cuatro pasos:

1. Compila todos los `.java` a `build\clases` (y borra la clase de las pruebas, que no forma parte del juego).
2. Extrae FlatLaf dentro de esas clases con `jar --extract`, quitando `META-INF` y `module-info.class`, que estorban al meterlo todo junto.
3. Crea **`Exodo.jar`** con `jar --create --main-class ProjectExodusAdvanced`. Es el juego entero en un archivo, pero necesita Java instalado.
4. Llama a **`jpackage`** para crear **`ejecutable\Exodo.exe`**, que lleva dentro una copia recortada de Java:

```
jpackage --type app-image --name Exodo --input build\dist
         --main-jar Exodo.jar --main-class ProjectExodusAdvanced
         --add-modules java.base,java.desktop,java.logging
         --java-options "--enable-native-access=ALL-UNNAMED"
```

`--add-modules` recorta el Java que se incluye a solo lo que el juego usa. Se averigua con:

```bash
jdeps --print-module-deps Exodo.jar
```

**`.gitignore`** — Evita subir a Git lo generado: `*.class`, `build/`, `ejecutable/`, `Exodo.jar`, los `.zip` y los archivos de partida.

---

## 3. MAPA DE ARCHIVOS

| Archivo | Líneas | Responsabilidad |
|---|---|---|
| `LabyrinthPanel.java` | 2.780 | El motor: bucle, colisiones, enemigos, armas, tienda, HUD, pantallas |
| `Sprites.java` | 955 | Todo el dibujo: materiales, Nox, perros, objetos, luces |
| `SmokeTest.java` | 894 | Pruebas automáticas |
| `Cinematics.java` | 622 | Prólogo y los dos finales |
| `ProjectExodusAdvanced.java` | 614 | Ventana, menú, logros, opciones, `main` |
| `AudioEngine.java` | 561 | Sintetiza música y efectos y los mezcla en estéreo |
| `Jumpscares.java` | 393 | Los diez sustos |
| `MapGenerator.java` | 361 | Genera el terreno de los seis barrios |
| `Gamepad.java` | 243 | Mando de Xbox por XInput |
| `SaveManager.java` | 223 | Guardado local y sincronización opcional |
| `Particles.java` | 207 | Chispas, humo, explosiones, números flotantes |
| `Achievements.java` | 168 | Los 25 logros |
| `server.py` | 147 | Servidor opcional (Python) |
| `Settings.java` | 73 | Opciones del jugador |
| `LevelInfo.java` | 62 | Datos de los seis barrios |
| `Memories.java` | 60 | Los 18 recuerdos de las fotos |
| `Tiles.java` | 39 | Tipos de casilla |

### Cómo se relacionan

```
                 ProjectExodusAdvanced        <- ventana, main, menú
                          |
                          v
                   LabyrinthPanel             <- el motor de juego
                          |
   +---------+------------+-----------+------------+-----------+
   v         v            v           v            v           v
MapGenerator Sprites  Particles  Jumpscares  Cinematics    Gamepad
   |         |
   v         v
 Tiles    LevelInfo

           AudioEngine  <- lo usan todos
           Achievements <- lo usan LabyrinthPanel y el menú
           Settings     <- lo usan todos
           SaveManager --> server.py (opcional, por red)
```

---

## 4. `ProjectExodusAdvanced.java`

**Contiene dos clases:** la ventana (`ProjectExodusAdvanced`) y el menú principal (`MainMenuPanel`).

### La ventana

Es un `JFrame` de 1024×768 que usa un **`CardLayout`**: un gestor que apila paneles y muestra uno cada vez. Aquí hay dos cartas, `"MENU"` y `"LABYRINTH"`, y cambiar de pantalla es una línea.

**Funciones:**

| Función | Qué hace |
|---|---|
| `main(String[])` | Arranca todo. Intenta enlazar el mando dentro de un `try/catch(Throwable)`, carga los logros, y en el hilo de Swing arranca el audio, carga las opciones y crea la ventana. |
| `goToMenu()` | Vuelve al menú, refresca la partida guardada y devuelve la música al tema del menú. |
| `startNewGame(int)` / `continueSavedGame()` | Cambian a la carta del juego y arrancan la partida. |
| `toggleFullscreen()` / `applyFullscreen()` | Alternan pantalla completa. Usan **ventana sin bordes maximizada** en vez del modo exclusivo del sistema: va igual de rápido, no cambia la resolución del escritorio y si el juego se cierra mal no deja la pantalla en un estado raro. |
| `gamepadEnabled()` / `isMenuActive()` / `isGoldenMode()` | Consultas de estado que necesita el resto del juego. |

**Por qué `catch (Throwable)` y no `catch (Exception)` al enlazar el mando:** en Java 17 la clase `java.lang.foreign` no existe, y eso produce un `NoClassDefFoundError`, que es un `Error` y **no** un `Exception`. Capturando `Throwable` el juego arranca igual en versiones antiguas y se juega con teclado.

**La marca `menuActive`.** El menú y el juego tienen cada uno su temporizador, y el del menú **no se detiene nunca**. Sin esta marca los dos leían el mando en el mismo fotograma: la primera lectura se comía la detección de "botón recién pulsado" y el juego no veía nada, mientras el menú seguía activando sus botones por detrás y cada (A) reiniciaba el barrio. Ahora **solo lee el mando la pantalla activa**.

### El menú principal (`MainMenuPanel`)

Un `JPanel` con `GridBagLayout` dentro de un `BorderLayout`. Los componentes van en un panel `content` aparte para poder **ocultarlos de golpe** cuando se abre la pantalla de logros o de opciones.

| Función | Qué hace |
|---|---|
| `refresh()` | Se llama al volver al menú: muestra u oculta CONTINUAR según haya partida, actualiza el contador de logros y el aviso del modo dorado. |
| `handleMenuKey(int)` | Teclado del menú: `F11`, navegación de las pantallas superpuestas y **el código Konami**. |
| `handleGamepad()` | Recorre los botones con la cruceta y los activa con (A). Se llama desde el temporizador de la lluvia, que ya va a 33 fps. |
| `drawAchievements()` / `drawOptions()` | Dibujan las pantallas superpuestas de logros y opciones. |
| `paintComponent()` | Fondo: siluetas de edificios, lluvia y Nox acechando en la esquina. |

**El código Konami** se comprueba con un contador de progreso: si la tecla coincide con la siguiente del código avanza, y si falla vuelve a cero — salvo que la tecla fallada sea la primera del código, en cuyo caso empieza de nuevo desde uno. Ese detalle evita que pulsar `↑ ↑ ↑` rompa la secuencia.

---

## 5. `LabyrinthPanel.java`

El archivo grande. Es un `JPanel` que implementa `ActionListener` (para el temporizador) y `KeyListener` (para el teclado).

### 5.1 El bucle de juego

```java
gameLoop = new Timer(16, this);   // 16 ms ≈ 60 fotogramas por segundo
```

Cada vez que salta, `actionPerformed()` hace esto **en orden**:

1. `animationFrame++` — el contador que anima todo el juego
2. `handleGamepad()` — lee el mando
3. `Achievements.updateToasts()` — avanza el aviso de logro
4. **Cortes por estado:** si hay una cinemática, una pantalla de historia, pausa, muerte, tienda, susto, intro de jefe o transición, se dibuja y se sale. Cada uno es un `if` con `return`.
5. `updatePlayer()` — mueve a Nox, recoge cosas, detecta tienda y salida
6. `updateBullets()` — mueve los proyectiles y comprueba impactos
7. `updateEnemies()` — mueve los perros, dispara, aplica daño, gestiona jefes
8. `updateAtmosphere()` — lluvia, parpadeos, brasas, temblor
9. `updateHallucinations()` — perros que no existen y demás
10. `fx.update()` — avanza las partículas
11. `repaint()` — le pide a Swing que dibuje

**Por qué esos `return` en cadena y no un `if/else` gigante:** cada estado que "congela" el juego necesita que no se ejecute nada de lo de abajo. Con `return` queda claro y es imposible que se cuele lógica de juego durante una pantalla.

### 5.2 El dibujo y la escala

```java
protected void paintComponent(Graphics g) {
    double scale = Math.min(realW / 1024.0, realH / 768.0);
    g2d.translate(offsetX, offsetY);
    g2d.scale(scale, scale);
    g2d.clipRect(0, 0, VIEW_W, VIEW_H);
    drawGame(g2d);
}
```

**Todo el juego se dibuja siempre como si la pantalla fuera de 1024×768** (las constantes `VIEW_W` y `VIEW_H`), y después se escala a la ventana real, centrado y con bandas negras si las proporciones no cuadran.

Sin esto, en pantalla completa a 1920×1080 el HUD se iría a las esquinas, el círculo de luz quedaría descolocado y el mapa se vería con proporciones distintas. Así **todo se ve igual, solo que más grande**.

`drawGame()` dibuja **en este orden** (lo que va después tapa lo anterior):

```
mundo -> personajes -> fantasmas -> partículas -> lluvia
      -> oscuridad -> luces -> HUD -> pantallas -> aviso de logro
```

### 5.3 La cámara

```java
camX = (int) playerX - VIEW_W / 2 + 15 + shakeX;
camX = Math.max(0, Math.min(camX, MAP_WIDTH * TILE_SIZE - VIEW_W));
g2d.translate(-camX, -camY);
```

El mundo mide 50×50 casillas de 60 px = 3000×3000 px. La cámara sigue a Nox pero **se frena en los bordes** para no enseñar el vacío.

> **Detalle importante:** como la cámara se frena pero Nox sigue moviéndose, el círculo de luz **no** puede dibujarse en el centro de la pantalla, sino en la posición real de Nox en pantalla. Si no, al llegar a una esquina del mapa el gato se quedaba a oscuras.

### 5.4 Colisiones

```java
private boolean blocked(double nx, double ny, int size) {
    int x1 = (int) (nx / TILE_SIZE);
    int y1 = (int) (ny / TILE_SIZE);
    int x2 = (int) ((nx + size - 1) / TILE_SIZE);
    int y2 = (int) ((ny + size - 1) / TILE_SIZE);
    return Tiles.blocks(map[x1][y1]) || ... ;   // las cuatro esquinas
}
```

No hay motor de física: se mira en qué casillas caerían las cuatro esquinas y si alguna bloquea.

**El truco del movimiento** es aplicarlo **eje por eje**:

```java
if (dx != 0 && !blocked(playerX + dx, playerY, PLAYER_SIZE)) playerX += dx;
if (dy != 0 && !blocked(playerX, playerY + dy, PLAYER_SIZE)) playerY += dy;
```

Así, al andar en diagonal contra una pared, Nox **se desliza por ella** en vez de frenarse en seco. Comprobando los dos ejes juntos, cualquier roce lo pararía.

**La caja de los perros es más pequeña que el dibujo:**

```java
private boolean enemyBlocked(Enemy en, double nx, double ny) {
    double inset = (en.size - en.collisionSize) / 2.0;
    return blocked(nx + inset, ny + inset, en.collisionSize);
}
```

Un jefe se dibuja de 78 px pero un pasillo mide 60. Con la caja del tamaño del dibujo **no cabía** y se quedaba clavado. La constante `MAX_COLLISION = 40` está calculada: el margen es `(78−40)/2 = 19`, y `19 + 40 = 59 < 60`, así que la caja **cabe entera dentro de una casilla**. Con 44 no cabía (`17 + 44 = 61`) y la caja invadía siempre la casilla de al lado: en un pasillo estrecho eso dejaba al jefe sin poder moverse en ninguna dirección.

### 5.5 La inteligencia de los perros

Hay **dos niveles**, según lo que importe cada enemigo.

**Los pandilleros normales** usan un método barato: van hacia el jugador eje por eje y, si no consiguen moverse en ninguno, activan un `detourTimer` y caminan **perpendicular** al jugador durante 45 fotogramas para rodear. Si por ese lado tampoco salen, prueban por el contrario:

```java
if (moved) {
    en.stuckFrames = 0;
} else {
    en.stuckFrames++;
    en.chargeTimer = 0;                 // una embestida contra un muro se corta
    if (en.stuckFrames > 10) {
        en.detourDir = -en.detourDir;   // por el otro lado
        en.detourTimer = 45;
        en.stuckFrames = 0;
    } else if (en.detourTimer <= 0) { ... }
}
```

**Los jefes usan búsqueda de ruta de verdad.** `nextStepToPlayer()` hace una **búsqueda en anchura (BFS)** sobre la rejilla y devuelve la siguiente casilla del camino más corto:

```java
pathPrev[start] = start;
pathQueue.add(start);
while (!pathQueue.isEmpty()) {
    int cur = pathQueue.poll();
    if (cur == goal) { found = true; break; }
    for (int[] d : DIRS) { ...marcar y encolar vecinos no bloqueados... }
}
// se retrocede desde Nox hasta el primer paso del perro
```

Se recalcula cada 14 fotogramas. **Por qué solo los jefes:** el método barato funciona en calle abierta, pero no sabe salir de un laberinto. Entre los contenedores del puerto el jefe recorría 4.900 px dando vueltas para avanzar 280 y no llegaba nunca — y como hay que derrotarlo para abrir la salida, eso dejaba el barrio bloqueado.

Un pandillero que lleve más de 18 fotogramas trabado también pide ruta, pero hay un **presupuesto de 4 cálculos por fotograma** (`pathBudget`) para que 90 perros a la vez no hundan el rendimiento. El array `pathPrev` y la cola se reutilizan entre llamadas para no crear basura.

**Medido:** barrio 6 en PESADILLA con 76 enemigos, **6,9 ms por fotograma** contando lógica y dibujo, sobre los 16,7 disponibles para ir a 60 fps.

### 5.6 Los enemigos

Siete tipos, definidos en el constructor de `Enemy` con un `switch`:

| Tipo | Vida | Vel. | Tamaño | Recompensa | Particularidad |
|---|---|---|---|---|---|
| Callejero | base | 2,7 | 40 | $150 | El común |
| Armado | ×0,85 | 2,2 | 42 | $180 | Dispara desde lejos |
| Corredor | ×0,55 | 4,5 | 34 | $130 | Rápido y frágil |
| Bruto | ×1,9 | 1,7 | 54 | $260 | Lento y muy duro |
| Con escudo | ×1,3 | 2,0 | 46 | $300 | Bloquea el 85% de frente |
| Alfa | ×1,5 | 2,9 | 50 | $350 | Aúlla y llama refuerzos |
| Kamikaze | ×0,35 | 5,2 | 30 | $120 | Corre y explota |

La vida base es `110 + nivel × 28`, multiplicada por la dificultad.

**El escudo** se resuelve comparando ángulos: se calcula desde dónde viene el golpe y hacia dónde mira el perro, y si la diferencia es menor de `PI/2.2` el daño baja al 15%:

```java
double diff = Math.abs(normalizeAngle(ang - facingAng));
if (diff < Math.PI / 2.2) { dealt = Math.max(1, (int)(dealt * 0.15)); blockedByShield = true; }
```

`normalizeAngle()` mete el ángulo entre −π y π; sin eso, comparar ángulos alrededor de 0 daría resultados absurdos.

**Los refuerzos del alfa** no se añaden directamente a `enemies`: se meten en una lista aparte `spawnQueue` y se vuelcan al terminar el bucle. Añadir a una lista mientras se recorre con un iterador lanza `ConcurrentModificationException`.

### 5.7 Los jefes

`bossSpecial()` es un `switch` por barrio y cada método usa el terreno de su zona:

| Función | Barrio | Qué hace |
|---|---|---|
| `rockyLanzaBidones()` | 1 | Crea 2-3 proyectiles con `molotov = true` que explotan al chocar |
| `spikeVuelcaPuestos()` | 2 | Convierte en suelo todos los muros en radio 3-4 y hace daño en área: **cambia el terreno de la pelea** |
| `rexEmboscada()` | 3 | Busca una casilla despejada cerca del jugador y **teletransporta al jefe allí**, más refuerzos |
| `titanLanzaContenedor()` | 4 | Un proyectil lento con `container = true` (radio 165, más daño) y una onda de 10 balas |
| `shadowSalta()` | 5 | Salta 230-300 px hacia el jugador **cruzando el vacío**, que el jugador no puede |
| `colmilloApagaLuces()` | 6 | Pone `lightsOutTimer`, que reduce el radio de visión un 68%, y suelta sombras |

**La segunda fase:** al bajar del 50% de vida se activa `bossEnraged`, que sube la velocidad un 30% y casi divide por dos los tiempos de espera entre ataques.

### 5.8 Las armas

| Arma | Daño | Espera | Cómo funciona |
|---|---|---|---|
| Garras | 30 | 17 frames | Golpe en radio 62 px |
| Garras de Acero | 85 | 22 | Golpe en radio 115 px |
| Ballesta | 55 | 16 | Un proyectil recto rápido |
| Plasma | 45 ×5 | 34 | Cinco proyectiles en abanico (`baseAngle + i * 0.16`) |
| Molotov | 90 en área | 58 | Proyectil que explota; **ignora escudos** |

El ataque cuerpo a cuerpo también llama a `breakPropsNear()`, que revienta bidones y cajas.

### 5.9 Los recursos del jugador

- **Cordura** (`sanity`): baja cerca de los perros, sube al alejarse. Decide el final.
- **Salud**: `maxHealth()` devuelve 100, o 125 si Mitsy está rescatada.
- **Armadura**: `ARMOR_REDUCTION = {0, 0.15, 0.30, 0.45}` aplicada en `damagePlayer()`.
- **Bonos de familia**: `familyDamageBoost()` (+15% con Will) y `sanityDrainFactor()` (×0,6 con Nina).

`damagePlayer()` tiene un `damageCooldown` de 26 fotogramas para que un perro pegado no vacíe la barra en medio segundo.

### 5.10 Las alucinaciones

```java
private double madness() {
    return Math.max(0, Math.min(1, (68 - sanity) / 68.0));
}
```

Devuelve 0 con la cordura por encima de 68 y 1 cuando está a cero. Todo lo demás escala con ese número: la probabilidad de que aparezca un perro fantasma, de que se oiga algo detrás, de que cruce una sombra.

**La regla de diseño es que ninguna alucinación puede hacer daño.** Si un perro imaginario matara, el jugador dejaría de explorar con la cordura baja y el sistema se volvería un castigo en vez de atmósfera. Los fantasmas se dibujan con `AlphaComposite` semitransparente y **se desvanecen si te acercas**:

```java
if (Point2D.distance(gh.x, gh.y, playerX, playerY) < 110) gh.life -= 6;
```

### 5.11 El susto en tres tiempos

Una máquina de tres estados (`SCARE_DREAD`, `SCARE_IMPACT`, `SCARE_AFTER`) que avanza en `updateJumpscare()`:

| Fase | Dura | Qué pasa |
|---|---|---|
| **Aviso** | 80 frames | `AudioEngine.duckMusic(3.2)` corta la música, la luz se cierra, se oyen pasos por un lado concreto |
| **Golpe** | 42 frames | El dibujo crece hacia la cámara, rugido, temblor, vibración |
| **Resaca** | 55 frames | Pantalla apagada, imagen quemada en la retina, pitido y latido |

**El corte de música es la pieza clave**: el silencio repentino avisa al cuerpo de que algo va a pasar, y el golpe posterior asusta mucho más.

### 5.12 Los estados de pantalla

Son `boolean` con su temporizador. Los tres de historia (`memoryActive`, `homeActive`, `rescueMessageActive`) se pueden **saltar** con `dismissStoryScreen()`.

> **Un bug que hubo aquí:** los restos del callejón bloquean el paso, así que Nox se queda parado al lado. Como durante la pantalla no se puede mover, en cuanto terminaba **se volvía a disparar sola**, una y otra vez, sin forma de salir. Se arregló con la marca `homeSeen` (solo salta una vez por barrio) y dejando saltar la pantalla.

---

## 6. `MapGenerator.java`

Genera el terreno. **Cada barrio tiene su propio algoritmo.**

| Función | Barrio | Algoritmo |
|---|---|---|
| `basurero()` | 1 | Parte de suelo libre y estampa **68 manchas circulares** de radio 1-3 |
| `mercado()` | 2 | **Hileras horizontales** de puestos de largo aleatorio con pasillos entre medias |
| `metro()` | 3 | Parte de **roca maciza** y excava túneles horizontales cada 6 casillas (andén-vía-andén), más conectores verticales entre túneles |
| `puerto()` | 4 | Bloques rectangulares de **contenedores** en filas, más charcas de agua |
| `azoteas()` | 5 | Parte de **vacío** y coloca tejados rectangulares, unidos con `plank()` |
| `mansion()` | 6 | **Salones** rectangulares que no se solapan, unidos con `corridor()` en L |

### El problema de los mapas aleatorios

Un mapa al azar **puede salir imposible de terminar**. Hay tres capas de protección, y las tres hicieron falta de verdad:

**Paso 1 — Camino garantizado.** `carve()` excava a la fuerza un pasillo de la entrada al centro y del centro a la salida, despejando un cuadrado de 3×3 en cada paso.

**Paso 2 — Conectar lo aislado.** `connectRegions()` recorre el mapa con `flood()` (inundación) desde donde aparece Nox. Toda zona de suelo sin marcar con más de 8 casillas está incomunicada, así que se le excava un pasillo hasta el punto más cercano de la zona principal (`nearest()`).

> Sin este paso, el Basurero dejaba **el 43% del mapa inaccesible** detrás de las montañas de basura. Con él sube al 98-100%.

**Paso 3 — Colocar los objetos solo donde se llega.** Esto vive en `LabyrinthPanel.buildLevel()`, y usa `MapGenerator.reachable()`, que devuelve un mapa de booleanos con lo alcanzable. El jefe se planta en la casilla accesible **más lejana** a la entrada (`farthestFromStart()`), prefiriendo las que tienen hueco alrededor.

> Este tercer paso salió de un fallo real: en la mansión el jefe se colocaba en una posición fija que a veces caía **dentro de un muro**. Como la salida exige derrotarlo, ese barrio quedaba imposible de terminar.

Además, `placeShop()` comprueba después de colocar un puesto que la salida siga siendo alcanzable, y si no, lo retira. Lo mismo con el callejón quemado.

**Verificación:** `SmokeTest` genera **150 mapas aleatorios** y comprueba en todos que se pueda llegar caminando a la salida, al jefe, a la tienda y a las tres fotos. En pruebas de esfuerzo se han comprobado **3.000 mapas** sin un solo fallo.

---

## 7. `Tiles.java`

El mapa es un simple `int[][]`. Cada número es un material:

| Valor | Constante | Bloquea | Qué es |
|---|---|---|---|
| 0 | `FLOOR` | no | Suelo libre |
| 2 | `COIN` | no | Moneda |
| 3 | `WALL` | sí | Estructura grande |
| 4 | `SHOP` | sí | Puesto del Mercado Negro |
| 5 | `EXIT` | no | Salida del barrio |
| 6 | `WATER` | sí | Agua del puerto |
| 7 | `VOID` | sí | Vacío entre azoteas |
| 8 | `DECOR` | no | Suelo decorado (vías, alfombra, tablón) |
| 9 | `PROP` | sí | Obstáculo pequeño, se puede romper |
| 10 | `PHOTO` | no | Foto escondida |
| 11 | `PROP_SECRET` | sí | Idéntico a `PROP` por fuera, guarda $500 |
| 12 | `HOME` | sí | Los restos del callejón |

Tres funciones: `blocks()`, `walkable()` y `breakable()`. Tenerlas centralizadas evita que el motor y el dibujo discrepen sobre qué se puede pisar.

---

## 8. `LevelInfo.java`

Una clase de datos con la ficha de cada barrio: nombre, jefe, raza, frase de presentación, a quién se rescata y la paleta de colores (fondo, niebla cercana, niebla lejana, color del jefe).

`buildCampaign()` devuelve el array con los seis. Tener esto separado permite cambiar un color o una frase sin tocar el motor.

---

## 9. `Sprites.java`

Todo se dibuja con **Java 2D**: rectángulos, óvalos, polígonos, curvas y degradados.

### La caché de casillas

En pantalla caben unas **234 casillas**. Dibujar cada una con sus 15-20 figuras **60 veces por segundo** serían más de 200.000 operaciones por segundo.

La solución es dibujar cada material **una sola vez** en una imagen en memoria y luego solo estamparla:

```java
public static BufferedImage tile(int type, int theme, int variant, int size) {
    long key = ((long) type << 44) | ((long) theme << 36) | ((long) variant << 20) | size;
    BufferedImage img = CACHE.get(key);
    if (img == null) { img = renderTile(type, theme, variant, size); CACHE.put(key, img); }
    return img;
}
```

La clave del mapa empaqueta los cuatro valores en un `long` con desplazamientos de bits, que es más rápido que crear un objeto por consulta.

Se guardan **8 variantes** de cada material. ¿Cuál le toca a cada casilla? Lo decide una **función hash** de sus coordenadas:

```java
public static int hash(int x, int y) {
    int h = (x * 73856093) ^ (y * 19349663);
    h ^= (h >>> 13);
    h *= 1274126177;
    h ^= (h >>> 16);
    return h & 0x7FFFFFFF;
}
```

Los números son primos grandes, elegidos para que coordenadas parecidas den resultados muy distintos. Como es determinista, **una casilla concreta se ve siempre igual** aunque se redibuje. Ese mismo hash decide qué ventanas están encendidas y dónde hay charcos.

### Las paletas

`PALETTE` es una matriz de colores, seis filas (una por barrio) de seis colores: suelo, suelo alternativo, muro, muro oscuro, muro claro y acento. Los métodos `floor()`, `wall()`, `prop()` y `decor()` son `switch` sobre el barrio que dibujan materiales distintos: bolsas de basura, puestos con toldo, azulejos con tuberías, contenedores corrugados, ladrillo con ventanas, papel tapiz con retratos.

### Los personajes

Nox y los perros **no se cachean**, porque se mueven:

- **`drawNox()`**: cola con una curva cuadrática (`QuadCurve2D`) que oscila con un seno del contador de fotogramas, orejas triangulares, bigotes, patas que alternan, ojos verdes con halo (`RadialGradientPaint`) y la cicatriz. Se dibuja **1,45 veces más grande** que su caja de colisión para que destaque sin ser difícil de mover por los callejones. Acepta el nivel de armadura y el modo dorado.
- **`drawDog()`**: la misma función sirve para los 7 tipos y los 6 jefes. Cambian tamaño, color, forma de las orejas según la raza (caídas o puntiagudas), si lleva arma, si lleva escudo y si está aturdido.

### La iluminación

Java 2D no tiene luces. Se simulan en dos pasos:

1. **La oscuridad**: un `RadialGradientPaint` centrado en Nox, de transparente a negro. Su radio es `330 + sanity * 1.9`: cuanto peor está, más se cierra.
2. **Las luces**: *después* de la oscuridad se pintan círculos de degradado semitransparente sobre neones, fuego, la salida y los ojos de Nox. Al ir encima, "levantan" la oscuridad y parecen focos.

---

## 10. `Particles.java`

Una lista de partículas con posición, velocidad, gravedad, rozamiento, color, tamaño y vida:

```java
p.x += p.vx;  p.y += p.vy;
p.vy += p.gravity;
p.vx *= p.drag;
if (--p.life <= 0) quitar;
```

| Tipo | Se usa en | Cómo se dibuja |
|---|---|---|
| `SPARK` | Impactos, disparos | Punto brillante con halo |
| `SMOKE` | Muertes, explosiones, pasos | Círculo que **crece** al desvanecerse |
| `EMBER` | Bidones ardiendo | Brasa naranja que sube |
| `DEBRIS` | Perros que caen | Cuadraditos despedidos |
| `RING` | Explosiones | Anillo que se expande |

Emisores: `hit()`, `death()`, `explosion()`, `ember()`, `splash()`, `muzzle()`, `step()` y `text()` (números flotantes). Hay un **tope de 700 partículas** para que una explosión grande no baje los fotogramas.

---

## 11. `Jumpscares.java`

Los diez sustos. Tres funciones públicas de dibujo, una por fase: `drawDread()`, `drawImpact()` y `drawAfter()`. `drawImpact()` es un `switch` con un método por susto.

| Susto | Cómo está dibujado |
|---|---|
| `EMBESTIDA` | Un perro que crece hacia la cámara sobre fondo rojo |
| `OJOS` | Dos elipses rojas que se abren (la altura crece con el avance) con halo radial |
| `ESTATICA` | 90 rectángulos blancos al azar y un perro semitransparente temblando |
| `JAURIA` | Siete perros colocados en círculo que se acercan al centro |
| `REFLEJO` | Una cara de gato dibujada a mano: orejas triangulares, ojos que **viran de verde a rojo** según avanza, demasiados dientes |
| `GARRAS` | Cuatro polígonos que cruzan la pantalla dejando ver "carne" debajo |
| `FAUCES` | Dos elipses concéntricas que se abren, con dientes triangulares arriba y abajo |
| `SUSURRO` | **Casi nada**: negro y una silueta al 13% de opacidad |
| `COLMILLO` | El jefe final a pantalla completa |
| `SOMBRA` | Una figura alta con dos ojos amarillos |

**Dos decisiones de diseño:**

- **`SUSURRO` no hace ruido.** Cuando el jugador ya se ha acostumbrado a que el susto sea un estruendo, que un día no lo sea desconcierta más.
- **`COLMILLO` no puede salir antes del barrio 4.** `pick()` lo filtra. Ver la cara del jefe final antes de conocerle no da miedo: revienta la sorpresa.

`pick()` además **nunca repite el anterior**: recibe el último tipo usado y sortea hasta sacar otro.

---

## 12. `Cinematics.java`

Las cinemáticas del prólogo y los dos finales. **No son vídeo ni imágenes**: son escenas dibujadas con Java 2D, igual que el resto del juego.

### Cómo está montado

Una cinemática es una lista de **planos**. Tres arrays paralelos guardan los datos:

```java
private static final int[][] SHOT_LENGTHS = { {300,340,300,280,340,300}, ... };
private static final String[][][] SHOT_TEXTS = { { {"La ciudad no duerme.", ...}, ... }, ... };
```

Dentro de cada plano hay un valor que va de 0 a 1:

```java
double t = 1 - timer / (double) total;   // 0 al empezar el plano, 1 al acabar
```

**Ese valor lo mueve todo.** Hacer que algo cruce la pantalla es `int arrastre = (int)(t * w * 0.35)`. Hacer que la cámara se acerque es `g.scale(4.2 + t * 0.5, ...)`. Los fundidos de entrada y salida y la aparición escalonada de cada frase salen del mismo número:

```java
if (t < 0.09) alpha = (int) (255 * (1 - t / 0.09));        // fundido de entrada
else if (t > 0.93) alpha = (int) (255 * ((t - 0.93) / 0.07));  // de salida
```

### Las escenas

| Cinemática | Planos | Escenas |
|---|---|---|
| **Prólogo** | 6 (~31 s) | La ciudad bajo la lluvia → el callejón con la familia bajo la bombilla → la pandilla acercándose → Nox negándose en primer plano → el incendio → las cenizas |
| **Final bueno** | 4 (~19 s) | Colmillo caído → los cuatro volviendo a casa → los cuatro en la azotea → el amanecer |
| **Final oscuro** | 4 (~20 s) | Colmillo caído → Mitsy llamándolo y él sin girarse → alejándose hasta que la oscuridad se lo traga → la ciudad llena de ojos |

### Las piezas reutilizables

Las escenas se montan combinando funciones comunes:

| Función | Qué dibuja |
|---|---|
| `skyline()` | Siluetas de edificios con ventanas encendidas (usa el mismo hash de `Sprites`) |
| `lluvia()` | Gotas repartidas con el hash. **Con multiplicaciones simples salían alineadas en diagonales visibles** |
| `viñeta()` | Oscurecimiento de los bordes, con intensidad regulable |
| `cieloNocturno()` / `cieloAmanecer()` | Fondos; el amanecer es un degradado línea a línea |
| `humo()` | Columnas que suben y se desvanecen |

A los personajes los dibuja **`Sprites`**, los mismos que se usan jugando, escalados con `AffineTransform`:

```java
java.awt.geom.AffineTransform saved = g.getTransform();
g.translate(w / 2, suelo);
g.scale(1.9, 1.9);
Sprites.drawNox(g, -74, -46, 3, frame, false, false, false, 0);
g.setTransform(saved);   // siempre restaurar
```

**Guardar y restaurar la transformación es obligatorio**: si se olvida, todo lo que se dibuje después sale escalado y desplazado.

### Control

`update()` avanza el temporizador y pasa de plano. `nextShot()` salta al siguiente (`ESPACIO` / botón A) y `skip()` termina la cinemática entera (`ESC` / START). En `LabyrinthPanel`, `cinematicAudioCue()` dispara un sonido al entrar en ciertos planos: el gruñido cuando llega la pandilla, la explosión en el incendio, el acorde en las cenizas.

---

## 13. `AudioEngine.java`

**No reproduce archivos de sonido: los calcula.**

### Cómo funciona

El sonido digital es una lista de números que representan la posición del altavoz miles de veces por segundo. El motor trabaja a **44.100 muestras por segundo, 16 bits, 2 canales**.

Un hilo aparte (`mixLoop`) rellena continuamente un búfer de 1024 muestras:

1. Pide una muestra de música (centrada en los dos canales)
2. Suma las muestras de todos los efectos que suenen, cada uno con su volumen por canal
3. Recorta para que no sature
4. Convierte a bytes y escribe en la tarjeta

Como música y efectos **se mezclan a mano en un solo canal de salida**, pueden sonar a la vez sin límite ni tirones.

### Los efectos

Cada efecto es una fórmula. El zarpazo:

```java
SFX.put("claw", render(0.18, t -> {
    double e = Math.exp(-t * 26);                                  // se apaga rápido
    return (noise() * 0.55                                         // ruido = "shhh"
          + Math.sin(2*Math.PI*(1500 - 3800*t) * t) * 0.45) * e;   // silbido descendente
}));
```

Las piezas son siempre las mismas:

- **Seno** → tono limpio, silbidos, campanas
- **Sierra** y **cuadrada** → sonido áspero: gruñidos, disparos
- **Ruido** (números aleatorios) → explosiones, viento, roces
- **Envolvente** (`Math.exp(-t * k)`) → cómo aparece y se apaga

Hay **42 efectos** hechos así, desde el ladrido hasta el pitido en los oídos.

### El sonido posicional

```java
public static void playAt(String name, double gain,
                          double sourceX, double sourceY,
                          double listenerX, double listenerY) {
    double falloff = 1 - dist / HEARING_RANGE;
    falloff *= falloff;                            // la caída no es lineal
    double pan = Math.max(-1, Math.min(1, dx / 420.0));
    play(name, gain * falloff, pan);
}
```

Para repartirlo entre canales se usa **panoramización de potencia constante**:

```java
double angle = (p + 1) * Math.PI / 4;
this.gainLeft = gain * Math.cos(angle);
this.gainRight = gain * Math.sin(angle);
```

Con el método ingenuo (`1−p` y `p`) un sonido centrado suena más flojo que uno del todo a un lado. Con seno y coseno el volumen percibido se mantiene al desplazarse.

### La música

Un **generador continuo**, no un archivo en bucle. Cada muestra suma cuatro capas: un **drone** grave, un **arpegio** de escala menor, un **pad** con vibrato y el **latido del corazón**, que aparece cuando baja la cordura y **se acelera** conforme empeora.

Cada barrio tiene su nota raíz y su patrón (la mansión usa la segunda menor, que suena siniestra). Con un jefe delante, `bossMode` acelera el arpegio de 0,42 a 0,20 segundos por nota.

**Detalle técnico:** las frecuencias se acumulan en un **acumulador de fase** en vez de recalcular el seno desde cero. Si no, al cambiar de nota habría un salto brusco en la onda y se oiría un "clic".

`duckMusic(segundos)` silencia la música con un fundido; se usa antes de los sustos.

Si el ordenador no tiene tarjeta de sonido, el motor lo detecta y **el juego sigue funcionando en silencio**.

---

## 14. `Gamepad.java`

Java **no tiene** API para mandos. Lo normal sería una librería externa con `.dll` propios. Aquí se usa la API **Foreign Function and Memory** de Java 22, que permite llamar a funciones de una DLL del sistema **desde Java puro**.

### Cómo se enlaza

```java
Linker linker = Linker.nativeLinker();
SymbolLookup lookup = SymbolLookup.libraryLookup("xinput1_4.dll", arena);
MemorySegment addr = lookup.find("XInputGetState").orElseThrow();
getState = linker.downcallHandle(addr, FunctionDescriptor.of(
    ValueLayout.JAVA_INT,      // devuelve DWORD
    ValueLayout.JAVA_INT,      // índice del mando (0 a 3)
    ValueLayout.ADDRESS));     // puntero a XINPUT_STATE
```

Se prueban tres nombres de DLL (`xinput1_4`, `xinput1_3`, `xinput9_1_0`) porque cambian según la versión de Windows.

### Cómo se leen los datos

XInput rellena una estructura de 16 bytes y se leen los campos por su posición:

| Posición | Campo |
|---|---|
| 0 | Contador de cambios |
| 4 | Los 14 botones, uno por bit |
| 6 / 7 | Gatillos (0-255) |
| 8, 10 | Stick izquierdo (−32768 a 32767) |
| 12, 14 | Stick derecho |

Los botones vienen **empaquetados en bits** (A es `0x1000`, B `0x2000`), así que se consultan con un AND.

### Tres detalles que lo hacen funcionar

**Zona muerta.** Los sticks nunca marcan cero exacto. Se descarta todo por debajo de 7849 (el valor que recomienda Microsoft) y el resto se reescala a 0-1, para que no haya un salto al salir de la zona muerta.

**Flanco de pulsación.** Se guardan los botones del fotograma anterior:

```java
public static boolean pressed(int button) {
    return (buttons & button) != 0 && (previousButtons & button) == 0;
}
```

Sin esto, pulsar START una vez alternaría la pausa 60 veces por segundo.

**Limitar la vibración.** Cada orden de vibrar es una llamada al sistema y cuesta tiempo. Si ya vibra igual o más fuerte, solo se alarga la duración. Sin este freno, en combate el tiempo por fotograma subía de 6,5 a 11,2 ms.

**Si algo falla**, todo está envuelto en `catch (Throwable)` y el juego sigue con teclado.

---

## 15. `Achievements.java`

Los 25 logros. Cada uno es un objeto `Ach` con identificador, nombre, descripción y si es secreto. Se guardan en un `LinkedHashMap`, que **mantiene el orden de inserción** — importante para que la lista salga siempre igual.

| Función | Qué hace |
|---|---|
| `unlock(String)` | Desbloquea si no lo estaba, encola el aviso, guarda y suena. Devuelve `true` solo la primera vez |
| `load()` / `save()` | Persisten en `exodo_logros.properties` |
| `updateToasts()` / `drawToasts()` | El aviso deslizante de la esquina |

El aviso entra y sale deslizándose, con el movimiento calculado desde el temporizador:

```java
double slide = t < 0.12 ? t / 0.12 : t > 0.9 ? (1 - t) / 0.1 : 1;
```

Se dibuja **abajo a la derecha**: arriba está el HUD y la barra de vida del jefe.

Los secretos muestran "LOGRO SECRETO" hasta que se consiguen.

---

## 16. `Settings.java`

Opciones guardadas en `exodo_config.properties`: música, efectos, sustos, destellos, temblor, números de daño, vibración y pantalla completa.

Las de accesibilidad **no son un adorno**: este es un juego de terror con sustos a pantalla completa y parpadeos rojos, y hay gente a la que eso le sienta mal. Poder apagarlos permite enseñar el juego a cualquiera. Cuando los sustos están apagados **la cordura baja igual**: el juego no cambia, solo deja de agredir.

---

## 17. `Memories.java`

Los 18 textos de las fotos (3 por barrio), en una matriz `String[6][3]`. Están separados del código del juego para poder reescribir la historia sin tocar el motor. `SANITY_REWARD = 12` es lo que da cada foto.

---

## 18. `SaveManager.java`

Dos niveles.

**Local (el que manda).** `exodo_save.properties` con `java.util.Properties`, el formato clave=valor de siempre:

```properties
level=3
money=1250
armor=2
family=110
photos=111000110000000000
```

Los arrays de booleanos se guardan como cadenas de ceros y unos (`familyAsString()`, `photosAsString()`). Se guarda al empezar cada barrio, al comprar y al morir.

**En red (opcional).** Manda lo mismo al servidor Python. Dos decisiones importantes:

- Se envía en un **hilo aparte**, nunca en el hilo del juego
- Con **900 ms de espera máxima**

Así, si el servidor está apagado, el juego **no se congela ni un fotograma**: el indicador de la esquina se queda en `LOCAL` en vez de `NUBE OK`.

---

## 19. `SmokeTest.java`

Prueba automática que arranca una partida **sin abrir ventana**, simula miles de fotogramas y dibuja todas las pantallas sobre una imagen en memoria. Usa **reflexión** (`Field.setAccessible`) para leer y forzar el estado interno del juego.

```bash
java --enable-native-access=ALL-UNNAMED -cp ".;flatlaf-3.4.1.jar" SmokeTest
```

Comprueba, entre otras cosas:

- 4.000 fotogramas de juego y dibujo sin excepciones
- Las 5 armas, los 7 tipos de perro, la tienda entera
- **150 mapas aleatorios**: salida, jefe, tienda y las 3 fotos siempre alcanzables
- Que los **6 jefes persiguen** sin quedarse atascados (mide distancia recorrida y % de tiempo parado)
- Los **6 ataques especiales** de jefe
- Los **10 sustos** en sus tres fases, y que no se repitan
- Las **3 cinemáticas** enteras y que se puedan saltar
- Que las alucinaciones aparezcan **solo** con la cordura baja y **no hagan daño**
- Que las pantallas de historia **no bloqueen** el juego
- Que el mando se lea **una sola vez por fotograma**
- Que el dibujo funcione en 5 resoluciones distintas

**Cómo se escriben estas pruebas:** cuando aparece un fallo, primero se escribe una prueba que lo reproduzca, **se comprueba que falla**, y solo entonces se arregla. Sin ese paso no hay forma de saber si la prueba sirve de algo. Varios de los arreglos de este proyecto se validaron así: quitando el arreglo a propósito y confirmando que la prueba lo detectaba.

---

## 20. `server.py`

La única parte que no es Java, y es **totalmente opcional**. Usa **FastAPI**.

| Ruta | Qué hace |
|---|---|
| `POST /api/save_player/{id}` | Guarda el progreso |
| `GET /api/load_player/{id}` | Lo devuelve |
| `GET /api/progress/{id}` | Resumen legible, incluido qué final llevas encaminado |
| `POST /api/score` | Registra una puntuación |
| `GET /api/scores` | Tabla de récords |
| `GET /api/status` | Estado del servidor |

Guarda en `server_data.json`. **Pydantic** valida lo que llega (que el barrio esté entre 1 y 6, la armadura entre 0 y 3), de modo que un dato corrupto no rompe el servidor.

```bash
pip install fastapi uvicorn
uvicorn server:app --reload
```

---

## 21. REPARTO DE LENGUAJES

| Lenguaje | Líneas | Porcentaje |
|---|---|---|
| **Java** | 8.255 | **98,3%** |
| Python | 147 | 1,7% |

El proyecto cumple de sobra la regla de que al menos el 60% sea Java. Ese porcentaje no es casualidad: elegir **gráficos dibujados con Java 2D**, **audio sintetizado con `javax.sound.sampled`** y el **mando leído con `java.lang.foreign`**, en lugar de librerías externas o archivos de recursos, mantiene todo el peso del proyecto en Java.
