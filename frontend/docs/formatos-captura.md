# Inventario de formatos de captura (Control de Cambios #38)

Revisión de los campos de captura de la web por tipo de dato. Criterio:

- **Importes**: `MoneyInput` — "$", separadores de miles mientras se escribe, moneda real al lado
  (MXN o USD, sin convertir), se puede pegar `5000000` o `$5,000,000.00`, el cursor no salta, el
  valor que se guarda es numérico y un campo vacío no se vuelve cero.
- **Porcentajes**: sufijo `%`, teclado decimal, rango validado por campo.
- **Superficies**: sufijo `m²`, decimales, nunca con formato de dinero.
- **Teléfonos**: `type="tel"` (teclado telefónico), se conserva la lada y el `+` internacional.
- **Fechas**: selector nativo `type="date"` (formato del dispositivo, sin ambigüedad día/mes, no
  admite fechas inexistentes); se muestran como "31 oct 2026".
- **Claves, folios e identificadores**: texto, sin separadores de miles; conservan letras y ceros.

## Importes

| Módulo / pantalla | Campo | Estado |
| --- | --- | --- |
| Factibilidad | Precio de la propiedad | `MoneyInput` (CC#38, 9246ff2) |
| Datos del contrato | Precio autorizado | `MoneyInput` (9246ff2) |
| Portal del cliente | Precio de venta sugerido | `MoneyInput` (CC#34) |
| Propiedades "En Genera" | Precio mínimo / máximo | `MoneyInput` con moneda elegida (CC#36) |
| Alta/edición de propiedad (21Online) | Precio de venta / renta mensual | `MoneyInput` con la moneda del formulario (este cambio) |
| Alta/edición de propiedad (21Online) | Cuota de mantenimiento | `MoneyInput` (este cambio) |
| Leads | Presupuesto mínimo / máximo | `MoneyInput` MXN (este cambio; antes texto con sufijo) |
| Operaciones de cierre (CC#32) | Monto de la operación | `MoneyInput` + moneda MXN/USD |
| Corregir datos (CC#39) | Precio autorizado | `MoneyInput` |

## Porcentajes

| Pantalla | Campo | Estado |
| --- | --- | --- |
| Alta de propiedad | Comisión | sufijo `%`, solo dígitos y punto (este cambio) |
| Leads | Probabilidad de cierre | sufijo `%`, validación 0–100 |
| Asesores | Peso en la ronda | número 0–100 (no es porcentaje de dinero) |

## Superficies

| Pantalla | Campo | Estado |
| --- | --- | --- |
| Alta de propiedad | Terreno / construcción | sufijo `m²`, decimales (este cambio) |
| Datos del contrato | Superficie de terreno / construcción | número con unidad en la etiqueta |
| Corregir datos | Superficie de terreno / construcción | sufijo `m²` (este cambio) |

## Teléfonos

Todos los campos de teléfono (participantes, leads, asesores, encuestas, corregir datos,
operaciones) usan `type="tel"`. La normalización a +52 solo se aplica al abrir WhatsApp.

## Pendientes por definir con C21 Genera

- Precisión de decimales por campo (hoy: 2 en importes, libre en superficies).
- Si se admiten otras monedas además de MXN y USD.
- Formato de fecha preferido para reportes impresos.
- Validar en teléfono real (Android/iPhone) el teclado y la edición de importes.
