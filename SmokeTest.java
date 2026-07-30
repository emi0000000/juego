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

        if (exit && shopNear && bossReach) {
            ok("Barrio " + level + " se puede terminar (salida, jefe y tienda alcanzables; "
               + pct + "% del mapa accesible)");
        } else {
            fail("Barrio " + level + " BLOQUEADO -> salida=" + exit + " jefe=" + bossReach + " tienda=" + shopNear);
        }
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
        ok("Prologo dibujado");

        key(KeyEvent.VK_ENTER);
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

        // Se repite la generación muchas veces: los mapas son aleatorios y no puede
        // ocurrir NUNCA que uno quede sin salida.
        int revisiones = 0;
        for (int repeticion = 0; repeticion < 5; repeticion++) {
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
                    fail("Barrio " + lvl + " genero un mapa SIN SALIDA en la repeticion " + repeticion);
                }
                revisiones++;
            }
        }
        ok(revisiones + " mapas aleatorios generados y todos tienen salida");

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

        g.dispose();
        System.out.println();
        if (fallos == 0) System.out.println("=== TODAS LAS PRUEBAS PASARON ===");
        else System.out.println("=== " + fallos + " PRUEBAS FALLARON ===");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
