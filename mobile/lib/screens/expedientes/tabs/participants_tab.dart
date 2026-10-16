import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../detected_data.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

/// Datos que el contrato necesita de cada participante (para avisar qué falta).
List<String> _missingFields(Participant p, bool isCompany) => [
      if (!isCompany && p.nationality == null) 'nacionalidad',
      if (!isCompany && (p.idDocumentType == null || p.idDocumentNumber == null)) 'identificación',
      if (!isCompany && p.idDocumentIssuer == null) 'autoridad emisora',
      if (!isCompany && p.birthDate == null) 'fecha de nacimiento',
      if ((p.role == 'OWNER' || p.role == 'CO_OWNER') && !isCompany && p.civilStatus == null) 'estado civil',
      if ((p.role == 'OWNER' || p.role == 'CO_OWNER') && p.rfc == null) 'RFC',
      if ((p.role == 'OWNER' || p.role == 'CO_OWNER') && p.address == null) 'domicilio',
    ];

/// La identificación (guardada o detectada) es de otro tipo que la credencial del INE.
bool _isForeignId(Participant p, String? detectedType) {
  final type = p.idDocumentType ?? detectedType;
  return type != null && type != 'INE';
}

const _participantFieldLabels = {
  'idDocumentType': 'Identificación',
  'idDocumentNumber': 'Número de identificación',
  'idDocumentIssuer': 'Emitida por',
  'nationality': 'Nacionalidad',
  'birthDate': 'Fecha de nacimiento',
  'curp': 'CURP',
  'rfc': 'RFC',
  'address': 'Domicilio',
};

class ParticipantsTab extends StatefulWidget {
  const ParticipantsTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<ParticipantsTab> createState() => _ParticipantsTabState();
}

/// Expedientes ya completados con sus documentos en esta sesión: reload() vuelve a montar la pestaña.
final _autofilled = <String>{};

class _ParticipantsTabState extends State<ParticipantsTab> {
  List<RequirementResponse> requirements = [];
  DetectedData? detected;

  ExpedienteCtx get ctx => widget.ctx;

  @override
  void initState() {
    super.initState();
    final e = ctx.expediente;
    final ownerId = ctx.participants.where((p) => p.role == 'OWNER').firstOrNull?.id;
    api.getRequirements(e.id).then((r) => mounted ? setState(() => requirements = r) : null).catchError((_) => null);
    api.getExpedienteExtractedFields(e.id).then((obs) => mounted ? setState(() => detected = buildDetectedData(obs, ownerId)) : null).catchError((_) => null);
    // Control de Cambios #7 y #9: lo validado (nacionalidad por INE, CURP, RFC...) se registra solo.
    if (context.read<AuthState>().can('EXPEDIENT_EDIT') && e.correctable && _autofilled.add(e.id)) {
      api.autofillExpediente(e.id).then((filled) async {
        if (filled > 0 && mounted) {
          showToast(context, 'Se registraron $filled dato(s) leídos y validados en los documentos.');
          await ctx.reload();
        }
      }).catchError((_) {});
    }
  }

  Future<void> edit(Participant? existing, String role) async {
    final value = existing?.copy() ?? Participant(role: role);
    var reason = '';
    var busy = false;
    final personType = ctx.expediente.personType;
    final found = existing?.id != null ? detected?.participants[existing!.id] : null;
    var version = 0;
    await showAppSheet(
      context,
      title: existing != null ? 'Editar participante' : 'Agregar participante',
      builder: (sheet, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, key: ValueKey(version), children: [
        _DetectedParticipantData(
          value: value,
          detected: found,
          onApply: (entries) => setSheet(() {
            for (final e in entries) {
              value.setField(e.key, e.value.value);
            }
            version++;
          }),
        ),
        ParticipantFields(value: value, personType: personType, onChanged: () => setSheet(() {})),
        const SizedBox(height: 14),
        AppTextField(label: 'Motivo del cambio (queda en la bitácora)', hint: 'Ej. Captura de datos de la identificación', onChanged: (v) => reason = v),
      ]),
      footer: (sheet, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(sheet)),
        Btn('Guardar', loading: busy, onPressed: value.fullName.trim().isEmpty
            ? null
            : () async {
                setSheet(() => busy = true);
                try {
                  if (existing?.id != null) {
                    await api.updateParticipant(ctx.expediente.id, existing!.id!, value.toRequest(), reason.trim());
                  } else {
                    await api.addParticipant(ctx.expediente.id, value.toRequest(), reason.trim());
                  }
                  if (sheet.mounted) Navigator.pop(sheet);
                  if (mounted) showToast(context, 'Participante guardado. Los documentos requeridos se actualizaron.');
                  await ctx.reload();
                } catch (err) {
                  setSheet(() => busy = false);
                  if (mounted) showToast(context, errorText(err));
                }
              }),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final e = ctx.expediente;
    final editable = auth.can('EXPEDIENT_EDIT') && e.correctable;
    final moral = e.personType == 'MORAL';
    final names = {for (final p in ctx.participants) p.id: p.fullName};
    final addable = moral
        ? [('LEGAL_REPRESENTATIVE', 'Agregar representante legal')]
        : [('CO_OWNER', 'Agregar copropietario'), if (e.signedByAttorney) ('ATTORNEY', 'Agregar apoderado')];
    return TabBody(
      onRefresh: ctx.reload,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(
              title: 'Participantes',
              description: 'Cada titular, representante o apoderado tiene sus propios datos, documentos y firma. Los datos marcados como faltantes los pide el contrato.',
            ),
            for (final p in ctx.participants)
              Builder(builder: (context) {
                final isCompany = moral && p.role == 'OWNER';
                final missing = _missingFields(p, isCompany);
                return Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(p.fullName, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                          MutedText(isCompany ? companyOwnerLabel : label(participantRoleLabels, p.role), size: 12),
                        ]),
                      ),
                      missing.isEmpty ? const AppBadge('Datos completos', tone: Tone.success) : const AppBadge('Faltan datos', tone: Tone.warning),
                    ]),
                    const SizedBox(height: 10),
                    FieldGrid(children: [
                      if (!isCompany) FieldView('Identificación', '${label(idDocumentLabels, p.idDocumentType)} ${p.idDocumentNumber ?? ''}'),
                      if (!isCompany && (p.role == 'OWNER' || p.role == 'CO_OWNER')) FieldView('Estado civil', label(civilStatusLabels, p.civilStatus)),
                      if (!isCompany) FieldView('Nacimiento', formatDate(p.birthDate)),
                      FieldView('RFC', p.rfc ?? '—'),
                      FieldView('Correo', p.email ?? '—'),
                      FieldView('Teléfono', p.phone ?? '—'),
                    ]),
                    if (missing.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text('Falta: ${missing.join(', ')}.', style: TextStyle(fontSize: 12, color: c.warningText))),
                    // Control de Cambios #9: con INE la nacionalidad es mexicana; con otra identificación hay que capturarla.
                    if (!isCompany && p.nationality == null && detected?.participants[p.id]?['nationality'] == null && _isForeignId(p, detected?.participants[p.id]?['idDocumentType']?.value))
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text('Su identificación no es una credencial del INE: captura su nacionalidad.', style: TextStyle(fontSize: 12, color: c.warningText)),
                      ),
                    if (editable)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Wrap(spacing: 8, children: [
                          Btn('Editar', icon: Icons.edit_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => edit(p, p.role)),
                          if (p.role != 'OWNER')
                            Btn('Quitar', icon: Icons.delete_outline, small: true, variant: BtnVariant.ghost, onPressed: () async {
                              final reason = await askReason(
                                context,
                                title: 'Quitar a ${p.fullName}',
                                description: 'Sus documentos dejarán de ser obligatorios y, si había un contrato sin firmar, quedará sin efecto.',
                                minLength: 5,
                                danger: true,
                                confirmLabel: 'Quitar',
                              );
                              if (reason == null) return;
                              try {
                                await api.removeParticipant(e.id, p.id!, reason);
                                if (context.mounted) showToast(context, 'Participante quitado.');
                                await ctx.reload();
                              } catch (err) {
                                if (context.mounted) showToast(context, errorText(err));
                              }
                            }),
                        ]),
                      ),
                  ]),
                );
              }),
            if (editable)
              Wrap(spacing: 8, runSpacing: 8, children: [
                for (final (role, text) in addable) Btn(text, icon: Icons.add, small: true, variant: BtnVariant.secondary, onPressed: () => edit(null, role)),
              ]),
          ]),
        ),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(title: 'Documentos requeridos', description: 'Se calculan con el tipo de cliente, cada participante, el tipo de inmueble y cómo se acredita la propiedad.'),
            for (final r in requirements)
              Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                child: Row(children: [
                  Expanded(
                    child: Text.rich(TextSpan(children: [
                      TextSpan(text: documentTypeLabel(r.type), style: TextStyle(color: c.fg)),
                      if (r.participantId != null && names[r.participantId] != null) TextSpan(text: ' — ${names[r.participantId]}', style: TextStyle(color: c.muted)),
                    ]), style: const TextStyle(fontSize: 13.5)),
                  ),
                  const SizedBox(width: 8),
                  AppBadge(r.required ? 'Obligatorio' : (r.conditional ? 'No aplica en este caso' : 'Opcional'), tone: r.required ? Tone.gold : Tone.neutral),
                ]),
              ),
          ]),
        ),
      ],
    );
  }
}

/// Lo que la revisión automática leyó en las identificaciones y constancias de este participante.
class _DetectedParticipantData extends StatelessWidget {
  const _DetectedParticipantData({required this.value, required this.detected, required this.onApply});
  final Participant value;
  final Map<String, Detected>? detected;
  final void Function(List<MapEntry<String, Detected>> entries) onApply;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final rows = (detected ?? {}).entries.where((e) => _participantFieldLabels.containsKey(e.key) && (value.field(e.key) ?? '') != e.value.value).toList();
    if (rows.isEmpty) return const SizedBox.shrink();
    final empty = rows.where((e) => (value.field(e.key) ?? '').isEmpty).toList();
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(12)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text('Datos detectados en sus documentos', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg))),
          if (empty.isNotEmpty) Btn('Llenar vacíos', small: true, variant: BtnVariant.secondary, onPressed: () => onApply(empty)),
        ]),
        const SizedBox(height: 6),
        for (final r in rows)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(children: [
              Expanded(
                child: Text.rich(TextSpan(children: [
                  TextSpan(text: '${_participantFieldLabels[r.key]} (${r.value.source}): '),
                  TextSpan(
                      text: r.key == 'idDocumentType' ? label(idDocumentLabels, r.value.value) : r.value.value, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
                ]), style: TextStyle(fontSize: 12, color: c.muted)),
              ),
              LinkText('Usar', onTap: () => onApply([r])),
            ]),
          ),
        const MutedText('Revísalos contra el documento antes de guardar.', size: 12),
      ]),
    );
  }
}

/// Campos de un participante. Se adaptan al tipo: la empresa titular (persona
/// moral) no tiene estado civil ni identificación personal.
class ParticipantFields extends StatelessWidget {
  const ParticipantFields({super.key, required this.value, required this.personType, required this.onChanged});
  final Participant value;
  final String personType;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final isCompany = personType == 'MORAL' && value.role == 'OWNER';
    final isOwner = value.role == 'OWNER' || value.role == 'CO_OWNER';
    Widget text(String key, String labelText, {String? hint, TextInputType? keyboard, int lines = 1}) => Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: AppTextField(
            label: labelText,
            hint: hint,
            value: value.field(key) ?? '',
            keyboardType: keyboard,
            maxLines: lines,
            onChanged: (v) {
              value.setField(key, v);
              onChanged();
            },
          ),
        );
    Widget select(String key, String labelText, Map<String, String> labels) => Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: AppSelect<String>(
            label: labelText,
            value: value.field(key) ?? '',
            options: optionsOf(labels, empty: 'Selecciona…'),
            onChanged: (v) {
              value.setField(key, v);
              onChanged();
            },
          ),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: AppTextField(
          label: isCompany ? 'Razón social' : 'Nombre completo (como aparece en su identificación)',
          hint: isCompany ? 'Ej. Inmobiliaria del Sol, S.A. de C.V.' : 'Ej. Juan Pérez López',
          value: value.fullName,
          onChanged: (v) {
            value.fullName = v;
            onChanged();
          },
        ),
      ),
      Padding(padding: const EdgeInsets.only(bottom: 12), child: MutedText('Rol: ${isCompany ? companyOwnerLabel : label(participantRoleLabels, value.role)}', size: 12)),
      if (!isCompany && isOwner && personType == 'FISICA') ...[
        select('civilStatus', 'Estado civil', civilStatusLabels),
        if (value.civilStatus == 'CASADO') select('maritalRegime', 'Régimen matrimonial', maritalRegimeLabels),
      ],
      text('email', 'Correo electrónico', keyboard: TextInputType.emailAddress),
      text('phone', 'Teléfono', keyboard: TextInputType.phone),
      if (!isCompany) ...[
        text('nationality', 'Nacionalidad', hint: 'Mexicana'),
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: DateField(
            label: 'Fecha de nacimiento',
            value: value.birthDate,
            onChanged: (v) {
              value.birthDate = v;
              onChanged();
            },
          ),
        ),
        select('idDocumentType', 'Identificación oficial', idDocumentLabels),
        text('idDocumentNumber', 'Folio de la identificación'),
        text('idDocumentIssuer', 'Autoridad que la emitió', hint: 'Instituto Nacional Electoral'),
        text('curp', 'CURP'),
      ],
      text('rfc', 'RFC'),
      text('address', isCompany ? 'Domicilio fiscal' : 'Domicilio particular', lines: 2),
    ]);
  }
}
