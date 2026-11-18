package com.compilador.ast;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador del código intermedio de 3 direcciones (Punto 7 - seudo-assembly).
 * Los nodos del AST le piden temporales y etiquetas nuevas y le emiten instrucciones.
 * El resultado (la lista de instrucciones) es la entrada del TraductorAssembly2D.
 */
public class GeneradorCodigo {
    private int contadorTemporales = 0;
    private int contadorEtiquetas = 0;
    private final List<Instruccion3D> instrucciones = new ArrayList<>();

    /** Devuelve T0, T1, T2... de forma secuencial. */
    public String nuevoTemporal() {
        return "T" + (contadorTemporales++);
    }

    /** Devuelve L0, L1, L2... (destinos de los saltos de if/while). */
    public String nuevaEtiqueta() {
        return "L" + (contadorEtiquetas++);
    }

    public void emitir(String operacion, String arg1, String arg2, String resultado) {
        instrucciones.add(new Instruccion3D(operacion, arg1, arg2, resultado));
    }

    public List<Instruccion3D> getInstrucciones() {
        return instrucciones;
    }

    public int getCantidadTemporales() {
        return contadorTemporales;
    }

    public void imprimir() {
        for (Instruccion3D i : instrucciones) System.out.println(i.formatear());
    }

    public void guardar(String archivo) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(archivo))) {
            out.print("# Código intermedio de 3 direcciones (seudo-assembly)\n");
            out.print("# Generado por el mini compilador - Pre-Proyecto Compiladores\n");
            for (Instruccion3D i : instrucciones) out.print(i.formatear() + "\n");
        }
    }
}
