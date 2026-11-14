import 'package:flutter/material.dart';

import 'widgets.dart';

/// Control de Cambios #46: respuesta a «¿Este documento aplica a la propiedad?».
/// later = sí aplica y se entrega después (text = razón); si no, no aplica (text = motivo).
typedef ApplicabilityAnswer = ({bool later, String text});

/// Pregunta si el documento aplica: «Sí aplica; lo entregaré después» (queda pendiente) o
/// «No aplica» (motivo obligatorio; deja de pedirse para este expediente).
Future<ApplicabilityAnswer?> askApplicability(
  BuildContext context, {
  required String documentName,
  required bool canMarkNotApplicable,
  String? notApplicableBlockedReason,
  int laterMinLength = 10,
}) {
  bool? later;
  var text = '';
  final naDisabled = !canMarkNotApplicable || notApplicableBlockedReason != null;
  return showAppSheet<ApplicabilityAnswer>(
    context,
    title: '¿"$documentName" aplica a la propiedad?',
    builder: (ctx, setSheet) {
      final min = later == false ? 15 : laterMinLength;
      return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const MutedText('¿Este documento aplica a la propiedad?'),
        RadioGroup<bool>(
          groupValue: later,
          onChanged: (v) => setSheet(() => later = v),
          child: Column(children: [
            const RadioListTile<bool>(
              value: true,
              contentPadding: EdgeInsets.zero,
              title: Text('Sí aplica; lo entregaré después.'),
              subtitle: Text('Queda como «Pendiente» y podrá solicitarse posteriormente.'),
            ),
            RadioListTile<bool>(
              value: false,
              enabled: !naDisabled,
              contentPadding: EdgeInsets.zero,
              title: const Text('No aplica.'),
              subtitle: Text(notApplicableBlockedReason ??
                  (canMarkNotApplicable
                      ? 'Quedará como «No aplica» y no se solicitará después para este expediente.'
                      : 'Tu rol no puede marcar documentos como «No aplica»; pídeselo a un revisor o director.')),
            ),
          ]),
        ),
        if (later != null) ...[
          const SizedBox(height: 8),
          AppTextField(
            label: later == false ? 'Indica el motivo (obligatorio)' : '¿Por qué se entrega después?',
            hint: later == false
                ? 'Ej. Es un terreno sin construcción: no tiene contrato de luz a su nombre.'
                : 'Ej. El cliente está tramitando el certificado y lo tendrá la próxima semana.',
            maxLines: 3,
            autofocus: true,
            helper: 'Mínimo $min caracteres. Queda en el expediente y en la bitácora.',
            onChanged: (v) => setSheet(() => text = v),
          ),
        ],
      ]);
    },
    footer: (ctx, setSheet) {
      final min = later == false ? 15 : laterMinLength;
      return [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.of(ctx).pop()),
        Btn(later == false ? 'Marcar como No aplica' : 'Dejar pendiente',
            onPressed: later != null && text.trim().length >= min ? () => Navigator.of(ctx).pop((later: later!, text: text.trim())) : null),
      ];
    },
  );
}
