package com.compilador.ast;

/**
 * Una instrucción del código intermedio de 3 direcciones (cuádrupla: operación, arg1, arg2, resultado).
 *
 * Formato: OPERACION arg1, arg2, resultado
 *
 *   LOAD    c|x, Ti         Ti <- constante c o variable x   (true = 1, false = 0)
 *   ADD     Ta, Tb, Tr      Tr <- Ta + Tb
 *   SUB     Ta, Tb, Tr      Tr <- Ta - Tb
 *   MUL     Ta, Tb, Tr      Tr <- Ta * Tb
 *   LT      Ta, Tb, Tr      Tr <- (Ta <  Tb) ? 1 : 0
 *   GT      Ta, Tb, Tr      Tr <- (Ta >  Tb) ? 1 : 0
 *   EQ      Ta, Tb, Tr      Tr <- (Ta == Tb) ? 1 : 0
 *   AND     Ta, Tb, Tr      Tr <- Ta && Tb
 *   OR      Ta, Tb, Tr      Tr <- Ta || Tb
 *   NOT     Ta, Tr          Tr <- !Ta
 *   STORE   Ti, x           x  <- Ti
 *   LABEL   L               define la etiqueta L (se imprime "L:")
 *   GOTO    L               salto incondicional a L
 *   IFFALSE Ti, L           si Ti == 0 (false) salta a L
 *   RETURN  Ti              retorna Ti y termina main
 *   RETURN                  termina main (sin valor)
 */
public class Instruccion3D {
    public final String operacion;
    public final String arg1;
    public final String arg2;
    public final String resultado;

    public Instruccion3D(String operacion, String arg1, String arg2, String resultado) {
        this.operacion = operacion;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
    }

    public boolean esEtiqueta() {
        return operacion.equals("LABEL");
    }

    /** Las etiquetas van pegadas al margen; el resto de las instrucciones, con sangría. */
    public String formatear() {
        return esEtiqueta() ? toString() : "    " + toString();
    }

    @Override
    public String toString() {
        if (esEtiqueta()) return resultado + ":";
        StringBuilder sb = new StringBuilder(String.format("%-8s", operacion));
        String sep = "";
        for (String a : new String[]{arg1, arg2, resultado}) {
            if (a != null) { sb.append(sep).append(a); sep = ", "; }
        }
        return sb.toString().trim();
    }
}
