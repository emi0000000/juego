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
    /** Obstáculo pequeño: bidón, caja, mueble. Bloquea. */
    public static final int PROP = 9;

    private Tiles() { }

    public static boolean blocks(int tile) {
        return tile == WALL || tile == WATER || tile == VOID || tile == PROP || tile == SHOP;
    }

    public static boolean walkable(int tile) {
        return tile == FLOOR || tile == COIN || tile == DECOR || tile == EXIT;
    }
}
