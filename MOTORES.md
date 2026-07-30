# CÓMO FUNCIONA POR DENTRO — ÉXODO: LA VENGANZA DE NOX

Este documento explica **los motores que hacen funcionar el juego**, qué hace cada archivo de código y qué librerías se usan. Está pensado para poder explicar el proyecto sin tener que leer las 3.000 líneas de código.

---

## 1. Idea general

El juego es una aplicación de **escritorio en Java** con interfaz **Swing**. No usa ningún motor de videojuegos (ni Unity, ni Godot, ni LibGDX): todo está programado desde cero sobre las librerías que ya trae Java.

La decisión de diseño más importante de todo el proyecto es esta:

> **No hay ni una sola imagen ni un solo archivo de sonido en el proyecto.**
> Los gráficos se dibujan con figuras geométricas y el audio se genera calculando ondas sonoras en el momento.

¿Por qué? Porque así el proyecto se copia a una USB o se clona de GitHub y **funciona igual en cualquier computadora**, sin que se pierda una carpeta de recursos ni haya que instalar nada aparte.

### Los cinco motores

```
                    ProjectExodusAdvanced  (ventana + menú)
                              |
                              v
                       LabyrinthPanel   <-- el corazón: bucle de juego
                              |
        +---------+-----------+-----------+------------+
        |         |           |           |            |
        v         v           v           v            v
   MapGenerator  Sprites   Particles  AudioEngine  SaveManager
   (escenarios) (gráficos) (efectos)   (sonido)    (guardado)
                                                        |
                                                        v
                                                    server.py
                                                (opcional, en red)
```

---

## 2. Motor de juego (`LabyrinthPanel.java`)

Es el archivo más grande y el que coordina todo lo demás.

### El bucle de juego

Un videojuego necesita repetir tres cosas muchas veces por segundo: **leer los controles**, **actualizar el estado** y **dibujar**. Aquí eso lo consigue un `javax.swing.Timer` configurado a **16 milisegundos**, es decir unos **60 fotogramas por segundo**:

```java
gameLoop = new Timer(16, this);   // cada 16 ms llama a actionPerformed()
```

Cada vez que el temporizador salta, `actionPerformed()` ejecuta en orden:

1. `updatePlayer()` — mueve a Nox, recoge monedas, detecta la tienda y la salida.
2. `updateBullets()` — mueve los proyectiles y comprueba impactos.
3. `updateEnemies()` — mueve a los perros, dispara, aplica daño, gestiona a los jefes.
4. `updateAtmosphere()` — lluvia, parpadeos, brasas, temblor de cámara.
5. `fx.update()` — avanza las partículas.
6. `repaint()` — pide a Swing que vuelva a dibujar la pantalla.

Después Swing llama a `paintComponent()`, que dibuja **en este orden concreto** (el orden importa: lo que se pinta después tapa lo anterior):

```
mundo -> personajes -> partículas -> lluvia -> oscuridad -> luces -> HUD -> menús
```

### La cámara

El mundo mide 50×50 casillas de 60 píxeles = 3000×3000 píxeles, pero la ventana solo mide 1024×768. La cámara resuelve esto **desplazando el sistema de coordenadas** antes de dibujar:

```java
g2d.translate(-camX, -camY);   // dibuja el mundo desplazado
...
g2d.translate(camX, camY);     // lo devuelve a su sitio para el HUD
```

La cámara se centra en Nox pero se **frena en los bordes** del mapa para no mostrar el vacío de afuera.

> **Detalle importante:** como la cámara se frena en los bordes pero Nox sigue moviéndose, el círculo de luz **no** puede dibujarse en el centro de la pantalla, sino en la posición real de Nox en pantalla. Si no, al llegar a una esquina del mapa el gato se quedaba a oscuras y no se veía.

### Colisiones

No se usa ningún motor de física. La comprobación es directa: se calcula en qué casillas caerían **las cuatro esquinas** del personaje y se mira si alguna es un muro.

```java
private boolean blocked(double nx, double ny, int size) { ... }
```

El truco está en cómo se aplica el movimiento: **primero se intenta el eje X y luego el eje Y, por separado**.

```java
if (dx != 0 && !blocked(playerX + dx, playerY, PLAYER_SIZE)) playerX += dx;
if (dy != 0 && !blocked(playerX, playerY + dy, PLAYER_SIZE)) playerY += dy;
```

Gracias a eso, si Nox camina en diagonal contra una pared **se desliza a lo largo de ella** en vez de quedarse trabado. Si se comprobaran los dos ejes juntos, cualquier roce lo detendría en seco.

### Inteligencia de los perros

Es intencionadamente sencilla, pero con un detalle que la salva:

1. Si el jugador está dentro del rango de detección, el perro avanza hacia él eje por eje.
2. Si **no consigue moverse en ninguno de los dos ejes** (está pegado a una esquina), activa un `detourTimer`: durante 32 fotogramas camina **perpendicular** al jugador, lo que le hace rodear el obstáculo.

Sin ese segundo paso los perros se quedaban pegados a los edificios empujando contra la pared, y se notaba muchísimo.

Los jefes añaden dos comportamientos con temporizador propio: una **embestida** (velocidad ×2,6 durante 55 fotogramas) y una **ráfaga circular** de 12 balas cada 4 segundos.

### Los dos finales

La barra de **cordura** baja al estar cerca de los perros y al recibir daño, y sube al alejarse, al cambiar de barrio o al comprar el Amuleto. Al derrotar a Don Colmillo:

```java
darkEnding = sanity < 45;
```

Ese único `boolean` decide qué pantalla, qué texto y qué música se usan. La barra de cordura se pone **roja** cuando estás por debajo del umbral, para avisar al jugador sin explicárselo.

---

## 3. Motor de escenarios (`MapGenerator.java`)

Genera el mapa de cada barrio. **Cada barrio tiene su propio algoritmo**, no es el mismo mapa repintado:

| Barrio | Algoritmo |
|---|---|
| 1 Basurero | Se parte de suelo libre y se estampan **68 manchas circulares** de basura de radio aleatorio |
| 2 Mercado | **Hileras horizontales** de puestos de largo aleatorio, separadas por pasillos |
| 3 Metro | Se parte de roca maciza y se **excavan túneles** horizontales cada 6 casillas, más conectores verticales |
| 4 Puerto | Bloques rectangulares de **contenedores** en filas, más charcos de agua rectangulares |
| 5 Azoteas | Se parte de **vacío** y se colocan tejados rectangulares, unidos por **tablones** de una casilla |
| 6 Mansión | **Salones** rectangulares que no se solapan, unidos por pasillos en L con alfombra |

### El problema de los mapas aleatorios (y cómo se resuelve)

Generar un mapa al azar tiene un peligro grave: **puede salir un mapa imposible de terminar**, con la salida encerrada detrás de un muro. El juego se protege con dos pasos:

**Paso 1 — Camino garantizado.** Después de generar el terreno se excava a la fuerza un pasillo de la entrada al centro y del centro a la salida:

```java
carve(m, rand, 2, 2, w/2, h/2, w, h);
carve(m, rand, w/2, h/2, w-3, h-3, w, h);
```

**Paso 2 — Conectar zonas aisladas.** Se recorre el mapa con un **algoritmo de inundación (flood fill)** partiendo de donde aparece Nox. Toda zona de suelo que quede sin marcar y tenga más de 8 casillas está incomunicada, así que se le excava un pasillo hasta la zona principal más cercana.

Este segundo paso no era un lujo: sin él, el Basurero dejaba **el 43% del mapa inaccesible** detrás de las montañas de basura. Con él sube al 99-100%.

`SmokeTest.java` verifica esto automáticamente generando **30 mapas aleatorios** y comprobando en todos que se pueda llegar caminando a la salida, al jefe y a la tienda.

### Tipos de casilla (`Tiles.java`)

El mapa es un simple `int[][]`. Cada número significa un material:

| Valor | Constante | Bloquea | Qué es |
|---|---|---|---|
| 0 | `FLOOR` | no | Suelo libre |
| 2 | `COIN` | no | Moneda |
| 3 | `WALL` | sí | Estructura grande (edificio, contenedor, montaña de basura) |
| 4 | `SHOP` | sí | Puesto del Mercado Negro |
| 5 | `EXIT` | no | Salida del barrio |
| 6 | `WATER` | sí | Agua del puerto |
| 7 | `VOID` | sí | Vacío entre azoteas |
| 8 | `DECOR` | no | Suelo decorado (vías, alfombra, tablón) |
| 9 | `PROP` | sí | Obstáculo pequeño (bidón, caja, mueble) |

---

## 4. Motor gráfico (`Sprites.java`)

Todo se dibuja con **Java 2D** (`Graphics2D`): rectángulos, óvalos, polígonos, curvas y degradados.

### Caché de casillas: el truco de rendimiento

En pantalla caben unas **234 casillas**. Si cada una se dibujara con sus 15-20 figuras (ladrillos, ventanas, corrugado, vetas de mármol) **60 veces por segundo**, serían más de 200.000 operaciones de dibujo por segundo y el juego iría a tirones.

La solución es **dibujar cada material una sola vez** en una imagen en memoria (`BufferedImage`) y después solo **estampar** esa imagen:

```java
public static BufferedImage tile(int type, int theme, int variant, int size) {
    long key = ...;                       // tipo + barrio + variante + tamaño
    BufferedImage img = CACHE.get(key);
    if (img == null) {
        img = renderTile(type, theme, variant, size);   // se dibuja UNA vez
        CACHE.put(key, img);
    }
    return img;
}
```

Se guardan **8 variantes** de cada material para que no se vea todo idéntico. ¿Y cómo se decide qué variante le toca a cada casilla? Con una **función hash** de sus coordenadas:

```java
public static int hash(int x, int y) {
    int h = (x * 73856093) ^ (y * 19349663);
    ...
}
```

Como el hash siempre devuelve lo mismo para las mismas coordenadas, **una casilla concreta se ve siempre igual** aunque se vuelva a dibujar. Ese mismo hash decide qué ventanas están encendidas y dónde hay charcos.

Gracias a la caché se puede meter muchísimo detalle sin coste: azulejos rotos en el metro, corrugado y códigos estarcidos en los contenedores, papel tapiz con retratos en la mansión.

### Los personajes

Nox y los perros **no** se cachean, porque se mueven y se animan. Se dibujan cada fotograma:

- **Nox**: cola que oscila con una curva cuadrática (`QuadCurve2D`), orejas triangulares, bigotes, patas que alternan al caminar, ojos verdes con un halo (`RadialGradientPaint`) y la cicatriz de la noche del incendio. Se dibuja **1,45 veces más grande** que su caja de colisión para que destaque entre los perros sin ser difícil de mover por los callejones.
- **Perros**: la misma función sirve para los 7 tipos y los 6 jefes. Cambian el tamaño, el color, la forma de las orejas según la raza (caídas o puntiagudas), si llevan arma, si llevan escudo y si están aturdidos.

### Iluminación

Java 2D no tiene luces. Se simulan en dos pasos:

1. **La oscuridad**: un `RadialGradientPaint` centrado en Nox que va de transparente en el centro a negro opaco en los bordes. Su radio depende de la cordura: `330 + sanity * 1.9`. Cuanto peor está Nox, más se cierra la oscuridad.
2. **Las luces**: *después* de la oscuridad se pintan círculos de degradado semitransparente sobre los neones, el fuego de los bidones, la salida desbloqueada y los ojos de Nox. Al pintarse encima, "levantan" la oscuridad y parecen fuentes de luz.

---

## 5. Motor de partículas (`Particles.java`)

Es lo que hace que un golpe se sienta como un golpe. Mantiene una lista de partículas, cada una con posición, velocidad, gravedad, rozamiento, color, tamaño y vida.

```java
p.x += p.vx;  p.y += p.vy;   // se mueve
p.vy += p.gravity;           // cae
p.vx *= p.drag;              // se frena
if (--p.life <= 0) quitar;   // desaparece
```

Hay cinco clases de partícula, cada una con su forma de dibujarse:

| Tipo | Se usa en | Cómo se ve |
|---|---|---|
| `SPARK` | Impactos, disparos, explosiones | Punto brillante con halo |
| `SMOKE` | Muertes, explosiones, polvo de los pasos | Círculo que **crece** mientras se desvanece |
| `EMBER` | Bidones ardiendo del basurero | Brasa naranja que sube flotando |
| `DEBRIS` | Cuando cae un perro | Cuadraditos que salen despedidos |
| `RING` | Explosiones | Anillo que se expande |

También gestiona los **números flotantes** de daño y dinero.

Hay un límite de **700 partículas**; pasado ese número se dejan de crear, para que una explosión grande no baje los fotogramas.

---

## 6. Motor de audio (`AudioEngine.java`)

Este es probablemente el motor más curioso: **no reproduce archivos de sonido, los calcula**.

### Cómo funciona

El sonido digital es una lista de números que representan la posición del altavoz miles de veces por segundo. Si se calculan esos números, se puede fabricar cualquier sonido. El motor trabaja a **44.100 muestras por segundo, 16 bits, mono**.

Un hilo aparte (`mixLoop`) rellena continuamente un búfer de 1024 muestras:

```java
1. Pide una muestra de música
2. Suma las muestras de todos los efectos que estén sonando
3. Recorta el resultado para que no sature
4. Lo convierte a bytes y lo escribe en la tarjeta de sonido
```

Como música y efectos se **mezclan a mano en un solo canal**, pueden sonar a la vez sin límite de canales ni tirones.

### Los efectos

Cada efecto es una fórmula matemática. Por ejemplo, el zarpazo de las garras:

```java
SFX.put("claw", render(0.18, t -> {
    double e = Math.exp(-t * 26);                                  // se apaga rápido
    return (noise() * 0.55                                         // ruido = "shhh"
          + Math.sin(2*Math.PI*(1500 - 3800*t) * t) * 0.45) * e;   // silbido descendente
}));
```

Las piezas que se combinan son siempre las mismas:

- **Seno** (`Math.sin`) → tono limpio, silbidos, campanas.
- **Sierra** y **cuadrada** → sonido áspero: gruñidos, disparos, zumbidos.
- **Ruido** (números aleatorios) → explosiones, viento, roces.
- **Envolvente** (`Math.exp(-t * k)`) → controla cómo aparece y se apaga el sonido.

Así están hechos los 25 efectos: gruñidos de jefe, ladridos, explosiones, el silbato ultrasónico, el escudo al bloquear, el aullido del alfa.

### La música

Es un **generador continuo**, no un archivo en bucle. Cada muestra se calcula sumando cuatro capas:

1. **Drone**: nota grave sostenida, la base.
2. **Arpegio**: notas de una escala menor que van saltando según un patrón por barrio.
3. **Pad**: capa suave con vibrato lento.
4. **Latido del corazón**: aparece cuando la cordura baja y **se acelera** conforme empeora.

Cada barrio tiene su nota raíz y su patrón (la mansión usa la segunda menor, que suena siniestra). Cuando aparece un jefe se activa `bossMode`: el arpegio pasa de 0,42 a 0,20 segundos por nota y se añade un pulso rítmico.

Un detalle técnico: las frecuencias se acumulan en un **acumulador de fase** en vez de recalcular el seno desde cero. Si no, al cambiar de nota habría un salto brusco en la onda y se oiría un "clic".

Si la computadora no tiene tarjeta de sonido, el motor lo detecta, avisa por consola y **el juego sigue funcionando en silencio**.

---

## 7. Motor de guardado (`SaveManager.java`)

Tiene dos niveles:

**Local (el que manda).** Guarda en `exodo_save.properties` con la clase `java.util.Properties`, que es el formato clave=valor de toda la vida. Funciona siempre, sin internet ni servidor.

```properties
level=3
money=1250
armor=2
family=110
```

Se guarda **al empezar cada barrio y al comprar** en la tienda. Eso da puntos de control: si Nox cae, se reintenta desde el último barrio sin perder armas ni familia rescatada.

**En red (opcional).** Manda los mismos datos al servidor Python. Dos decisiones importantes aquí:

- Se envía en un **hilo aparte** (`Thread`), nunca en el hilo del juego.
- Con **tiempo de espera de 900 ms**.

Así, si el servidor está apagado, el juego **no se congela ni un fotograma**: simplemente el indicador de la esquina se queda en `LOCAL` en vez de `NUBE OK`.

---

## 8. El servidor (`server.py`)

Es la única parte que no está en Java, y es **totalmente opcional**. Usa **FastAPI**.

| Ruta | Qué hace |
|---|---|
| `POST /api/save_player/{id}` | Guarda el progreso |
| `GET /api/load_player/{id}` | Devuelve el progreso |
| `GET /api/progress/{id}` | Resumen legible (incluye qué final llevas encaminado) |
| `POST /api/score` | Registra una puntuación |
| `GET /api/scores` | Tabla de récords |
| `GET /api/status` | Estado del servidor |

Guarda todo en `server_data.json`, así que no se pierde al reiniciarlo. **Pydantic** valida los datos que llegan (por ejemplo, que el barrio esté entre 1 y 6), de modo que un dato corrupto no rompe el servidor.

---

## 9. Qué hace cada archivo

### Código Java (el juego)

| Archivo | Líneas aprox. | Responsabilidad |
|---|---|---|
| `ProjectExodusAdvanced.java` | 250 | Crea la ventana, aplica el tema oscuro, cambia entre menú y juego con `CardLayout`. Contiene el `main`. También el menú principal con su lluvia animada. |
| `LabyrinthPanel.java` | 1.500 | El motor de juego: bucle, cámara, colisiones, enemigos, armas, tienda, HUD y todas las pantallas. |
| `MapGenerator.java` | 340 | Genera el terreno de los 6 barrios y garantiza que se puedan terminar. |
| `Sprites.java` | 900 | Dibuja todo: materiales cacheados, Nox, los perros, monedas, tienda, salida y luces. |
| `Particles.java` | 260 | Chispas, humo, brasas, explosiones y números flotantes. |
| `Tiles.java` | 35 | Constantes de los tipos de casilla y si bloquean el paso. |
| `LevelInfo.java` | 80 | Datos de los 6 barrios: nombre, jefe, raza, frase, paleta de colores. |
| `AudioEngine.java` | 430 | Sintetiza música y efectos, y los mezcla en tiempo real. |
| `SaveManager.java` | 230 | Guardado local y sincronización opcional con el servidor. |
| `SmokeTest.java` | 290 | Prueba automática: simula miles de fotogramas y verifica que los mapas se puedan terminar. |

### Otros archivos

| Archivo | Para qué sirve |
|---|---|
| `server.py` | Servidor opcional de progreso y récords |
| `flatlaf-3.4.1.jar` | Librería del tema oscuro |
| `HISTORIA.md` | La historia y el contenido del juego |
| `COMO_EJECUTAR.md` | Instalación y ejecución en una computadora nueva |
| `MOTORES.md` | Este documento |
| `JUGAR.bat` | Doble clic para jugar |
| `CREAR-EJECUTABLE.bat` | Genera el `.exe` y el `.jar` |
| `.vscode/settings.json` | Le dice a VS Code dónde está la librería |
| `.gitignore` | Evita subir a Git los archivos compilados y las partidas |

---

## 10. Librerías utilizadas

### Externa (una sola)

| Librería | Versión | Para qué | Licencia |
|---|---|---|---|
| **FlatLaf** | 3.4.1 | Tema oscuro moderno para los botones y ventanas de Swing. Sin ella el juego se vería con el aspecto gris de Java de los años 90. | Apache 2.0 (gratis, uso libre) |

Es la **única** dependencia externa del juego, y viene incluida en el repositorio como `flatlaf-3.4.1.jar`.

### Del propio Java (no hay que instalar nada)

| Paquete | Para qué se usa |
|---|---|
| `javax.swing` | Ventana, paneles, botones, `Timer` del bucle de juego |
| `java.awt` / `java.awt.geom` | `Graphics2D`, colores, fuentes, degradados, curvas |
| `java.awt.image` | `BufferedImage` para la caché de casillas |
| `javax.sound.sampled` | Salida de audio en crudo (`SourceDataLine`) |
| `java.util` | Listas, mapas, `Random`, `Properties` |
| `java.io` / `java.net` | Guardado en archivo y peticiones HTTP al servidor |
| `javax.imageio` | Solo en las herramientas de captura de pantalla |

### Del servidor Python (opcional)

| Librería | Para qué |
|---|---|
| **FastAPI** | Crear la API web |
| **Uvicorn** | Servidor que ejecuta FastAPI |
| **Pydantic** | Validar los datos que llegan (viene con FastAPI) |

Se instalan con: `pip install fastapi uvicorn`

---

## 11. Reparto de lenguajes

| Lenguaje | Líneas aprox. | Porcentaje |
|---|---|---|
| **Java** | ~4.300 | **~97%** |
| Python | ~170 | ~3% |

El proyecto cumple de sobra la regla de que al menos el 60% sea Java. Ese porcentaje no es casualidad: elegir gráficos dibujados con Java 2D y audio sintetizado con `javax.sound.sampled`, en lugar de librerías externas o archivos de recursos, mantiene todo el peso del proyecto en Java.

---

## 12. Preguntas que te pueden hacer

**¿Por qué Swing y no un motor de videojuegos?**
Porque es un proyecto de Java y la idea es demostrar programación propia. Un motor haría el 90% del trabajo (bucle, colisiones, dibujo, audio) y aquí todo eso está escrito a mano.

**¿Cómo consigues 60 fotogramas por segundo?**
Con un `Timer` de Swing a 16 ms. Para que dé tiempo a dibujar todo en ese margen se usan dos optimizaciones: la caché de casillas y dibujar **solo las casillas visibles** en pantalla, no las 2.500 del mapa.

**¿De dónde salen los sonidos?**
De fórmulas matemáticas. No hay archivos de audio: cada efecto es una onda calculada muestra a muestra.

**¿Qué pasa si el mapa aleatorio sale sin salida?**
No puede pasar: se excava un camino garantizado y después un *flood fill* conecta cualquier zona aislada. La prueba automática lo verifica en 30 mapas.

**¿Qué pasa si no hay internet o el servidor está apagado?**
Nada. El guardado local es el que manda; la sincronización va en un hilo aparte con 900 ms de espera máxima y el juego ni se entera.

**¿Cuál es la parte más difícil del código?**
El motor de audio, porque hay que entender cómo se representa el sonido digitalmente y cuidar detalles como el acumulador de fase para que no se oigan clics al cambiar de nota.
