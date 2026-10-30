import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'widgets.dart';

/// "2026-10-29" → "29 oct 2026" (o "29 de octubre de 2026" con [long]); es una fecha, no un instante.
String formatClosingDate(String isoDate, {bool long = false}) {
  final parts = isoDate.split('-').map(int.tryParse).toList();
  if (parts.length != 3 || parts.contains(null)) return isoDate;
  final d = DateTime(parts[0]!, parts[1]!, parts[2]!);
  return (long ? DateFormat("d 'de' MMMM 'de' y", 'es_MX') : DateFormat('d MMM y', 'es_MX')).format(d);
}

/// CC#32: la operación ya tiene el seguimiento de cierre completado. Es una marca aparte del estatus
/// del expediente (que no cambia), con la fecha de cierre cuando está registrada.
class ClosedBadge extends StatelessWidget {
  const ClosedBadge({super.key, required this.closedOn, this.withDate = true});
  final String closedOn;
  final bool withDate;

  @override
  Widget build(BuildContext context) =>
      AppBadge(withDate && closedOn.isNotEmpty ? 'Cerrada · ${formatClosingDate(closedOn)}' : 'Cerrada', tone: Tone.success, icon: Icons.flag_outlined);
}
