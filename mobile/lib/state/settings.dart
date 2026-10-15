import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../ui/theme.dart';

enum FontSizeId { sm, md, lg, xl }

const fontSizeOptions = [
  (id: FontSizeId.sm, label: 'Pequeño', scale: 0.875),
  (id: FontSizeId.md, label: 'Normal', scale: 1.0),
  (id: FontSizeId.lg, label: 'Grande', scale: 1.125),
  (id: FontSizeId.xl, label: 'Extra grande', scale: 1.25),
];

/// Preferencias de apariencia (equivale a SettingsProvider de la web;
/// se guardan en el dispositivo).
class SettingsState extends ChangeNotifier {
  static const _key = 'c21genera-settings-v1';

  ThemeId theme = ThemeId.light;
  FontSizeId fontSize = FontSizeId.md;

  double get textScale => fontSizeOptions.firstWhere((o) => o.id == fontSize).scale;

  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getStringList(_key);
      if (raw == null) return;
      for (final entry in raw) {
        final [key, value] = entry.split('=');
        if (key == 'theme') theme = ThemeId.values.firstWhere((t) => t.name == value, orElse: () => ThemeId.light);
        if (key == 'fontSize') fontSize = FontSizeId.values.firstWhere((t) => t.name == value, orElse: () => FontSizeId.md);
      }
    } catch (_) {
      // Sin almacenamiento: se usan los valores predeterminados.
    }
  }

  Future<void> _save() async {
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(_key, ['theme=${theme.name}', 'fontSize=${fontSize.name}']);
    } catch (_) {}
  }

  void setTheme(ThemeId value) {
    theme = value;
    _save();
  }

  void setFontSize(FontSizeId value) {
    fontSize = value;
    _save();
  }
}
