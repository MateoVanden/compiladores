package com.compilador.ast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado que se va pasando por el AST durante el análisis semántico.
 *
 *   - tabla           : tabla de símbolos (variables declaradas)
 *   - tipoRetornoMain : para chequear las sentencias return
 *   - errores         : errores semánticos (impiden seguir con la compilación)
 *   - advertencias    : avisos que no impiden compilar (variable sin inicializar,
 *                       código inalcanzable, variable sin usar, main sin return)
 *   - inicializadas   : variables que SEGURO tienen un valor asignado en el punto
 *                       actual del programa (análisis de "asignación definida",
 *                       el mismo que hace Java con las variables locales).
 */
public class ContextoSemantico {
    public final TablaSimbolos tabla;
    public final TipoDato tipoRetornoMain;
    public final List<String> errores = new ArrayList<>();
    public final List<String> advertencias = new ArrayList<>();

    Set<String> inicializadas = new HashSet<>();
    private final Set<String> yaAdvertidas = new HashSet<>();

    public ContextoSemantico(TablaSimbolos tabla, TipoDato tipoRetornoMain) {
        this.tabla = tabla;
        this.tipoRetornoMain = tipoRetornoMain;
    }

    public void error(int linea, String mensaje) {
        errores.add("Línea " + linea + ": " + mensaje);
    }

    public void advertir(int linea, String mensaje) {
        advertencias.add("Línea " + linea + ": " + mensaje);
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    /** Avisa (una sola vez por variable) que se lee una variable que podría no tener valor asignado. */
    void advertirSinInicializar(int linea, Simbolo s) {
        if (yaAdvertidas.add(s.nombre)) {
            advertir(linea, "la variable '" + s.nombre + "' podría usarse sin haber sido inicializada"
                    + " (se toma su valor por defecto: " + (s.tipo == TipoDato.BOOL ? "false" : "0") + ").");
        }
    }

    /** Después de un return el código es inalcanzable: se consideran todas inicializadas para no dar avisos de más. */
    void marcarTodasInicializadas() {
        for (Simbolo s : tabla.getSimbolos()) inicializadas.add(s.nombre);
    }
}
