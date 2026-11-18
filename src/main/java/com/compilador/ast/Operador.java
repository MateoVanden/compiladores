package com.compilador.ast;

/**
 * Operadores del lenguaje con la información que necesitan las distintas etapas:
 *   - simbolo     : cómo se escribe en el código fuente (para imprimir el AST y los errores)
 *   - mnemonico3D : instrucción del código de 3 direcciones que lo implementa
 *   - operandos   : tipo que deben tener los operandos (null = ambos del mismo tipo, cualquiera)
 *   - resultado   : tipo del valor que produce
 *
 * Reglas de tipos:
 *   +  -  *        int  x int  -> int
 *   <  >           int  x int  -> bool
 *   ==             T    x T    -> bool   (T = int o bool, ambos iguales)
 *   && ||          bool x bool -> bool
 *   !              bool        -> bool
 */
public enum Operador {
    SUMA          ("+",  "ADD", TipoDato.INT,  TipoDato.INT),
    RESTA         ("-",  "SUB", TipoDato.INT,  TipoDato.INT),
    MULTIPLICACION("*",  "MUL", TipoDato.INT,  TipoDato.INT),
    MENOR         ("<",  "LT",  TipoDato.INT,  TipoDato.BOOL),
    MAYOR         (">",  "GT",  TipoDato.INT,  TipoDato.BOOL),
    IGUAL         ("==", "EQ",  null,          TipoDato.BOOL),
    AND           ("&&", "AND", TipoDato.BOOL, TipoDato.BOOL),
    OR            ("||", "OR",  TipoDato.BOOL, TipoDato.BOOL),
    NOT           ("!",  "NOT", TipoDato.BOOL, TipoDato.BOOL);

    public final String simbolo;
    public final String mnemonico3D;
    public final TipoDato operandos;
    public final TipoDato resultado;

    Operador(String simbolo, String mnemonico3D, TipoDato operandos, TipoDato resultado) {
        this.simbolo = simbolo;
        this.mnemonico3D = mnemonico3D;
        this.operandos = operandos;
        this.resultado = resultado;
    }

    /**
     * Aplica el operador binario a dos valores ya evaluados (lo usa el intérprete).
     * Las operaciones enteras detectan desbordamiento (overflow) en lugar de
     * dar un resultado incorrecto en silencio.
     */
    public Object aplicar(Object a, Object b) {
        switch (this) {
            case SUMA:           return Math.addExact((Integer) a, (Integer) b);
            case RESTA:          return Math.subtractExact((Integer) a, (Integer) b);
            case MULTIPLICACION: return Math.multiplyExact((Integer) a, (Integer) b);
            case MENOR:          return (Integer) a < (Integer) b;
            case MAYOR:          return (Integer) a > (Integer) b;
            case IGUAL:          return a.equals(b);
            case AND:            return (Boolean) a && (Boolean) b;
            case OR:             return (Boolean) a || (Boolean) b;
            default: throw new IllegalStateException(this + " no es un operador binario");
        }
    }
}
