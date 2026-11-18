package com.compilador.ast;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tabla de símbolos: guarda la información de cada variable declarada
 * (nombre, tipo, línea de declaración, cantidad de usos y valor actual).
 * El lenguaje tiene un único ámbito (el cuerpo de main), por eso alcanza con un mapa.
 */
public class TablaSimbolos {
    // LinkedHashMap para mantener el orden de declaración
    private final Map<String, Simbolo> simbolos = new LinkedHashMap<>();

    /** Registra una variable. Devuelve false si ya estaba declarada. */
    public boolean declarar(String nombre, TipoDato tipo, int linea) {
        if (simbolos.containsKey(nombre)) return false;
        simbolos.put(nombre, new Simbolo(nombre, tipo, linea));
        return true;
    }

    /** Busca una variable. Devuelve null si no fue declarada. */
    public Simbolo buscar(String nombre) {
        return simbolos.get(nombre);
    }

    public Collection<Simbolo> getSimbolos() {
        return simbolos.values();
    }

    /** Vuelve todas las variables a su valor inicial (antes de interpretar). */
    public void reiniciarValores() {
        for (Simbolo s : simbolos.values()) s.reiniciar();
    }

    public void imprimir(boolean conValores) {
        String borde = "  +------------+--------+-------+-------+" + (conValores ? "---------+" : "");
        System.out.println(borde);
        System.out.println("  | Variable   | Tipo   | Línea | Usos  |" + (conValores ? " Valor   |" : ""));
        System.out.println(borde);
        if (simbolos.isEmpty()) {
            System.out.println("  | (no hay variables declaradas)" );
        }
        for (Simbolo s : simbolos.values()) {
            String fila = String.format("  | %-10s | %-6s | %5d | %5d |", s.nombre, s.tipo, s.lineaDeclaracion, s.usos);
            if (conValores) fila += String.format(" %-7s |", s.valor);
            System.out.println(fila);
        }
        System.out.println(borde);
    }
}
