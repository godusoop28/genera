import 'package:flutter/material.dart';

import '../api/api.dart' as api;
import '../api/models.dart';
import 'widgets.dart';

/// Control de Cambios #26: campo "Tipo de inmueble". Al tocarlo se abre la lista completa del
/// catálogo: cada categoría como encabezado y debajo TODAS sus subcategorías, que se recorren
/// con desplazamiento (la búsqueda solo es de apoyo). Solo se elige una subcategoría; la
/// categoría se asigna sola. "Otro — especificar" pide describir el inmueble.
class PropertyTypePicker extends StatefulWidget {
  const PropertyTypePicker({
    super.key,
    required this.subtypeId,
    required this.detail,
    required this.onChanged,
    this.label = 'Tipo de inmueble',
    this.currentLabel,
    this.error,
  });

  final String? subtypeId;
  final String detail;
  final void Function(String? subtypeId, String detail, CatalogSubtype? subtype) onChanged;
  final String label;

  /// Etiqueta de la subcategoría ya guardada (por si después se desactivó en el catálogo).
  final String? currentLabel;
  final String? error;

  @override
  State<PropertyTypePicker> createState() => _PropertyTypePickerState();
}

class _PropertyTypePickerState extends State<PropertyTypePicker> {
  List<CatalogCategory>? _catalog;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    api.listPropertyCatalog().then((c) {
      if (mounted) setState(() => _catalog = c);
    }).catchError((Object _) {
      if (mounted) setState(() => _failed = true);
    });
  }

  Future<void> _open() async {
    final catalog = _catalog;
    if (catalog == null) return;
    var query = '';
    final picked = await showAppSheet<CatalogSubtype>(
      context,
      title: widget.label,
      builder: (ctx, setSheet) {
        final c = ctx.c;
        final q = query.trim().toLowerCase();
        final groups = [
          for (final cat in catalog)
            (category: cat, subtypes: cat.subtypes.where((s) => q.isEmpty || s.name.toLowerCase().contains(q) || cat.name.toLowerCase().contains(q)).toList()),
        ].where((g) => g.subtypes.isNotEmpty).toList();
        return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          AppTextField(hint: 'Buscar (opcional)', onChanged: (v) => setSheet(() => query = v)),
          const SizedBox(height: 8),
          if (groups.isEmpty) const Padding(padding: EdgeInsets.symmetric(vertical: 24), child: MutedText('Ningún tipo coincide con la búsqueda.')),
          for (final g in groups) ...[
            Padding(
              padding: const EdgeInsets.only(top: 14, bottom: 4),
              child: Text(g.category.name.toUpperCase(), style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: 0.6, color: c.goldText)),
            ),
            for (final s in g.subtypes)
              ListTile(
                dense: true,
                contentPadding: const EdgeInsets.only(left: 12, right: 4),
                title: Text(s.name, style: TextStyle(fontSize: 15, color: c.fg)),
                trailing: s.id == widget.subtypeId ? Icon(Icons.check, color: c.goldText) : null,
                onTap: () => Navigator.of(ctx).pop(s),
              ),
          ],
        ]);
      },
    );
    if (picked != null) widget.onChanged(picked.id, picked.requiresDetail ? widget.detail : '', picked);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final selected = findSubtype(_catalog, widget.subtypeId);
    final keepsInactive = selected == null && widget.subtypeId != null && widget.currentLabel != null;
    final text = selected != null
        ? selected.label(widget.detail)
        : keepsInactive
            ? '${widget.currentLabel} (ya no se ofrece)'
            : _catalog == null
                ? (_failed ? 'No se pudo cargar el catálogo' : 'Cargando catálogo…')
                : 'Selecciona el tipo de inmueble';
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(widget.label, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)),
      const SizedBox(height: 6),
      InkWell(
        onTap: _catalog == null ? null : _open,
        borderRadius: BorderRadius.circular(12),
        child: InputDecorator(
          decoration: InputDecoration(errorText: widget.error, suffixIcon: const Icon(Icons.expand_more)),
          child: Text(text, style: TextStyle(fontSize: 15, color: selected != null || keepsInactive ? c.fg : c.muted)),
        ),
      ),
      if (selected?.requiresDetail == true) ...[
        const SizedBox(height: 12),
        AppTextField(
          label: 'Describe el tipo de inmueble',
          value: widget.detail,
          hint: 'Ej. Capilla, gasolinera, escuela',
          maxLength: 160,
          onChanged: (v) => widget.onChanged(widget.subtypeId, v, selected),
        ),
      ],
    ]);
  }
}

/// Validación: subcategoría elegida y, si es "Otro", su descripción.
String? propertyTypeError(List<CatalogCategory>? catalog, String? subtypeId, String detail, {String? currentId}) {
  if (subtypeId == null) return 'Selecciona el tipo de inmueble (la subcategoría, no solo la categoría).';
  final s = findSubtype(catalog, subtypeId);
  if (s == null && subtypeId != currentId) return 'Selecciona un tipo de inmueble del catálogo.';
  if (s != null && s.requiresDetail && detail.trim().isEmpty) return 'Describe el tipo de inmueble.';
  return null;
}

/// Filtros de cartera por categoría y subcategoría (Control de Cambios #26). subtypeId
/// [api.unclassifiedSubtype] = pendientes de clasificación. Incluye lo desactivado del catálogo
/// para poder filtrar registros históricos.
class PropertyTypeFilters extends StatefulWidget {
  const PropertyTypeFilters({super.key, required this.categoryId, required this.subtypeId, required this.onChanged});
  final String categoryId, subtypeId;
  final void Function(String categoryId, String subtypeId) onChanged;

  @override
  State<PropertyTypeFilters> createState() => _PropertyTypeFiltersState();
}

class _PropertyTypeFiltersState extends State<PropertyTypeFilters> {
  List<CatalogCategory> _catalog = const [];

  @override
  void initState() {
    super.initState();
    api.listPropertyCatalog(includeInactive: true).then((c) {
      if (mounted) setState(() => _catalog = c);
    }).catchError((Object _) {});
  }

  @override
  Widget build(BuildContext context) {
    final category = _catalog.where((c) => c.id == widget.categoryId).firstOrNull;
    final subtypes = [for (final c in category == null ? _catalog : [category]) ...c.subtypes];
    final knownCategory = widget.categoryId.isEmpty || category != null;
    final knownSubtype = widget.subtypeId.isEmpty || widget.subtypeId == api.unclassifiedSubtype || subtypes.any((s) => s.id == widget.subtypeId);
    return Column(children: [
      AppSelect<String>(
        value: widget.categoryId,
        options: [
          (value: '', label: 'Todas las categorías'),
          for (final c in _catalog) (value: c.id, label: c.active ? c.name : '${c.name} (inactiva)'),
          if (!knownCategory) (value: widget.categoryId, label: 'Categoría seleccionada'),
        ],
        onChanged: (v) => widget.onChanged(v, ''),
      ),
      const SizedBox(height: 10),
      AppSelect<String>(
        value: widget.subtypeId,
        options: [
          (value: '', label: category == null ? 'Todas las subcategorías' : 'Todo ${category.name}'),
          (value: api.unclassifiedSubtype, label: 'Pendientes de clasificación'),
          for (final s in subtypes) (value: s.id, label: category == null ? '${s.categoryName} → ${s.name}' : (s.active ? s.name : '${s.name} (inactiva)')),
          if (!knownSubtype) (value: widget.subtypeId, label: 'Subcategoría seleccionada'),
        ],
        onChanged: (v) => widget.onChanged(widget.categoryId, v),
      ),
    ]);
  }
}
