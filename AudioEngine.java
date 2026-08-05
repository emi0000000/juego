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

    /**
     * Dos canales: hace falta estéreo para que un gruñido a la izquierda se oiga
     * a la izquierda. La música va centrada y los efectos se colocan según dónde
     * ocurren respecto a Nox.
     */
    private static final AudioFormat FORMAT = new AudioFormat(SR, 16, 2, true, false);
    private static final int BUFFER = 1024;

    /** A partir de esta distancia en píxeles, un sonido del mundo ya no se oye. */
    private static final double HEARING_RANGE = 780;

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

    /** Para el hilo de mezcla y suelta la tarjeta de sonido. */
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

    public static void toggleMusic() { musicOn = !musicOn; Settings.save(); }

    public static void toggleSfx() { sfxOn = !sfxOn; Settings.save(); }

    public static void setMusicOn(boolean on) { musicOn = on; }

    public static void setSfxOn(boolean on) { sfxOn = on; }

    /**
     * Corta la música durante un rato.
     *
     * Se usa justo antes de un susto: el silencio repentino avisa al cuerpo de que
     * algo va a pasar, y el golpe posterior asusta mucho más que si sonara encima
     * de la música de siempre.
     */
    public static void duckMusic(double seconds) {
        musicDuckSamples = Math.max(musicDuckSamples, (int) (seconds * SR));
    }

    private static volatile int musicDuckSamples = 0;

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

    public static void play(String name) { play(name, 1.0, 0.0); }

    public static void play(String name, double gain) { play(name, gain, 0.0); }

    /**
     * @param pan -1 todo a la izquierda, 0 centrado, 1 todo a la derecha
     */
    public static void play(String name, double gain, double pan) {
        if (!available || !sfxOn) return;
        float[] data = SFX.get(name);
        if (data == null) return;
        synchronized (PLAYING) {
            if (PLAYING.size() > 24) PLAYING.remove(0);
            PLAYING.add(new ActiveSfx(data, gain, pan));
        }
    }

    /**
     * Reproduce un sonido que ocurre en un punto del mundo: se oye más flojo cuanto
     * más lejos esté de Nox y se desplaza al lado por el que viene.
     *
     * En un juego de terror esto importa: saber que el gruñido viene de tu espalda
     * izquierda es información de juego, no un adorno.
     */
    public static void playAt(String name, double gain,
                              double sourceX, double sourceY,
                              double listenerX, double listenerY) {
        double dx = sourceX - listenerX;
        double dy = sourceY - listenerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > HEARING_RANGE) return;

        // Caída suave: fuerte de cerca, se apaga al llegar al límite
        double falloff = 1 - dist / HEARING_RANGE;
        falloff *= falloff;

        double pan = Math.max(-1, Math.min(1, dx / 420.0));
        play(name, gain * falloff, pan);
    }

    private static final class ActiveSfx {
        final float[] data;
        final double gainLeft, gainRight;
        int pos = 0;

        ActiveSfx(float[] data, double gain, double pan) {
            this.data = data;
            double p = Math.max(-1, Math.min(1, pan));
            // Ley de panoramización de potencia constante: al moverse de un lado a
            // otro el volumen percibido no sube ni baja.
            double angle = (p + 1) * Math.PI / 4;
            this.gainLeft = gain * Math.cos(angle);
            this.gainRight = gain * Math.sin(angle);
        }
    }

    // ------------------------------------------------------------------
    // Bucle de mezcla
    // ------------------------------------------------------------------

    private static void mixLoop() {
        byte[] out = new byte[BUFFER * 4];        // 2 canales x 2 bytes por muestra
        float[] left = new float[BUFFER];
        float[] right = new float[BUFFER];

        while (running) {
            Arrays.fill(left, 0f);
            Arrays.fill(right, 0f);

            // La música va centrada: suena igual por los dos altavoces
            for (int i = 0; i < BUFFER; i++) {
                float m = (float) nextMusicSample();
                if (musicDuckSamples > 0) {
                    musicDuckSamples--;
                    // Se apaga y se recupera de forma gradual, no de golpe
                    double fade = Math.min(1.0, musicDuckSamples / (SR * 0.35));
                    m *= (float) (1 - fade);
                }
                left[i] = m;
                right[i] = m;
            }

            synchronized (PLAYING) {
                Iterator<ActiveSfx> it = PLAYING.iterator();
                while (it.hasNext()) {
                    ActiveSfx a = it.next();
                    int n = Math.min(BUFFER, a.data.length - a.pos);
                    for (int i = 0; i < n; i++) {
                        float s = a.data[a.pos + i];
                        left[i] += (float) (s * a.gainLeft);
                        right[i] += (float) (s * a.gainRight);
                    }
                    a.pos += n;
                    if (a.pos >= a.data.length) it.remove();
                }
            }

            for (int i = 0; i < BUFFER; i++) {
                writeSample(out, i * 4, left[i]);
                writeSample(out, i * 4 + 2, right[i]);
            }

            try {
                line.write(out, 0, out.length);
            } catch (Exception ex) {
                running = false;
            }
        }
    }

    private static void writeSample(byte[] out, int offset, float value) {
        double s = value * masterVolume;
        if (s > 1) s = 1;
        if (s < -1) s = -1;
        short v = (short) (s * 32000);
        out[offset] = (byte) (v & 0xFF);
        out[offset + 1] = (byte) ((v >> 8) & 0xFF);
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

    /**
     * Calcula la siguiente muestra de música sumando cuatro capas: el drone grave,
     * el arpegio de la escala menor, el pad con vibrato y el latido del corazón.
     *
     * Las frecuencias se acumulan en un acumulador de fase en vez de recalcular el
     * seno desde cero: si no, al cambiar de nota la onda daría un salto y se oiría un clic.
     */
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

    /**
     * Convierte una fórmula en un efecto de sonido: evalúa la función para cada
     * muestra y devuelve el resultado ya listo para reproducir.
     */
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

    /**
     * Fabrica los 42 efectos de sonido del juego, cada uno con su fórmula.
     *
     * Las piezas que se combinan son siempre las mismas: senos para tonos limpios,
     * sierra y cuadrada para lo áspero, ruido para explosiones y roces, y una
     * envolvente exponencial que decide cómo aparece y se apaga cada sonido.
     */
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

        // Explosión del molotov y del kamikaze
        SFX.put("boom", render(0.85, t -> {
            double e = Math.exp(-t * 4.2);
            double low = Math.sin(2 * Math.PI * (95 - 70 * t) * t);
            double blast = noise() * Math.exp(-t * 6.5);
            double rumble = saw(38 + 12 * Math.sin(2 * Math.PI * 3 * t), t) * Math.exp(-t * 3);
            return (low * 0.45 + blast * 0.7 + rumble * 0.35) * e * 0.62;
        }));

        // Botella girando por el aire
        SFX.put("throw", render(0.30, t -> {
            double f = 300 + 200 * Math.sin(2 * Math.PI * 9 * t);
            return Math.sin(2 * Math.PI * f * t) * Math.exp(-t * 7) * 0.20;
        }));

        // El escudo detiene el golpe
        SFX.put("clang", render(0.55, t -> {
            double e = Math.exp(-t * 7);
            double m = Math.sin(2 * Math.PI * 2100 * t)
                     + Math.sin(2 * Math.PI * 3170 * t) * 0.75
                     + Math.sin(2 * Math.PI * 4630 * t) * 0.5;
            return (m * 0.24 + noise() * 0.20 * Math.exp(-t * 30)) * e * 0.46;
        }));

        // Aullido del alfa llamando refuerzos
        SFX.put("howl", render(1.60, t -> {
            double glide = t < 0.4 ? t / 0.4 : 1;
            double f = 150 + 210 * glide - 60 * Math.max(0, t - 1.0);
            double vib = 1 + 0.035 * Math.sin(2 * Math.PI * 5.5 * t);
            double e = Math.min(1, t / 0.15) * Math.min(1, (1.60 - t) / 0.45);
            return (saw(f * vib, t) * 0.42 + Math.sin(2 * Math.PI * f * vib * t) * 0.42) * e * 0.44;
        }));

        // Silbato ultrasónico
        SFX.put("whistle", render(0.80, t -> {
            double f = 3100 + 700 * Math.sin(2 * Math.PI * 7 * t);
            double e = Math.min(1, t / 0.04) * Math.min(1, (0.80 - t) / 0.25);
            return (Math.sin(2 * Math.PI * f * t) * 0.55 + Math.sin(2 * Math.PI * f * 1.5 * t) * 0.25) * e * 0.30;
        }));

        // Lata de atún: Nox se cura
        SFX.put("heal", renderNotes(new double[]{392.00, 523.25, 659.25}, 0.11, 0.20, 0));

        // Logro desbloqueado
        SFX.put("achievement", renderNotes(new double[]{659.25, 783.99, 1046.50, 1318.51}, 0.10, 0.16, 0));

        // Foto encontrada: acorde suave, casi un suspiro
        SFX.put("photo", render(0.90, t -> {
            double e = Math.min(1, t / 0.06) * Math.exp(-t * 2.2);
            return (Math.sin(2 * Math.PI * 523.25 * t) * 0.4
                  + Math.sin(2 * Math.PI * 659.25 * t) * 0.3
                  + Math.sin(2 * Math.PI * 783.99 * t) * 0.2) * e * 0.30;
        }));

        // Madera partiéndose al romper un bidón o una caja
        SFX.put("crack", render(0.35, t -> {
            double e = Math.exp(-t * 13);
            return (noise() * 0.6 + saw(160 - 90 * t, t) * 0.3) * e * 0.45;
        }));

        // El jefe se enfurece al llegar a la mitad de su vida
        SFX.put("enrage", render(1.30, t -> {
            double f = 70 + 55 * t;
            double e = Math.min(1, t / 0.08) * Math.min(1, (1.30 - t) / 0.4);
            return (saw(f, t) * 0.5 + square(f * 1.5, t) * 0.25 + noise() * 0.18) * e * 0.52;
        }));

        // Rex desaparece en el túnel y reaparece detrás
        SFX.put("teleport", render(0.55, t -> {
            double f = 900 - 700 * t;
            double e = Math.exp(-t * 4.5);
            return (Math.sin(2 * Math.PI * f * t) * 0.4 + noise() * 0.35 * Math.exp(-t * 9)) * e * 0.42;
        }));

        // Titán estrella un contenedor contra el muelle
        SFX.put("slam", render(0.95, t -> {
            double e = Math.exp(-t * 3.6);
            return (Math.sin(2 * Math.PI * (60 - 35 * t) * t) * 0.55
                  + noise() * 0.5 * Math.exp(-t * 8)) * e * 0.60;
        }));

        // Se apagan las luces de la mansión
        SFX.put("lightsout", render(1.10, t -> {
            double f = 420 * Math.exp(-t * 2.4);
            double e = Math.exp(-t * 1.6);
            return (Math.sin(2 * Math.PI * f * t) * 0.42 + noise() * 0.12) * e * 0.44;
        }));

        // --- Sonidos de la cabeza de Nox: sólo existen cuando la cordura falla ---

        // Pasos que se acercan por detrás
        SFX.put("steps", render(1.40, t -> {
            double paso = 0;
            for (int i = 0; i < 4; i++) {
                double inicio = i * 0.33;
                if (t < inicio) continue;
                double dt = t - inicio;
                double golpe = Math.exp(-dt * 26);
                paso += (noise() * 0.5 + Math.sin(2 * Math.PI * 95 * dt) * 0.5) * golpe * (0.5 + i * 0.16);
            }
            return paso * 0.30;
        }));

        // Susurro sin palabras
        SFX.put("whisper", render(1.80, t -> {
            double e = Math.min(1, t / 0.4) * Math.min(1, (1.80 - t) / 0.6);
            double modulacion = 0.5 + 0.5 * Math.sin(2 * Math.PI * 3.1 * t + Math.sin(t * 7));
            return noise() * modulacion * e * 0.16;
        }));

        // Pitido en los oídos justo después de un susto
        SFX.put("tinnitus", render(2.40, t -> {
            double e = Math.exp(-t * 1.15);
            return (Math.sin(2 * Math.PI * 4200 * t) * 0.5
                  + Math.sin(2 * Math.PI * 6350 * t) * 0.2) * e * 0.16;
        }));

        // El aire que se corta justo antes del susto: es el silencio lo que asusta
        SFX.put("dread", render(1.60, t -> {
            double f = 40 + 26 * t;
            double e = Math.min(1, t / 0.5) * Math.min(1, (1.60 - t) / 0.3);
            return (saw(f, t) * 0.30 + Math.sin(2 * Math.PI * f * 0.5 * t) * 0.25) * e * 0.34;
        }));

        // El zarpazo del susto en sí
        SFX.put("lunge", render(1.10, t -> {
            double e = Math.exp(-t * 2.6);
            double rugido = saw(150 - 70 * t, t) * 0.45 + square(74 - 30 * t, t) * 0.25;
            double golpe = noise() * Math.exp(-t * 12) * 0.55;
            return (rugido + golpe) * e * 0.72;
        }));

        // Chillido agudo: el susto de los ojos y el del reflejo
        SFX.put("shriek", render(1.05, t -> {
            double f = 900 + 1500 * Math.exp(-t * 3) + 220 * Math.sin(2 * Math.PI * 23 * t);
            double e = Math.min(1, t / 0.02) * Math.exp(-t * 2.6);
            return (Math.sin(2 * Math.PI * f * t) * 0.42
                  + saw(f * 0.5, t) * 0.28
                  + noise() * 0.22) * e * 0.60;
        }));

        // Tela y metal rasgándose: el susto de las garras
        SFX.put("rip", render(0.75, t -> {
            double e = Math.exp(-t * 4.2);
            double rasgado = noise() * (0.6 + 0.4 * Math.sin(2 * Math.PI * 40 * t));
            return (rasgado * 0.7 + saw(320 - 180 * t, t) * 0.3) * e * 0.55;
        }));

        // Una respiración muy cerca de la nuca
        SFX.put("breath", render(2.00, t -> {
            double ciclo = Math.sin(2 * Math.PI * 0.75 * t);
            double aire = noise() * Math.max(0, ciclo) * 0.5;
            double e = Math.min(1, t / 0.3) * Math.min(1, (2.00 - t) / 0.5);
            return aire * e * 0.30;
        }));

        // El callejón quemado: un acorde menor sostenido y el crujido de las brasas
        SFX.put("home", render(2.20, t -> {
            double e = Math.min(1, t / 0.35) * Math.min(1, (2.20 - t) / 0.9);
            double acorde = Math.sin(2 * Math.PI * 220.00 * t) * 0.34
                          + Math.sin(2 * Math.PI * 261.63 * t) * 0.26
                          + Math.sin(2 * Math.PI * 329.63 * t) * 0.20;
            double brasas = noise() * 0.06 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 2.3 * t));
            return (acorde + brasas) * e * 0.34;
        }));

        SFX.put("rescue", renderNotes(new double[]{523.25, 659.25, 783.99, 1046.50, 1318.51}, 0.16, 0.20, 0));

        // Final oscuro: los mismos grados que la victoria, pero en menor y descendiendo
        SFX.put("darkending", renderNotes(
            new double[]{329.63, 261.63, 220.00, 174.61, 130.81, 110.00}, 0.42, 0.20, 2));
        SFX.put("levelclear", renderNotes(new double[]{392.00, 523.25, 659.25, 783.99}, 0.20, 0.22, 0));
        SFX.put("death", renderNotes(new double[]{261.63, 233.08, 207.65, 155.56, 130.81}, 0.30, 0.24, 2));
        SFX.put("victory", renderNotes(
            new double[]{523.25, 659.25, 783.99, 1046.50, 783.99, 1046.50, 1318.51}, 0.26, 0.22, 0));
    }
}
