import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../config.dart';
import '../../labels.dart';
import '../../ui/widgets.dart';

// Piezas compartidas del CRM (equivale a src/components/crm/CrmUi.tsx).

class StageBadge extends StatelessWidget {
  const StageBadge(this.stage, {super.key});
  final String stage;

  @override
  Widget build(BuildContext context) => AppBadge(label(stageLabels, stage), tone: stageTones[stage] ?? Tone.neutral);
}

const sourceIcons = {
  'WHATSAPP_BOT': Icons.chat_outlined,
  'WHATSAPP': Icons.chat_outlined,
  'PORTAL': Icons.apartment_outlined,
  'SOCIAL': Icons.share_outlined,
  'CAMPAIGN': Icons.campaign_outlined,
  'WEBSITE': Icons.language,
  'OUTDOOR': Icons.flag_outlined,
  'REFERRAL': Icons.person_add_alt,
  'WALK_IN': Icons.directions_walk,
  'EVENT': Icons.event_outlined,
  'PHONE': Icons.phone_outlined,
  'REACTIVATION': Icons.replay,
  'OTHER': Icons.help_outline,
};

class SourceLabel extends StatelessWidget {
  const SourceLabel({super.key, required this.source, this.detail, this.sourceLabel, this.color, this.size = 13});
  final String source;
  final String? detail;

  /// Control de Cambios #29: nombre de la fuente en el catálogo (si no viene, el del canal).
  final String? sourceLabel;
  final Color? color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final fg = color ?? context.c.muted;
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(sourceIcons[source] ?? Icons.label_outline, size: size + 1, color: fg),
      const SizedBox(width: 5),
      Flexible(
        child: Text(
          [sourceLabel ?? label(sourceLabels, source), ?detail].join(' · '),
          style: TextStyle(fontSize: size, color: fg),
          overflow: TextOverflow.ellipsis,
        ),
      ),
    ]);
  }
}

class AdvisorChip extends StatelessWidget {
  const AdvisorChip({super.key, required this.advisor, this.emptyLabel = 'Sin asignar'});
  final NamedRef? advisor;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) {
    if (advisor == null) return AppBadge(emptyLabel, tone: Tone.warning, icon: Icons.person_add_alt);
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Avatar(advisor!.name, size: 26),
      const SizedBox(width: 8),
      Flexible(child: Text(advisor!.name, style: TextStyle(fontSize: 13.5, color: context.c.fg), overflow: TextOverflow.ellipsis)),
    ]);
  }
}

/// Propiedad de 21Online asociada al lead (solo consulta; la ficha vive en 21Online).
class PropertyMini extends StatelessWidget {
  const PropertyMini({super.key, required this.property, this.emptyLabel = 'Sin propiedad identificada'});
  final LeadProperty? property;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final p = property;
    if (p == null) return MutedText(emptyLabel, size: 13);
    final title = p.title ?? p.key ?? 'Propiedad';
    return Row(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
      Padding(padding: const EdgeInsets.only(top: 2), child: Icon(Icons.home_outlined, size: 16, color: c.muted)),
      const SizedBox(width: 6),
      Flexible(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg), maxLines: 2, overflow: TextOverflow.ellipsis),
          if (p.online21Id != null)
            InkWell(
              onTap: () => openUrl(context, '${AppConfig.online21BaseUrl}/propiedades/ver/${p.online21Id}'),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                Text('${p.key ?? p.online21Id}', style: TextStyle(fontSize: 12, color: c.goldText)),
                const SizedBox(width: 3),
                Icon(Icons.open_in_new, size: 12, color: c.goldText),
              ]),
            )
          else if (p.key != null)
            Text(p.key!, style: TextStyle(fontSize: 12, color: c.muted)),
        ]),
      ),
    ]);
  }
}

/// Lista de barras horizontales: el ancho es proporcional al máximo de la lista.
class BarList extends StatelessWidget {
  const BarList({super.key, required this.rows, this.emptyLabel = 'Sin datos en el periodo'});
  final List<({String key, Widget label, int count, Widget? leading, VoidCallback? onTap})> rows;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final total = rows.fold<int>(0, (s, r) => s + r.count);
    final max = rows.fold<int>(1, (m, r) => r.count > m ? r.count : m);
    if (rows.isEmpty || total == 0) return Padding(padding: const EdgeInsets.symmetric(vertical: 20), child: MutedText(emptyLabel, center: true));
    return Column(children: [
      for (final r in rows)
        InkWell(
          onTap: r.onTap,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Column(children: [
              Row(children: [
                if (r.leading != null) ...[r.leading!, const SizedBox(width: 8)],
                Expanded(child: DefaultTextStyle.merge(style: TextStyle(fontSize: 13.5, color: c.fg), child: r.label)),
                Text.rich(TextSpan(children: [
                  TextSpan(text: '${r.count}', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                  TextSpan(text: ' · ${(r.count * 100 / total).round()}%', style: TextStyle(color: c.muted)),
                ]), style: const TextStyle(fontSize: 13)),
              ]),
              const SizedBox(height: 6),
              ProgressLine(r.count / max, height: 8),
            ]),
          ),
        ),
    ]);
  }
}

enum Period { today, d7, d30, all }

const periodLabels = {Period.today: 'Hoy', Period.d7: '7 días', Period.d30: '30 días', Period.all: 'Todo'};

/// Inicio del periodo en ISO (null = todo).
String? periodFrom(Period period) {
  final now = DateTime.now();
  return switch (period) {
    Period.all => null,
    Period.today => DateTime(now.year, now.month, now.day).toUtc().toIso8601String(),
    Period.d7 => now.subtract(const Duration(days: 7)).toUtc().toIso8601String(),
    Period.d30 => now.subtract(const Duration(days: 30)).toUtc().toIso8601String(),
  };
}

/// Colores por asesor (estables por id).
const _advisorPalette = [
  (bg: Color(0xFFE8F0FB), border: Color(0xFF3B6FB6), text: Color(0xFF1F3F6B)),
  (bg: Color(0xFFE9F6EE), border: Color(0xFF2F8A55), text: Color(0xFF1B4F31)),
  (bg: Color(0xFFFBEFE3), border: Color(0xFFC4772B), text: Color(0xFF6B3D10)),
  (bg: Color(0xFFF3E9F8), border: Color(0xFF8A4FB0), text: Color(0xFF4D2466)),
  (bg: Color(0xFFFDE8EC), border: Color(0xFFC2445E), text: Color(0xFF6E1F31)),
  (bg: Color(0xFFE6F5F5), border: Color(0xFF2C8C8C), text: Color(0xFF164B4B)),
  (bg: Color(0xFFF6F1DF), border: Color(0xFFA3862A), text: Color(0xFF5A4813)),
  (bg: Color(0xFFECEBFA), border: Color(0xFF5A57C2), text: Color(0xFF2D2B6B)),
];

({Color bg, Color border, Color text}) advisorColor(String id) {
  var h = 0;
  for (final unit in id.codeUnits) {
    h = (h * 31 + unit) & 0xFFFFFFFF;
  }
  return _advisorPalette[h % _advisorPalette.length];
}

class AdvisorDot extends StatelessWidget {
  const AdvisorDot(this.id, {super.key, this.size = 10});
  final String id;
  final double size;

  @override
  Widget build(BuildContext context) => Container(width: size, height: size, decoration: BoxDecoration(color: advisorColor(id).border, shape: BoxShape.circle));
}
