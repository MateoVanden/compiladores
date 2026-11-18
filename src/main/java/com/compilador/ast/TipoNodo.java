package com.compilador.ast;

/**
 * Clase de cada nodo del AST (su "estructura"). Se muestra al imprimir el árbol.
 * Se separa de TipoDato: una cosa es QUÉ nodo es (ej: OPERACION_BINARIA) y otra
 * el TIPO del valor que produce (ej: INT).
 */
public enum TipoNodo {
    PROGRAMA,
    DECLARACION,
    // Sentencias
    ASIGNACION,
    RETURN,
    IF,
    WHILE,
    // Expresiones
    IDENTIFICADOR,
    NUMERO,
    BOOLEANO,
    OPERACION_BINARIA,
    OPERACION_UNARIA
}
