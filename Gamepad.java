import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Soporte para mando de Xbox usando XInput, la API de mandos de Windows.
 *
 * No se usa ninguna librería externa ni código nativo propio: Java 22 en adelante
 * incorpora la API "Foreign Function and Memory" (java.lang.foreign), que permite
 * llamar directamente a funciones de una DLL del sistema desde Java puro.
 *
 * Se llama a dos funciones de xinput:
 *
 *   DWORD XInputGetState(DWORD indice, XINPUT_STATE *estado);
 *   DWORD XInputSetState(DWORD indice, XINPUT_VIBRATION *vibracion);
 *
 * Si algo falla —no es Windows, no hay DLL, la versión de Java es anterior a la 22—
 * la clase simplemente queda desactivada y el juego sigue funcionando con el teclado.
 */
public final class Gamepad {

    // Máscaras de los botones, tal como las define XInput
    public static final int DPAD_UP = 0x0001;
    public static final int DPAD_DOWN = 0x0002;
    public static final int DPAD_LEFT = 0x0004;
    public static final int DPAD_RIGHT = 0x0008;
    public static final int START = 0x0010;
    public static final int BACK = 0x0020;
    public static final int LS = 0x0040;
    public static final int RS = 0x0080;
    public static final int LB = 0x0100;
    public static final int RB = 0x0200;
    public static final int A = 0x1000;
    public static final int B = 0x2000;
    public static final int X = 0x4000;
    public static final int Y = 0x8000;

    /** Zona muerta del stick que recomienda Microsoft, sobre un máximo de 32767. */
    private static final short STICK_DEADZONE = 7849;
    private static final int TRIGGER_THRESHOLD = 30;

    private static final String[] DLL_NAMES = {"xinput1_4.dll", "xinput1_3.dll", "xinput9_1_0.dll"};

    private static MethodHandle getState;
    private static MethodHandle setState;
    private static MemorySegment stateBuffer;
    private static MemorySegment vibrationBuffer;
    private static Arena arena;

    private static boolean available = false;
    private static boolean connected = false;
    private static String deviceInfo = "sin mando";

    private static int buttons = 0;
    private static int previousButtons = 0;
    private static double stickX = 0, stickY = 0;
    private static double leftTrigger = 0, rightTrigger = 0;

    private static int searchCooldown = 0;
    private static int padIndex = 0;
    private static int rumbleFrames = 0;
    private static double currentLow = 0;
    private static long pollCount = 0;

    private Gamepad() { }

    // ------------------------------------------------------------------
    // Arranque
    // ------------------------------------------------------------------

    /**
     * Intenta enlazar con XInput.
     *
     * @return true si el sistema puede usar mando. Devuelve false sin lanzar
     *         excepciones si no se puede: el juego debe seguir con el teclado.
     */
    public static boolean init() {
        if (available) return true;

        try {
            Linker linker = Linker.nativeLinker();
            arena = Arena.ofShared();

            SymbolLookup lookup = null;
            for (String dll : DLL_NAMES) {
                try {
                    lookup = SymbolLookup.libraryLookup(dll, arena);
                    deviceInfo = dll;
                    break;
                } catch (Throwable ignored) {
                    // Se prueba con la siguiente versión de la DLL
                }
            }
            if (lookup == null) return false;

            MemorySegment getAddr = lookup.find("XInputGetState").orElse(null);
            if (getAddr == null) return false;

            getState = linker.downcallHandle(getAddr, FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            MemorySegment setAddr = lookup.find("XInputSetState").orElse(null);
            if (setAddr != null) {
                setState = linker.downcallHandle(setAddr, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            }

            // XINPUT_STATE ocupa 16 bytes; XINPUT_VIBRATION, 4
            stateBuffer = arena.allocate(16, 4);
            vibrationBuffer = arena.allocate(4, 2);

            available = true;
            return true;

        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public static boolean isAvailable() { return available; }

    public static boolean isConnected() { return connected; }

    public static String getStatus() {
        if (!available) return "MANDO NO DISPONIBLE";
        return connected ? "MANDO CONECTADO" : "MANDO DESCONECTADO";
    }

    public static String getDeviceInfo() { return deviceInfo; }

    // ------------------------------------------------------------------
    // Lectura, una vez por fotograma
    // ------------------------------------------------------------------

    /**
     * Cuántas veces se ha leído el mando. Solo debe crecer de uno en uno por
     * fotograma: si dos pantallas leen a la vez, la detección de botones recién
     * pulsados deja de funcionar. La prueba automática lo vigila.
     */
    public static long pollCount() { return pollCount; }

    /**
     * Lee el estado del mando. Hay que llamarlo UNA vez por fotograma y desde una
     * sola pantalla: dos lecturas en el mismo fotograma se comen la detección de
     * los botones recién pulsados y el juego deja de responder.
     *
     * Si el hueco está vacío, cada segundo prueba con otro de los cuatro posibles,
     * así que conectar el mando a mitad de partida funciona sin hacer nada.
     */
    public static void poll() {
        pollCount++;
        if (!available) return;

        previousButtons = buttons;

        try {
            int result = (int) getState.invokeExact(padIndex, stateBuffer);

            if (result != 0) {
                // Ese hueco está vacío. Cada segundo se buscan los otros tres.
                connected = false;
                buttons = 0;
                stickX = stickY = leftTrigger = rightTrigger = 0;
                if (--searchCooldown <= 0) {
                    searchCooldown = 60;
                    padIndex = (padIndex + 1) % 4;
                }
                return;
            }

            connected = true;
            searchCooldown = 0;

            buttons = stateBuffer.get(ValueLayout.JAVA_SHORT, 4) & 0xFFFF;
            leftTrigger = (stateBuffer.get(ValueLayout.JAVA_BYTE, 6) & 0xFF) / 255.0;
            rightTrigger = (stateBuffer.get(ValueLayout.JAVA_BYTE, 7) & 0xFF) / 255.0;

            short rawX = stateBuffer.get(ValueLayout.JAVA_SHORT, 8);
            short rawY = stateBuffer.get(ValueLayout.JAVA_SHORT, 10);
            stickX = applyDeadzone(rawX);
            stickY = -applyDeadzone(rawY);   // en XInput el eje Y crece hacia arriba

            if (rumbleFrames > 0 && --rumbleFrames == 0) stopRumble();

        } catch (Throwable t) {
            // Si el mando falla en caliente se desactiva todo antes que romper la partida
            available = false;
            connected = false;
        }
    }

    /** Convierte el valor bruto del stick a -1..1 descartando la zona muerta. */
    private static double applyDeadzone(short raw) {
        double value = raw;
        if (Math.abs(value) < STICK_DEADZONE) return 0;
        double sign = Math.signum(value);
        double scaled = (Math.abs(value) - STICK_DEADZONE) / (32767.0 - STICK_DEADZONE);
        return sign * Math.min(1.0, scaled);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** El botón está pulsado ahora mismo. */
    public static boolean down(int button) {
        return connected && (buttons & button) != 0;
    }

    /** El botón se acaba de pulsar en este fotograma (no se repite si lo mantienes). */
    public static boolean pressed(int button) {
        return connected && (buttons & button) != 0 && (previousButtons & button) == 0;
    }

    public static double stickX() { return stickX; }
    public static double stickY() { return stickY; }

    public static boolean leftTriggerDown() { return connected && leftTrigger > TRIGGER_THRESHOLD / 255.0; }
    public static boolean rightTriggerDown() { return connected && rightTrigger > TRIGGER_THRESHOLD / 255.0; }

    /** Movimiento combinado del stick y la cruceta, en -1..1. */
    public static double moveX() {
        double x = stickX;
        if (down(DPAD_LEFT)) x = -1;
        if (down(DPAD_RIGHT)) x = 1;
        return x;
    }

    public static double moveY() {
        double y = stickY;
        if (down(DPAD_UP)) y = -1;
        if (down(DPAD_DOWN)) y = 1;
        return y;
    }

    // ------------------------------------------------------------------
    // Vibración
    // ------------------------------------------------------------------

    /**
     * Hace vibrar el mando.
     *
     * @param low    motor grande, golpes sordos (0 a 1)
     * @param high   motor pequeño, detalles finos (0 a 1)
     * @param frames cuántos fotogramas dura
     */
    public static void rumble(double low, double high, int frames) {
        if (!available || !connected || setState == null) return;

        // Cada orden de vibración es una llamada al sistema y cuesta tiempo. Si ya
        // está vibrando igual o más fuerte, solo se alarga la duración: así una
        // ráfaga de golpes no dispara treinta llamadas nativas seguidas.
        if (rumbleFrames > 0 && low <= currentLow + 0.05) {
            rumbleFrames = Math.max(rumbleFrames, frames);
            return;
        }
        currentLow = low;

        try {
            int l = (int) (Math.max(0, Math.min(1, low)) * 65535);
            int h = (int) (Math.max(0, Math.min(1, high)) * 65535);
            vibrationBuffer.set(ValueLayout.JAVA_SHORT, 0, (short) l);
            vibrationBuffer.set(ValueLayout.JAVA_SHORT, 2, (short) h);
            int ignored = (int) setState.invokeExact(padIndex, vibrationBuffer);
            rumbleFrames = Math.max(rumbleFrames, frames);
        } catch (Throwable ignored) {
            // La vibración es un extra: si falla, no pasa nada
        }
    }

    /** Corta la vibración. Se llama al salir al menú, al morir y al ganar. */
    public static void stopRumble() {
        if (!available || setState == null) return;
        try {
            vibrationBuffer.set(ValueLayout.JAVA_SHORT, 0, (short) 0);
            vibrationBuffer.set(ValueLayout.JAVA_SHORT, 2, (short) 0);
            int ignored = (int) setState.invokeExact(padIndex, vibrationBuffer);
            rumbleFrames = 0;
            currentLow = 0;
        } catch (Throwable ignored) {
            // Nada que hacer
        }
    }

    /** Deja el mando quieto y libera la memoria nativa al cerrar el juego. */
    public static void shutdown() {
        stopRumble();
        available = false;
        connected = false;
        try {
            if (arena != null) arena.close();
        } catch (Throwable ignored) {
            // El Arena compartido puede estar en uso; no importa al cerrar
        }
    }
}
