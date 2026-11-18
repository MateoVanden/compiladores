package com.compilador;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.compilador.ast.ContextoSemantico;
import com.compilador.ast.ErrorEjecucion;
import com.compilador.ast.EstadoEjecucion;
import com.compilador.ast.EstructuraAST.NodoPrograma;
import com.compilador.ast.GeneradorCodigo;
import com.compilador.ast.TraductorAssembly2D;

/**
 * Mini compilador - Pre-Proyecto Compiladores 2026.
 *
 * Etapas:
 *   1. Análisis léxico (JFlex) y sintáctico (CUP)
 *   2. Análisis semántico + tabla de símbolos
 *   3. Impresión del AST (decorado con los tipos calculados en el paso anterior)
 *   4. Intérprete sobre el AST
 *   5. Código intermedio de 3 direcciones      -> programa_3d.txt
 *   6. Traducción a assembler x86-64 de 2 dir.  -> programa.s  (se ensambla con gcc)
 *
 * Uso: java com.compilador.Main [archivo_fuente] [--sin-traza]
 *      (por defecto compila codigo.txt)
 *
 * Código de salida del proceso: 0 si compiló bien, 1 si hubo algún error.
 */
public class Main {
    static final String ARCHIVO_3D = "programa_3d.txt";
    static final String ARCHIVO_ASM = "programa.s";

    public static void main(String[] args) {
        String rutaArchivo = "codigo.txt";
        boolean mostrarTraza = true;
        for (String a : args) {
            if (a.equals("--sin-traza")) mostrarTraza = false;
            else rutaArchivo = a;
        }
        System.exit(compilar(rutaArchivo, mostrarTraza) ? 0 : 1);
    }

    /** Ejecuta todas las etapas. Devuelve true si la compilación terminó sin errores. */
    public static boolean compilar(String rutaArchivo, boolean mostrarTraza) {
        // ---------------------------------------------------------------
        titulo("1. ANÁLISIS LÉXICO Y SINTÁCTICO");
        String fuente;
        try {
            fuente = Files.readString(Path.of(rutaArchivo));
        } catch (Exception e) {
            return abortar("no se pudo leer el archivo '" + rutaArchivo + "'.");
        }
        System.out.println("Archivo fuente: " + rutaArchivo + "\n");
        imprimirFuenteNumerado(fuente);
        System.out.println();

        NodoPrograma ast;
        Lexer lexer = new Lexer(new StringReader(fuente));
        parser p = new parser(lexer);
        try {
            ast = (NodoPrograma) p.parse().value;
        } catch (Exception e) {
            return abortar(e.getMessage());
        }
        int erroresSintaxis = lexer.erroresLexicos + p.erroresSintacticos;
        if (erroresSintaxis > 0) {
            String detalle = "";
            if (lexer.erroresLexicos > 0) detalle += lexer.erroresLexicos + " error(es) léxico(s)";
            if (lexer.erroresLexicos > 0 && p.erroresSintacticos > 0) detalle += " y ";
            if (p.erroresSintacticos > 0) detalle += p.erroresSintacticos + " error(es) sintáctico(s)";
            return abortar(detalle + ".");
        }
        System.out.println("[OK] El programa es léxica y sintácticamente correcto.");

        // ---------------------------------------------------------------
        // El análisis semántico se hace ANTES de imprimir el AST para poder
        // mostrar el árbol "decorado" con el tipo de cada expresión.
        ContextoSemantico semantica = ast.analizarSemantica();

        titulo("2. ÁRBOL SINTÁCTICO ABSTRACTO (AST decorado con tipos)");
        ast.imprimir("", "");

        // ---------------------------------------------------------------
        titulo("3. ANÁLISIS SEMÁNTICO Y TABLA DE SÍMBOLOS");
        ast.tabla.imprimir(false);
        for (String adv : semantica.advertencias) System.out.println("[ADVERTENCIA] " + adv);
        if (semantica.hayErrores()) {
            for (String err : semantica.errores) System.out.println("[ERROR SEMÁNTICO] " + err);
            return abortar(semantica.errores.size() + " error(es) semántico(s).");
        }
        System.out.println("[OK] Todas las variables están declaradas y los tipos son correctos.");

        // ---------------------------------------------------------------
        titulo("4. INTÉRPRETE (evaluación sobre el AST)");
        Object resultado = null;
        boolean errorEjecucion = false;
        try {
            resultado = ast.interpretar(new EstadoEjecucion(mostrarTraza));
        } catch (ErrorEjecucion e) {
            // El programa es válido (pasó el análisis semántico): el error es de la EJECUCIÓN,
            // así que se informa y se sigue generando código igual.
            System.out.println("[ERROR DE EJECUCIÓN] " + e.getMessage());
            System.out.println("El intérprete se detuvo. La generación de código continúa porque el programa es válido.");
            errorEjecucion = true;
        }
        System.out.println("\nEstado final de las variables:");
        ast.tabla.imprimir(true);
        if (!errorEjecucion) {
            System.out.println("Valor retornado por main: " + (resultado != null ? resultado : "(ninguno, main es void)"));
        }

        try {
            // -----------------------------------------------------------
            titulo("5. CÓDIGO DE 3 DIRECCIONES (seudo-assembly)");
            GeneradorCodigo gen = ast.generarCodigo3D();
            gen.imprimir();
            gen.guardar(ARCHIVO_3D);
            System.out.println("\n[OK] Guardado en '" + ARCHIVO_3D + "'.");

            // -----------------------------------------------------------
            titulo("6. ASSEMBLER x86-64 DE 2 DIRECCIONES (traducido del código 3D)");
            TraductorAssembly2D traductor = new TraductorAssembly2D(ast.declaraciones, gen);
            for (String linea : traductor.traducir()) System.out.println(linea);
            traductor.guardar(ARCHIVO_ASM);
            System.out.println("\n[OK] Guardado en '" + ARCHIVO_ASM + "'.");
        } catch (Exception e) {
            return abortar("no se pudieron escribir los archivos de salida: " + e.getMessage());
        }

        titulo("COMPILACIÓN TERMINADA");
        if (errorEjecucion) {
            System.out.println("Atención: el intérprete terminó con error, el ejecutable puede no comportarse como se espera.");
        } else if (resultado instanceof Boolean) {
            System.out.println("Resultado esperado del ejecutable: " + ((Boolean) resultado ? 1 : 0)
                    + "  (bool: true = 1, false = 0)");
        } else {
            System.out.println("Resultado esperado del ejecutable: " + (resultado != null ? resultado : 0));
        }
        System.out.println("Para ensamblar y ejecutar con gcc:");
        System.out.println("   gcc " + ARCHIVO_ASM + " -o programa.exe");
        System.out.println("   .\\programa.exe");
        System.out.println("   echo $LASTEXITCODE        (PowerShell)  ->  valor retornado por main");
        System.out.println("o simplemente:  .\\ejecutar_gcc.bat");
        return true;
    }

    private static void imprimirFuenteNumerado(String fuente) {
        String[] lineas = fuente.stripTrailing().split("\\R");
        for (int i = 0; i < lineas.length; i++) {
            System.out.printf("%4d | %s%n", i + 1, lineas[i]);
        }
    }

    private static void titulo(String texto) {
        System.out.println("\n==================================================================");
        System.out.println("  " + texto);
        System.out.println("==================================================================");
    }

    private static boolean abortar(String motivo) {
        System.out.println("\n[COMPILACIÓN ABORTADA] " + motivo);
        return false;
    }
}
