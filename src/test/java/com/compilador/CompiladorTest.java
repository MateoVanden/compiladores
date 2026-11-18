package com.compilador;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.compilador.ast.ContextoSemantico;
import com.compilador.ast.ErrorEjecucion;
import com.compilador.ast.EstadoEjecucion;
import com.compilador.ast.EstructuraAST.NodoPrograma;
import com.compilador.ast.GeneradorCodigo;
import com.compilador.ast.TraductorAssembly2D;

/**
 * Pruebas automáticas del compilador: se ejecutan con  mvn test
 * Cubren cada etapa: léxico, sintáctico, semántico, intérprete y generación de código.
 */
class CompiladorTest {

    // ------------------------------------------------------------------ helpers

    /** Resultado del análisis léxico + sintáctico. */
    private record Analisis(NodoPrograma ast, int erroresLexicos, int erroresSintacticos) {}

    private static Analisis analizar(String fuente) {
        Lexer lexer = new Lexer(new StringReader(fuente));
        parser p = new parser(lexer);
        NodoPrograma ast = null;
        try {
            ast = (NodoPrograma) p.parse().value;
        } catch (Exception e) {
            // error sintáctico irrecuperable: ast queda en null
        }
        return new Analisis(ast, lexer.erroresLexicos, p.erroresSintacticos);
    }

    /** Parsea, verifica que no haya errores de ningún tipo y ejecuta el intérprete. */
    private static Object ejecutar(String fuente) {
        Analisis a = analizar(fuente);
        assertEquals(0, a.erroresLexicos(), "errores léxicos");
        assertEquals(0, a.erroresSintacticos(), "errores sintácticos");
        ContextoSemantico ctx = a.ast().analizarSemantica();
        assertTrue(ctx.errores.isEmpty(), "errores semánticos: " + ctx.errores);
        return a.ast().interpretar(new EstadoEjecucion(false));
    }

    private static ContextoSemantico semantica(String fuente) {
        Analisis a = analizar(fuente);
        assertNotNull(a.ast());
        return a.ast().analizarSemantica();
    }

    private static String leer(String ejemplo) throws Exception {
        return Files.readString(Path.of("ejemplos", ejemplo));
    }

    // ------------------------------------------------------------------ ejemplos de la consigna

    @Test
    @DisplayName("Ejemplo 1 de la consigna (void main)")
    void ejemploConsigna1() {
        assertNull(ejecutar("void main(){int x; int y; x = 1 ; y=1; x = x+3*2*y; }"));
    }

    @Test
    @DisplayName("Ejemplo 2 de la consigna (se corta en el primer return)")
    void ejemploConsigna2() {
        // y vale 0 (valor por defecto) -> x = 1 + 3*2*0 = 1
        assertEquals(1, ejecutar("int main(){int x; int y; x = 1 ; x = x+3*2*y; return x; y = 4; x = 2; return y*2;}"));
    }

    // ------------------------------------------------------------------ todos los ejemplos válidos

    @ParameterizedTest(name = "{0} retorna {1}")
    @CsvSource({
            "ejemplo2_returns.txt,      1",
            "ejemplo3_bool.txt,         true",
            "ejemplo4_precedencia.txt,  34",
            "ejemplo5_if_else.txt,      12",
            "ejemplo6_while.txt,        120",
            "ejemplo7_logicos.txt,      true",
            "ejemplo8_anidado.txt,      8",
            "ejemplo9_fibonacci.txt,    55",
    })
    void ejemplosValidos(String archivo, String esperado) throws Exception {
        Object resultado = ejecutar(leer(archivo));
        assertEquals(esperado, String.valueOf(resultado));
    }

    @Test
    void ejemploVoidNoRetornaValor() throws Exception {
        assertNull(ejecutar(leer("ejemplo1_void.txt")));
    }

    // ------------------------------------------------------------------ precedencia y asociatividad

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = ';', value = {
            "2 + 3 * 4           ; 14",
            "(2 + 3) * 4         ; 20",
            "10 - 3 - 2          ; 5",     // asociativo a izquierda: (10-3)-2
            "2 * 3 - 4 * 1       ; 2",
    })
    void precedenciaAritmetica(String expr, int esperado) {
        assertEquals(esperado, ejecutar("int main(){ return " + expr + "; }"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = ';', value = {
            "!true || true             ; true",   // ! liga más fuerte que ||
            "true || false && false    ; true",   // && liga más fuerte que ||
            "1 + 1 == 2                ; true",   // + liga más fuerte que ==
            "1 < 2 == 3 > 4            ; false",  // < > ligan más fuerte que ==
            "!(1 < 2)                  ; false",
    })
    void precedenciaLogica(String expr, boolean esperado) {
        assertEquals(esperado, ejecutar("bool main(){ return " + expr + "; }"));
    }

    // ------------------------------------------------------------------ errores léxicos y sintácticos

    @Test
    void erroresLexicos() throws Exception {
        assertEquals(2, analizar(leer("error_lexico.txt")).erroresLexicos());
    }

    @Test
    void comentarioSinCerrarEsErrorLexico() {
        assertEquals(1, analizar("int main(){ /* sin cerrar return 1; }").erroresLexicos());
    }

    @Test
    void comentariosSeIgnoran() {
        assertEquals(3, ejecutar("int main(){ // linea\n /* bloque\n de varias lineas */ return 3; }"));
    }

    @Test
    void errorSintactico() throws Exception {
        assertEquals(1, analizar(leer("error_sintactico.txt")).erroresSintacticos());
    }

    @Test
    @DisplayName("El parser se recupera y reporta varios errores sintácticos")
    void recuperacionDeErrores() throws Exception {
        assertEquals(3, analizar(leer("error_sintactico_multiple.txt")).erroresSintacticos());
    }

    @Test
    void declaracionDespuesDeSentenciaEsErrorSintactico() {
        assertTrue(analizar("int main(){ int x; x = 1; int y; return x; }").erroresSintacticos() > 0);
    }

    // ------------------------------------------------------------------ errores semánticos

    @Test
    void erroresSemanticos() throws Exception {
        ContextoSemantico ctx = semantica(leer("error_semantico.txt"));
        assertEquals(5, ctx.errores.size(), ctx.errores.toString());
    }

    @Test
    void erroresDeTipos() throws Exception {
        ContextoSemantico ctx = semantica(leer("error_tipos.txt"));
        assertEquals(5, ctx.errores.size(), ctx.errores.toString());
    }

    @Test
    void variableNoDeclarada() {
        ContextoSemantico ctx = semantica("int main(){ return z; }");
        assertEquals(1, ctx.errores.size());
        assertTrue(ctx.errores.get(0).contains("no declarada 'z'"));
    }

    @Test
    void returnIncompatibleConVoid() {
        assertEquals(1, semantica("void main(){ return 1; }").errores.size());
        assertEquals(1, semantica("int main(){ return; }").errores.size());
    }

    @Test
    void constanteFueraDeRango() {
        assertEquals(1, semantica("int main(){ return 99999999999; }").errores.size());
    }

    // ------------------------------------------------------------------ advertencias

    @Test
    void advertenciaVariableSinInicializar() {
        ContextoSemantico ctx = semantica("int main(){ int x; int y; x = y + 1; return x; }");
        assertTrue(ctx.errores.isEmpty());
        assertTrue(ctx.advertencias.stream().anyMatch(a -> a.contains("'y' podría usarse sin haber sido inicializada")));
    }

    @Test
    void inicializadaEnAmbasRamasDelIfNoAdvierte() {
        ContextoSemantico ctx = semantica(
                "int main(){ int x; if (true) { x = 1; } else { x = 2; } return x; }");
        assertTrue(ctx.advertencias.isEmpty(), ctx.advertencias.toString());
    }

    @Test
    void advertenciaCodigoInalcanzable() {
        ContextoSemantico ctx = semantica("int main(){ int x; x = 1; return x; x = 2; }");
        assertTrue(ctx.advertencias.stream().anyMatch(a -> a.contains("inalcanzable")));
    }

    @Test
    void advertenciaMainSinReturn() {
        ContextoSemantico ctx = semantica("int main(){ int x; x = 1; if (x > 0) { return x; } }");
        assertTrue(ctx.advertencias.stream().anyMatch(a -> a.contains("sin ejecutar un return")));
    }

    // ------------------------------------------------------------------ intérprete

    @Test
    void desbordamientoEsErrorDeEjecucion() throws Exception {
        assertThrows(ErrorEjecucion.class, () -> ejecutar(leer("error_ejecucion.txt")));
    }

    @Test
    void bucleInfinitoSeDetecta() {
        assertThrows(ErrorEjecucion.class,
                () -> ejecutar("int main(){ int i; i = 0; while (true) { i = i + 1; } return i; }"));
    }

    @Test
    void mainSinReturnDevuelveValorPorDefecto() {
        assertEquals(0, ejecutar("int main(){ int x; x = 5; }"));
        assertEquals(false, ejecutar("bool main(){ }"));
    }

    // ------------------------------------------------------------------ generación de código

    @Test
    void codigo3DDelWhileTieneEtiquetasYSaltos() throws Exception {
        Analisis a = analizar(leer("ejemplo6_while.txt"));
        a.ast().analizarSemantica();
        GeneradorCodigo gen = a.ast().generarCodigo3D();
        String codigo = gen.getInstrucciones().toString();
        assertTrue(codigo.contains("L0:"));
        assertTrue(codigo.contains("IFFALSE"));
        assertTrue(codigo.contains("GOTO"));
        assertTrue(codigo.contains("SUB"));
    }

    @Test
    void assemblerTieneMainYEpilogo() throws Exception {
        Analisis a = analizar(leer("ejemplo5_if_else.txt"));
        a.ast().analizarSemantica();
        String asm = String.join("\n",
                new TraductorAssembly2D(a.ast().declaraciones, a.ast().generarCodigo3D()).traducir());
        assertTrue(asm.contains("main:"));
        assertTrue(asm.contains("setg %al"));
        assertTrue(asm.contains("v_max: .space 8"));
        assertTrue(asm.trim().endsWith("# volver al sistema (rax = valor de retorno)"));
    }
}
