/**
 * Las fotos quemadas que Nox va encontrando por la ciudad.
 *
 * Son el motivo para explorar el barrio en vez de ir directo al jefe: cada una
 * devuelve cordura (y la cordura decide el final) y cuenta un trozo del pasado
 * de Nox que no aparece en ningún otro sitio.
 */
public final class Memories {

    public static final int PER_LEVEL = 3;
    public static final int TOTAL = PER_LEVEL * 6;

    /** Cuánta cordura devuelve encontrar una. */
    public static final int SANITY_REWARD = 12;

    private static final String[][] TEXTS = {
        // Barrio 1 — EL BASURERO
        {
            "Aquí nació Nox, entre cajas mojadas.\nSu madre lo lamió hasta que dejó de temblar.",
            "Su primera comida salió de este basurero.\nTenía tres semanas y ya sabía escarbar.",
            "Aprendió a pelear antes que a maullar.\nNadie se lo enseñó: la calle no da clases.",
        },
        // Barrio 2 — MERCADO CENTRAL
        {
            "Aquí conoció a Mitsy.\nElla le robó un pescado y él la dejó escapar.",
            "Will nació un martes de lluvia,\ndetrás del puesto de frutas.",
            "El pescadero les dejaba las sobras.\nNunca supo cómo se llamaban.",
        },
        // Barrio 3 — METRO ABANDONADO
        {
            "Nina se perdió aquí una vez.\nLa encontró dormida encima de un vagón.",
            "En estos túneles se escondieron\nel invierno que nevó sobre la ciudad.",
            "Nox le enseñó a Nina a caminar\nsobre los rieles sin mirar abajo.",
        },
        // Barrio 4 — PUERTO INDUSTRIAL
        {
            "Colmillo le ofreció trabajo en este muelle.\nNox escupió al suelo y se fue.",
            "El primer perro que lo mordió fue aquí.\nTenía dos años y aprendió rápido.",
            "Mitsy le pidió que se fueran de la ciudad.\nÉl dijo que mañana. Nunca hubo mañana.",
        },
        // Barrio 5 — LAS AZOTEAS
        {
            "Desde aquí veían amanecer.\nLos cuatro, sentados en el borde.",
            "Will preguntó si las estrellas\neran de otros gatos. Nox dijo que sí.",
            "La noche del incendio, el humo\nse vio desde estas azoteas.",
        },
        // Barrio 6 — LA MANSIÓN DE COLMILLO
        {
            "Colmillo guarda los collares de todos\nlos que no quisieron pagarle.",
            "Hay un retrato de Colmillo de cachorro.\nTambién él tuvo miedo alguna vez.",
            "En un cajón, la foto que le arrancaron\na Nox de su familia. Está intacta.",
        },
    };

    private Memories() { }

    public static String text(int level, int index) {
        int l = Math.max(1, Math.min(6, level)) - 1;
        int i = Math.max(0, Math.min(PER_LEVEL - 1, index));
        return TEXTS[l][i];
    }

    public static String title(int level, int index) {
        return "RECUERDO " + (index + 1) + " DE " + PER_LEVEL;
    }
}
