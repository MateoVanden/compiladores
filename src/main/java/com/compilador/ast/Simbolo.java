package com.compilador.ast;

/**
 * Entrada de la tabla de símbolos: una variable declarada.
 * Los nodos Identificador y Asignacion del AST guardan una referencia a su Simbolo
 * (se enlazan durante el análisis semántico), así el intérprete accede directo al
 * valor sin volver a buscar por nombre.
 */
public class Simbolo {
    public final String nombre;
    public final TipoDato tipo;   // INT o BOOL
    public final int lineaDeclaracion;
    public Object valor;          // valor actual (lo usa el intérprete)
    public int usos;              // cantidad de veces que se lee la variable (para advertencias)

    public Simbolo(String nombre, TipoDato tipo, int lineaDeclaracion) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.lineaDeclaracion = lineaDeclaracion;
        reiniciar();
    }

    /**
     * Valor inicial por defecto: 0 para int, false para bool.
     * Coincide con el assembler, donde las variables viven en la sección .bss
     * (que el sistema operativo inicializa en cero).
     */
    public void reiniciar() {
        valor = (tipo == TipoDato.BOOL) ? Boolean.FALSE : Integer.valueOf(0);
    }
}
