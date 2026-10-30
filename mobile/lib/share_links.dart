// CC#35: mensaje con el que se comparte la liga del cliente (igual que frontend/src/lib/share-links.ts).

import 'api/models.dart';

/// Cómo se identifica el expediente al compartir su liga: "Casa Las Flores — María Pérez".
/// El nombre del inmueble es la referencia que capturó el asesor; si no hay, el tipo de inmueble con
/// su clave (o el folio). Todo sale del mismo expediente de la liga.
String uploadLinkIdentifier(ExpedienteResponse e) {
  final reference = e.propertyReference?.trim() ?? '';
  final property = reference.isNotEmpty
      ? reference
      : [e.propertyTypeLabel?.trim(), e.propertyKey ?? e.folio].where((s) => s != null && s.isNotEmpty).join(' ');
  final owner = e.ownerDisplayName.trim();
  return owner.isNotEmpty ? '$property — $owner' : property;
}

/// Mensaje para WhatsApp con el identificador, el texto habitual de acceso y la liga.
String uploadLinkMessage(String identifier, String url) => [
      identifier,
      '',
      'Hola, te compartimos la liga de CENTURY 21 Genera para capturar tus datos y cargar los documentos de tu inmueble.',
      'No necesitas crear una cuenta. La liga es personal: no la compartas.',
      '',
      url,
    ].join('\n');

/// Liga de WhatsApp con el mensaje; con teléfono de 10 dígitos se abre el chat del cliente (+52).
String whatsappShareUrl(String message, [String? phone]) {
  var digits = (phone ?? '').replaceAll(RegExp(r'\D'), '');
  if (RegExp(r'^\d{10}$').hasMatch(digits)) digits = '52$digits';
  return 'https://wa.me/${digits.length >= 11 ? digits : ''}?text=${Uri.encodeComponent(message)}';
}
