package com.compilador.ast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nodos del AST (Árbol Sintáctico Abstracto).
 *
 * Cada nodo sabe:
 *   - imprimirse                      (Punto 5 - AST)
 *   - verificarse semánticamente      (tabla de símbolos + chequeo de tipos)
 *   - evaluarse / ejecutarse          (Punto 6 - intérprete)
 *   - generar código de 3 direcciones (Punto 7 - seudo-assembly)
 *
 * Es decir, se usa el patrón "Interpreter": cada etapa del compilador es un método
 * recursivo que recorre el árbol. El assembler x86-64 de 2 direcciones NO se genera
 * desde el AST, sino traduciendo el código de 3 direcciones (ver TraductorAssembly2D).
 *
 * Jerarquía:
 *   NodoAST
 *   ├── NodoPrograma
 *   ├── NodoDeclaracion
 *   ├── NodoSentencia:  Asignacion, Return, If, While
 *   └── NodoExpresion:  Identificador, Numero, Booleano, OperacionBinaria, OperacionUnaria
 */
public class EstructuraAST {

    public abstract static class NodoAST {
        public final TipoNodo tipoNodo;
        public final int linea;

        public NodoAST(TipoNodo tipoNodo, int linea) { this.tipoNodo = tipoNodo; this.linea = linea; }

        /**
         * Imprime el nodo como árbol.
         * @param prefijo      lo que se imprime antes de este nodo (ej: "│   ├── ")
         * @param prefijoHijos sangría que heredan sus hijos (ej: "│   │   ")
         */
        public abstract void imprimir(String prefijo, String prefijoHijos);

        /** Imprime un hijo: si es el último usa └──, si no ├──. */
        protected static void imprimirHijo(NodoAST hijo, String prefijoHijos, boolean ultimo) {
            hijo.imprimir(prefijoHijos + (ultimo ? "└── " : "├── "), prefijoHijos + (ultimo ? "    " : "│   "));
        }

        /** Imprime un "grupo" con título (ej: DECLARACIONES, Entonces, Sino) y sus hijos debajo. */
        protected static void imprimirGrupo(String titulo, List<? extends NodoAST> hijos, String prefijoHijos, boolean ultimo) {
            System.out.println(prefijoHijos + (ultimo ? "└── " : "├── ") + titulo);
            String h = prefijoHijos + (ultimo ? "    " : "│   ");
            if (hijos.isEmpty()) System.out.println(h + "└── (vacío)");
            for (int i = 0; i < hijos.size(); i++) imprimirHijo(hijos.get(i), h, i == hijos.size() - 1);
        }

        protected String estructura() { return "[Estructura: " + tipoNodo + "]"; }
    }

    // =====================================================================
    // EXPRESIONES
    // =====================================================================
    public abstract static class NodoExpresion extends NodoAST {
        /** Tipo de la expresión (INT/BOOL). Lo completa el análisis semántico; null si hubo error. */
        public TipoDato tipo;

        public NodoExpresion(TipoNodo tipoNodo, int linea) { super(tipoNodo, linea); }

        /** Chequeo semántico. Decora el nodo con su tipo y lo devuelve (null si hay error). */
        public TipoDato verificar(ContextoSemantico ctx) {
            tipo = calcularTipo(ctx);
            return tipo;
        }

        protected abstract TipoDato calcularTipo(ContextoSemantico ctx);

        /** Intérprete: devuelve Integer o Boolean. */
        public abstract Object evaluar();

        /** Emite código de 3 direcciones y devuelve el temporal donde queda el resultado. */
        public abstract String generarCodigo3D(GeneradorCodigo gen);

        @Override
        protected String estructura() {
            return "[Estructura: " + tipoNodo + ", Tipo: " + (tipo != null ? tipo : "?") + "]";
        }
    }

    public static class Identificador extends NodoExpresion {
        public final String nombre;
        public Simbolo simbolo; // referencia a la tabla de símbolos (se enlaza en verificar)

        public Identificador(String nombre, int linea) {
            super(TipoNodo.IDENTIFICADOR, linea);
            this.nombre = nombre;
        }

        public void imprimir(String s, String h) { System.out.println(s + "Variable: " + nombre + " " + estructura()); }

        protected TipoDato calcularTipo(ContextoSemantico ctx) {
            simbolo = ctx.tabla.buscar(nombre);
            if (simbolo == null) {
                ctx.error(linea, "uso de variable no declarada '" + nombre + "'.");
                return null;
            }
            simbolo.usos++;
            if (!ctx.inicializadas.contains(nombre)) ctx.advertirSinInicializar(linea, simbolo);
            return simbolo.tipo;
        }

        public Object evaluar() { return simbolo.valor; }

        public String generarCodigo3D(GeneradorCodigo gen) {
            String temp = gen.nuevoTemporal();
            gen.emitir("LOAD", nombre, null, temp);
            return temp;
        }
    }

    public static class Numero extends NodoExpresion {
        public final String valor;

        public Numero(String valor, int linea) {
            super(TipoNodo.NUMERO, linea);
            this.valor = valor;
        }

        public void imprimir(String s, String h) { System.out.println(s + "Numero: " + valor + " " + estructura()); }

        protected TipoDato calcularTipo(ContextoSemantico ctx) {
            try {
                Integer.parseInt(valor);
            } catch (NumberFormatException e) {
                ctx.error(linea, "la constante " + valor + " está fuera del rango de int (máximo " + Integer.MAX_VALUE + ").");
                return null;
            }
            return TipoDato.INT;
        }

        public Object evaluar() { return Integer.parseInt(valor); }

        public String generarCodigo3D(GeneradorCodigo gen) {
            String temp = gen.nuevoTemporal();
            gen.emitir("LOAD", valor, null, temp);
            return temp;
        }
    }

    public static class Booleano extends NodoExpresion {
        public final boolean valor;

        public Booleano(boolean valor, int linea) {
            super(TipoNodo.BOOLEANO, linea);
            this.valor = valor;
        }

        public void imprimir(String s, String h) { System.out.println(s + "Booleano: " + valor + " " + estructura()); }

        protected TipoDato calcularTipo(ContextoSemantico ctx) { return TipoDato.BOOL; }

        public Object evaluar() { return valor; }

        public String generarCodigo3D(GeneradorCodigo gen) {
            String temp = gen.nuevoTemporal();
            gen.emitir("LOAD", valor ? "1" : "0", null, temp); // true = 1, false = 0
            return temp;
        }
    }

    /** Operación con dos operandos: + - * < > == && || */
    public static class OperacionBinaria extends NodoExpresion {
        public final NodoExpresion izq;
        public final Operador operador;
        public final NodoExpresion der;

        public OperacionBinaria(NodoExpresion izq, Operador operador, NodoExpresion der, int linea) {
            super(TipoNodo.OPERACION_BINARIA, linea);
            this.izq = izq; this.operador = operador; this.der = der;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "Operacion: " + operador + " (" + operador.simbolo + ") " + estructura());
            imprimirHijo(izq, h, false);
            imprimirHijo(der, h, true);
        }

        protected TipoDato calcularTipo(ContextoSemantico ctx) {
            TipoDato tIzq = izq.verificar(ctx);
            TipoDato tDer = der.verificar(ctx);
            if (tIzq == null || tDer == null) return null; // el error ya se informó más abajo en el árbol

            if (operador.operandos == null) { // == : ambos operandos del mismo tipo
                if (tIzq != tDer) {
                    ctx.error(linea, "el operador '" + operador.simbolo + "' compara valores del mismo tipo"
                            + " (se encontró " + tIzq + " " + operador.simbolo + " " + tDer + ").");
                    return null;
                }
            } else if (tIzq != operador.operandos || tDer != operador.operandos) {
                ctx.error(linea, "el operador '" + operador.simbolo + "' solo acepta operandos " + operador.operandos
                        + " (se encontró " + tIzq + " " + operador.simbolo + " " + tDer + ").");
                return null;
            }
            return operador.resultado;
        }

        public Object evaluar() {
            // && y || se evalúan en cortocircuito, como en C/Java
            if (operador == Operador.AND) return (Boolean) izq.evaluar() && (Boolean) der.evaluar();
            if (operador == Operador.OR)  return (Boolean) izq.evaluar() || (Boolean) der.evaluar();
            try {
                return operador.aplicar(izq.evaluar(), der.evaluar());
            } catch (ArithmeticException e) {
                throw new ErrorEjecucion(linea, "desbordamiento de enteros en la operación '" + operador.simbolo + "'.");
            }
        }

        public String generarCodigo3D(GeneradorCodigo gen) {
            String regIzq = izq.generarCodigo3D(gen);
            String regDer = der.generarCodigo3D(gen);
            String resultado = gen.nuevoTemporal();
            gen.emitir(operador.mnemonico3D, regIzq, regDer, resultado);
            return resultado;
        }
    }

    /** Operación con un operando: ! */
    public static class OperacionUnaria extends NodoExpresion {
        public final Operador operador;
        public final NodoExpresion operando;

        public OperacionUnaria(Operador operador, NodoExpresion operando, int linea) {
            super(TipoNodo.OPERACION_UNARIA, linea);
            this.operador = operador; this.operando = operando;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "Operacion: " + operador + " (" + operador.simbolo + ") " + estructura());
            imprimirHijo(operando, h, true);
        }

        protected TipoDato calcularTipo(ContextoSemantico ctx) {
            TipoDato t = operando.verificar(ctx);
            if (t == null) return null;
            if (t != operador.operandos) {
                ctx.error(linea, "el operador '" + operador.simbolo + "' solo acepta un operando "
                        + operador.operandos + " (se encontró " + t + ").");
                return null;
            }
            return operador.resultado;
        }

        public Object evaluar() { return !(Boolean) operando.evaluar(); }

        public String generarCodigo3D(GeneradorCodigo gen) {
            String reg = operando.generarCodigo3D(gen);
            String resultado = gen.nuevoTemporal();
            gen.emitir(operador.mnemonico3D, reg, null, resultado);
            return resultado;
        }
    }

    // =====================================================================
    // SENTENCIAS
    // =====================================================================
    public abstract static class NodoSentencia extends NodoAST {
        public NodoSentencia(TipoNodo tipoNodo, int linea) { super(tipoNodo, linea); }

        public abstract void verificar(ContextoSemantico ctx);

        /** true si la sentencia termina SIEMPRE con un return (por cualquier camino). */
        public boolean siempreRetorna() { return false; }

        /** Ejecuta la sentencia. Devuelve true si se ejecutó un return (el programa termina). */
        public abstract boolean ejecutar(EstadoEjecucion estado);

        public abstract void generarCodigo3D(GeneradorCodigo gen);

        // ---------- Operaciones sobre bloques (listas de sentencias) ----------

        /**
         * Verifica un bloque. Avisa si hay código inalcanzable (sentencias después de
         * un return). Devuelve true si el bloque termina siempre con return.
         */
        public static boolean verificarBloque(List<NodoSentencia> bloque, ContextoSemantico ctx) {
            boolean retorna = false;
            boolean advertido = false;
            for (NodoSentencia s : bloque) {
                if (retorna && !advertido) {
                    // Primera sentencia inalcanzable: se avisa una sola vez por bloque.
                    advertido = true;
                    ctx.advertir(s.linea, "código inalcanzable: hay un return antes, esta sentencia"
                            + " y las siguientes del bloque nunca se ejecutan.");
                    ctx.marcarTodasInicializadas();
                }
                s.verificar(ctx); // se verifica igual, para informar errores de tipos/declaración
                if (s.siempreRetorna()) retorna = true;
            }
            return retorna;
        }

        public static boolean bloqueRetorna(List<NodoSentencia> bloque) {
            for (NodoSentencia s : bloque) if (s.siempreRetorna()) return true;
            return false;
        }

        public static boolean ejecutarBloque(List<NodoSentencia> bloque, EstadoEjecucion estado) {
            for (NodoSentencia s : bloque) if (s.ejecutar(estado)) return true;
            return false;
        }

        public static void generarBloque(List<NodoSentencia> bloque, GeneradorCodigo gen) {
            for (NodoSentencia s : bloque) s.generarCodigo3D(gen);
        }
    }

    public static class Asignacion extends NodoSentencia {
        public final String id;
        public final NodoExpresion valor;
        public Simbolo simbolo; // referencia a la tabla de símbolos

        public Asignacion(String id, NodoExpresion valor, int linea) {
            super(TipoNodo.ASIGNACION, linea);
            this.id = id; this.valor = valor;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "Asignacion a '" + id + "' " + estructura());
            imprimirHijo(valor, h, true);
        }

        public void verificar(ContextoSemantico ctx) {
            simbolo = ctx.tabla.buscar(id);
            if (simbolo == null) ctx.error(linea, "asignación a variable no declarada '" + id + "'.");
            TipoDato tValor = valor.verificar(ctx); // primero el lado derecho: en "x = x + 1" x se lee antes de asignarse
            if (simbolo != null && tValor != null && tValor != simbolo.tipo) {
                ctx.error(linea, "no se puede asignar un valor " + tValor + " a la variable '"
                        + id + "' de tipo " + simbolo.tipo + ".");
            }
            ctx.inicializadas.add(id);
        }

        public boolean ejecutar(EstadoEjecucion estado) {
            simbolo.valor = valor.evaluar();
            estado.traza("  [línea " + linea + "] " + id + " = " + simbolo.valor);
            return false;
        }

        public void generarCodigo3D(GeneradorCodigo gen) {
            String regValor = valor.generarCodigo3D(gen);
            gen.emitir("STORE", regValor, null, id);
        }
    }

    public static class Return extends NodoSentencia {
        public final NodoExpresion valor;      // null si es "return;"

        public Return(NodoExpresion valor, int linea) {
            super(TipoNodo.RETURN, linea);
            this.valor = valor;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "Return " + estructura());
            if (valor != null) imprimirHijo(valor, h, true);
        }

        public void verificar(ContextoSemantico ctx) {
            TipoDato t = (valor != null) ? valor.verificar(ctx) : null;
            TipoDato esperado = ctx.tipoRetornoMain;
            if (esperado == TipoDato.VOID && valor != null) {
                ctx.error(linea, "main es void y no puede retornar un valor.");
            } else if (esperado != TipoDato.VOID && valor == null) {
                ctx.error(linea, "main debe retornar un valor de tipo " + esperado + ".");
            } else if (t != null && t != esperado) {
                ctx.error(linea, "main retorna " + esperado + " pero se intenta retornar un " + t + ".");
            }
        }

        @Override
        public boolean siempreRetorna() { return true; }

        public boolean ejecutar(EstadoEjecucion estado) {
            estado.valorRetornado = (valor != null) ? valor.evaluar() : null;
            estado.huboReturn = true;
            estado.traza("  [línea " + linea + "] return" + (estado.valorRetornado != null ? " " + estado.valorRetornado : "")
                    + "   (fin de main)");
            return true;
        }

        public void generarCodigo3D(GeneradorCodigo gen) {
            if (valor != null) {
                String regValor = valor.generarCodigo3D(gen);
                gen.emitir("RETURN", regValor, null, null);
            } else {
                gen.emitir("RETURN", null, null, null);
            }
        }
    }

    /** if (condicion) { entonces } [else { sino }] */
    public static class If extends NodoSentencia {
        public final NodoExpresion condicion;
        public final List<NodoSentencia> entonces;
        public final List<NodoSentencia> sino;   // null si no tiene else

        public If(NodoExpresion condicion, List<NodoSentencia> entonces, List<NodoSentencia> sino, int linea) {
            super(TipoNodo.IF, linea);
            this.condicion = condicion; this.entonces = entonces; this.sino = sino;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "If " + estructura());
            imprimirGrupo("Condicion", List.of(condicion), h, false);
            imprimirGrupo("Entonces", entonces, h, sino == null);
            if (sino != null) imprimirGrupo("Sino", sino, h, true);
        }

        public void verificar(ContextoSemantico ctx) {
            TipoDato t = condicion.verificar(ctx);
            if (t != null && t != TipoDato.BOOL) {
                ctx.error(linea, "la condición del if debe ser bool (se encontró " + t + ").");
            }
            // Asignación definida: después del if, una variable está inicializada solo si
            // lo está por AMBAS ramas (o por la rama que no termina en return).
            Set<String> antes = new HashSet<>(ctx.inicializadas);
            boolean retornaEntonces = verificarBloque(entonces, ctx);
            Set<String> trasEntonces = ctx.inicializadas;

            ctx.inicializadas = new HashSet<>(antes);
            if (sino == null) return; // si no entra al if, quedan las de antes

            boolean retornaSino = verificarBloque(sino, ctx);
            Set<String> trasSino = ctx.inicializadas;
            if (retornaEntonces && !retornaSino)      ctx.inicializadas = trasSino;
            else if (retornaSino && !retornaEntonces) ctx.inicializadas = trasEntonces;
            else { trasEntonces.retainAll(trasSino);  ctx.inicializadas = trasEntonces; }
        }

        @Override
        public boolean siempreRetorna() {
            return sino != null && bloqueRetorna(entonces) && bloqueRetorna(sino);
        }

        public boolean ejecutar(EstadoEjecucion estado) {
            boolean c = (Boolean) condicion.evaluar();
            estado.traza("  [línea " + linea + "] if (" + c + ") -> "
                    + (c ? "ejecuta el bloque" : (sino != null ? "ejecuta el else" : "no entra")));
            if (c) return ejecutarBloque(entonces, estado);
            if (sino != null) return ejecutarBloque(sino, estado);
            return false;
        }

        /*
         *      <condicion -> Tc>              <condicion -> Tc>
         *      IFFALSE Tc, Lsino              IFFALSE Tc, Lfin
         *      <entonces>                     <entonces>
         *      GOTO Lfin                    Lfin:
         *    Lsino:
         *      <sino>
         *    Lfin:
         */
        public void generarCodigo3D(GeneradorCodigo gen) {
            String regCond = condicion.generarCodigo3D(gen);
            String etiqFin = gen.nuevaEtiqueta();
            if (sino == null) {
                gen.emitir("IFFALSE", regCond, null, etiqFin);
                generarBloque(entonces, gen);
            } else {
                String etiqSino = gen.nuevaEtiqueta();
                gen.emitir("IFFALSE", regCond, null, etiqSino);
                generarBloque(entonces, gen);
                gen.emitir("GOTO", null, null, etiqFin);
                gen.emitir("LABEL", null, null, etiqSino);
                generarBloque(sino, gen);
            }
            gen.emitir("LABEL", null, null, etiqFin);
        }
    }

    /** while (condicion) { cuerpo } */
    public static class While extends NodoSentencia {
        public final NodoExpresion condicion;
        public final List<NodoSentencia> cuerpo;

        public While(NodoExpresion condicion, List<NodoSentencia> cuerpo, int linea) {
            super(TipoNodo.WHILE, linea);
            this.condicion = condicion; this.cuerpo = cuerpo;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "While " + estructura());
            imprimirGrupo("Condicion", List.of(condicion), h, false);
            imprimirGrupo("Cuerpo", cuerpo, h, true);
        }

        public void verificar(ContextoSemantico ctx) {
            TipoDato t = condicion.verificar(ctx);
            if (t != null && t != TipoDato.BOOL) {
                ctx.error(linea, "la condición del while debe ser bool (se encontró " + t + ").");
            }
            // El cuerpo puede no ejecutarse nunca: lo que se asigne adentro no cuenta después del while.
            Set<String> antes = new HashSet<>(ctx.inicializadas);
            verificarBloque(cuerpo, ctx);
            ctx.inicializadas = antes;
        }

        public boolean ejecutar(EstadoEjecucion estado) {
            int iteraciones = 0;
            while ((Boolean) condicion.evaluar()) {
                if (++iteraciones > EstadoEjecucion.MAX_ITERACIONES) {
                    throw new ErrorEjecucion(linea, "el while superó las " + EstadoEjecucion.MAX_ITERACIONES
                            + " iteraciones (posible bucle infinito).");
                }
                if (ejecutarBloque(cuerpo, estado)) return true;
            }
            estado.traza("  [línea " + linea + "] while: la condición es false, fin del ciclo ("
                    + iteraciones + " iteración/es)");
            return false;
        }

        /*
         *    Linicio:
         *      <condicion -> Tc>
         *      IFFALSE Tc, Lfin
         *      <cuerpo>
         *      GOTO Linicio
         *    Lfin:
         */
        public void generarCodigo3D(GeneradorCodigo gen) {
            String etiqInicio = gen.nuevaEtiqueta();
            String etiqFin = gen.nuevaEtiqueta();
            gen.emitir("LABEL", null, null, etiqInicio);
            String regCond = condicion.generarCodigo3D(gen);
            gen.emitir("IFFALSE", regCond, null, etiqFin);
            generarBloque(cuerpo, gen);
            gen.emitir("GOTO", null, null, etiqInicio);
            gen.emitir("LABEL", null, null, etiqFin);
        }
    }

    // =====================================================================
    // DECLARACIONES Y PROGRAMA
    // =====================================================================
    public static class NodoDeclaracion extends NodoAST {
        public final TipoDato tipoVar;
        public final String id;

        public NodoDeclaracion(TipoDato tipoVar, String id, int linea) {
            super(TipoNodo.DECLARACION, linea);
            this.tipoVar = tipoVar; this.id = id;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "Declaracion: " + tipoVar + " " + id + " " + estructura());
        }
    }

    public static class NodoPrograma extends NodoAST {
        public final TipoDato tipoRetorno;
        public final List<NodoDeclaracion> declaraciones;
        public final List<NodoSentencia> sentencias;
        public final int lineaFin;  // línea de la '}' que cierra main
        public TablaSimbolos tabla = new TablaSimbolos();

        public NodoPrograma(TipoDato tipoRetorno, List<NodoDeclaracion> declaraciones,
                            List<NodoSentencia> sentencias, int linea, int lineaFin) {
            super(TipoNodo.PROGRAMA, linea);
            this.tipoRetorno = tipoRetorno; this.declaraciones = declaraciones;
            this.sentencias = sentencias; this.lineaFin = lineaFin;
        }

        public void imprimir(String s, String h) {
            System.out.println(s + "PROGRAMA: " + tipoRetorno + " main() " + estructura());
            imprimirGrupo("DECLARACIONES", declaraciones, h, false);
            imprimirGrupo("SENTENCIAS", sentencias, h, true);
        }

        /**
         * Análisis semántico: arma la tabla de símbolos, enlaza los identificadores del AST
         * con sus símbolos, chequea tipos y decora las expresiones con su tipo.
         */
        public ContextoSemantico analizarSemantica() {
            tabla = new TablaSimbolos();
            ContextoSemantico ctx = new ContextoSemantico(tabla, tipoRetorno);

            for (NodoDeclaracion d : declaraciones) {
                if (!tabla.declarar(d.id, d.tipoVar, d.linea)) {
                    ctx.error(d.linea, "la variable '" + d.id + "' ya fue declarada en la línea "
                            + tabla.buscar(d.id).lineaDeclaracion + ".");
                }
            }

            boolean retorna = NodoSentencia.verificarBloque(sentencias, ctx);

            if (tipoRetorno != TipoDato.VOID && !retorna) {
                ctx.advertir(lineaFin, "main es de tipo " + tipoRetorno + " y puede terminar sin ejecutar un return"
                        + " (en ese caso retorna " + valorPorDefecto(tipoRetorno) + ").");
            }
            for (Simbolo s : tabla.getSimbolos()) {
                if (s.usos == 0) ctx.advertir(s.lineaDeclaracion, "la variable '" + s.nombre + "' se declara pero su valor nunca se usa.");
            }
            return ctx;
        }

        /**
         * Intérprete (Punto 6). Requiere haber pasado el análisis semántico sin errores.
         * Devuelve el valor retornado por main (null si main es void).
         */
        public Object interpretar(EstadoEjecucion estado) {
            tabla.reiniciarValores();
            boolean huboReturn = NodoSentencia.ejecutarBloque(sentencias, estado);
            if (!huboReturn && tipoRetorno != TipoDato.VOID) {
                estado.traza("  main terminó sin return: retorna el valor por defecto " + valorPorDefecto(tipoRetorno));
                return valorPorDefecto(tipoRetorno);
            }
            return estado.valorRetornado;
        }

        /** Genera el código intermedio de 3 direcciones (Punto 7). */
        public GeneradorCodigo generarCodigo3D() {
            GeneradorCodigo gen = new GeneradorCodigo();
            NodoSentencia.generarBloque(sentencias, gen);
            return gen;
        }

        private static Object valorPorDefecto(TipoDato t) {
            return t == TipoDato.BOOL ? Boolean.FALSE : Integer.valueOf(0);
        }
    }
}
