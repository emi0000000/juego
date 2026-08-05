import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

/**
 * Logros del juego. Se desbloquean jugando, se guardan entre partidas y aparecen
 * como un aviso deslizante en la esquina cuando se consiguen.
 *
 * Los marcados como secretos no muestran su nombre hasta que se desbloquean: son
 * los que premian encontrar cosas escondidas.
 */
public final class Achievements {

    private static final File FILE = new File("exodo_logros.properties");

    public static final class Ach {
        public final String id, name, description;
        public final boolean secret;
        public boolean unlocked;

        Ach(String id, String name, String description, boolean secret) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.secret = secret;
        }
    }

    private static final LinkedHashMap<String, Ach> ALL = new LinkedHashMap<>();
    private static final ArrayDeque<Ach> TOASTS = new ArrayDeque<>();
    private static int toastTimer = 0;

    private Achievements() { }

    private static void define(String id, String name, String desc) {
        ALL.put(id, new Ach(id, name, desc, false));
    }

    private static void defineSecret(String id, String name, String desc) {
        ALL.put(id, new Ach(id, name, desc, true));
    }

    static {
        // --- Progreso ---
        define("primer_golpe", "PRIMER ZARPAZO", "Derrota a tu primer perro de la pandilla.");
        define("barrio1", "EL BASURERO ES MÍO", "Derrota a Rocky el Bulldog.");
        define("barrio2", "MERCADO LIBRE", "Derrota a Spike el Pit Bull.");
        define("barrio3", "ÚLTIMA PARADA", "Derrota a Rex el Pastor Alemán.");
        define("barrio4", "AGUAS TRANQUILAS", "Derrota a Titán el Gran Danés.");
        define("barrio5", "SOBRE LA CIUDAD", "Derrota a Shadow el Dóberman.");
        define("barrio6", "CAE EL COLMILLO", "Derrota a Don Colmillo.");

        // --- Familia e historia ---
        define("familia", "FAMILIA COMPLETA", "Rescata a Will, Nina y Mitsy.");
        define("album", "ÁLBUM FAMILIAR", "Encuentra las 18 fotos escondidas por la ciudad.");
        define("final_bueno", "EL GATO QUE VOLVIÓ", "Termina el juego con la cordura por encima del 45%.");
        define("final_oscuro", "EL GATO QUE NO VOLVIÓ", "Termina el juego con la cordura por los suelos.");

        // --- Combate y equipo ---
        define("arsenal", "ARSENAL COMPLETO", "Compra las cinco armas del Mercado Negro.");
        define("blindado", "BLINDADO", "Consigue las Placas de Acero.");
        define("rico", "GATO DE NEGOCIOS", "Acumula 5.000 en la cartera.");
        define("piromano", "PIRÓMANO", "Acaba con 4 perros de un solo molotov.");
        define("silbato", "SILENCIO EN LA CALLE", "Aturde a 8 perros de un solo silbido.");
        define("cazador", "CAZADOR", "Derrota a 200 perros en una partida.");

        // --- Desafíos ---
        define("intacto", "SIN UN RASGUÑO", "Termina un barrio entero sin recibir un solo golpe.");
        define("sereno", "NERVIOS DE ACERO", "Termina un barrio sin que te den un solo susto.");
        define("pesadilla", "VIVIR LA PESADILLA", "Termina el juego en dificultad PESADILLA.");
        define("nueve_vidas", "NUEVE VIDAS", "Cae nueve veces y sigue intentándolo.");

        // --- Secretos ---
        defineSecret("suerte", "GOLPE DE SUERTE", "Rompiste el bidón correcto.");
        defineSecret("hogar", "VOLVER A CASA", "Encontraste lo que quedó del callejón.");
        defineSecret("konami", "GATO DORADO", "Alguien todavía se acuerda de ese código.");
        defineSecret("paciente", "EL GATO PACIENTE", "Llegaste al jefe sin tocar a nadie por el camino.");
    }

    // ------------------------------------------------------------------

    public static void load() {
        if (!FILE.exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(FILE)) {
            p.load(in);
        } catch (Exception e) {
            return;
        }
        for (Ach a : ALL.values()) {
            a.unlocked = Boolean.parseBoolean(p.getProperty(a.id, "false"));
        }
    }

    /** Escribe los logros a disco. Se llama en cada desbloqueo, no al cerrar. */
    private static void save() {
        Properties p = new Properties();
        for (Ach a : ALL.values()) p.setProperty(a.id, String.valueOf(a.unlocked));
        try (FileOutputStream out = new FileOutputStream(FILE)) {
            p.store(out, "Logros de EXODO: LA VENGANZA DE NOX");
        } catch (Exception ignored) {
            // Sin permisos de escritura los logros duran solo esta sesión
        }
    }

    /** @return true si se acaba de desbloquear ahora mismo. */
    public static boolean unlock(String id) {
        Ach a = ALL.get(id);
        if (a == null || a.unlocked) return false;
        a.unlocked = true;
        TOASTS.add(a);
        save();
        AudioEngine.play("achievement");
        return true;
    }

    public static boolean isUnlocked(String id) {
        Ach a = ALL.get(id);
        return a != null && a.unlocked;
    }

    public static List<Ach> all() { return new ArrayList<>(ALL.values()); }

    public static int total() { return ALL.size(); }

    public static int unlockedCount() {
        int n = 0;
        for (Ach a : ALL.values()) if (a.unlocked) n++;
        return n;
    }

    /** Solo para las pruebas: vuelve a dejarlo todo bloqueado. */
    static void resetAll() {
        for (Ach a : ALL.values()) a.unlocked = false;
        TOASTS.clear();
        toastTimer = 0;
    }

    // ------------------------------------------------------------------
    // Aviso deslizante
    // ------------------------------------------------------------------

    public static void updateToasts() {
        if (TOASTS.isEmpty()) return;
        if (toastTimer <= 0) toastTimer = 240;
        if (--toastTimer <= 0) {
            TOASTS.poll();
            toastTimer = TOASTS.isEmpty() ? 0 : 240;
        }
    }

    /**
     * Dibuja el aviso del logro recién conseguido, abajo a la derecha.
     *
     * Va abajo porque arriba están el HUD y la barra de vida del jefe. Entra y sale
     * deslizándose, con el desplazamiento calculado desde su propio temporizador.
     */
    public static void drawToasts(Graphics2D g, int screenW, int screenH) {
        if (TOASTS.isEmpty()) return;
        Ach a = TOASTS.peek();

        int w = 380, h = 66;
        int x = screenW - w - 20;

        // Abajo a la derecha: arriba está el HUD y la barra de vida del jefe
        double t = 1 - toastTimer / 240.0;
        double slide = t < 0.12 ? t / 0.12 : t > 0.9 ? (1 - t) / 0.1 : 1;
        slide = Math.max(0, Math.min(1, slide));
        int y = screenH - h - 46 + (int) ((1 - slide) * 90);

        g.setColor(new Color(12, 10, 16, (int) (238 * slide)));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(255, 190, 70, (int) (255 * slide)));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1f));

        g.setFont(new Font("Consolas", Font.BOLD, 12));
        g.setColor(new Color(160, 160, 170, (int) (255 * slide)));
        g.drawString("LOGRO DESBLOQUEADO", x + 58, y + 20);

        g.setFont(new Font("Consolas", Font.BOLD, 16));
        g.setColor(new Color(255, 200, 80, (int) (255 * slide)));
        g.drawString(a.name, x + 58, y + 40);

        g.setFont(new Font("Consolas", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 190, (int) (230 * slide)));
        String d = a.description.length() > 46 ? a.description.substring(0, 44) + "..." : a.description;
        g.drawString(d, x + 58, y + 56);

        // Medalla
        g.setColor(new Color(255, 190, 70, (int) (255 * slide)));
        g.fillOval(x + 14, y + 16, 32, 32);
        g.setColor(new Color(30, 22, 10, (int) (255 * slide)));
        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.drawString("*", x + 26, y + 39);
    }
}
