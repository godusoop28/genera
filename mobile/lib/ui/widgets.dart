import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../labels.dart';
import 'theme.dart';
import 'tone.dart';

export 'theme.dart';
export 'tone.dart';

// ---------------------------------------------------------------------------
// Estructura de página
// ---------------------------------------------------------------------------

/// Contenido desplazable de una pantalla (equivale a PageContainer): subtítulo,
/// acciones y el cuerpo, con "jalar para actualizar".
class PageBody extends StatelessWidget {
  const PageBody({super.key, this.subtitle, this.actions, required this.children, this.onRefresh, this.controller, this.bottomPadding = 32});

  final String? subtitle;
  final List<Widget>? actions;
  final List<Widget> children;
  final Future<void> Function()? onRefresh;
  final ScrollController? controller;
  final double bottomPadding;

  @override
  Widget build(BuildContext context) {
    final list = ListView(
      controller: controller,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: EdgeInsets.fromLTRB(16, 16, 16, bottomPadding),
      children: [
        if (subtitle != null) ...[Text(subtitle!, style: TextStyle(color: context.c.muted, fontSize: 14, height: 1.4)), const SizedBox(height: 12)],
        if (actions != null && actions!.isNotEmpty) ...[Wrap(spacing: 8, runSpacing: 8, children: actions!), const SizedBox(height: 16)],
        ...children,
      ],
    );
    return onRefresh == null ? list : RefreshIndicator(color: Brand.darkGold, onRefresh: onRefresh!, child: list);
  }
}

/// Separación vertical estándar entre tarjetas.
List<Widget> spaced(Iterable<Widget> items, [double gap = 16]) {
  final out = <Widget>[];
  for (final w in items) {
    if (out.isNotEmpty) out.add(SizedBox(height: gap));
    out.add(w);
  }
  return out;
}

class AppCard extends StatelessWidget {
  const AppCard({super.key, required this.child, this.padding = const EdgeInsets.all(16), this.color, this.borderColor, this.onTap});

  final Widget child;
  final EdgeInsets padding;
  final Color? color;
  final Color? borderColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(14), side: BorderSide(color: borderColor ?? c.border));
    return Material(
      color: color ?? c.card,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: InkWell(onTap: onTap, child: Padding(padding: padding, child: child)),
    );
  }
}

class CardHeader extends StatelessWidget {
  const CardHeader({super.key, required this.title, this.description, this.action});

  final String title;
  final String? description;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: context.c.fg)),
                if (description != null) ...[
                  const SizedBox(height: 4),
                  Text(description!, style: TextStyle(fontSize: 13, color: context.c.muted, height: 1.4)),
                ],
              ],
            ),
          ),
          if (action != null) ...[const SizedBox(width: 8), action!],
        ],
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(text.toUpperCase(), style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, letterSpacing: 0.6, color: context.c.muted)),
      );
}

// ---------------------------------------------------------------------------
// Insignias, avisos y avatares
// ---------------------------------------------------------------------------

class AppBadge extends StatelessWidget {
  const AppBadge(this.text, {super.key, this.tone = Tone.neutral, this.icon});

  final String text;
  final Tone tone;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = context.c.tone(tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3.5),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 13, color: fg), const SizedBox(width: 4)],
          Flexible(child: Text(text, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: fg), overflow: TextOverflow.ellipsis)),
        ],
      ),
    );
  }
}

/// Aviso con fondo de color (bg-warning-bg, bg-info-bg…).
class Notice extends StatelessWidget {
  const Notice({super.key, required this.tone, this.icon, this.title, this.text, this.child, this.action});

  final Tone tone;
  final IconData? icon;
  final String? title;
  final String? text;
  final Widget? child;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = context.c.tone(tone);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(12), border: Border.all(color: fg.withValues(alpha: 0.2))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (icon != null) ...[Padding(padding: const EdgeInsets.only(top: 1), child: Icon(icon, size: 18, color: fg)), const SizedBox(width: 10)],
              Expanded(
                child: DefaultTextStyle.merge(
                  style: TextStyle(color: fg, fontSize: 13.5, height: 1.4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (title != null) Text(title!, style: const TextStyle(fontWeight: FontWeight.w600)),
                      if (text != null) Text(text!),
                      ?child,
                    ],
                  ),
                ),
              ),
            ],
          ),
          if (action != null) ...[const SizedBox(height: 10), action!],
        ],
      ),
    );
  }
}

class Avatar extends StatelessWidget {
  const Avatar(this.name, {super.key, this.size = 36});
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(color: Brand.gold.withValues(alpha: 0.25), shape: BoxShape.circle),
        child: Text(initials(name), style: TextStyle(fontSize: size * 0.34, fontWeight: FontWeight.w600, color: context.c.goldText)),
      );
}

/// Dato con etiqueta (dt/dd de la web).
class FieldView extends StatelessWidget {
  const FieldView(this.label, this.value, {super.key, this.child, this.upper = false});
  final String label;
  final String? value;
  final Widget? child;
  final bool upper;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(upper ? label.toUpperCase() : label,
              style: TextStyle(fontSize: upper ? 11 : 12, color: context.c.muted, letterSpacing: upper ? 0.5 : 0, fontWeight: upper ? FontWeight.w500 : FontWeight.normal)),
          const SizedBox(height: 2),
          child ?? Text(value ?? '—', style: TextStyle(fontSize: 14, color: context.c.fg)),
        ],
      );
}

/// Rejilla de datos en dos columnas.
class FieldGrid extends StatelessWidget {
  const FieldGrid({super.key, required this.children, this.columns = 2});
  final List<Widget> children;
  final int columns;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, box) {
        final w = (box.maxWidth - (columns - 1) * 16) / columns;
        return Wrap(spacing: 16, runSpacing: 14, children: [for (final c in children) SizedBox(width: w, child: c)]);
      });
}

// ---------------------------------------------------------------------------
// Botones
// ---------------------------------------------------------------------------

enum BtnVariant { primary, secondary, ghost, danger, dark }

class Btn extends StatelessWidget {
  const Btn(this.label, {super.key, this.onPressed, this.icon, this.variant = BtnVariant.primary, this.small = false, this.large = false, this.loading = false, this.expand = false});

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final BtnVariant variant;
  final bool small, large, loading, expand;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final (bg, fg, side) = switch (variant) {
      BtnVariant.primary => (Brand.gold, Brand.ink, BorderSide.none),
      BtnVariant.secondary => (c.card, c.fg, BorderSide(color: c.border)),
      BtnVariant.ghost => (Colors.transparent, c.fg, BorderSide.none),
      BtnVariant.danger => (c.dangerBg, c.dangerText, BorderSide(color: c.dangerText.withValues(alpha: 0.25))),
      BtnVariant.dark => (Brand.ink, Colors.white, BorderSide.none),
    };
    final enabled = onPressed != null && !loading;
    final height = small ? 36.0 : (large ? 52.0 : 44.0);
    final child = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (loading)
          SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: fg))
        else if (icon != null)
          Icon(icon, size: small ? 16 : 18, color: fg),
        if ((loading || icon != null) && label.isNotEmpty) const SizedBox(width: 8),
        if (label.isNotEmpty)
          Flexible(child: Text(label, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: small ? 13 : (large ? 16 : 14), fontWeight: FontWeight.w600, color: fg))),
      ],
    );
    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: Material(
        color: bg,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: side),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: enabled ? onPressed : null,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: height, minWidth: label.isEmpty ? height : 0),
            child: Padding(padding: EdgeInsets.symmetric(horizontal: label.isEmpty ? 0 : (small ? 12 : 16), vertical: 6), child: Center(widthFactor: 1, child: child)),
          ),
        ),
      ),
    );
  }
}

class LinkText extends StatelessWidget {
  const LinkText(this.text, {super.key, required this.onTap, this.icon, this.trailingIcon});
  final String text;
  final VoidCallback onTap;
  final IconData? icon;
  final IconData? trailingIcon;

  @override
  Widget build(BuildContext context) {
    final color = context.c.goldText;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(6),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (icon != null) ...[Icon(icon, size: 16, color: color), const SizedBox(width: 6)],
          Flexible(child: Text(text, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 14))),
          if (trailingIcon != null) ...[const SizedBox(width: 4), Icon(trailingIcon, size: 16, color: color)],
        ]),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Indicadores
// ---------------------------------------------------------------------------

class StatCard extends StatelessWidget {
  const StatCard({super.key, required this.icon, required this.value, required this.label, this.hint, this.tone = Tone.neutral, this.onTap});

  final IconData icon;
  final String value;
  final String label;
  final String? hint;
  final Tone tone;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final (bg, fg) = tone == Tone.neutral ? (Brand.gold.withValues(alpha: 0.2), c.goldText) : c.tone(tone);
    return AppCard(
      onTap: onTap,
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          Container(width: 42, height: 42, decoration: BoxDecoration(color: bg, shape: BoxShape.circle), child: Icon(icon, size: 20, color: fg)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(value, style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: c.fg, height: 1.1, fontFeatures: const [FontFeature.tabularFigures()])),
                const SizedBox(height: 2),
                Text(label, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: c.fg), maxLines: 1, overflow: TextOverflow.ellipsis),
                if (hint != null) Text(hint!, style: TextStyle(fontSize: 11, color: c.muted), maxLines: 2, overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Cuadrícula de 2 columnas para StatCard.
class StatGrid extends StatelessWidget {
  const StatGrid({super.key, required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, box) {
        final cols = box.maxWidth > 700 ? 4 : 2;
        final w = (box.maxWidth - (cols - 1) * 10) / cols;
        return Wrap(spacing: 10, runSpacing: 10, children: [for (final c in children) SizedBox(width: w, child: c)]);
      });
}

class ProgressLine extends StatelessWidget {
  const ProgressLine(this.value, {super.key, this.color, this.height = 6});
  final double value;
  final Color? color;
  final double height;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(99),
        child: LinearProgressIndicator(value: value.clamp(0, 1), minHeight: height, backgroundColor: context.c.appBg, color: color ?? Brand.gold),
      );
}

/// Filtros tipo "píldora" con contador (Segmented de la web), desplazables en horizontal.
class FilterChips<T> extends StatelessWidget {
  const FilterChips({super.key, required this.value, required this.options, required this.onChanged, this.wrap = false});

  final T value;
  final List<({T id, String label, int? count})> options;
  final ValueChanged<T> onChanged;
  final bool wrap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final chips = [
      for (final o in options)
        Padding(
          padding: EdgeInsets.only(right: wrap ? 0 : 8),
          child: Material(
            color: o.id == value ? Brand.gold : c.card,
            shape: StadiumBorder(side: BorderSide(color: o.id == value ? Brand.gold : c.border)),
            child: InkWell(
              customBorder: const StadiumBorder(),
              onTap: () => onChanged(o.id),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Text(o.label, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: o.id == value ? Brand.ink : c.muted)),
                  if (o.count != null) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                      decoration: BoxDecoration(color: o.id == value ? Colors.white.withValues(alpha: 0.4) : c.appBg, borderRadius: BorderRadius.circular(99)),
                      child: Text('${o.count}', style: TextStyle(fontSize: 11.5, color: o.id == value ? Brand.ink : c.muted)),
                    ),
                  ],
                ]),
              ),
            ),
          ),
        ),
    ];
    if (wrap) return Wrap(spacing: 8, runSpacing: 8, children: chips);
    return SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(children: chips));
  }
}

// ---------------------------------------------------------------------------
// Estados (cargando, error, vacío, restringido)
// ---------------------------------------------------------------------------

enum StateKind { loading, error, empty, noResults, restricted }

class StateMessage extends StatelessWidget {
  const StateMessage({super.key, required this.kind, required this.title, this.description, this.action});

  final StateKind kind;
  final String title;
  final String? description;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final icon = switch (kind) {
      StateKind.loading => null,
      StateKind.error => Icons.error_outline,
      StateKind.empty => Icons.inbox_outlined,
      StateKind.noResults => Icons.search_off,
      StateKind.restricted => Icons.lock_outline,
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 36),
      decoration: BoxDecoration(color: c.card, borderRadius: BorderRadius.circular(14), border: Border.all(color: c.border)),
      child: Column(
        children: [
          if (icon == null)
            const SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 2.5))
          else
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(color: kind == StateKind.error ? c.dangerBg : c.appBg, shape: BoxShape.circle),
              child: Icon(icon, color: kind == StateKind.error ? c.dangerText : c.muted),
            ),
          const SizedBox(height: 14),
          Text(title, textAlign: TextAlign.center, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: c.fg)),
          if (description != null) ...[
            const SizedBox(height: 6),
            Text(description!, textAlign: TextAlign.center, style: TextStyle(fontSize: 13.5, color: c.muted, height: 1.4)),
          ],
          if (action != null) ...[const SizedBox(height: 16), action!],
        ],
      ),
    );
  }
}

class LoadError extends StatelessWidget {
  const LoadError({super.key, required this.message, required this.onRetry, this.title = 'No se pudo cargar'});
  final String message;
  final String title;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => StateMessage(
        kind: StateKind.error,
        title: title,
        description: message,
        action: Btn('Reintentar', icon: Icons.refresh, variant: BtnVariant.secondary, onPressed: onRetry),
      );
}

class MutedText extends StatelessWidget {
  const MutedText(this.text, {super.key, this.size = 13.5, this.center = false, this.italic = false});
  final String text;
  final double size;
  final bool center;
  final bool italic;

  @override
  Widget build(BuildContext context) => Text(text,
      textAlign: center ? TextAlign.center : null,
      style: TextStyle(fontSize: size, color: context.c.muted, height: 1.4, fontStyle: italic ? FontStyle.italic : null));
}

// ---------------------------------------------------------------------------
// Formularios
// ---------------------------------------------------------------------------

/// Campo de texto con etiqueta arriba (Input de la web).
class AppTextField extends StatefulWidget {
  const AppTextField({
    super.key,
    this.label,
    this.value,
    this.onChanged,
    this.hint,
    this.helper,
    this.error,
    this.keyboardType,
    this.maxLines = 1,
    this.enabled = true,
    this.readOnly = false,
    this.obscure = false,
    this.autofocus = false,
    this.maxLength,
    this.onSubmitted,
    this.textInputAction,
    this.inputFormatters,
    this.autofillHints,
    this.controller,
  });

  final String? label;
  final String? value;
  final ValueChanged<String>? onChanged;
  final String? hint, helper, error;
  final TextInputType? keyboardType;
  final int maxLines;
  final bool enabled, readOnly, obscure, autofocus;
  final int? maxLength;
  final ValueChanged<String>? onSubmitted;
  final TextInputAction? textInputAction;
  final List<TextInputFormatter>? inputFormatters;
  final Iterable<String>? autofillHints;
  final TextEditingController? controller;

  @override
  State<AppTextField> createState() => _AppTextFieldState();
}

class _AppTextFieldState extends State<AppTextField> {
  late final TextEditingController _ctrl = widget.controller ?? TextEditingController(text: widget.value ?? '');

  @override
  void didUpdateWidget(covariant AppTextField old) {
    super.didUpdateWidget(old);
    // El valor cambió desde fuera (p. ej. "Usar dato detectado"): se refleja sin perder el cursor si es igual.
    if (widget.controller == null && widget.value != null && widget.value != _ctrl.text) {
      _ctrl.value = TextEditingValue(text: widget.value!, selection: TextSelection.collapsed(offset: widget.value!.length));
    }
  }

  @override
  void dispose() {
    if (widget.controller == null) _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (widget.label != null) ...[
          Text(widget.label!, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)),
          const SizedBox(height: 6),
        ],
        TextField(
          controller: _ctrl,
          onChanged: widget.onChanged,
          enabled: widget.enabled,
          readOnly: widget.readOnly,
          obscureText: widget.obscure,
          autofocus: widget.autofocus,
          keyboardType: widget.keyboardType ?? (widget.maxLines > 1 ? TextInputType.multiline : null),
          maxLines: widget.obscure ? 1 : widget.maxLines,
          minLines: widget.maxLines > 1 ? 2 : 1,
          maxLength: widget.maxLength,
          onSubmitted: widget.onSubmitted,
          textInputAction: widget.textInputAction,
          inputFormatters: widget.inputFormatters,
          autofillHints: widget.autofillHints,
          style: TextStyle(fontSize: 15, color: widget.enabled ? c.fg : c.muted),
          decoration: InputDecoration(hintText: widget.hint, helperText: widget.helper, errorText: widget.error, counterText: ''),
        ),
      ],
    );
  }
}

/// Selector desplegable (Select de la web).
class AppSelect<T> extends StatelessWidget {
  const AppSelect({super.key, this.label, required this.value, required this.options, required this.onChanged, this.enabled = true, this.hint});

  final String? label;
  final T value;
  final List<({T value, String label})> options;
  final ValueChanged<T>? onChanged;
  final bool enabled;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final hasValue = options.any((o) => o.value == value);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label != null) ...[Text(label!, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)), const SizedBox(height: 6)],
        DropdownButtonFormField<T>(
          initialValue: hasValue ? value : null,
          key: ValueKey('$label-$value-${options.length}'),
          isExpanded: true,
          hint: hint == null ? null : Text(hint!),
          dropdownColor: c.card,
          style: TextStyle(fontSize: 15, color: c.fg),
          items: [for (final o in options) DropdownMenuItem(value: o.value, child: Text(o.label, overflow: TextOverflow.ellipsis))],
          onChanged: enabled && onChanged != null ? (v) => onChanged!(v as T) : null,
        ),
      ],
    );
  }
}

List<({String value, String label})> optionsOf(Map<String, String> labels, {String? empty}) => [
      if (empty != null) (value: '', label: empty),
      for (final e in labels.entries) (value: e.key, label: e.value),
    ];

class ToggleRow extends StatelessWidget {
  const ToggleRow({super.key, required this.label, this.description, required this.value, this.onChanged});
  final String label;
  final String? description;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onChanged == null ? null : () => onChanged!(!value),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(label, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: context.c.fg)),
                if (description != null) Text(description!, style: TextStyle(fontSize: 12.5, color: context.c.muted)),
              ]),
            ),
            Switch(value: value, onChanged: onChanged),
          ]),
        ),
      );
}

/// Opción grande seleccionable (radio con texto), como los BigChoice del calendario.
class ChoiceTile extends StatelessWidget {
  const ChoiceTile({super.key, required this.selected, required this.onTap, required this.title, this.subtitle, this.icon, this.enabled = true});
  final bool selected;
  final VoidCallback onTap;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Material(
      color: selected ? Brand.gold.withValues(alpha: 0.12) : c.card,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: selected ? Brand.gold : c.border, width: selected ? 2 : 1)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: enabled ? onTap : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(selected ? Icons.radio_button_checked : Icons.radio_button_unchecked, size: 20, color: selected ? c.goldText : c.muted),
            const SizedBox(width: 10),
            if (icon != null) ...[Icon(icon, size: 20, color: selected ? c.goldText : c.muted), const SizedBox(width: 8)],
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: TextStyle(fontSize: 14.5, fontWeight: selected ? FontWeight.w600 : FontWeight.w500, color: c.fg)),
                if (subtitle != null) ...[const SizedBox(height: 2), Text(subtitle!, style: TextStyle(fontSize: 13, color: c.muted, height: 1.35))],
              ]),
            ),
          ]),
        ),
      ),
    );
  }
}

/// Sí / No (YesNo de la web).
class YesNo extends StatelessWidget {
  const YesNo({super.key, this.label, required this.value, required this.onChanged, this.enabled = true});
  final String? label;
  final bool? value;
  final ValueChanged<bool> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    Widget option(bool v) => Material(
          color: value == v ? Brand.gold.withValues(alpha: 0.15) : c.card,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10), side: BorderSide(color: value == v ? Brand.gold : c.border)),
          child: InkWell(
            borderRadius: BorderRadius.circular(10),
            onTap: enabled ? () => onChanged(v) : null,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Text(v ? 'Sí' : 'No', style: TextStyle(fontWeight: value == v ? FontWeight.w600 : FontWeight.normal, color: c.fg)),
            ),
          ),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (label != null && label!.isNotEmpty) ...[Text(label!, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)), const SizedBox(height: 6)],
      Row(children: [option(true), const SizedBox(width: 8), option(false)]),
    ]);
  }
}

/// Selector de fecha que guarda "YYYY-MM-DD".
class DateField extends StatelessWidget {
  const DateField({super.key, required this.label, required this.value, required this.onChanged, this.enabled = true, this.first, this.helper});
  final String label;
  final String? value;
  final ValueChanged<String?> onChanged;
  final bool enabled;
  final String? first;
  final String? helper;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final v = value != null && value!.length >= 10 ? value!.substring(0, 10) : null;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)),
      const SizedBox(height: 6),
      InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: !enabled
            ? null
            : () async {
                final initial = DateTime.tryParse(v ?? '') ?? DateTime.now();
                final firstDate = DateTime.tryParse(first ?? '') ?? DateTime(1900);
                final picked = await showDatePicker(
                  context: context,
                  initialDate: initial.isBefore(firstDate) ? firstDate : initial,
                  firstDate: firstDate,
                  lastDate: DateTime(2100),
                  locale: const Locale('es', 'MX'),
                );
                if (picked != null) {
                  onChanged('${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}');
                }
              },
        child: InputDecorator(
          decoration: InputDecoration(
            enabled: enabled,
            helperText: helper,
            suffixIcon: v != null && enabled
                ? IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () => onChanged(null), tooltip: 'Borrar fecha')
                : const Icon(Icons.calendar_today_outlined, size: 18),
          ),
          child: Text(v == null ? 'Elegir fecha' : formatDate(v), style: TextStyle(fontSize: 15, color: v == null ? c.muted : c.fg)),
        ),
      ),
    ]);
  }
}

// ---------------------------------------------------------------------------
// Mensajes, hojas y diálogos
// ---------------------------------------------------------------------------

void showToast(BuildContext context, String message) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  messenger?.hideCurrentSnackBar();
  messenger?.showSnackBar(SnackBar(content: Text(message), duration: Duration(milliseconds: 2500 + message.length * 30)));
}

Future<void> copyText(BuildContext context, String text, [String message = 'Copiado.']) async {
  await Clipboard.setData(ClipboardData(text: text));
  if (context.mounted) showToast(context, message);
}

Future<void> openUrl(BuildContext context, String url) async {
  final ok = await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  if (!ok && context.mounted) showToast(context, 'No se pudo abrir el enlace.');
}

/// Hoja inferior a pantalla casi completa (Modal de la web): título, contenido desplazable y botones fijos.
Future<T?> showAppSheet<T>(BuildContext context, {required String title, required Widget Function(BuildContext, StateSetter) builder, List<Widget> Function(BuildContext, StateSetter)? footer}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetContext) => StatefulBuilder(builder: (ctx, setState) {
      final c = ctx.c;
      final actions = footer?.call(ctx, setState);
      return Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: MediaQuery.of(ctx).size.height * 0.92),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 8, 8),
                child: Row(children: [
                  Expanded(child: Text(title, style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: c.fg))),
                  IconButton(icon: const Icon(Icons.close), tooltip: 'Cerrar', onPressed: () => Navigator.of(ctx).pop()),
                ]),
              ),
              Divider(height: 1, color: c.border),
              Flexible(child: SingleChildScrollView(padding: const EdgeInsets.fromLTRB(20, 16, 20, 16), child: builder(ctx, setState))),
              if (actions != null && actions.isNotEmpty) ...[
                Divider(height: 1, color: c.border),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  child: Wrap(alignment: WrapAlignment.end, spacing: 8, runSpacing: 8, children: actions),
                ),
              ],
            ],
          ),
        ),
      );
    }),
  );
}

/// Pide un motivo (ReasonModal de la web). Devuelve el texto o null si se canceló.
Future<String?> askReason(
  BuildContext context, {
  required String title,
  String? description,
  Widget? descriptionWidget,
  String label = 'Motivo',
  String? placeholder,
  int minLength = 5,
  bool danger = false,
  String confirmLabel = 'Confirmar',
}) {
  var text = '';
  return showAppSheet<String>(
    context,
    title: title,
    builder: (ctx, setState) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (description != null) ...[MutedText(description), const SizedBox(height: 12)],
      if (descriptionWidget != null) ...[DefaultTextStyle.merge(style: TextStyle(color: ctx.c.muted, fontSize: 13.5, height: 1.4), child: descriptionWidget), const SizedBox(height: 12)],
      AppTextField(
        label: label,
        hint: placeholder,
        maxLines: 3,
        autofocus: true,
        onChanged: (v) => setState(() => text = v),
        helper: minLength > 0 ? 'Mínimo $minLength caracteres.' : null,
      ),
    ]),
    footer: (ctx, setState) => [
      Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.of(ctx).pop()),
      Btn(confirmLabel, variant: danger ? BtnVariant.danger : BtnVariant.primary, onPressed: text.trim().length >= minLength ? () => Navigator.of(ctx).pop(text.trim()) : null),
    ],
  );
}

Future<bool> confirmDialog(BuildContext context, {required String title, String? message, String confirmLabel = 'Confirmar', bool danger = false}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
      content: message == null ? null : Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text('Cancelar', style: TextStyle(color: ctx.c.muted))),
        TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(confirmLabel, style: TextStyle(color: danger ? ctx.c.dangerText : ctx.c.goldText, fontWeight: FontWeight.w600))),
      ],
    ),
  );
  return result ?? false;
}

/// Lista con viñetas.
class Bullets extends StatelessWidget {
  const Bullets(this.items, {super.key, this.color, this.size = 13});
  final List<String> items;
  final Color? color;
  final double size;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final i in items)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('•  ', style: TextStyle(color: color ?? context.c.muted, fontSize: size)),
                Expanded(child: Text(i, style: TextStyle(color: color ?? context.c.muted, fontSize: size, height: 1.35))),
              ]),
            ),
        ],
      );
}

/// Sección plegable (details/summary de la web).
class Expandable extends StatefulWidget {
  const Expandable({super.key, required this.title, required this.child, this.icon, this.initiallyOpen = false});
  final String title;
  final Widget child;
  final IconData? icon;
  final bool initiallyOpen;

  @override
  State<Expandable> createState() => _ExpandableState();
}

class _ExpandableState extends State<Expandable> {
  late bool open = widget.initiallyOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: () => setState(() => open = !open),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(children: [
              if (widget.icon != null) ...[Icon(widget.icon, size: 18, color: c.muted), const SizedBox(width: 8)],
              Expanded(child: Text(widget.title, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg, fontSize: 13.5))),
              Icon(open ? Icons.expand_less : Icons.expand_more, color: c.muted),
            ]),
          ),
        ),
        if (open) Padding(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), child: widget.child),
      ]),
    );
  }
}
