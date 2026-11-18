package com.compilador.ast;

import com.compilador.ast.EstructuraAST.NodoDeclaracion;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Traductor de código de 3 DIRECCIONES  ->  assembler x86-64 de 2 DIRECCIONES (sintaxis AT&T / GNU as).
 *
 * En 3 direcciones una instrucción tiene dos operandos y un destino separado:
 *      ADD T0, T1, T2          (T2 = T0 + T1)
 * En x86-64 las instrucciones tienen 2 direcciones: el destino también es un operando:
 *      addq %rbx, %rax         (rax = rax + rbx)
 * Además x86 no permite operaciones memoria-memoria, así que se usa %rax como acumulador.
 *
 * Ubicación de los datos:
 *   - Variables del programa  -> sección .bss (8 bytes c/u, inicializadas en 0):  v_x(%rip)
 *     (se les agrega el prefijo "v_" para que nunca choquen con nombres reservados del ensamblador)
 *   - Temporales T0, T1, ...  -> pila de main (marco de %rbp):  Ti = -8*(i+1)(%rbp)
 *   - Booleanos               -> 1 = true, 0 = false
 *
 * Reglas de traducción (una instrucción 3D -> una o varias de 2D):
 *   LOAD c, Ti        ->  movq $c, Ti
 *   LOAD x, Ti        ->  movq v_x(%rip), %rax ; movq %rax, Ti
 *   ADD  Ta, Tb, Tr   ->  movq Ta, %rax ; addq  Tb, %rax ; movq %rax, Tr
 *   SUB  Ta, Tb, Tr   ->  movq Ta, %rax ; subq  Tb, %rax ; movq %rax, Tr
 *   MUL  Ta, Tb, Tr   ->  movq Ta, %rax ; imulq Tb, %rax ; movq %rax, Tr
 *   AND  Ta, Tb, Tr   ->  movq Ta, %rax ; andq  Tb, %rax ; movq %rax, Tr
 *   OR   Ta, Tb, Tr   ->  movq Ta, %rax ; orq   Tb, %rax ; movq %rax, Tr
 *   LT|GT|EQ Ta,Tb,Tr ->  movq Ta, %rax ; cmpq Tb, %rax ; setl|setg|sete %al ; movzbq %al, %rax ; movq %rax, Tr
 *   NOT  Ta, Tr       ->  movq Ta, %rax ; xorq $1, %rax ; movq %rax, Tr
 *   STORE Ti, x       ->  movq Ti, %rax ; movq %rax, v_x(%rip)
 *   LABEL L           ->  .L:
 *   GOTO  L           ->  jmp .L
 *   IFFALSE Ti, L     ->  cmpq $0, Ti ; je .L
 *   RETURN Ti         ->  movq Ti, %rax ; jmp .Lfin_main
 *   RETURN            ->  movq $0, %rax ; jmp .Lfin_main
 *
 * El valor de retorno de main queda en %rax, que el sistema operativo toma como código de salida.
 */
public class TraductorAssembly2D {
    private static final String ETIQUETA_FIN = ".Lfin_main";
    private static final String PREFIJO_VARIABLE = "v_";

    private final List<NodoDeclaracion> variables;
    private final List<Instruccion3D> codigo3D;
    private final int cantidadTemporales;
    private final List<String> lineas = new ArrayList<>();

    public TraductorAssembly2D(List<NodoDeclaracion> variables, GeneradorCodigo codigo3D) {
        this.variables = variables;
        this.codigo3D = codigo3D.getInstrucciones();
        this.cantidadTemporales = codigo3D.getCantidadTemporales();
    }

    public List<String> traducir() {
        lineas.clear();
        comentario("Assembler x86-64 de 2 direcciones (sintaxis AT&T) generado a partir");
        comentario("del código de 3 direcciones. Compilar con:  gcc programa.s -o programa");
        emitirDirectiva(".global main");

        // ---- Variables globales ----
        if (!variables.isEmpty()) {
            emitirDirectiva(".bss");
            emitirDirectiva(".p2align 3");
            for (NodoDeclaracion d : variables) {
                lineas.add(String.format("%-28s# %s %s", PREFIJO_VARIABLE + d.id + ": .space 8", d.tipoVar, d.id));
            }
        }

        // ---- Prólogo de main ----
        emitirDirectiva(".text");
        lineas.add("main:");
        instr("pushq %rbp", "guardar el marco anterior");
        instr("movq %rsp, %rbp", "nuevo marco de pila");
        int bytesTemporales = ((cantidadTemporales * 8) + 15) / 16 * 16; // alineado a 16 bytes
        if (bytesTemporales > 0) {
            instr("subq $" + bytesTemporales + ", %rsp", "espacio para " + cantidadTemporales + " temporales");
        }

        // ---- Cuerpo: traducción instrucción por instrucción ----
        for (Instruccion3D i : codigo3D) {
            if (i.esEtiqueta()) {
                lineas.add("");
                lineas.add(etiqueta(i.resultado) + ":");
                continue;
            }
            lineas.add("");
            comentario("3D: " + i);
            traducirInstruccion(i);
        }

        // ---- Epílogo de main ----
        lineas.add("");
        instr("movq $0, %rax", "si no hubo return, main devuelve 0");
        lineas.add(ETIQUETA_FIN + ":");
        instr("movq %rbp, %rsp", "liberar temporales");
        instr("popq %rbp", "restaurar el marco anterior");
        instr("ret", "volver al sistema (rax = valor de retorno)");
        return lineas;
    }

    private void traducirInstruccion(Instruccion3D i) {
        switch (i.operacion) {
            case "LOAD":
                if (esConstante(i.arg1)) {
                    instr("movq $" + i.arg1 + ", " + temporal(i.resultado), null);
                } else {
                    instr("movq " + variable(i.arg1) + ", %rax", null);
                    instr("movq %rax, " + temporal(i.resultado), null);
                }
                break;
            case "ADD":
            case "SUB":
            case "MUL":
            case "AND":
            case "OR":
                instr("movq " + temporal(i.arg1) + ", %rax", null);
                instr(instruccionAritmetica(i.operacion) + " " + temporal(i.arg2) + ", %rax",
                        "rax = rax " + simbolo(i.operacion) + " " + i.arg2);
                instr("movq %rax, " + temporal(i.resultado), null);
                break;
            case "LT":
            case "GT":
            case "EQ":
                instr("movq " + temporal(i.arg1) + ", %rax", null);
                instr("cmpq " + temporal(i.arg2) + ", %rax", "compara " + i.arg1 + " con " + i.arg2);
                instr(instruccionSet(i.operacion) + " %al", "al = 1 si " + i.arg1 + " " + simbolo(i.operacion) + " " + i.arg2);
                instr("movzbq %al, %rax", "extiende al (8 bits) a rax (64 bits)");
                instr("movq %rax, " + temporal(i.resultado), null);
                break;
            case "NOT":
                instr("movq " + temporal(i.arg1) + ", %rax", null);
                instr("xorq $1, %rax", "1 -> 0 y 0 -> 1");
                instr("movq %rax, " + temporal(i.resultado), null);
                break;
            case "STORE":
                instr("movq " + temporal(i.arg1) + ", %rax", null);
                instr("movq %rax, " + variable(i.resultado), null);
                break;
            case "GOTO":
                instr("jmp " + etiqueta(i.resultado), null);
                break;
            case "IFFALSE":
                instr("cmpq $0, " + temporal(i.arg1), "¿la condición es false?");
                instr("je " + etiqueta(i.resultado), "si es false, salta");
                break;
            case "RETURN":
                if (i.arg1 != null) instr("movq " + temporal(i.arg1) + ", %rax", "valor de retorno en rax");
                else instr("movq $0, %rax", "return sin valor");
                instr("jmp " + ETIQUETA_FIN, null);
                break;
            default:
                throw new IllegalStateException("Instrucción 3D desconocida: " + i);
        }
    }

    private static String instruccionAritmetica(String op3D) {
        switch (op3D) {
            case "ADD": return "addq";
            case "SUB": return "subq";
            case "MUL": return "imulq";
            case "AND": return "andq";
            default:    return "orq";
        }
    }

    private static String instruccionSet(String op3D) {
        switch (op3D) {
            case "LT": return "setl";
            case "GT": return "setg";
            default:   return "sete";
        }
    }

    private static String simbolo(String op3D) {
        switch (op3D) {
            case "ADD": return "+";
            case "SUB": return "-";
            case "MUL": return "*";
            case "AND": return "&";
            case "OR":  return "|";
            case "LT":  return "<";
            case "GT":  return ">";
            default:    return "==";
        }
    }

    /** Ti -> posición en la pila. */
    private String temporal(String t) {
        int indice = Integer.parseInt(t.substring(1));
        return "-" + (8 * (indice + 1)) + "(%rbp)";
    }

    /** Variable global, direccionamiento relativo a rip. */
    private String variable(String nombre) {
        return PREFIJO_VARIABLE + nombre + "(%rip)";
    }

    /** Etiqueta local del ensamblador (las que empiezan con ".L" no se exportan). */
    private String etiqueta(String nombre3D) {
        return "." + nombre3D;
    }

    private boolean esConstante(String s) {
        return !s.isEmpty() && Character.isDigit(s.charAt(0));
    }

    private void instr(String instruccion, String comentario) {
        String linea = "    " + instruccion;
        if (comentario != null) linea = String.format("%-32s# %s", linea, comentario);
        lineas.add(linea);
    }

    private void emitirDirectiva(String d) { lineas.add("    " + d); }

    private void comentario(String c) { lineas.add("    # " + c); }

    public void guardar(String archivo) throws IOException {
        if (lineas.isEmpty()) traducir();
        try (PrintWriter out = new PrintWriter(new FileWriter(archivo))) {
            for (String l : lineas) out.print(l + "\n"); // saltos de línea Unix: sirve en Windows y Linux
        }
    }
}
