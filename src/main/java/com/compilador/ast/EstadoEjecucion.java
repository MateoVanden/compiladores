package com.compilador.ast;

/**
 * Estado del intérprete mientras recorre el AST: valor retornado por main
 * y la traza de ejecución que se muestra por pantalla.
 */
public class EstadoEjecucion {
    /** Límite de vueltas de un while (para detectar bucles infinitos). */
    public static final int MAX_ITERACIONES = 1_000_000;
    /** Máximo de líneas de traza a mostrar (un while largo podría imprimir miles). */
    public static final int MAX_LINEAS_TRAZA = 150;

    public Object valorRetornado;
    public boolean huboReturn;
    private int lineasTraza;
    private final boolean mostrarTraza;

    public EstadoEjecucion(boolean mostrarTraza) {
        this.mostrarTraza = mostrarTraza;
    }

    public void traza(String linea) {
        if (!mostrarTraza) return;
        lineasTraza++;
        if (lineasTraza <= MAX_LINEAS_TRAZA) {
            System.out.println(linea);
        } else if (lineasTraza == MAX_LINEAS_TRAZA + 1) {
            System.out.println("  ... (traza truncada, la ejecución continúa)");
        }
    }
}
