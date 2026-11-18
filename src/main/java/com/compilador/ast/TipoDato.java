package com.compilador.ast;

/**
 * Tipos de datos del lenguaje.
 *   INT  : variables, constantes y expresiones enteras.
 *   BOOL : variables, constantes (true/false) y expresiones lógicas.
 *   VOID : solo como tipo de retorno de main (no retorna valor).
 */
public enum TipoDato {
    INT,
    BOOL,
    VOID;

    /** Nombre tal como se escribe en el código fuente: int, bool, void. */
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
