package com.compilador;
import java_cup.runtime.Symbol;

/*
 * ANALIZADOR LÉXICO (Puntos 2 y 3 del Pre-Proyecto)
 *
 * Expresiones regulares del lenguaje:
 *
 *   LETRA               = [a-zA-Z]
 *   DIGITO              = [0-9]
 *
 *   Palabras reservadas : int | bool | void | main | return | if | else | while
 *   Constantes lógicas  : true | false
 *   Constantes enteras  : {DIGITO}+
 *   Identificadores     : {LETRA} ({LETRA} | {DIGITO})*
 *   Operadores aritm.   : + | - | *
 *   Operadores relac.   : < | > | ==
 *   Operadores lógicos  : && | || | !
 *   Asignación          : =
 *   Delimitadores       : ( | ) | { | } | ;
 *   Espacios (ignorados): [ \t\r\n]+
 *   Comentarios (ignor.): "//" [^\r\n]*   |   "/*" ~"*" "/"
 *
 * Las palabras reservadas se escriben ANTES que la regla de identificadores:
 * ante dos reglas que reconocen el mismo lexema de igual longitud, JFlex elige
 * la que aparece primero (por eso "int" es PR_INT y no ID). Para "intx" gana
 * ID porque es el lexema más largo (regla del "longest match").
 *
 * Cada token lleva la línea y columna donde aparece (para reportar errores).
 */

%%

%class Lexer
%cup
%unicode
%line
%column

%{
    /** Cantidad de errores léxicos encontrados (si es > 0 se aborta la compilación). */
    public int erroresLexicos = 0;

    private Symbol symbol(int tipo) {
        return new Symbol(tipo, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int tipo, Object valor) {
        return new Symbol(tipo, yyline + 1, yycolumn + 1, valor);
    }

    private void errorLexico(String mensaje) {
        erroresLexicos++;
        System.out.println("[ERROR LÉXICO] Línea " + (yyline + 1) + ", columna " + (yycolumn + 1) + ": " + mensaje);
    }
%}

LETRA         = [a-zA-Z]
DIGITO        = [0-9]
ESPACIO       = [ \t\r\n]+
COMENTARIO_LINEA  = "//" [^\r\n]*
COMENTARIO_BLOQUE = "/*" ~"*/"
NUMERO        = {DIGITO}+
IDENTIFICADOR = {LETRA} ({LETRA} | {DIGITO})*

// Estado exclusivo: después de un comentario de bloque sin cerrar se descarta el resto del archivo
%xstate COMENTARIO_SIN_CERRAR

%%

/* ---------------- Palabras reservadas ---------------- */
"int"            { return symbol(sym.PR_INT); }
"bool"           { return symbol(sym.PR_BOOL); }
"void"           { return symbol(sym.PR_VOID); }
"main"           { return symbol(sym.PR_MAIN); }
"return"         { return symbol(sym.PR_RETURN); }
"if"             { return symbol(sym.PR_IF); }
"else"           { return symbol(sym.PR_ELSE); }
"while"          { return symbol(sym.PR_WHILE); }

/* ---------------- Constantes lógicas ---------------- */
"true"           { return symbol(sym.BOOL_TRUE); }
"false"          { return symbol(sym.BOOL_FALSE); }

/* ---------------- Operadores ---------------- */
"+"              { return symbol(sym.OP_SUMA); }
"-"              { return symbol(sym.OP_RESTA); }
"*"              { return symbol(sym.OP_MULT); }
"<"              { return symbol(sym.OP_MENOR); }
">"              { return symbol(sym.OP_MAYOR); }
"=="             { return symbol(sym.OP_IGUAL); }
"&&"             { return symbol(sym.OP_AND); }
"||"             { return symbol(sym.OP_OR); }
"!"              { return symbol(sym.OP_NOT); }
"="              { return symbol(sym.OP_ASIGNACION); }

/* ---------------- Delimitadores ---------------- */
"("              { return symbol(sym.PAREN_IZQ); }
")"              { return symbol(sym.PAREN_DER); }
"{"              { return symbol(sym.LLAVE_IZQ); }
"}"              { return symbol(sym.LLAVE_DER); }
";"              { return symbol(sym.PUNTO_COMA); }

/* ---------------- Constantes e identificadores ---------------- */
{NUMERO}         { return symbol(sym.NUMERO, yytext()); }
{IDENTIFICADOR}  { return symbol(sym.ID, yytext()); }

/* ---------------- Ignorados ---------------- */
{ESPACIO}           { /* Ignorar */ }
{COMENTARIO_LINEA}  { /* Ignorar */ }
{COMENTARIO_BLOQUE} { /* Ignorar */ }
"/*"                { errorLexico("comentario de bloque sin cerrar (falta '*/')."); yybegin(COMENTARIO_SIN_CERRAR); }

/* ---------------- Error léxico ---------------- */
[^]              { errorLexico("caracter no reconocido '" + yytext() + "'"); }

<COMENTARIO_SIN_CERRAR> [^]  { /* Ignorar hasta el fin de archivo */ }
