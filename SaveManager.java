import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Guardado del progreso de Nox.
 *
 * El guardado local (archivo exodo_save.properties junto al juego) es el que manda:
 * funciona siempre, aunque no haya servidor ni internet.
 * La sincronización con server.py es un extra opcional y nunca bloquea el juego,
 * porque corre en un hilo aparte con tiempos de espera muy cortos.
 */
public final class SaveManager {

    public static final String SAVE_FILE = "exodo_save.properties";
    public static final String PLAYER_ID = "nox";

    private static String serverUrl = "http://127.0.0.1:8000";
    private static volatile String cloudStatus = "LOCAL";

    private SaveManager() { }

    public static String getCloudStatus() { return cloudStatus; }

    // ------------------------------------------------------------------
    // Datos de la partida
    // ------------------------------------------------------------------

    public static class Progress {
        public int level = 1;
        public int health = 100;
        public int money = 0;
        public int difficulty = 1;
        public double sanity = 100;
        public boolean claws = false;
        public boolean crossbow = false;
        public boolean plasma = false;
        public boolean molotov = false;
        public boolean whistle = false;
        public int armor = 0;
        public int tuna = 0;
        public boolean[] family = new boolean[3];
        /** Una casilla por cada foto escondida de la ciudad. */
        public boolean[] photos = new boolean[Memories.TOTAL];
        public int deaths = 0;
        public int score = 0;
        public long playTimeMs = 0;

        String photosAsString() {
            StringBuilder sb = new StringBuilder();
            for (boolean b : photos) sb.append(b ? '1' : '0');
            return sb.toString();
        }

        void photosFromString(String s) {
            photos = new boolean[Memories.TOTAL];
            if (s == null) return;
            for (int i = 0; i < Math.min(s.length(), photos.length); i++) {
                photos[i] = s.charAt(i) == '1';
            }
        }

        public String familyAsString() {
            StringBuilder sb = new StringBuilder();
            for (boolean b : family) sb.append(b ? '1' : '0');
            return sb.toString();
        }

        public void familyFromString(String s) {
            family = new boolean[3];
            if (s == null) return;
            for (int i = 0; i < Math.min(3, s.length()); i++) family[i] = s.charAt(i) == '1';
        }
    }

    // ------------------------------------------------------------------
    // Guardado local
    // ------------------------------------------------------------------

    public static boolean hasSave() {
        return new File(SAVE_FILE).exists();
    }

    /** Guarda la partida en disco y, si hay servidor, la manda también por red. */
    public static void save(Progress p) {
        Properties props = new Properties();
        props.setProperty("level", String.valueOf(p.level));
        props.setProperty("health", String.valueOf(p.health));
        props.setProperty("money", String.valueOf(p.money));
        props.setProperty("difficulty", String.valueOf(p.difficulty));
        props.setProperty("sanity", String.valueOf((int) p.sanity));
        props.setProperty("claws", String.valueOf(p.claws));
        props.setProperty("crossbow", String.valueOf(p.crossbow));
        props.setProperty("plasma", String.valueOf(p.plasma));
        props.setProperty("molotov", String.valueOf(p.molotov));
        props.setProperty("whistle", String.valueOf(p.whistle));
        props.setProperty("armor", String.valueOf(p.armor));
        props.setProperty("tuna", String.valueOf(p.tuna));
        props.setProperty("family", p.familyAsString());
        props.setProperty("photos", p.photosAsString());
        props.setProperty("deaths", String.valueOf(p.deaths));
        props.setProperty("score", String.valueOf(p.score));
        props.setProperty("playTimeMs", String.valueOf(p.playTimeMs));

        try (OutputStream out = new FileOutputStream(SAVE_FILE)) {
            props.store(out, "ÉXODO: LA VENGANZA DE NOX - progreso guardado");
        } catch (Exception ex) {
            System.err.println("[GUARDADO] No se pudo escribir " + SAVE_FILE + ": " + ex.getMessage());
        }

        syncToServerAsync(p);
    }

    /**
     * Lee la partida guardada. Si el archivo no existe o está corrupto devuelve una
     * partida nueva en vez de fallar: perder el guardado no puede impedir jugar.
     */
    public static Progress load() {
        Progress p = new Progress();
        File f = new File(SAVE_FILE);
        if (!f.exists()) return p;

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (Exception ex) {
            System.err.println("[GUARDADO] No se pudo leer la partida: " + ex.getMessage());
            return p;
        }

        p.level = intOf(props, "level", 1);
        p.health = intOf(props, "health", 100);
        p.money = intOf(props, "money", 0);
        p.difficulty = intOf(props, "difficulty", 1);
        p.sanity = intOf(props, "sanity", 100);
        p.claws = Boolean.parseBoolean(props.getProperty("claws", "false"));
        p.crossbow = Boolean.parseBoolean(props.getProperty("crossbow", "false"));
        p.plasma = Boolean.parseBoolean(props.getProperty("plasma", "false"));
        p.molotov = Boolean.parseBoolean(props.getProperty("molotov", "false"));
        p.whistle = Boolean.parseBoolean(props.getProperty("whistle", "false"));
        p.armor = intOf(props, "armor", 0);
        p.tuna = intOf(props, "tuna", 0);
        p.familyFromString(props.getProperty("family", "000"));
        p.photosFromString(props.getProperty("photos", ""));
        p.deaths = intOf(props, "deaths", 0);
        p.score = intOf(props, "score", 0);
        try {
            p.playTimeMs = Long.parseLong(props.getProperty("playTimeMs", "0").trim());
        } catch (Exception ignored) { }
        return p;
    }

    public static void deleteSave() {
        File f = new File(SAVE_FILE);
        if (f.exists() && !f.delete()) {
            System.err.println("[GUARDADO] No se pudo borrar " + SAVE_FILE);
        }
    }

    private static int intOf(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Sincronización opcional con server.py
    // ------------------------------------------------------------------

    private static String toJson(Progress p) {
        return "{"
             + "\"level\":" + p.level + ","
             + "\"health\":" + p.health + ","
             + "\"money\":" + p.money + ","
             + "\"difficulty\":" + p.difficulty + ","
             + "\"sanity\":" + (int) p.sanity + ","
             + "\"claws\":" + p.claws + ","
             + "\"crossbow\":" + p.crossbow + ","
             + "\"plasma\":" + p.plasma + ","
             + "\"molotov\":" + p.molotov + ","
             + "\"whistle\":" + p.whistle + ","
             + "\"armor\":" + p.armor + ","
             + "\"tuna\":" + p.tuna + ","
             + "\"family\":\"" + p.familyAsString() + "\","
             + "\"photos\":\"" + p.photosAsString() + "\","
             + "\"deaths\":" + p.deaths + ","
             + "\"score\":" + p.score + ","
             + "\"play_time_ms\":" + p.playTimeMs
             + "}";
    }

    /**
     * Manda la partida al servidor en un hilo aparte.
     *
     * Nunca en el hilo del juego: si el servidor estuviera apagado, esperar la
     * respuesta congelaría la pantalla. Así como mucho el indicador se queda en LOCAL.
     */
    public static void syncToServerAsync(Progress p) {
        Progress copy = new Progress();
        copy.level = p.level;
        copy.health = p.health;
        copy.money = p.money;
        copy.difficulty = p.difficulty;
        copy.sanity = p.sanity;
        copy.claws = p.claws;
        copy.crossbow = p.crossbow;
        copy.plasma = p.plasma;
        copy.molotov = p.molotov;
        copy.whistle = p.whistle;
        copy.armor = p.armor;
        copy.tuna = p.tuna;
        copy.family = p.family.clone();
        copy.photos = p.photos.clone();
        copy.deaths = p.deaths;
        copy.score = p.score;
        copy.playTimeMs = p.playTimeMs;

        Thread t = new Thread(() -> post("/api/save_player/" + PLAYER_ID, toJson(copy)), "exodo-cloud");
        t.setDaemon(true);
        t.start();
    }

    /** Envía la puntuación final a la tabla de récords del servidor. */
    public static void submitScoreAsync(String name, int score, int level, int difficulty) {
        String safe = name == null ? "NOX" : name.replaceAll("[^A-Za-z0-9 _-]", "");
        if (safe.isEmpty()) safe = "NOX";
        final String json = "{\"name\":\"" + safe + "\",\"score\":" + score
                          + ",\"level\":" + level + ",\"difficulty\":" + difficulty + "}";
        Thread t = new Thread(() -> post("/api/score", json), "exodo-score");
        t.setDaemon(true);
        t.start();
    }

    /** Envío HTTP con 900 ms de espera máxima. Si falla, se marca el modo LOCAL. */
    private static void post(String path, String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(serverUrl + path).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(900);
            conn.setReadTimeout(900);
            conn.setDoOutput(true);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            cloudStatus = (code >= 200 && code < 300) ? "NUBE OK" : "NUBE ERR " + code;
        } catch (Exception ex) {
            cloudStatus = "LOCAL";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
