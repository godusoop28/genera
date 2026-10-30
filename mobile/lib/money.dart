// CC#38: captura y presentación de importes (igual que frontend/src/lib/money.ts). El valor "crudo"
// que se guarda y se manda al servidor es siempre texto con solo dígitos y, si acaso, un punto
// decimal ("5000000" o "5000000.5"); el formato con "$" y separadores de miles es solo visual.
// Vacío sigue siendo vacío (nunca 0).

import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

/// Convierte lo que el usuario escribe o pega ("$5,000,000.00 MXN", "5 000 000", "5.000.000") en el
/// valor crudo. Las comas siempre son separadores de miles (es-MX); si hay más de un punto, también
/// se toman como separadores de miles.
String normalizeMoneyInput(String text, {int maxDecimals = 2}) {
  var s = text.replaceAll(RegExp(r'[^\d.]'), '');
  if ('.'.allMatches(s).length > 1) s = s.replaceAll('.', '');
  final parts = s.split('.');
  final integer = parts[0].replaceFirst(RegExp(r'^0+(?=\d)'), '');
  if (parts.length == 1 || maxDecimals == 0) return integer;
  final decimals = parts[1].length > maxDecimals ? parts[1].substring(0, maxDecimals) : parts[1];
  return '${integer.isEmpty ? '0' : integer}.$decimals';
}

/// "5000000.5" → "5,000,000.5". Conserva un punto o ceros finales mientras se escribe ("5,000.").
String groupThousands(String raw) {
  if (raw.isEmpty) return '';
  final parts = raw.split('.');
  final grouped = parts[0].replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => ',');
  return parts.length == 1 ? grouped : '$grouped.${parts[1]}';
}

/// Texto que se ve en el campo: "$5,000,000".
String formatMoneyInput(String raw) => raw.isEmpty ? '' : '\$${groupThousands(raw)}';

/// Valor del servidor (número o texto decimal) → valor crudo del campo; null/vacío → "".
String moneyToRaw(Object? value) {
  if (value == null || value == '') return '';
  final n = value is num ? value : num.tryParse('$value');
  if (n == null || !n.isFinite) return '';
  // Sin ceros decimales de más ("5000000.00" → "5000000"; "5000000.50" → "5000000.5").
  final fixed = double.parse(n.toStringAsFixed(2));
  return normalizeMoneyInput(fixed == fixed.truncateToDouble() ? fixed.toInt().toString() : fixed.toString());
}

/// "5000000" → "$5,000,000 MXN"; con centavos, dos decimales. Vacío → fallback.
String formatMoneyWithCurrency(Object? value, {String currency = 'MXN', String fallback = '—'}) {
  if (value == null || value == '') return fallback;
  final n = value is num ? value : num.tryParse('$value');
  if (n == null || !n.isFinite) return fallback;
  final cents = (n * 100).round() % 100 != 0;
  final amount = NumberFormat.decimalPatternDigits(locale: 'es_MX', decimalDigits: cents ? 2 : 0).format(n);
  return '\$$amount $currency';
}

/// Da formato mientras se escribe ("$5,000,000") y conserva el cursor después del mismo dígito.
class MoneyInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final raw = normalizeMoneyInput(newValue.text);
    final formatted = formatMoneyInput(raw);
    final cursor = newValue.selection.baseOffset < 0 ? newValue.text.length : newValue.selection.baseOffset;
    final digits = RegExp(r'[\d.]').allMatches(newValue.text.substring(0, cursor.clamp(0, newValue.text.length))).length;
    var offset = formatted.length;
    if (digits <= 0) {
      offset = formatted.isEmpty ? 0 : 1;
    } else {
      var seen = 0;
      for (var i = 0; i < formatted.length; i++) {
        if (RegExp(r'[\d.]').hasMatch(formatted[i]) && ++seen == digits) {
          offset = i + 1;
          break;
        }
      }
    }
    return TextEditingValue(text: formatted, selection: TextSelection.collapsed(offset: offset));
  }
}
