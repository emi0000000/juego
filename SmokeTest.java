import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Prueba automática sin abrir ventana: arranca una partida, simula miles de frames
 * (moverse, atacar, comprar, pelear contra el jefe) y dibuja todas las pantallas
 * para detectar cualquier excepción antes de jugar de verdad.
 *
 * Ejecutar:  java -cp ".;flatlaf-3.4.1.jar" SmokeTest
 */
public class SmokeTest {

    static LabyrinthPanel panel;

    static void set(String name, Object value) throws Exception {
        Field f = LabyrinthPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(panel, value);
    }

    static Object get(String name) throws Exception {
        Field f = LabyrinthPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(panel);
    }

    static void call(String name) throws Exception {
        Method m = LabyrinthPanel.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(panel);
    }

    static void key(int code) {
        panel.keyPressed(new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, code, 'x'));
    }

    static void keyUp(int code) {
        panel.keyReleased(new KeyEvent(panel, KeyEvent.KEY_RELEASED, 0, 0, code, 'x'));
    }

    public static void main(String[] args) throws Exception {
        panel = new LabyrinthPanel(null);
        panel.setSize(1024, 768);

        Method start = LabyrinthPanel.class.getDeclaredMethod("startNewGame", int.class);
        start.setAccessible(true);
        start.invoke(panel, 1);

        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        ActionEvent tick = new ActionEvent(panel, 0, "tick");

        panel.paint(g);
        System.out.println("[OK] Prologo dibujado");

        key(KeyEvent.VK_ENTER);
        set("levelIntroActive", false);

        java.util.Random r = new java.util.Random(7);
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
            set("playerHealth", 100);          // salud infinita para poder seguir probando
            set("isJumpscareActive", false);
            set("bossIntroActive", false);
        }
        System.out.println("[OK] 4000 frames de juego + dibujo, sin excepciones");
        System.out.println("     dinero=" + get("playerMoney") + "  bajas=" + get("kills")
                           + "  cordura=" + Math.round((Double) get("sanity")));

        set("playerMoney", 5000);
        set("nearStore", true);
        key(KeyEvent.VK_E);
        for (int item = 0; item < 5; item++) {
            key(KeyEvent.VK_ENTER);
            panel.paint(g);
            key(KeyEvent.VK_S);
        }
        key(KeyEvent.VK_ESCAPE);
        System.out.println("[OK] Tienda: acero=" + get("hasSteelClaws")
                           + " ballesta=" + get("hasCrossbow") + " plasma=" + get("hasPlasmaLauncher"));

        for (int wpn = 0; wpn <= 3; wpn++) {
            set("activeWeapon", wpn);
            set("weaponCooldown", 0);
            key(KeyEvent.VK_SPACE);
            for (int i = 0; i < 30; i++) panel.actionPerformed(tick);
            panel.paint(g);
        }
        System.out.println("[OK] Las 4 armas disparan sin errores");

        Object boss = get("currentBoss");
        if (boss != null) {
            Field hp = boss.getClass().getDeclaredField("hp");
            hp.setAccessible(true);
            hp.setInt(boss, 0);
            set("bossEncountered", true);
            for (int i = 0; i < 5; i++) panel.actionPerformed(tick);
            panel.paint(g);
            System.out.println("[OK] Jefe derrotado -> salida abierta=" + get("bossDefeated"));
        }

        set("rescueMessageActive", false);
        set("isJumpscareActive", true);  panel.paint(g);  set("isJumpscareActive", false);
        set("paused", true);             panel.paint(g);  set("paused", false);
        set("isDead", true);             panel.paint(g);  set("isDead", false);
        set("gameWon", true);            panel.paint(g);  set("gameWon", false);
        set("showMinimap", true);        panel.paint(g);
        set("isTransitioning", true);    panel.paint(g);  set("isTransitioning", false);
        System.out.println("[OK] Pantallas de susto, pausa, muerte, victoria, mapa y transicion");

        for (int lvl = 2; lvl <= 6; lvl++) {
            set("currentLevel", lvl);
            call("loadLevel");
            set("levelIntroActive", false);
            set("playerHealth", 100);
            for (int i = 0; i < 150; i++) {
                panel.actionPerformed(tick);
                set("playerHealth", 100);
                set("isJumpscareActive", false);
                set("bossIntroActive", false);
            }
            panel.paint(g);
        }
        System.out.println("[OK] Los 6 barrios se generan y se dibujan");

        SaveManager.Progress p = SaveManager.load();
        System.out.println("[OK] Guardado leido: barrio=" + p.level + " dinero=" + p.money
                           + " dificultad=" + p.difficulty);

        AudioEngine.start();
        AudioEngine.play("claw");
        AudioEngine.play("scare");
        Thread.sleep(400);
        System.out.println("[OK] AudioEngine arranco. Sonido disponible=" + AudioEngine.isAvailable());
        AudioEngine.shutdown();

        g.dispose();
        System.out.println();
        System.out.println("=== TODAS LAS PRUEBAS PASARON ===");
        System.exit(0);
    }
}
