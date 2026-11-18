# Mini compilador – Pre-Proyecto Compiladores 2026

**Universidad Nacional de Villa Mercedes** – Escuela de Ingeniería y Ciencias Ambientales
Materia: **Compiladores** – Ingeniería en Sistemas

## Integrantes

- **Vanden Panhuysen, Mateo**
- **Nievas, Axel Samuel**

---

## Descripción

Compilador completo para un lenguaje sencillo parecido a C, con una única función `main`.
Está hecho en **Java** con **JFlex** (analizador léxico) y **Java CUP** (analizador sintáctico).

A partir de un archivo fuente, el compilador hace estas etapas:

| # | Etapa | Qué hace | Consigna |
|---|-------|----------|----------|
| 1 | **Análisis léxico** (JFlex) | Divide el texto en tokens según las expresiones regulares del lenguaje. Informa caracteres inválidos y comentarios sin cerrar. | Puntos 2 y 3 |
| 2 | **Análisis sintáctico** (CUP) | Verifica la gramática con un parser LALR(1). Si hay un error, se recupera en el siguiente `;` y sigue, así informa varios errores en una sola pasada. | Puntos 1 y 4 |
| 3 | **AST** | Las acciones semánticas del parser arman el Árbol Sintáctico Abstracto, que se imprime decorado con el tipo de cada expresión. | Punto 5 |
| 4 | **Análisis semántico + tabla de símbolos** | Arma la tabla de símbolos, verifica que las variables usadas estén declaradas (y no repetidas), chequea tipos y enlaza cada identificador del AST con su entrada en la tabla. También da advertencias. | Nota del punto 6 |
| 5 | **Intérprete** | Recorre el AST y ejecuta el programa: muestra paso a paso cada asignación, el estado final de las variables y el valor que retorna `main`. | Punto 6 |
| 6 | **Código de 3 direcciones** (seudo-assembly) | Genera código intermedio con temporales (`T0, T1…`) y etiquetas (`L0, L1…`). Se guarda en `programa_3d.txt`. | Punto 7 |
| 7 | **Assembler x86-64** | Traduce el código de 3 direcciones a assembler real de 2 direcciones (GNU as, sintaxis AT&T), que se puede ensamblar con **gcc** y ejecutar. Se guarda en `programa.s`. | Punto 7 (extra) |

---

## El lenguaje

### Ejemplos de la consigna

```c
void main(){int x; int y; x = 1 ; y=1; x = x+3*2*y; }
int main(){int x; int y; x = 1 ; x = x+3*2*y; return x; y = 4; x = 2; return y*2;}
```

### Gramática extendida (punto 1)

La gramática original era `P → E ;` y `E → E + E | E * E | ( E ) | nro`. La extendimos así:

```
programa      → tipo_retorno main ( ) { lista_decl lista_sent }
tipo_retorno  → int | bool | void
lista_decl    → lista_decl declaracion | λ
declaracion   → tipo_dato ID ;
tipo_dato     → int | bool
lista_sent    → lista_sent sentencia | λ
sentencia     → ID = E ;
              | return E ;
              | return ;
              | if ( E ) bloque
              | if ( E ) bloque else bloque
              | while ( E ) bloque
bloque        → { lista_sent }
E             → E + E | E - E | E * E
              | E < E | E > E | E == E
              | E && E | E || E | ! E
              | ( E ) | NUMERO | ID | true | false
```

- Primero van **una o más declaraciones** (`int x;`, `bool aux1;`) y después las **sentencias**.
- La gramática de `E` es ambigua. La ambigüedad se resuelve con la precedencia y asociatividad de CUP, de menor a mayor:
  `||` < `&&` < `==` < `<` `>` < `+` `-` < `*` < `!` (unario). Todos los operadores binarios asocian a izquierda.
- Como los bloques llevan llaves obligatorias, no aparece el problema del *else colgante*.
- Las listas son recursivas a izquierda: es lo más eficiente para un parser LR, porque la pila no crece con el largo de la lista.

> Lo que pide la consigna es declaraciones, asignaciones, `return`, constantes lógicas y los operadores `+` y `*`.
> Además agregamos `if`/`else`, `while`, resta, operadores relacionales y operadores lógicos, para que las variables `bool` sirvan para algo y el lenguaje quede más completo.

### Expresiones regulares (punto 2)

| Categoría | Expresión regular |
|-----------|-------------------|
| Letra / dígito | `LETRA = [a-zA-Z]`  `DIGITO = [0-9]` |
| Palabras reservadas | `int` \| `bool` \| `void` \| `main` \| `return` \| `if` \| `else` \| `while` |
| Constantes lógicas | `true` \| `false` |
| Constantes enteras | `{DIGITO}+` |
| Identificadores (variables) | `{LETRA}({LETRA}\|{DIGITO})*` |
| Operadores aritméticos | `+` \| `-` \| `*` |
| Operadores relacionales | `<` \| `>` \| `==` |
| Operadores lógicos | `&&` \| `\|\|` \| `!` |
| Asignación | `=` |
| Delimitadores | `(` \| `)` \| `{` \| `}` \| `;` |
| Se ignoran | espacios `[ \t\r\n]+`, comentarios `// ...` y `/* ... */` |

Las palabras reservadas están escritas antes que la regla de identificadores. Así, ante dos reglas que reconocen el mismo lexema, JFlex elige la palabra reservada. En cambio, `intx` es un identificador porque gana el lexema más largo.

### Reglas de tipos

| Operador | Operandos | Resultado |
|----------|-----------|-----------|
| `+` `-` `*` | `int`, `int` | `int` |
| `<` `>` | `int`, `int` | `bool` |
| `==` | dos del mismo tipo | `bool` |
| `&&` `\|\|` | `bool`, `bool` | `bool` |
| `!` | `bool` | `bool` |

- Las condiciones de `if` y `while` tienen que ser `bool`.
- En una asignación, el tipo de la expresión tiene que coincidir con el de la variable.
- `return` tiene que coincidir con el tipo de `main`. Un `main` `void` no puede retornar un valor, y uno `int`/`bool` no puede usar `return;`.

### Errores y advertencias que detecta

- **Léxicos:** caracteres no reconocidos (`@`, `#`, `&` solo…) y comentarios de bloque sin cerrar.
- **Sintácticos:** token inesperado, junto con la lista de tokens que se esperaban. El parser se recupera y reporta todos los errores juntos.
- **Semánticos:** variable no declarada, variable declarada dos veces, tipos incompatibles, condición que no es `bool`, `return` incompatible con `main` y constante fuera del rango de `int`.
- **Advertencias** (no frenan la compilación):
  - una variable que se usa sin haberle asignado nada antes (se analiza la *asignación definida* por cada camino del `if`/`while`);
  - código inalcanzable después de un `return`;
  - una variable declarada que nunca se usa;
  - un `main` no void que puede terminar sin `return`.
- **De ejecución** (los detecta el intérprete): desbordamiento de enteros y bucles infinitos (más de 1.000.000 de vueltas).

Las variables sin inicializar valen `0` (int) o `false` (bool), igual que en el assembler, donde viven en la sección `.bss`.

---

## Estructura del proyecto

```text
PreProyecto/
├── pom.xml                         # Maven: dependencias y plugins (JFlex, CUP, JUnit, jar ejecutable)
├── README.md
├── codigo.txt                      # Programa que se compila por defecto
├── ejemplos/                       # Programas de prueba (válidos y con errores)
├── ejecutar_gcc.bat                # Ensambla programa.s con gcc, lo ejecuta y muestra el resultado
├── probar_ejemplos.ps1             # Prueba de punta a punta: intérprete vs. ejecutable de gcc
└── src/
    ├── main/
    │   ├── jflex/Lexer.flex        # Analizador léxico (expresiones regulares)
    │   ├── cup/parser.cup          # Gramática + construcción del AST
    │   └── java/com/compilador/
    │       ├── Main.java                    # Orquesta todas las etapas
    │       └── ast/
    │           ├── EstructuraAST.java       # Nodos del AST: imprimir / verificar / evaluar / generar 3D
    │           ├── TipoDato.java            # int, bool, void
    │           ├── TipoNodo.java            # Clase de cada nodo del AST
    │           ├── Operador.java            # Operadores con sus reglas de tipos y su instrucción 3D
    │           ├── TablaSimbolos.java       # Tabla de símbolos
    │           ├── Simbolo.java             # Entrada de la tabla (nombre, tipo, línea, usos, valor)
    │           ├── ContextoSemantico.java   # Errores, advertencias y asignación definida
    │           ├── EstadoEjecucion.java     # Estado del intérprete (valor de retorno, traza)
    │           ├── ErrorEjecucion.java      # Errores en tiempo de ejecución
    │           ├── GeneradorCodigo.java     # Generador de código de 3 direcciones
    │           ├── Instruccion3D.java       # Una instrucción (cuádrupla) de 3 direcciones
    │           └── TraductorAssembly2D.java # 3 direcciones -> assembler x86-64 (2 direcciones)
    └── test/java/com/compilador/
        └── CompiladorTest.java     # 40 pruebas automáticas con JUnit 5
```

Las clases `Lexer.java`, `parser.java` y `sym.java` no están en el repositorio: Maven las **genera** en `target/generated-sources` a partir de `Lexer.flex` y `parser.cup`.

---

## Requisitos

- **Java JDK 21** o superior
- **Maven 3** (viene incluido en IntelliJ IDEA)
- **gcc** (MinGW en Windows) para ensamblar el assembler generado. Es opcional: hace falta solo para el paso final.
- Recomendado: **IntelliJ IDEA**

---

## Cómo ejecutarlo

### Opción A: IntelliJ IDEA

1. `File → Open…` y abrir la carpeta del proyecto (donde está `pom.xml`). Esperar a que Maven descargue las dependencias.
2. En el panel **Maven** (a la derecha): `Lifecycle → clean` y después `Lifecycle → compile`. Así se generan `Lexer.java`, `parser.java` y `sym.java`.
3. Abrir `src/main/java/com/compilador/Main.java` y ejecutarlo (**Shift + F10**).
   - Por defecto compila `codigo.txt`.
   - Para compilar otro archivo: `Run → Edit Configurations… → Program arguments`, por ejemplo `ejemplos/ejemplo6_while.txt`.

### Opción B: línea de comandos (PowerShell)

Desde la carpeta del proyecto, compilar todo, correr las pruebas y armar el jar ejecutable:

```powershell
mvn clean package
```

Compilar el programa por defecto (`codigo.txt`):

```powershell
java -jar target/compilador.jar
```

Compilar otro archivo:

```powershell
java -jar target/compilador.jar ejemplos/ejemplo6_while.txt
```

Compilar sin mostrar la traza del intérprete:

```powershell
java -jar target/compilador.jar ejemplos/ejemplo9_fibonacci.txt --sin-traza
```

> Si `mvn` no está en el PATH, se puede usar el Maven que trae IntelliJ:
> `& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" clean package`

El compilador muestra por consola las 6 etapas: código fuente numerado, AST, tabla de símbolos con advertencias, traza del intérprete, código de 3 direcciones y assembler. Además genera dos archivos:

- `programa_3d.txt`: el código de 3 direcciones
- `programa.s`: el assembler x86-64

### Ensamblar y ejecutar el assembler con gcc

```powershell
gcc programa.s -o programa.exe
```

```powershell
.\programa.exe
```

```powershell
echo $LASTEXITCODE
```

El valor que retorna `main` queda en el registro `%rax` y el sistema operativo lo toma como **código de salida** del proceso: `$LASTEXITCODE` muestra ese valor. Un `bool` sale como 1 (`true`) o 0 (`false`). Tiene que coincidir con el resultado del intérprete.

También está el script que hace los tres pasos juntos:

```powershell
.\ejecutar_gcc.bat
```

### Pruebas automáticas

Correr las 40 pruebas JUnit (léxico, sintaxis, semántica, intérprete, precedencia y generación de código):

```powershell
mvn test
```

Prueba de punta a punta: compila cada ejemplo, lo ensambla con gcc y compara el resultado del ejecutable con el del intérprete (requiere `mvn package` antes):

```powershell
powershell -ExecutionPolicy Bypass -File .\probar_ejemplos.ps1
```

---

## Ejemplos incluidos

| Archivo | Qué muestra | Resultado |
|---------|-------------|-----------|
| `ejemplo1_void.txt` | Ejemplo 1 de la consigna (`void main`) | sin valor |
| `ejemplo2_returns.txt` | Ejemplo 2 de la consigna: corta en el primer `return` | `1` |
| `ejemplo3_bool.txt` | `main` que retorna `bool` | `true` (1) |
| `ejemplo4_precedencia.txt` | Precedencia de `*` sobre `+` y paréntesis | `34` |
| `ejemplo5_if_else.txt` | `if` / `else` (máximo de dos números) | `12` |
| `ejemplo6_while.txt` | `while` (factorial de 5) | `120` |
| `ejemplo7_logicos.txt` | `<` `>` `==` `&&` `\|\|` `!` | `true` (1) |
| `ejemplo8_anidado.txt` | `if` dentro de `while` y `return` en medio del ciclo | `8` |
| `ejemplo9_fibonacci.txt` | Fibonacci(10) con resta, `while` e `if` | `55` |
| `error_lexico.txt` | Caracteres inválidos | 2 errores léxicos |
| `error_sintactico.txt` | Expresión incompleta | 1 error sintáctico |
| `error_sintactico_multiple.txt` | Recuperación de errores del parser | 3 errores sintácticos |
| `error_semantico.txt` | Variable repetida o no declarada, asignaciones incompatibles | 5 errores semánticos |
| `error_tipos.txt` | Tipos incorrectos en operadores y condiciones | 5 errores semánticos |
| `error_ejecucion.txt` | Desbordamiento de `int` detectado por el intérprete | error de ejecución |

### Ejemplo de salida: código de 3 direcciones y assembler

Para `ejemplo5_if_else.txt` (`if (a > b) { max = a; } else { max = b; }`):

```text
    LOAD    7, T0              # Ti <- constante o variable
    STORE   T0, a              # variable <- Ti
    LOAD    12, T1
    STORE   T1, b
    LOAD    a, T2
    LOAD    b, T3
    GT      T2, T3, T4         # T4 <- (T2 > T3)
    IFFALSE T4, L1             # si es false salta al else
    LOAD    a, T5
    STORE   T5, max
    GOTO    L0
L1:
    LOAD    b, T6
    STORE   T6, max
L0:
    LOAD    max, T7
    RETURN  T7
```

Cada instrucción de 3 direcciones se traduce a una o más instrucciones x86-64 de 2 direcciones, usando `%rax` como acumulador. Por ejemplo, `GT T2, T3, T4` queda así:

```asm
    movq -24(%rbp), %rax        # rax <- T2
    cmpq -32(%rbp), %rax        # compara T2 con T3
    setg %al                    # al = 1 si T2 > T3
    movzbq %al, %rax            # extiende al (8 bits) a rax (64 bits)
    movq %rax, -40(%rbp)        # T4 <- rax
```

Cómo se ubican los datos en el assembler:

- las variables del programa están en la sección `.bss` (`v_x: .space 8`);
- los temporales están en la pila de `main` (`Ti = -8*(i+1)(%rbp)`);
- las etiquetas `Ln` pasan a etiquetas locales `.Ln`.

---

## Herramientas utilizadas

| Herramienta | Uso |
|-------------|-----|
| **Java 21** | Lenguaje de implementación del compilador |
| **JFlex 1.8.2** | Generador del analizador léxico (`Lexer.flex` → `Lexer.java`) |
| **Java CUP 11b** | Generador del parser LALR(1) (`parser.cup` → `parser.java`, `sym.java`) |
| **Maven** | Build, manejo de dependencias, plugins de JFlex/CUP y jar ejecutable |
| **JUnit 5** | Pruebas automáticas |
| **GCC (MinGW)** | Ensamblar y enlazar el assembler x86-64 generado |
| **IntelliJ IDEA** | Entorno de desarrollo |
| **Git / GitHub** | Control de versiones |
