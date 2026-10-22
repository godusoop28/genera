import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api.dart' as api;
import '../api/client.dart';
import 'widgets.dart';

/// Control de Cambios #27 (adenda, como PropertyKeyModal en la web): asignar o corregir a mano la
/// clave de un expediente. Valida mientras se escribe (formato y si otro expediente ya la tiene) y no
/// deja guardar una clave en uso. Devuelve true si se guardó.
Future<bool> showPropertyKeySheet(BuildContext context, {required String expedienteId, required String folio, String? currentKey, String? suggestion}) async {
  final saved = await showAppSheet<bool>(
    context,
    title: currentKey != null ? 'Corregir clave de $folio' : 'Asignar clave a $folio',
    builder: (ctx, _) => _PropertyKeyForm(expedienteId: expedienteId, currentKey: currentKey, initial: currentKey ?? suggestion ?? ''),
  );
  return saved == true;
}

class _PropertyKeyForm extends StatefulWidget {
  const _PropertyKeyForm({required this.expedienteId, required this.currentKey, required this.initial});
  final String expedienteId;
  final String? currentKey;
  final String initial;

  @override
  State<_PropertyKeyForm> createState() => _PropertyKeyFormState();
}

class _PropertyKeyFormState extends State<_PropertyKeyForm> {
  late String key = widget.initial;
  String reason = '';
  Timer? _debounce;
  bool checking = false;
  bool saving = false;
  ({bool valid, bool available, String message, String key})? check;
  String? error;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    _validate(key);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  void _validate(String value) {
    _debounce?.cancel();
    final trimmed = value.trim();
    final id = ++_request;
    setState(() {
      check = null;
      error = null;
      checking = trimmed.isNotEmpty;
    });
    if (trimmed.isEmpty) return;
    _debounce = Timer(const Duration(milliseconds: 350), () async {
      try {
        final r = await api.checkPropertyKey(trimmed, widget.expedienteId);
        if (mounted && id == _request) {
          setState(() {
            check = (valid: r.valid, available: r.available, message: r.message, key: r.key);
            checking = false;
          });
        }
      } catch (err) {
        if (mounted && id == _request) {
          setState(() {
            error = errorText(err, 'No se pudo validar la clave.');
            checking = false;
          });
        }
      }
    });
  }

  Future<void> _save() async {
    setState(() => saving = true);
    try {
      await api.assignPropertyKey(widget.expedienteId, key.trim(), reason.trim().isEmpty ? null : reason.trim());
      if (mounted) Navigator.pop(context, true);
    } catch (err) {
      if (mounted) {
        setState(() {
          saving = false;
          error = errorText(err);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final r = check;
    final ok = r != null && r.valid && r.available;
    final canSave = !saving && !checking && ok && r.key != widget.currentKey;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const MutedText('Formato: tipo + operación (V venta, R renta) + iniciales del asesor + consecutivo, p. ej. CVEA03. Se guarda tal como la capturas.', size: 13),
        const SizedBox(height: 12),
        AppTextField(
          label: 'Clave',
          value: key,
          autofocus: true,
          maxLength: 24,
          onChanged: (v) {
            key = v.toUpperCase();
            _validate(key);
          },
        ),
        const SizedBox(height: 8),
        if (checking)
          const MutedText('Validando clave…', size: 13)
        else if (r != null)
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: ok ? c.successBg : c.dangerBg, borderRadius: BorderRadius.circular(10)),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(ok ? Icons.check_circle_outline : Icons.warning_amber_rounded, size: 18, color: ok ? c.successText : c.dangerText),
              const SizedBox(width: 8),
              Expanded(child: Text(r.message, style: TextStyle(fontSize: 13, color: ok ? c.successText : c.dangerText))),
            ]),
          ),
        if (error != null) ...[const SizedBox(height: 8), Text(error!, style: TextStyle(fontSize: 13, color: c.dangerText))],
        if (widget.currentKey != null) ...[
          const SizedBox(height: 12),
          AppTextField(label: 'Motivo (opcional)', maxLength: 500, hint: 'Ej. migración del inventario', onChanged: (v) => reason = v),
        ],
        const SizedBox(height: 12),
        Btn('Guardar clave', loading: saving, onPressed: canSave ? _save : null, expand: true),
      ],
    );
  }
}
