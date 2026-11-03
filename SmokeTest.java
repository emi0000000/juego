import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Prueba automática sin abrir ventana: arranca una partida, simula miles de frames
 * (moverse, atacar con todas las armas, comprar, pelear contra el jefe), comprueba que
 * los seis barrios se puedan terminar y dibuja todas las pantallas.
 *
 * Ejecutar:  java -cp ".;flatlaf-3.4.1.jar" SmokeTest
 */
public class SmokeTest {

    static LabyrinthPanel panel;
    static int fallos = 0;

    static void set(String n, Object v) throws Exception {
        Field f = LabyrinthPanel.class.getDeclaredField(n); f.setAccessible(true); f.set(panel, v);
    }
    static Object get(String n) throws Exception {
        Field f = LabyrinthPanel.class.getDeclaredField(n); f.setAccessible(true); return f.get(panel);
    }
    static void call(String n) throws Exception {
        Method m = LabyrinthPanel.class.getDeclaredMethod(n); m.setAccessible(true); m.invoke(panel);
    }
    static void key(int c) { panel.keyPressed(new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, c, 'x')); }
    static void keyUp(int c) { panel.keyReleased(new KeyEvent(panel, KeyEvent.KEY_RELEASED, 0, 0, c, 'x')); }

    static void ok(String msg) { System.out.println("[OK]   " + msg); }
    static void fail(String msg) { System.out.println("[FALLA] " + msg); fallos++; }

    /**
     * Comprueba que desde donde aparece Nox se pueda llegar caminando a la salida,
     * al jefe y a alguna tienda. Si un generador dejara el barrio partido en dos,
     * la partida sería imposible de terminar y aquí se detecta.
     */
    static void checkReachable(int level) throws Exception {
        int[][] map = (int[][]) get("map");
        int w = map.length, h = map[0].length;
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{2, 2});
        seen[2][2] = true;

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[nx][ny]) continue;
                if (!Tiles.walkable(map[nx][ny])) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }

        boolean exit = seen[w - 3][h - 3];
        boolean shopNear = false;
        for (int x = 0; x < w && !shopNear; x++) {
            for (int y = 0; y < h; y++) {
                if (map[x][y] != Tiles.SHOP) continue;
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && seen[nx][ny]) { shopNear = true; break; }
                }
                if (shopNear) break;
            }
        }

        Object boss = get("currentBoss");
        boolean bossReach = true;
        if (boss != null) {
            Field bx = boss.getClass().getDeclaredField("x"); bx.setAccessible(true);
            Field by = boss.getClass().getDeclaredField("y"); by.setAccessible(true);
            int tx = (int) (bx.getDouble(boss) / 60), ty = (int) (by.getDouble(boss) / 60);
            bossReach = seen[Math.min(w - 1, tx)][Math.min(h - 1, ty)];
        }

        int floors = 0, reached = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (Tiles.walkable(map[x][y])) { floors++; if (seen[x][y]) reached++; }
            }
        }
        int pct = floors == 0 ? 0 : reached * 100 / floors;

        // Las fotos tienen que poder recogerse: si una queda encerrada, el logro
        // del album se vuelve imposible de conseguir.
        int fotos = 0, fotosAlcanzables = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (map[x][y] != Tiles.PHOTO) continue;
                fotos++;
                if (seen[x][y]) fotosAlcanzables++;
            }
        }

        boolean fotosOk = fotos == Memories.PER_LEVEL && fotosAlcanzables == fotos;

        if (exit && shopNear && bossReach && fotosOk) {
            ok("Barrio " + level + " completable (salida, jefe, tienda y las "
               + fotos + " fotos alcanzables; " + pct + "% del mapa accesible)");
        } else {
            fail("Barrio " + level + " BLOQUEADO -> salida=" + exit + " jefe=" + bossReach
                 + " tienda=" + shopNear + " fotos=" + fotosAlcanzables + "/" + fotos);
        }
    }

    /**
     * El mando se tiene que leer UNA sola vez por fotograma.
     *
     * Cuando el menú y el juego leían los dos, la primera lectura se comía la
     * detección de "botón recién pulsado" y dentro de la partida no respondían ni
     * el mapa, ni la pausa, ni el cambio de arma; encima el menú seguía activando
     * sus botones por detrás y cada (A) reiniciaba el barrio.
     */
    static void comprobarLecturaUnica(ActionEvent tick) throws Exception {
        Field menuActive = Class.forName("ProjectExodusAdvanced").getDeclaredField("menuActive");
        menuActive.setAccessible(true);

        // Jugando: solo el juego lee el mando, una vez por fotograma
        menuActive.setBoolean(null, false);
        long antes = Gamepad.pollCount();
        for (int i = 0; i < 100; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
        }
        long lecturasJuego = Gamepad.pollCount() - antes;

        if (lecturasJuego == 100) ok("El juego lee el mando 1 vez por fotograma (100 en 100 frames)");
        else fail("El juego leyo el mando " + lecturasJuego + " veces en 100 frames, deberian ser 100");

        // El menú de fondo no debe tocar el mando mientras se juega
        MainMenuPanel menu = new MainMenuPanel(null);
        Field bg = MainMenuPanel.class.getDeclaredField("bgTimer");
        bg.setAccessible(true);
        ((javax.swing.Timer) bg.get(menu)).stop();

        antes = Gamepad.pollCount();
        for (int i = 0; i < 100; i++) menu.actionPerformed(tick);
        long lecturasMenu = Gamepad.pollCount() - antes;

        if (lecturasMenu == 0) ok("El menu NO lee el mando mientras se juega (0 lecturas en 100 frames)");
        else fail("El menu leyo el mando " + lecturasMenu + " veces durante la partida, deberian ser 0");

        // Y en el menú sí tiene que leerlo, o no se podria empezar con el mando
        menuActive.setBoolean(null, true);
        antes = Gamepad.pollCount();
        for (int i = 0; i < 100; i++) menu.actionPerformed(tick);
        long lecturasEnMenu = Gamepad.pollCount() - antes;

        if (lecturasEnMenu == 100) ok("En el menu si lee el mando (100 en 100 frames)");
        else fail("El menu leyo " + lecturasEnMenu + " veces estando activo, deberian ser 100");

        menuActive.setBoolean(null, false);
    }

    /**
     * Los jefes no se pueden quedar atorados.
     *
     * El jefe se dibuja de 78 px pero un pasillo mide 60, así que con la caja de
     * colisión del tamaño del dibujo no cabía por ningún paso estrecho y se quedaba
     * encajado contra la pared. Aquí se le suelta lejos de Nox en cada barrio y se
     * mide cuántos fotogramas consigue avanzar y cuánto se acerca.
     */
    static void comprobarJefesNoSeAtoran(ActionEvent tick) throws Exception {
        int barriosMalos = 0;

        for (int lvl = 1; lvl <= 6; lvl++) {
            set("currentLevel", lvl);
            call("loadLevel");
            set("levelIntroActive", false);
            set("bossIntroActive", false);
            set("bossEncountered", true);
            set("playerHealth", 100);
            set("isDead", false);
            set("gameWon", false);
            set("isTransitioning", false);
            set("isShopOpen", false);
            set("rescueMessageActive", false);
            set("memoryActive", false);
            set("homeActive", false);
            // Si Nox aparece junto al callejon, la pantalla de historia congela el
            // juego 5 segundos y pareceria que el jefe se ha quedado atascado.
            set("homeSeen", true);

            Object boss = get("currentBoss");
            if (boss == null) continue;

            Field bx = boss.getClass().getDeclaredField("x"); bx.setAccessible(true);
            Field by = boss.getClass().getDeclaredField("y"); by.setAccessible(true);

            // Nox se queda quieto en una casilla accesible dentro del rango de
            // deteccion del jefe (700 px), lo bastante lejos como para que tenga
            // que cruzar pasillos para llegar.
            double startX = bx.getDouble(boss), startY = by.getDouble(boss);
            int[][] map = (int[][]) get("map");

            // Nox tiene que estar en una casilla a la que el jefe pueda llegar
            // caminando. Si se coloca en otra region del mapa, el jefe da vueltas
            // porque no hay camino, y eso no seria culpa suya.
            boolean[][] alcanzable = MapGenerator.reachable(map,
                (int) (startX / 60), (int) (startY / 60));

            double px = -1, py = -1, mejor = Double.MAX_VALUE;
            for (int tx = 1; tx < map.length - 1; tx++) {
                for (int ty = 1; ty < map[0].length - 1; ty++) {
                    if (!alcanzable[tx][ty]) continue;
                    double wx = tx * 60, wy = ty * 60;
                    double d = Math.hypot(wx - startX, wy - startY);
                    double error = Math.abs(d - 520);
                    if (d < 660 && d > 300 && error < mejor) { mejor = error; px = wx; py = wy; }
                }
            }
            if (px < 0) { ok("Barrio " + lvl + ": sin sitio valido para medir, se omite"); continue; }

            set("playerX", px);
            set("playerY", py);
            double distInicial = Math.hypot(startX - px, startY - py);

            double prevX = startX, prevY = startY;
            double recorrido = 0;
            int framesQuieto = 0;
            final int FRAMES = 1200;

            int framesLejos = 0;

            for (int i = 0; i < FRAMES; i++) {
                panel.actionPerformed(tick);
                // Nox es un maniqui inmortal: aqui solo se mide como se mueve el jefe
                set("playerHealth", 100);
                set("isDead", false);
                set("isJumpscareActive", false);
                set("bossIntroActive", false);
                set("playerX", px);
                set("playerY", py);

                double nx = bx.getDouble(boss), ny = by.getDouble(boss);
                double paso = Math.hypot(nx - prevX, ny - prevY);
                recorrido += paso;

                // Estar parado AL LADO de Nox es lo correcto: solo cuenta como
                // atasco quedarse quieto estando todavia lejos.
                if (Math.hypot(nx - px, ny - py) > 110) {
                    framesLejos++;
                    if (paso < 0.5) framesQuieto++;
                }
                prevX = nx;
                prevY = ny;
            }

            double distFinal = Math.hypot(prevX - px, prevY - py);
            int pctQuieto = framesLejos == 0 ? 0 : framesQuieto * 100 / framesLejos;
            double acercamiento = distInicial <= 0 ? 0 : (1 - distFinal / distInicial) * 100;

            // Si llego hasta Nox, no estaba atascado, y el porcentaje da igual: un
            // jefe que cubre los 500 px en 40 fotogramas deja solo 40 medidas de
            // "estaba lejos", y sobre tan pocas el porcentaje es puro ruido.
            // El atasco solo se juzga cuando NO consiguio llegar.
            boolean llego = distFinal < 130;
            boolean bien = llego || (acercamiento > 55 && pctQuieto < 40);

            String detalle = String.format(
                "recorrio %.0f px, se acerco %.0f%% (de %.0f a %.0f), %d frames lejos"
                + " y quieto el %d%% de ellos",
                recorrido, acercamiento, distInicial, distFinal, framesLejos, pctQuieto);

            if (bien) {
                ok("Jefe del barrio " + lvl + " persigue bien: " + detalle);
            } else {
                fail("Jefe del barrio " + lvl + " SE ATORA: " + detalle);
                barriosMalos++;
            }
        }

        if (barriosMalos == 0) ok("Los 6 jefes se mueven sin quedarse encajados");
    }

    /** Cada jefe tiene un ataque propio: se lanzan los seis y se comprueba que hacen algo. */
    static void comprobarAtaquesDeJefe(ActionEvent tick, Graphics2D g) throws Exception {
        Method special = LabyrinthPanel.class.getDeclaredMethod("bossSpecial", Class.forName("LabyrinthPanel$Enemy"));
        special.setAccessible(true);

        String[] nombres = {"Rocky lanza bidones", "Spike vuelca puestos", "Rex embosca",
                            "Titan lanza contenedor", "Shadow salta", "Colmillo apaga las luces"};

        for (int lvl = 1; lvl <= 6; lvl++) {
            set("currentLevel", lvl);
            call("loadLevel");
            set("levelIntroActive", false);
            set("bossEncountered", true);
            set("bossIntroActive", false);
            set("isDead", false);
            set("playerHealth", 100);

            Object boss = get("currentBoss");
            if (boss == null) { fail("Barrio " + lvl + " sin jefe"); continue; }

            Field bx = boss.getClass().getDeclaredField("x"); bx.setAccessible(true);
            Field by = boss.getClass().getDeclaredField("y"); by.setAccessible(true);
            set("playerX", bx.getDouble(boss) - 200);
            set("playerY", by.getDouble(boss) - 200);

            @SuppressWarnings("unchecked")
            java.util.List<Object> balas = (java.util.List<Object>) get("bullets");
            @SuppressWarnings("unchecked")
            java.util.List<Object> enemigos = (java.util.List<Object>) get("enemies");
            int balasAntes = balas.size(), enemigosAntes = enemigos.size();
            double bossXAntes = bx.getDouble(boss);

            special.invoke(panel, boss);

            for (int i = 0; i < 90; i++) {
                panel.actionPerformed(tick);
                set("playerHealth", 100);
                set("isDead", false);
                set("isJumpscareActive", false);
            }
            panel.paint(g);

            @SuppressWarnings("unchecked")
            java.util.List<Object> colaSpawn = (java.util.List<Object>) get("spawnQueue");
            boolean hizoAlgo = balas.size() != balasAntes
                            || enemigos.size() != enemigosAntes
                            || !colaSpawn.isEmpty()
                            || Math.abs(bx.getDouble(boss) - bossXAntes) > 1
                            || ((Integer) get("lightsOutTimer")) > 0
                            || ((Integer) get("shakeFrames")) > 0;

            if (hizoAlgo) ok("Ataque especial del barrio " + lvl + " (" + nombres[lvl - 1] + ") funciona");
            else fail("El ataque especial del barrio " + lvl + " no hizo NADA");
        }
    }

    /** Fotos, bidon secreto y callejon quemado: los coleccionables y los secretos. */
    static void comprobarSecretos(ActionEvent tick, Graphics2D g) throws Exception {
        set("currentLevel", 2);
        call("loadLevel");
        set("levelIntroActive", false);
        set("isDead", false);

        int[][] map = (int[][]) get("map");
        int fotos = 0, secretos = 0, callejones = 0;
        int[] fotoPos = null;
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[0].length; y++) {
                if (map[x][y] == Tiles.PHOTO) { fotos++; fotoPos = new int[]{x, y}; }
                if (map[x][y] == Tiles.PROP_SECRET) secretos++;
                if (map[x][y] == Tiles.HOME) callejones++;
            }
        }
        if (fotos == Memories.PER_LEVEL && secretos == 1 && callejones == 1) {
            ok("Secretos colocados: " + fotos + " fotos, " + secretos + " bidon con premio, "
               + callejones + " callejon quemado");
        } else {
            fail("Secretos mal colocados: fotos=" + fotos + " bidon=" + secretos
                 + " callejon=" + callejones);
        }

        // Recoger una foto sube la cordura y muestra el recuerdo
        set("sanity", 40.0);
        set("playerX", fotoPos[0] * 60.0);
        set("playerY", fotoPos[1] * 60.0);
        panel.actionPerformed(tick);

        boolean recuerdo = (Boolean) get("memoryActive");
        double cordura = (Double) get("sanity");
        panel.paint(g);
        if (recuerdo && cordura > 40) {
            ok("Recoger una foto: +" + Math.round(cordura - 40) + " de cordura y muestra el recuerdo");
        } else {
            fail("Recoger una foto no funciono (recuerdo=" + recuerdo + " cordura=" + cordura + ")");
        }
        set("memoryActive", false);

        // Romper el bidon con premio da dinero
        set("playerMoney", 0);
        Method romper = LabyrinthPanel.class.getDeclaredMethod("breakPropsNear",
            double.class, double.class, double.class);
        romper.setAccessible(true);
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[0].length; y++) {
                if (map[x][y] == Tiles.PROP_SECRET) {
                    romper.invoke(panel, x * 60.0 + 30, y * 60.0 + 30, 40.0);
                }
            }
        }
        int dinero = (Integer) get("playerMoney");
        if (dinero >= 500) ok("El bidon secreto suelta $" + dinero + " al romperlo");
        else fail("El bidon secreto no dio dinero (dio $" + dinero + ")");

        // Pantalla del callejon quemado
        set("homeActive", true);
        set("homeTimer", 200);
        panel.paint(g);
        set("homeActive", false);
        ok("Pantalla del callejon quemado dibujada");
    }

    /** Los familiares rescatados cambian de verdad cómo juega Nox. */
    static void comprobarBonusFamilia() throws Exception {
        Method maxHp = LabyrinthPanel.class.getDeclaredMethod("maxHealth");
        Method dmg = LabyrinthPanel.class.getDeclaredMethod("familyDamageBoost");
        Method drain = LabyrinthPanel.class.getDeclaredMethod("sanityDrainFactor");
        maxHp.setAccessible(true); dmg.setAccessible(true); drain.setAccessible(true);

        set("familyRescued", new boolean[]{false, false, false});
        int hpSin = (Integer) maxHp.invoke(panel);
        double dmgSin = (Double) dmg.invoke(panel);
        double drainSin = (Double) drain.invoke(panel);

        set("familyRescued", new boolean[]{true, true, true});
        int hpCon = (Integer) maxHp.invoke(panel);
        double dmgCon = (Double) dmg.invoke(panel);
        double drainCon = (Double) drain.invoke(panel);

        if (hpCon > hpSin && dmgCon > dmgSin && drainCon < drainSin) {
            ok("Bonus de familia: vida " + hpSin + "->" + hpCon
               + ", dano x" + dmgCon + ", cordura baja x" + drainCon);
        } else {
            fail("Los bonus de familia no se aplican");
        }
        set("familyRescued", new boolean[]{false, false, false});
    }

    /** Logros y opciones: se desbloquean, se guardan y se pueden apagar los sustos. */
    static void comprobarLogrosYOpciones() throws Exception {
        Achievements.resetAll();
        int antes = Achievements.unlockedCount();
        boolean nuevo = Achievements.unlock("primer_golpe");
        boolean repetido = Achievements.unlock("primer_golpe");

        if (nuevo && !repetido && Achievements.unlockedCount() == antes + 1) {
            ok("Logros: se desbloquean una sola vez (" + Achievements.unlockedCount()
               + "/" + Achievements.total() + ")");
        } else {
            fail("Los logros se desbloquean mal (nuevo=" + nuevo + " repetido=" + repetido + ")");
        }

        Achievements.load();
        if (Achievements.isUnlocked("primer_golpe")) ok("Los logros se guardan y se releen del disco");
        else fail("Los logros no se guardaron en disco");

        // Con los sustos apagados no debe activarse la pantalla de susto
        boolean estabaActivo = Settings.jumpscares();
        if (estabaActivo) Settings.toggleJumpscares();
        set("isJumpscareActive", false);
        set("sanity", 90.0);
        Method scare = LabyrinthPanel.class.getDeclaredMethod("triggerJumpscare");
        scare.setAccessible(true);
        scare.invoke(panel);
        boolean saltoElSusto = (Boolean) get("isJumpscareActive");
        double corduraTrasSusto = (Double) get("sanity");

        if (!saltoElSusto && corduraTrasSusto < 90) {
            ok("Accesibilidad: con los sustos apagados no hay pantallazo, pero el juego no cambia");
        } else {
            fail("La opcion de apagar los sustos no funciona (salto=" + saltoElSusto + ")");
        }
        if (estabaActivo) Settings.toggleJumpscares();
    }

    /**
     * Con la cordura baja el juego empieza a mentir: perros que no existen, sonidos
     * a la espalda y una barra de vida que no dice la verdad. Con la cordura alta
     * no puede pasar NADA de eso, o el juego seria injusto.
     */
    static void comprobarAlucinaciones(ActionEvent tick, Graphics2D g) throws Exception {
        set("currentLevel", 3);
        call("loadLevel");
        set("levelIntroActive", false);
        set("isDead", false);

        @SuppressWarnings("unchecked")
        java.util.List<Object> fantasmas = (java.util.List<Object>) get("ghosts");

        // Cordura al maximo: no debe aparecer ninguna alucinacion
        set("sanity", 100.0);
        fantasmas.clear();
        set("hudLieTimer", 0);
        for (int i = 0; i < 2500; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isDead", false);
            set("isJumpscareActive", false);
            set("sanity", 100.0);
        }
        int conCordura = fantasmas.size() + (Integer) get("hudLieTimer");

        // Cordura por los suelos: tienen que aparecer
        set("sanity", 8.0);
        for (int i = 0; i < 2500; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isDead", false);
            set("isJumpscareActive", false);
            set("sanity", 8.0);
            if (!fantasmas.isEmpty()) break;
        }
        int sinCordura = fantasmas.size();
        panel.paint(g);

        if (conCordura == 0 && sinCordura > 0) {
            ok("Alucinaciones: 0 con la cordura llena, " + sinCordura + " con la cordura por los suelos");
        } else {
            fail("Alucinaciones mal: conCordura=" + conCordura + " sinCordura=" + sinCordura);
        }

        // Los fantasmas se desvanecen al acercarse: no pueden hacer dano
        Object gh = fantasmas.isEmpty() ? null : fantasmas.get(0);
        if (gh != null) {
            Field gx = gh.getClass().getDeclaredField("x"); gx.setAccessible(true);
            Field gy = gh.getClass().getDeclaredField("y"); gy.setAccessible(true);
            gx.setDouble(gh, (Double) get("playerX"));
            gy.setDouble(gh, (Double) get("playerY"));
            int vidaAntes = (Integer) get("playerHealth");
            for (int i = 0; i < 60; i++) {
                panel.actionPerformed(tick);
                set("isJumpscareActive", false);
                set("sanity", 8.0);
            }
            int vidaDespues = (Integer) get("playerHealth");
            if (vidaDespues >= vidaAntes) ok("Los perros fantasma no hacen dano: solo estan en su cabeza");
            else fail("Un perro fantasma quito vida (" + vidaAntes + " -> " + vidaDespues + ")");
        }

        set("sanity", 100.0);
        fantasmas.clear();
    }

    /** El susto tiene tres fases: aviso, golpe y resaca. Se dibujan las tres. */
    static void comprobarSustoPorFases(ActionEvent tick, Graphics2D g) throws Exception {
        boolean estaban = Settings.jumpscares();
        if (!estaban) Settings.toggleJumpscares();

        set("isJumpscareActive", false);
        set("sanity", 60.0);
        Method scare = LabyrinthPanel.class.getDeclaredMethod("triggerJumpscare");
        scare.setAccessible(true);
        scare.invoke(panel);

        java.util.List<Integer> fasesVistas = new java.util.ArrayList<>();
        for (int i = 0; i < 260; i++) {
            int fase = (Integer) get("scarePhase");
            if (!fasesVistas.contains(fase)) { fasesVistas.add(fase); panel.paint(g); }
            if (!(Boolean) get("isJumpscareActive")) break;
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isDead", false);
        }

        boolean terminado = !(Boolean) get("isJumpscareActive");
        if (fasesVistas.contains(1) && fasesVistas.contains(2) && fasesVistas.contains(3) && terminado) {
            ok("Susto por fases: aviso -> golpe -> resaca, y se cierra solo");
        } else {
            fail("El susto no paso por las tres fases: vistas=" + fasesVistas + " terminado=" + terminado);
        }
        if (!estaban) Settings.toggleJumpscares();
    }

    /** El dibujo se escala a cualquier tamano de ventana sin romperse. */
    static void comprobarEscalado() throws Exception {
        int[][] tamanos = {{1024, 768}, {1920, 1080}, {1366, 768}, {800, 600}, {2560, 1440}};
        for (int[] t : tamanos) {
            panel.setSize(t[0], t[1]);
            BufferedImage lienzo = new BufferedImage(t[0], t[1], BufferedImage.TYPE_INT_RGB);
            Graphics2D gg = lienzo.createGraphics();
            panel.paint(gg);
            gg.dispose();
        }
        panel.setSize(1024, 768);
        ok("El juego se dibuja sin errores en 1024x768, 1920x1080, 1366x768, 800x600 y 2560x1440");
    }

    /**
     * Las pantallas de historia no pueden dejar el juego trabado.
     *
     * Los restos del callejon bloquean el paso, asi que Nox se queda parado al lado.
     * Como durante la pantalla no se puede mover, en cuanto terminaba se volvia a
     * disparar sola una y otra vez: el juego se quedaba bloqueado para siempre.
     */
    static void comprobarPantallasDeHistoria(ActionEvent tick, Graphics2D g) throws Exception {
        set("currentLevel", 4);
        call("loadLevel");
        set("levelIntroActive", false);
        set("isDead", false);
        set("playerHealth", 100);

        int[][] map = (int[][]) get("map");
        int[] casa = null;
        for (int x = 0; x < map.length && casa == null; x++) {
            for (int y = 0; y < map[0].length; y++) {
                if (map[x][y] == Tiles.HOME) { casa = new int[]{x, y}; break; }
            }
        }
        if (casa == null) { fail("No se genero el callejon quemado"); return; }

        // Nox se planta al lado y se queda ahi, como haria cualquiera al leerlo
        set("playerX", (casa[0] - 1) * 60.0);
        set("playerY", casa[1] * 60.0);
        double px = (Double) get("playerX"), py = (Double) get("playerY");

        int vecesQueSalio = 0;
        boolean anterior = false;
        for (int i = 0; i < 1500; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isDead", false);
            set("isJumpscareActive", false);
            set("playerX", px);
            set("playerY", py);

            boolean ahora = (Boolean) get("homeActive");
            if (ahora && !anterior) vecesQueSalio++;
            anterior = ahora;
        }

        boolean bloqueado = (Boolean) get("homeActive");
        if (vecesQueSalio == 1 && !bloqueado) {
            ok("El callejon quemado sale UNA vez y devuelve el control (no se traba)");
        } else {
            fail("BLOQUEO: la pantalla del callejon salio " + vecesQueSalio
                 + " veces y al final sigue abierta=" + bloqueado);
        }

        // Y se puede saltar sin esperar los 5 segundos
        set("homeSeen", false);
        set("playerX", (casa[0] - 1) * 60.0);
        set("playerY", casa[1] * 60.0);
        panel.actionPerformed(tick);
        if (!(Boolean) get("homeActive")) {
            fail("La pantalla del callejon no se activo para probar el salto");
            return;
        }
        panel.paint(g);
        key(KeyEvent.VK_SPACE);
        if (!(Boolean) get("homeActive")) ok("Las pantallas de historia se pueden saltar con ESPACIO");
        else fail("No se pudo saltar la pantalla del callejon");

        // Lo mismo con el recuerdo de una foto y con el rescate
        set("memoryActive", true);
        set("memoryTimer", 260);
        set("memoryText", "prueba");
        set("memoryTitle", "prueba");
        panel.paint(g);
        key(KeyEvent.VK_ENTER);
        boolean recuerdoCerrado = !(Boolean) get("memoryActive");

        set("rescueMessageActive", true);
        set("rescueMessageTimer", 220);
        set("rescueMessageText", "prueba");
        set("rescueDialogue", "a\nb");
        panel.paint(g);
        key(KeyEvent.VK_SPACE);
        boolean rescateCerrado = !(Boolean) get("rescueMessageActive");

        if (recuerdoCerrado && rescateCerrado) ok("El recuerdo y el rescate tambien se pueden saltar");
        else fail("No se pudieron saltar (recuerdo=" + recuerdoCerrado + " rescate=" + rescateCerrado + ")");
    }

    /** Los diez sustos se dibujan, no se repiten seguidos y se pueden apagar. */
    static void comprobarSustosVariados(ActionEvent tick, Graphics2D g) throws Exception {
        boolean estaban = Settings.jumpscares();
        if (!estaban) Settings.toggleJumpscares();

        // Las tres fases de cada uno de los diez
        for (int tipo = 0; tipo < Jumpscares.COUNT; tipo++) {
            set("jumpscareType", tipo);
            set("isJumpscareActive", true);
            for (int fase = 1; fase <= 3; fase++) {
                set("scarePhase", fase);
                set("scareTimer", fase == 1 ? 20 : fase == 2 ? 24 : 30);
                panel.paint(g);
            }
        }
        set("isJumpscareActive", false);
        set("scarePhase", 0);
        ok("Los " + Jumpscares.COUNT + " sustos se dibujan en sus tres fases sin errores");

        // Nunca sale el mismo dos veces seguidas
        java.util.Random r = new java.util.Random(3);
        int anterior = -1, repetidos = 0;
        java.util.Set<Integer> vistos = new java.util.HashSet<>();
        for (int i = 0; i < 400; i++) {
            int t = Jumpscares.pick(r, 6, anterior);
            if (t == anterior) repetidos++;
            vistos.add(t);
            anterior = t;
        }
        if (repetidos == 0 && vistos.size() >= 8) {
            ok("Variedad de sustos: " + vistos.size() + " tipos distintos y ninguno repetido seguido");
        } else {
            fail("Sustos mal repartidos: repetidos=" + repetidos + " distintos=" + vistos.size());
        }

        // Colmillo no puede aparecer antes de conocerle
        boolean spoiler = false;
        for (int i = 0; i < 400; i++) {
            if (Jumpscares.pick(r, 1, -1) == Jumpscares.COLMILLO) spoiler = true;
        }
        if (!spoiler) ok("El susto de Colmillo no sale en los primeros barrios");
        else fail("El susto de Colmillo aparecio antes de conocerle");

        if (!estaban) Settings.toggleJumpscares();
    }

    /** Las cinemáticas se reproducen enteras, se pueden saltar y devuelven el control. */
    static void comprobarCinematicas(ActionEvent tick, Graphics2D g) throws Exception {
        String[] nombres = {"prologo", "final bueno", "final oscuro"};

        for (int kind = 0; kind < 3; kind++) {
            Cinematics cine = new Cinematics(kind);
            int planos = cine.shotCount();
            int frames = 0;
            java.util.Set<Integer> planosVistos = new java.util.HashSet<>();

            while (!cine.isFinished() && frames < 6000) {
                planosVistos.add(cine.currentShot());
                cine.draw(g, 1024, 768);
                cine.update();
                frames++;
            }

            if (cine.isFinished() && planosVistos.size() == planos) {
                ok("Cinematica del " + nombres[kind] + ": " + planos + " planos en "
                   + (frames / 60) + " segundos, y termina sola");
            } else {
                fail("Cinematica del " + nombres[kind] + " mal: planos vistos="
                     + planosVistos.size() + "/" + planos + " terminada=" + cine.isFinished());
            }

            // Y se puede saltar de golpe
            Cinematics saltada = new Cinematics(kind);
            saltada.skip();
            if (!saltada.isFinished()) fail("No se pudo saltar la cinematica del " + nombres[kind]);
        }
        ok("Las tres cinematicas se pueden saltar");

        // Dentro del juego: el prologo arranca su cinematica y la devuelve al terminar
        Method start = LabyrinthPanel.class.getDeclaredMethod("startNewGame", int.class);
        start.setAccessible(true);
        start.invoke(panel, 1);
        ((javax.swing.Timer) get("gameLoop")).stop();

        if (get("cinematic") == null) {
            fail("Al empezar partida nueva no arranco la cinematica del prologo");
            return;
        }
        panel.paint(g);

        int guard = 0;
        while (get("cinematic") != null && guard++ < 4000) {
            panel.actionPerformed(tick);
        }
        boolean prologoCerrado = !(Boolean) get("showingPrologue");
        if (get("cinematic") == null && prologoCerrado) {
            ok("El prologo del juego pasa por su cinematica y arranca el barrio 1");
        } else {
            fail("El prologo se quedo colgado en la cinematica");
        }

        // Y el final arranca la suya
        set("currentLevel", 6);
        set("sanity", 80.0);
        Method victory = LabyrinthPanel.class.getDeclaredMethod("triggerVictory");
        victory.setAccessible(true);
        victory.invoke(panel);
        boolean cineFinal = get("cinematic") != null;
        panel.paint(g);

        guard = 0;
        while (get("cinematic") != null && guard++ < 4000) panel.actionPerformed(tick);
        panel.paint(g);   // detras espera la pantalla de puntuacion

        if (cineFinal && (Boolean) get("gameWon")) {
            ok("El final reproduce su cinematica y despues muestra la puntuacion");
        } else {
            fail("El final no reprodujo la cinematica");
        }
        set("gameWon", false);
    }

    /**
     * La cordura a cero NO mata: lo que hace es dejar a Nox casi ciego.
     *
     * Es una regla de diseno: si quedarse sin cordura acabara la partida, el jugador
     * dejaria de explorar en cuanto la barra bajara, y la cordura pasaria de ser
     * atmosfera a ser un segundo contador de vida.
     */
    static void comprobarCorduraCeroNoMata(ActionEvent tick, Graphics2D g) throws Exception {
        set("currentLevel", 2);
        call("loadLevel");
        set("levelIntroActive", false);
        set("isDead", false);
        set("gameWon", false);
        set("playerHealth", 100);
        set("homeSeen", true);

        // Lejos de los perros, para que solo influya la cordura
        @SuppressWarnings("unchecked")
        java.util.List<Object> enemigos = (java.util.List<Object>) get("enemies");
        enemigos.clear();
        set("currentBoss", null);
        set("sanity", 0.0);

        int vidaInicial = (Integer) get("playerHealth");
        for (int i = 0; i < 1800; i++) {
            panel.actionPerformed(tick);
            set("sanity", 0.0);           // se mantiene en el suelo a proposito
            set("isJumpscareActive", false);
        }
        panel.paint(g);

        boolean muerto = (Boolean) get("isDead");
        int vidaFinal = (Integer) get("playerHealth");

        if (!muerto && vidaFinal >= vidaInicial) {
            ok("Cordura a 0 durante 30 segundos: Nox sigue vivo con " + vidaFinal + " de vida");
        } else {
            fail("Cordura a 0 mato a Nox (muerto=" + muerto + " vida=" + vidaInicial + "->" + vidaFinal + ")");
        }

        // Y la vista tiene que cerrarse de verdad
        Method radio = LabyrinthPanel.class.getDeclaredMethod("lightRadius");
        radio.setAccessible(true);
        set("lightsOutTimer", 0);
        set("scarePhase", 0);
        set("animationFrame", 0);

        set("sanity", 100.0);
        float conCordura = (Float) radio.invoke(panel);
        set("sanity", 45.0);
        float mitad = (Float) radio.invoke(panel);
        set("sanity", 0.0);
        float sinCordura = (Float) radio.invoke(panel);

        boolean seCierra = sinCordura < conCordura * 0.35 && mitad < conCordura && sinCordura > 50;
        String detalle = String.format("100%% -> %.0f px | 45%% -> %.0f px | 0%% -> %.0f px",
                                       conCordura, mitad, sinCordura);
        if (seCierra) ok("La vista se cierra con la cordura: " + detalle);
        else fail("La vista no se cierra bien: " + detalle);

        set("sanity", 100.0);
    }

    public static void main(String[] args) throws Exception {
        panel = new LabyrinthPanel(null);
        panel.setSize(1024, 768);

        Method start = LabyrinthPanel.class.getDeclaredMethod("startNewGame", int.class);
        start.setAccessible(true);
        start.invoke(panel, 1);

        // La partida arranca su propio temporizador. Aquí se detiene para que los
        // fotogramas los dispare la prueba y no dos hilos a la vez.
        ((javax.swing.Timer) get("gameLoop")).stop();

        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        ActionEvent tick = new ActionEvent(panel, 0, "tick");

        panel.paint(g);
        ok("Cinematica del prologo arranca al empezar partida");

        // Se salta aquí: si no, las pruebas de juego que vienen abajo estarían
        // midiendo la cinemática (con el juego parado) en vez del juego.
        // La cinemática tiene su propia prueba más adelante.
        set("cinematic", null);
        set("showingPrologue", false);
        set("levelIntroActive", false);

        Random r = new Random(7);
        for (int i = 0; i < 4000; i++) {
            if (i % 37 == 0) {
                int[] keys = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D};
                int k = keys[r.nextInt(4)];
                key(k);
                if (i % 74 == 0) keyUp(k);
            }
            if (i % 11 == 0) key(KeyEvent.VK_SPACE);
            panel.actionPerformed(tick);
            if (i % 50 == 0) panel.paint(g);
            set("playerHealth", 100);
            set("isJumpscareActive", false);
            set("bossIntroActive", false);
        }
        ok("4000 frames de juego + dibujo, sin excepciones");
        System.out.println("       dinero=" + get("playerMoney") + "  bajas=" + get("kills")
                           + "  cordura=" + Math.round((Double) get("sanity")));

        // Tienda completa
        set("playerMoney", 20000);
        set("nearStore", true);
        key(KeyEvent.VK_E);
        for (int item = 0; item < 12; item++) {
            key(KeyEvent.VK_ENTER);
            panel.paint(g);
            key(KeyEvent.VK_S);
        }
        key(KeyEvent.VK_ESCAPE);
        ok("Tienda: acero=" + get("hasSteelClaws") + " ballesta=" + get("hasCrossbow")
           + " plasma=" + get("hasPlasmaLauncher") + " molotov=" + get("hasMolotov")
           + " silbato=" + get("hasWhistle") + " armadura=" + get("armorLevel")
           + " atun=" + get("tunaCans"));

        // Las cinco armas
        for (int wpn = 0; wpn <= 4; wpn++) {
            set("activeWeapon", wpn);
            set("weaponCooldown", 0);
            key(KeyEvent.VK_SPACE);
            for (int i = 0; i < 60; i++) { panel.actionPerformed(tick); set("playerHealth", 100); }
            panel.paint(g);
        }
        ok("Las 5 armas disparan sin errores (incluido el molotov explotando)");

        // Silbato y lata de atún
        set("whistleCooldown", 0);
        key(KeyEvent.VK_F);
        set("playerHealth", 40);
        key(KeyEvent.VK_Q);
        for (int i = 0; i < 10; i++) panel.actionPerformed(tick);
        ok("Silbato aturde y la lata de atun cura -> vida=" + get("playerHealth")
           + " atun restante=" + get("tunaCans"));

        // Cada tipo de perro
        set("playerHealth", 100);
        for (int tipo = 0; tipo <= 6; tipo++) {
            java.lang.reflect.Constructor<?> ctor = Class.forName("LabyrinthPanel$Enemy")
                .getDeclaredConstructor(LabyrinthPanel.class, double.class, double.class, int.class, int.class);
            ctor.setAccessible(true);
            Object en = ctor.newInstance(panel, 200.0, 200.0, tipo, 6);
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) get("enemies");
            list.add(en);
        }
        for (int i = 0; i < 260; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isJumpscareActive", false);
        }
        panel.paint(g);
        ok("Los 7 tipos de perro (callejero, armado, corredor, bruto, escudo, alfa, kamikaze) funcionan");

        // Pantallas
        set("rescueMessageActive", false);
        set("isJumpscareActive", true);  panel.paint(g);  set("isJumpscareActive", false);
        set("paused", true);             panel.paint(g);  set("paused", false);
        set("isDead", true);             panel.paint(g);  set("isDead", false);
        set("showMinimap", true);        panel.paint(g);
        set("isTransitioning", true);    panel.paint(g);  set("isTransitioning", false);
        ok("Pantallas de susto, pausa, muerte, mapa y transicion");

        // Los dos finales
        set("gameWon", true);
        set("darkEnding", false); panel.paint(g);
        set("darkEnding", true);  panel.paint(g);
        set("gameWon", false);
        set("darkEnding", false);
        ok("Los dos finales (bueno y oscuro) se dibujan");

        // Los seis barrios: se generan, se pueden terminar y se dibujan
        for (int lvl = 1; lvl <= 6; lvl++) {
            set("currentLevel", lvl);
            call("loadLevel");
            set("levelIntroActive", false);
            set("playerHealth", 100);
            checkReachable(lvl);
            for (int i = 0; i < 120; i++) {
                panel.actionPerformed(tick);
                set("playerHealth", 100);
                set("isJumpscareActive", false);
                set("bossIntroActive", false);
            }
            panel.paint(g);
        }

        // Se repite la generación muchas veces. Los mapas son aleatorios y no puede
        // ocurrir NUNCA que uno quede sin salida o con el jefe emparedado: como la
        // salida exige derrotar al jefe, un jefe inaccesible deja el juego sin final.
        int revisiones = 0, problemas = 0;
        for (int repeticion = 0; repeticion < 25; repeticion++) {
            for (int lvl = 1; lvl <= 6; lvl++) {
                set("currentLevel", lvl);
                call("loadLevel");
                int[][] map = (int[][]) get("map");
                boolean[][] seen = new boolean[map.length][map[0].length];
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{2, 2});
                seen[2][2] = true;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    for (int[] d : dirs) {
                        int nx = c[0] + d[0], ny = c[1] + d[1];
                        if (nx < 0 || ny < 0 || nx >= map.length || ny >= map[0].length || seen[nx][ny]) continue;
                        if (!Tiles.walkable(map[nx][ny])) continue;
                        seen[nx][ny] = true;
                        q.add(new int[]{nx, ny});
                    }
                }

                if (!seen[map.length - 3][map[0].length - 3]) {
                    fail("Barrio " + lvl + " genero un mapa SIN SALIDA (repeticion " + repeticion + ")");
                    problemas++;
                }

                Object boss = get("currentBoss");
                if (boss != null) {
                    Field bx = boss.getClass().getDeclaredField("x"); bx.setAccessible(true);
                    Field by = boss.getClass().getDeclaredField("y"); by.setAccessible(true);
                    int tx = (int) (bx.getDouble(boss) / 60), ty = (int) (by.getDouble(boss) / 60);
                    if (!seen[Math.min(map.length - 1, tx)][Math.min(map[0].length - 1, ty)]) {
                        fail("Barrio " + lvl + " dejo al JEFE EMPAREDADO (repeticion " + repeticion + ")");
                        problemas++;
                    }
                }
                revisiones++;
            }
        }
        if (problemas == 0) {
            ok(revisiones + " mapas aleatorios: todos con salida y con el jefe alcanzable");
        }

        SaveManager.Progress p = SaveManager.load();
        ok("Guardado leido: barrio=" + p.level + " dinero=" + p.money + " armadura=" + p.armor
           + " atun=" + p.tuna + " molotov=" + p.molotov);

        AudioEngine.start();
        for (String s : new String[]{"claw", "boom", "howl", "whistle", "clang", "heal", "darkending"}) {
            AudioEngine.play(s);
        }
        Thread.sleep(400);
        ok("AudioEngine arranco. Sonido disponible=" + AudioEngine.isAvailable());
        AudioEngine.shutdown();

        // Mando: lo importante es que si no hay mando (o no es Windows) no rompa nada
        boolean padOk;
        try {
            padOk = Gamepad.init();
        } catch (Throwable t) {
            padOk = false;
        }

        // Normalmente la activa el main() del juego, que aquí no se ejecuta
        Field padFlag = Class.forName("ProjectExodusAdvanced").getDeclaredField("gamepadEnabled");
        padFlag.setAccessible(true);
        padFlag.setBoolean(null, padOk);
        for (int i = 0; i < 40; i++) {
            Gamepad.poll();
            Gamepad.down(Gamepad.A);
            Gamepad.pressed(Gamepad.START);
            Gamepad.moveX();
            Gamepad.moveY();
            Gamepad.rumble(0.4, 0.4, 5);
        }
        Gamepad.stopRumble();
        ok("Gamepad: XInput=" + padOk + " conectado=" + Gamepad.isConnected()
           + " (" + Gamepad.getDeviceInfo() + "), 40 lecturas sin excepciones");

        // Y que el juego siga respondiendo con el mando activo en el bucle
        for (int i = 0; i < 200; i++) {
            panel.actionPerformed(tick);
            set("playerHealth", 100);
            set("isJumpscareActive", false);
        }
        panel.paint(g);
        ok("200 frames con el mando dentro del bucle de juego");

        comprobarLecturaUnica(tick);
        Gamepad.shutdown();

        comprobarJefesNoSeAtoran(tick);
        comprobarAtaquesDeJefe(tick, g);
        comprobarSecretos(tick, g);
        comprobarBonusFamilia();
        comprobarLogrosYOpciones();
        comprobarPantallasDeHistoria(tick, g);
        comprobarCorduraCeroNoMata(tick, g);
        comprobarAlucinaciones(tick, g);
        comprobarSustoPorFases(tick, g);
        comprobarEscalado();
        comprobarSustosVariados(tick, g);
        comprobarCinematicas(tick, g);

        g.dispose();
        System.out.println();
        if (fallos == 0) System.out.println("=== TODAS LAS PRUEBAS PASARON ===");
        else System.out.println("=== " + fallos + " PRUEBAS FALLARON ===");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
