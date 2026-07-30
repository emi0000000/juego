import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Motor de audio de ÉXODO.
 *
 * Todo el sonido se sintetiza en tiempo real: no hay archivos .wav ni .mp3 en el
 * proyecto, así que el juego suena igual en cualquier computadora sin copiar nada extra.
 * Música y efectos se mezclan en una sola línea de audio para que puedan sonar a la vez.
 */
public final class AudioEngine {

    private static final float SR = 44100f;
    private static final AudioFormat FORMAT = new AudioFormat(SR, 16, 1, true, false);
    private static final int BUFFER = 1024;

    private static final Map<String, float[]> SFX = new HashMap<>();
    private static final List<ActiveSfx> PLAYING = new ArrayList<>();
    private static final Random RND = new Random();

    private static SourceDataLine line;
    private static Thread audioThread;
    private static volatile boolean running = false;
    private static volatile boolean available = false;

    private static volatile boolean musicOn = true;
    private static volatile boolean sfxOn = true;
    private static volatile double masterVolume = 0.75;

    /** 0 = menú, 1..6 = barrio. */
    private static volatile int musicMode = 0;
    private static volatile boolean bossMode = false;
    /** 0 = tranquilo, 1 = pánico. Controla el latido del corazón. */
    private static volatile double tension = 0;

    private AudioEngine() { }

    // ------------------------------------------------------------------
    // Arranque y apagado
    // ------------------------------------------------------------------

    public static void start() {
        if (running) return;
        buildSfxBank();
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, BUFFER * 4);
            line.start();
            available = true;
        } catch (Exception ex) {
            available = false;
            System.err.println("[AUDIO] Sin tarjeta de sonido disponible, el juego seguirá en silencio.");
            return;
        }
        running = true;
        audioThread = new Thread(AudioEngine::mixLoop, "exodo-audio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public static void shutdown() {
        running = false;
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) { }
        }
    }

    public static boolean isAvailable() { return available; }

    public static boolean isMusicOn() { return musicOn; }

    public static boolean isSfxOn() { return sfxOn; }

    public static void toggleMusic() { musicOn = !musicOn; }

    public static void toggleSfx() { sfxOn = !sfxOn; }

    public static void setMusicMode(int mode) {
        if (musicMode != mode) {
            musicMode = mode;
            lastStep = -1;
        }
    }

    public static void setBossMode(boolean b) { bossMode = b; }

    public static void setTension(double t) { tension = Math.max(0, Math.min(1, t)); }

    // ------------------------------------------------------------------
    // Reproducción de efectos
    // ------------------------------------------------------------------

    public static void play(String name) { play(name, 1.0); }

    public static void play(String name, double gain) {
        if (!available || !sfxOn) return;
        float[] data = SFX.get(name);
        if (data == null) return;
        synchronized (PLAYING) {
            if (PLAYING.size() > 24) PLAYING.remove(0);
            PLAYING.add(new ActiveSfx(data, gain));
        }
    }

    private static final class ActiveSfx {
        final float[] data;
        final double gain;
        int pos = 0;
        ActiveSfx(float[] data, double gain) { this.data = data; this.gain = gain; }
    }

    // ------------------------------------------------------------------
    // Bucle de mezcla
    // ------------------------------------------------------------------

    private static void mixLoop() {
        byte[] out = new byte[BUFFER * 2];
        float[] mix = new float[BUFFER];
        while (running) {
            Arrays.fill(mix, 0f);

            for (int i = 0; i < BUFFER; i++) mix[i] = (float) nextMusicSample();

            synchronized (PLAYING) {
                Iterator<ActiveSfx> it = PLAYING.iterator();
                while (it.hasNext()) {
                    ActiveSfx a = it.next();
                    int n = Math.min(BUFFER, a.data.length - a.pos);
                    for (int i = 0; i < n; i++) mix[i] += (float) (a.data[a.pos + i] * a.gain);
                    a.pos += n;
                    if (a.pos >= a.data.length) it.remove();
                }
            }

            for (int i = 0; i < BUFFER; i++) {
                double s = mix[i] * masterVolume;
                if (s > 1) s = 1;
                if (s < -1) s = -1;
                short v = (short) (s * 32000);
                out[i * 2] = (byte) (v & 0xFF);
                out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }

            try {
                line.write(out, 0, out.length);
            } catch (Exception ex) {
                running = false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Música procedural (ambiente oscuro en escala menor)
    // ------------------------------------------------------------------

    /** Frecuencia raíz de cada zona: índice 0 = menú, 1..6 = barrios. */
    private static final double[] ROOTS = {49.00, 55.00, 58.27, 43.65, 61.74, 46.25, 41.20};

    /** Grados de la escala menor por zona (-1 = silencio). */
    private static final int[][] PATTERNS = {
        {0, -1, 3, -1, 4, -1, 3, -1},              // menú: lento y melancólico
        {0, 3, 4, 3, 0, -1, 5, 4},                 // basurero
        {0, 4, 5, 4, 2, 4, 0, -1},                 // mercado
        {0, -1, 2, 3, -1, 3, 5, 4},                // metro
        {0, 5, 4, 5, 7, 5, 4, 2},                  // puerto
        {0, 2, 3, 5, 7, 5, 3, 2},                  // azoteas
        {0, 1, 0, 4, 3, 1, 0, -1},                 // mansión (usa la segunda menor: suena siniestro)
    };

    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10, 12};

    private static double dronePhase, fifthPhase, arpPhase, padPhase, subPhase;
    private static long musicPos = 0;
    private static int lastStep = -1;
    private static double arpFreq = 220, arpAge = 1e9;
    private static double heartAge = 1e9;
    private static long heartLast = 0;

    private static double rootFreq() {
        int idx = Math.max(0, Math.min(ROOTS.length - 1, musicMode));
        return ROOTS[idx];
    }

    private static double noteFreq(double root, int degree, int octave) {
        int semis = MINOR[Math.max(0, Math.min(MINOR.length - 1, degree))];
        return root * Math.pow(2, semis / 12.0) * Math.pow(2, octave);
    }

    private static double nextMusicSample() {
        musicPos++;
        if (!musicOn) return 0;

        double root = rootFreq();
        int stepLen = (int) (SR * (bossMode ? 0.20 : 0.42));
        int step = (int) (musicPos / stepLen);

        if (step != lastStep) {
            lastStep = step;
            int[] pat = PATTERNS[Math.max(0, Math.min(PATTERNS.length - 1, musicMode))];
            int deg = pat[Math.floorMod(step, pat.length)];
            if (deg >= 0) {
                arpFreq = noteFreq(root, deg, bossMode ? 3 : 2);
                arpAge = 0;
            }
        }

        dronePhase += 2 * Math.PI * root / SR;
        fifthPhase += 2 * Math.PI * (root * 1.4983) / SR;
        arpPhase += 2 * Math.PI * arpFreq / SR;
        padPhase += 2 * Math.PI * (root * 4) / SR;
        subPhase += 2 * Math.PI * (root * 0.5) / SR;

        double attack = Math.min(1, arpAge / 400.0);
        double decay = Math.exp(-arpAge / (SR * (bossMode ? 0.16 : 0.32)));
        double arpEnv = attack * decay;
        arpAge++;

        double drone = Math.sin(dronePhase) * 0.20 + Math.sin(fifthPhase) * 0.06;
        double sub = Math.sin(subPhase) * 0.10;
        double arp = (Math.sin(arpPhase) * 0.65 + Math.sin(arpPhase * 2) * 0.18) * arpEnv * 0.16;

        double slow = musicPos / (double) SR;
        double pad = Math.sin(padPhase + Math.sin(slow * 0.27) * 2.5) * 0.030;

        double pulse = 0;
        if (bossMode) {
            double beat = Math.max(0, Math.sin(2 * Math.PI * 2.4 * slow));
            pulse = beat * beat * Math.sin(dronePhase * 0.5) * 0.16;
        }

        // Latido del corazón: aparece cuando la cordura baja
        double heart = 0;
        if (tension > 0.15) {
            long period = (long) (SR * (1.15 - 0.55 * tension));
            if (musicPos - heartLast > period) {
                heartLast = musicPos;
                heartAge = 0;
            }
            if (heartAge < SR * 0.35) {
                double ht = heartAge / SR;
                double thump = Math.sin(2 * Math.PI * (58 - 40 * ht) * ht) * Math.exp(-ht * 16);
                double thump2 = ht > 0.13 ? Math.sin(2 * Math.PI * 52 * (ht - 0.13)) * Math.exp(-(ht - 0.13) * 20) * 0.7 : 0;
                heart = (thump + thump2) * 0.30 * tension;
            }
            heartAge++;
        }

        return (drone + sub + arp + pad + pulse + heart) * 0.85;
    }

    // ------------------------------------------------------------------
    // Síntesis de efectos de sonido
    // ------------------------------------------------------------------

    private interface Voice { double at(double t); }

    private static float[] render(double seconds, Voice v) {
        int n = (int) (SR * seconds);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double s = v.at(i / (double) SR);
            if (s > 1) s = 1;
            if (s < -1) s = -1;
            out[i] = (float) s;
        }
        return out;
    }

    private static double noise() { return RND.nextDouble() * 2 - 1; }

    private static double saw(double f, double t) { return 2 * ((f * t) % 1.0) - 1; }

    private static double square(double f, double t) { return Math.sin(2 * Math.PI * f * t) >= 0 ? 1 : -1; }

    /** Secuencia de notas con envolvente de pluck. wave: 0 seno, 1 cuadrada, 2 sierra. */
    private static float[] renderNotes(double[] freqs, double noteLen, double amp, int wave) {
        int total = (int) (SR * (freqs.length * noteLen + 0.45));
        float[] out = new float[total];
        for (int k = 0; k < freqs.length; k++) {
            int start = (int) (k * noteLen * SR);
            int len = (int) ((noteLen + 0.40) * SR);
            double phase = 0;
            for (int i = 0; i < len && start + i < total; i++) {
                double tt = i / SR;
                phase += 2 * Math.PI * freqs[k] / SR;
                double s;
                if (wave == 1) s = Math.sin(phase) >= 0 ? 1 : -1;
                else if (wave == 2) s = 2 * ((phase / (2 * Math.PI)) % 1.0) - 1;
                else s = Math.sin(phase) * 0.8 + Math.sin(phase * 2) * 0.2;
                double env = Math.min(1, tt / 0.012) * Math.exp(-tt * 4.5);
                out[start + i] += (float) (s * env * amp);
            }
        }
        return out;
    }

    private static void buildSfxBank() {
        if (!SFX.isEmpty()) return;

        // Zarpazo de garras
        SFX.put("claw", render(0.18, t -> {
            double e = Math.exp(-t * 26);
            return (noise() * 0.55 + Math.sin(2 * Math.PI * (1500 - 3800 * t) * t) * 0.45) * e * 0.45;
        }));

        // Garras de acero: metálico e inarmónico
        SFX.put("steel", render(0.30, t -> {
            double e = Math.exp(-t * 12);
            double m = Math.sin(2 * Math.PI * 1780 * t)
                     + Math.sin(2 * Math.PI * 2460 * t) * 0.7
                     + Math.sin(2 * Math.PI * 3310 * t) * 0.45;
            return (m * 0.30 + noise() * 0.35) * e * 0.40;
        }));

        // Disparo de ballesta
        SFX.put("shoot", render(0.22, t -> {
            double f = 880 * Math.exp(-t * 9) + 130;
            double e = Math.exp(-t * 13);
            return (square(f, t) * 0.7 + noise() * 0.25) * e * 0.30;
        }));

        // Lanzador de plasma
        SFX.put("plasma", render(0.45, t -> {
            double f = 300 * Math.exp(-t * 3.2) + 55;
            double wobble = 1 + 0.55 * Math.sin(2 * Math.PI * 26 * t);
            double e = Math.exp(-t * 5.5);
            return (Math.sin(2 * Math.PI * f * wobble * t) * 0.75 + noise() * 0.22) * e * 0.42;
        }));

        // Impacto en enemigo
        SFX.put("hit", render(0.16, t -> {
            double e = Math.exp(-t * 24);
            return (Math.sin(2 * Math.PI * (190 - 260 * t) * t) * 0.75 + noise() * 0.45) * e * 0.38;
        }));

        // Moneda recogida
        SFX.put("coin", render(0.26, t -> {
            double a = t < 0.06 ? Math.exp(-t * 22) : 0;
            double b = t >= 0.06 ? Math.exp(-(t - 0.06) * 15) : 0;
            return (Math.sin(2 * Math.PI * 1050 * t) * a + Math.sin(2 * Math.PI * 1580 * t) * b) * 0.26;
        }));

        // Nox recibe daño (maullido de dolor)
        SFX.put("hurt", render(0.55, t -> {
            double f = 780 * Math.exp(-t * 2.4) + 190;
            double vib = 1 + 0.07 * Math.sin(2 * Math.PI * 15 * t);
            double e = Math.min(1, t / 0.02) * Math.exp(-t * 4.2);
            return (saw(f * vib, t) * 0.55 + Math.sin(2 * Math.PI * f * t) * 0.35) * e * 0.34;
        }));

        // Ladrido de perro
        SFX.put("bark", render(0.28, t -> {
            double f = 320 * Math.exp(-t * 6) + 95;
            double e = Math.min(1, t / 0.008) * Math.exp(-t * 11);
            return (saw(f, t) * 0.6 + noise() * 0.35) * e * 0.40;
        }));

        // Gruñido de jefe
        SFX.put("growl", render(1.40, t -> {
            double f = 68 + 22 * Math.sin(2 * Math.PI * 5.5 * t);
            double e = Math.min(1, t / 0.12) * Math.min(1, (1.40 - t) / 0.35);
            return (saw(f, t) * 0.62 + saw(f * 1.51, t) * 0.22 + noise() * 0.30) * e * 0.48;
        }));

        // Susto
        SFX.put("scare", render(1.10, t -> {
            double e = Math.exp(-t * 3.0);
            double cluster = Math.sin(2 * Math.PI * 176 * t)
                           + Math.sin(2 * Math.PI * 187 * t)
                           + Math.sin(2 * Math.PI * 372 * t) * 0.6
                           + Math.sin(2 * Math.PI * 59 * t) * 1.1;
            double burst = noise() * Math.exp(-t * 8);
            return (cluster * 0.17 + burst * 0.65) * e * 0.72;
        }));

        // Puerta / salida desbloqueada
        SFX.put("door", render(0.9, t -> {
            double e = Math.min(1, t / 0.05) * Math.exp(-t * 3.4);
            return (saw(52 + 30 * t, t) * 0.5 + noise() * 0.35 * Math.exp(-t * 6)) * e * 0.38;
        }));

        // Bloqueado
        SFX.put("locked", render(0.30, t -> {
            double e = Math.exp(-t * 11);
            return square(95, t) * e * 0.24;
        }));

        SFX.put("blip", render(0.09, t -> Math.sin(2 * Math.PI * 900 * t) * Math.exp(-t * 30) * 0.22));

        SFX.put("buy", renderNotes(new double[]{523.25, 659.25, 1046.50}, 0.09, 0.22, 0));
        SFX.put("error", render(0.35, t -> square(78, t) * Math.exp(-t * 6) * 0.24));

        SFX.put("rescue", renderNotes(new double[]{523.25, 659.25, 783.99, 1046.50, 1318.51}, 0.16, 0.20, 0));
        SFX.put("levelclear", renderNotes(new double[]{392.00, 523.25, 659.25, 783.99}, 0.20, 0.22, 0));
        SFX.put("death", renderNotes(new double[]{261.63, 233.08, 207.65, 155.56, 130.81}, 0.30, 0.24, 2));
        SFX.put("victory", renderNotes(
            new double[]{523.25, 659.25, 783.99, 1046.50, 783.99, 1046.50, 1318.51}, 0.26, 0.22, 0));
    }
}
