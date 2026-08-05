/** Tipos de casilla del mapa, compartidos entre el generador, el dibujo y el motor. */
public final class Tiles {

    /** Suelo libre. */
    public static final int FLOOR = 0;
    /** Moneda tirada en el suelo. */
    public static final int COIN = 2;
    /** Estructura grande: edificio, contenedor, montaña de basura. Bloquea. */
    public static final int WALL = 3;
    /** Puesto del Mercado Negro. Bloquea, pero se compra estando al lado. */
    public static final int SHOP = 4;
    /** Salida del barrio. */
    public static final int EXIT = 5;
    /** Agua del puerto. Bloquea. */
    public static final int WATER = 6;
    /** Vacío entre azoteas. Bloquea (es una caída de veinte pisos). */
    public static final int VOID = 7;
    /** Suelo decorado: vías, alfombra, tablón. Se puede pisar. */
    public static final int DECOR = 8;
    /** Obstáculo pequeño: bidón, caja, mueble. Bloquea, pero se puede romper. */
    public static final int PROP = 9;
    /** Foto quemada de la familia de Nox: recuerdo escondido por el barrio. */
    public static final int PHOTO = 10;
    /** Igual que PROP por fuera, pero guarda dinero dentro. Uno por barrio. */
    public static final int PROP_SECRET = 11;
    /** Lo que quedó del callejón donde vivía Nox con su familia. */
    public static final int HOME = 12;

    private Tiles() { }

    public static boolean blocks(int tile) {
        return tile == WALL || tile == WATER || tile == VOID || tile == PROP
            || tile == SHOP || tile == PROP_SECRET || tile == HOME;
    }

    public static boolean walkable(int tile) {
        return tile == FLOOR || tile == COIN || tile == DECOR || tile == EXIT || tile == PHOTO;
    }

    /** Se puede destruir a golpes. */
    public static boolean breakable(int tile) {
        return tile == PROP || tile == PROP_SECRET;
    }
}
