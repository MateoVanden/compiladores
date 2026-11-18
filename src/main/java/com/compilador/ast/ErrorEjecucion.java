package com.compilador.ast;

/**
 * Error detectado en tiempo de ejecución por el intérprete
 * (desbordamiento de enteros o posible bucle infinito).
 */
public class ErrorEjecucion extends RuntimeException {
    public ErrorEjecucion(int linea, String mensaje) {
        super("Línea " + linea + ": " + mensaje);
    }
}
