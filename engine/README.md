# amiva · engine

Motor de cálculo de fórmulas, sin estado y sin lógica de negocio. Solo lo llama el backend. Volver al [README principal](../README.md).

## Stack

- Python 3.12+
- Empaquetado con hatchling (`pyproject.toml`), sin dependencias de ejecución por ahora
- pytest para tests

## Requisitos

- Python 3.12 o superior

## Comandos

```bash
python3.12 -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'   # instalar en modo editable con pytest
pytest                    # tests
```

## Qué hace

El backend envía una fórmula con sus series ya preparadas y el engine devuelve el resultado. No accede a bases de datos ni a otros servicios.

1. Preparar: construye la rejilla según la granularidad (por respuesta, día, semana, mes, trimestre, año) y rellena huecos (null, arrastrar el último valor o cero).
2. Calcular: evalúa la fórmula.
3. Formatear: según el subtipo del widget.

## Fórmulas

- Árbol JSON con un catálogo cerrado de funciones. Nunca se usa `eval`.
- Tipos: {escalar, serie} × {numérico, booleano}. Los infiere el backend.
- Un escalar se extiende a serie automáticamente. De serie a escalar solo con una función explícita (`media`, `suma`…).
- Funciones: `+ - * /` · `> < >= <= = !=` · `and or not` · `where(c, a, b)` · `hay_dato` · `media suma máximo mínimo conteo`.

## Nulls

- Aritmética con null → null. Comparadores con null → falso. Un booleano nunca es null.
- `where` con hueco → rama "si no".
- Las agregaciones ignoran null; si todo es null → null.
- Operaciones indefinidas (÷0, log ≤ 0…) → null.

## Salida

- Escalar: `{tipo, valor, rango}`.
- Serie: según el esquema del subtipo del widget.
- Incluye la fecha del dato más reciente.

## Estructura

```
src/amiva_engine/
  api/          entrada desde el backend
  expression/   modelo y validación del árbol de la fórmula
  functions/    catálogo de funciones
  preparation/  rejilla, granularidad y huecos
  evaluation/   cálculo de la fórmula
  output/       formato de la salida
tests/
```
