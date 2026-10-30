import 'package:flutter/material.dart';

import '../money.dart';
import 'widgets.dart';

/// CC#38: campo de importe ("$5,000,000" + moneda); [value] y [onChanged] usan el valor crudo ("5000000").
class MoneyField extends StatelessWidget {
  const MoneyField({super.key, this.label, required this.value, this.onChanged, this.currency = 'MXN', this.hint, this.helper, this.error, this.enabled = true});

  final String? label;
  final String value;
  final ValueChanged<String>? onChanged;
  final String currency;
  final String? hint, helper, error;
  final bool enabled;

  @override
  Widget build(BuildContext context) => AppTextField(
        label: label,
        value: formatMoneyInput(normalizeMoneyInput(value)),
        hint: hint,
        helper: helper,
        error: error,
        enabled: enabled,
        suffixText: currency,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        inputFormatters: [MoneyInputFormatter()],
        onChanged: onChanged == null ? null : (text) => onChanged!(normalizeMoneyInput(text)),
      );
}
