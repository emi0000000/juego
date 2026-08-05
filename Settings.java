import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Opciones del jugador, guardadas entre partidas.
 *
 * Las de accesibilidad no son un adorno: este es un juego de terror con sustos de
 * pantalla completa y parpadeos rojos, y hay gente a la que eso le sienta mal de
 * verdad. Poder apagarlos permite enseñar el juego a cualquiera.
 */
public final class Settings {

    private static final File FILE = new File("exodo_config.properties");

    private static boolean jumpscares = true;
    private static boolean screenFlashes = true;
    private static boolean damageNumbers = true;
    private static boolean screenShake = true;
    private static boolean rumble = true;
    private static boolean fullscreen = false;

    private Settings() { }

    public static boolean fullscreen() { return fullscreen; }

    public static void setFullscreen(boolean on) { fullscreen = on; save(); }

    public static boolean jumpscares() { return jumpscares; }
    public static boolean screenFlashes() { return screenFlashes; }
    public static boolean damageNumbers() { return damageNumbers; }
    public static boolean screenShake() { return screenShake; }
    public static boolean rumble() { return rumble; }

    public static void toggleJumpscares() { jumpscares = !jumpscares; save(); }
    public static void toggleScreenFlashes() { screenFlashes = !screenFlashes; save(); }
    public static void toggleDamageNumbers() { damageNumbers = !damageNumbers; save(); }
    public static void toggleScreenShake() { screenShake = !screenShake; save(); }
    public static void toggleRumble() {
        rumble = !rumble;
        if (!rumble) Gamepad.stopRumble();
        save();
    }

    /** Lee las opciones del disco. Si no hay archivo, se quedan las de por defecto. */
    public static void load() {
        if (!FILE.exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(FILE)) {
            p.load(in);
        } catch (Exception e) {
            return;
        }
        jumpscares = flag(p, "jumpscares", true);
        screenFlashes = flag(p, "flashes", true);
        damageNumbers = flag(p, "damageNumbers", true);
        screenShake = flag(p, "screenShake", true);
        rumble = flag(p, "rumble", true);
        fullscreen = flag(p, "fullscreen", false);
        AudioEngine.setMusicOn(flag(p, "music", true));
        AudioEngine.setSfxOn(flag(p, "sfx", true));
    }

    /** Escribe las opciones al disco. Se llama en cada cambio, no al salir. */
    public static void save() {
        Properties p = new Properties();
        p.setProperty("jumpscares", String.valueOf(jumpscares));
        p.setProperty("flashes", String.valueOf(screenFlashes));
        p.setProperty("damageNumbers", String.valueOf(damageNumbers));
        p.setProperty("screenShake", String.valueOf(screenShake));
        p.setProperty("rumble", String.valueOf(rumble));
        p.setProperty("fullscreen", String.valueOf(fullscreen));
        p.setProperty("music", String.valueOf(AudioEngine.isMusicOn()));
        p.setProperty("sfx", String.valueOf(AudioEngine.isSfxOn()));
        try (FileOutputStream out = new FileOutputStream(FILE)) {
            p.store(out, "Opciones de EXODO: LA VENGANZA DE NOX");
        } catch (Exception ignored) {
            // Si no se puede escribir, se juega igual con las opciones por defecto
        }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(fallback)));
    }
}
