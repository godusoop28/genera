import 'package:flutter/material.dart';

import 'tone.dart';

/// Paleta oficial CENTURY 21 Genera (constante en todos los temas).
class Brand {
  static const gold = Color(0xFFB5A477);
  static const goldHover = Color(0xFFA69462);

  /// Versión del dorado para TEXTO (contraste ≥ 4.5:1 sobre blanco y marfil).
  static const darkGold = Color(0xFF76653A);

  /// Superficie de marca (menú, avatares, toasts): fija en todos los temas.
  static const ink = Color(0xFF242723);
  static const mediumGrey = Color(0xFF808285);
}

/// Tokens que cambian con el tema (equivalen a las variables CSS de globals.css).
class AppColors extends ThemeExtension<AppColors> {
  const AppColors({
    required this.fg,
    required this.appBg,
    required this.card,
    required this.border,
    required this.muted,
    required this.successBg,
    required this.successText,
    required this.warningBg,
    required this.warningText,
    required this.dangerBg,
    required this.dangerText,
    required this.infoBg,
    required this.infoText,
    required this.manualBg,
    required this.manualText,
  });

  final Color fg, appBg, card, border, muted;
  final Color successBg, successText, warningBg, warningText, dangerBg, dangerText, infoBg, infoText, manualBg, manualText;

  static const light = AppColors(
    fg: Color(0xFF1F2120),
    appBg: Color(0xFFF7F6F2),
    card: Color(0xFFFFFFFF),
    border: Color(0xFFE5E2DA),
    muted: Color(0xFF5F625D),
    successBg: Color(0xFFE8F3EA),
    successText: Color(0xFF256B39),
    warningBg: Color(0xFFFBF0DC),
    warningText: Color(0xFF8A5310),
    dangerBg: Color(0xFFFBE9E8),
    dangerText: Color(0xFFA3352C),
    infoBg: Color(0xFFE8EFF7),
    infoText: Color(0xFF2F557D),
    manualBg: Color(0xFFF1EFE9),
    manualText: Color(0xFF6B5A34),
  );

  static const dark = AppColors(
    fg: Color(0xFFF2F1EE),
    appBg: Color(0xFF17181A),
    card: Color(0xFF232427),
    border: Color(0xFF37383C),
    muted: Color(0xFF9A9A9E),
    successBg: Color(0xFF16301F),
    successText: Color(0xFF7BDCA0),
    warningBg: Color(0xFF3A2A12),
    warningText: Color(0xFFF0B35C),
    dangerBg: Color(0xFF3A1C1A),
    dangerText: Color(0xFFF4948B),
    infoBg: Color(0xFF1C2730),
    infoText: Color(0xFFA4CBE3),
    manualBg: Color(0xFF2C2717),
    manualText: Color(0xFFDDCCA0),
  );

  static const gold = AppColors(
    fg: Color(0xFF2B2418),
    appBg: Color(0xFFFAF6EE),
    card: Color(0xFFFFFDF9),
    border: Color(0xFFE6DCC7),
    muted: Color(0xFF7A6F5A),
    successBg: Color(0xFFE9F2E6),
    successText: Color(0xFF1F7A3D),
    warningBg: Color(0xFFF7ECD8),
    warningText: Color(0xFF8A5314),
    dangerBg: Color(0xFFF6E6E2),
    dangerText: Color(0xFF97362A),
    infoBg: Color(0xFFEEF1EE),
    infoText: Color(0xFF3D5A70),
    manualBg: Color(0xFFF1E9D4),
    manualText: Color(0xFF6B5A34),
  );

  static const contrast = AppColors(
    fg: Color(0xFF000000),
    appBg: Color(0xFFFFFFFF),
    card: Color(0xFFFFFFFF),
    border: Color(0xFF111111),
    muted: Color(0xFF333333),
    successBg: Color(0xFFD9F7E0),
    successText: Color(0xFF04521C),
    warningBg: Color(0xFFFFF0CC),
    warningText: Color(0xFF5C3D00),
    dangerBg: Color(0xFFFFE1DE),
    dangerText: Color(0xFF7A0D02),
    infoBg: Color(0xFFDBEEFF),
    infoText: Color(0xFF0B3A66),
    manualBg: Color(0xFFF0EAD8),
    manualText: Color(0xFF4A3A12),
  );

  /// Fondo y texto de una insignia/aviso según su tono.
  (Color bg, Color fg) tone(Tone t) => switch (t) {
        Tone.neutral => (appBg, muted),
        Tone.gold => (Brand.gold.withValues(alpha: 0.18), fg == light.fg || fg == gold.fg || fg == contrast.fg ? Brand.darkGold : Brand.gold),
        Tone.obsessed => (fg.withValues(alpha: 0.08), fg),
        Tone.success => (successBg, successText),
        Tone.warning => (warningBg, warningText),
        Tone.danger => (dangerBg, dangerText),
        Tone.info => (infoBg, infoText),
        Tone.manual => (manualBg, manualText),
      };

  /// Dorado para texto: en tema oscuro el dark-gold no contrasta, se usa el champagne.
  Color get goldText => this == dark ? Brand.gold : Brand.darkGold;

  @override
  AppColors copyWith() => this;

  @override
  AppColors lerp(ThemeExtension<AppColors>? other, double t) => (other is AppColors && t > 0.5) ? other : this;
}

extension AppColorsX on BuildContext {
  AppColors get c => Theme.of(this).extension<AppColors>()!;
}

enum ThemeId { light, dark, gold, contrast }

const themeOptions = [
  (id: ThemeId.light, label: 'Claro', description: 'Fondo claro, ideal para uso diurno.'),
  (id: ThemeId.dark, label: 'Oscuro', description: 'Fondo oscuro, reduce el brillo en ambientes con poca luz.'),
  (id: ThemeId.gold, label: 'Dorado', description: 'Superficies cálidas con acento premium.'),
  (id: ThemeId.contrast, label: 'Alto contraste', description: 'Máximo contraste para mejor legibilidad.'),
];

AppColors colorsFor(ThemeId id) => switch (id) {
      ThemeId.light => AppColors.light,
      ThemeId.dark => AppColors.dark,
      ThemeId.gold => AppColors.gold,
      ThemeId.contrast => AppColors.contrast,
    };

ThemeData buildTheme(ThemeId id) {
  final c = colorsFor(id);
  final isDark = id == ThemeId.dark;
  final scheme = ColorScheme(
    brightness: isDark ? Brightness.dark : Brightness.light,
    primary: Brand.gold,
    onPrimary: Brand.ink,
    secondary: Brand.ink,
    onSecondary: Colors.white,
    error: c.dangerText,
    onError: Colors.white,
    surface: c.card,
    onSurface: c.fg,
  );
  final border = OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: c.border));
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: c.appBg,
    extensions: [c],
    dividerColor: c.border,
    dividerTheme: DividerThemeData(color: c.border, space: 1, thickness: 1),
    textTheme: Typography.material2021().black.apply(bodyColor: c.fg, displayColor: c.fg),
    appBarTheme: AppBarTheme(
      backgroundColor: c.card,
      foregroundColor: c.fg,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0.5,
      shape: Border(bottom: BorderSide(color: c.border)),
      titleTextStyle: TextStyle(color: c.fg, fontSize: 17, fontWeight: FontWeight.w600),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: c.card,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      border: border,
      enabledBorder: border,
      disabledBorder: border.copyWith(borderSide: BorderSide(color: c.border.withValues(alpha: 0.6))),
      focusedBorder: border.copyWith(borderSide: const BorderSide(color: Brand.gold, width: 2)),
      errorBorder: border.copyWith(borderSide: BorderSide(color: c.dangerText)),
      focusedErrorBorder: border.copyWith(borderSide: BorderSide(color: c.dangerText, width: 2)),
      labelStyle: TextStyle(color: c.muted),
      floatingLabelStyle: TextStyle(color: c.goldText),
      hintStyle: TextStyle(color: c.muted.withValues(alpha: 0.8)),
      helperMaxLines: 4,
      errorMaxLines: 3,
    ),
    textSelectionTheme: TextSelectionThemeData(cursorColor: c.goldText, selectionColor: Brand.gold.withValues(alpha: 0.35), selectionHandleColor: Brand.gold),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: Brand.gold),
    checkboxTheme: CheckboxThemeData(fillColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? Brand.darkGold : null)),
    radioTheme: RadioThemeData(fillColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? Brand.darkGold : c.muted)),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? Colors.white : null),
      trackColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? Brand.gold : null),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: Brand.ink,
      contentTextStyle: const TextStyle(color: Colors.white, fontSize: 14),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    bottomSheetTheme: BottomSheetThemeData(backgroundColor: c.card, surfaceTintColor: Colors.transparent, showDragHandle: true),
    dialogTheme: DialogThemeData(backgroundColor: c.card, surfaceTintColor: Colors.transparent),
    popupMenuTheme: PopupMenuThemeData(color: c.card, surfaceTintColor: Colors.transparent),
    drawerTheme: const DrawerThemeData(backgroundColor: Brand.ink),
    tabBarTheme: TabBarThemeData(
      labelColor: c.fg,
      unselectedLabelColor: c.muted,
      indicatorColor: Brand.gold,
      dividerColor: c.border,
      labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
      tabAlignment: TabAlignment.start,
    ),
  );
}
