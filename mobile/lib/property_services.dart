/// Control de Cambios #22: servicios con los que cuenta el inmueble, como casillas agrupadas. Igual
/// que la web (frontend/src/lib/property-services.ts): se guardan como texto legible en el mismo
/// campo ("Agua potable, Gas natural. Otros servicios: cisterna").
const propertyServiceGroups = <({String title, List<String> items})>[
  (title: 'Servicios básicos', items: ['Agua potable', 'Luz / Energía eléctrica', 'Gas natural', 'Gas LP (tanque / cilindro)']),
  (title: 'Comunicaciones', items: ['Telefonía fija', 'Telefonía móvil (cobertura)', 'Internet / Fibra óptica', 'Cable / TV de paga']),
  (
    title: 'Servicios urbanos / Infraestructura',
    items: ['Drenaje / Alcantarillado', 'Recolección de basura', 'Seguridad / Vigilancia privada', 'Estacionamiento', 'Áreas verdes / Jardín'],
  ),
  (
    title: 'Amenidades (condominios / fraccionamientos)',
    items: [
      'Alberca / Piscina',
      'Gimnasio',
      'Acceso controlado / Portón eléctrico',
      'Salón de usos múltiples',
      'Intercomunicador',
      'CCTV / Cámaras de seguridad',
      'Planta de emergencia / Generador',
      'Paneles solares',
    ],
  ),
];

final List<String> propertyServices = [for (final g in propertyServiceGroups) ...g.items];

const _othersPrefix = 'Otros servicios:';

String _norm(String v) {
  const from = 'áéíóúüñÁÉÍÓÚÜÑ';
  const to = 'aeiouunAEIOUUN';
  final b = StringBuffer();
  for (final ch in v.split('')) {
    final i = from.indexOf(ch);
    b.write(i >= 0 ? to[i] : ch);
  }
  return b.toString().toLowerCase().replaceAll(RegExp(r'\s+'), ' ').trim();
}

({List<String> selected, String others}) parseServices(String? value) {
  if (value == null || value.trim().isEmpty) return (selected: <String>[], others: '');
  var text = value.trim();
  var others = '';
  final idx = text.indexOf(_othersPrefix);
  if (idx >= 0) {
    others = text.substring(idx + _othersPrefix.length).trim().replaceFirst(RegExp(r'\.$'), '');
    text = text.substring(0, idx).trim().replaceFirst(RegExp(r'\.$'), '');
  }
  final byKey = {for (final s in propertyServices) _norm(s): s};
  final selected = <String>{};
  final leftovers = <String>[];
  for (final part in text.split(',').map((p) => p.trim()).where((p) => p.isNotEmpty)) {
    final match = byKey[_norm(part)];
    if (match != null) {
      selected.add(match);
    } else {
      leftovers.add(part);
    }
  }
  return (selected: propertyServices.where(selected.contains).toList(), others: [...leftovers, if (others.isNotEmpty) others].join(', '));
}

String formatServices(List<String> selected, String others) {
  final ordered = propertyServices.where(selected.contains).toList();
  return [if (ordered.isNotEmpty) ordered.join(', '), if (others.trim().isNotEmpty) '$_othersPrefix ${others.trim()}'].join('. ');
}
