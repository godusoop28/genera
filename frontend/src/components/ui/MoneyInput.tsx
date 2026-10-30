"use client";

import { Input } from "@/components/ui/Input";
import { digitsBefore, formatMoneyInput, normalizeMoneyInput, positionAfterDigits } from "@/lib/money";
import { type ComponentProps, useLayoutEffect, useRef } from "react";

type MoneyInputProps = Omit<ComponentProps<typeof Input>, "value" | "onChange" | "type" | "suffix"> & {
  /** Valor crudo: solo dígitos y punto decimal ("5000000.5"); "" = vacío. */
  value: string;
  onChange: (raw: string) => void;
  /** Moneda que se muestra al lado del importe. */
  currency?: string;
  maxDecimals?: number;
};

/**
 * CC#38: importe con "$" y separadores de miles mientras se escribe. Se puede escribir, pegar
 * (con o sin formato), borrar y editar en medio sin que el cursor salte al final; el valor que sale
 * nunca lleva formato y un campo vacío no se vuelve cero.
 */
export function MoneyInput({ value, onChange, currency = "MXN", maxDecimals = 2, ...props }: MoneyInputProps) {
  const ref = useRef<HTMLInputElement>(null);
  const caretDigits = useRef<number | null>(null);
  const shown = formatMoneyInput(value);

  useLayoutEffect(() => {
    const el = ref.current;
    if (el && caretDigits.current !== null && document.activeElement === el) {
      const position = positionAfterDigits(shown, caretDigits.current);
      el.setSelectionRange(position, position);
    }
    caretDigits.current = null;
  }, [shown]);

  return (
    <Input
      {...props}
      ref={ref}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      suffix={currency}
      className={props.className ? `${props.className} pr-14` : "pr-14"}
      value={shown}
      onChange={(e) => {
        const text = e.target.value;
        const caret = e.target.selectionStart ?? text.length;
        caretDigits.current = digitsBefore(text, caret);
        onChange(normalizeMoneyInput(text, maxDecimals));
      }}
    />
  );
}
