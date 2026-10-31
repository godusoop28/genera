import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_recognition_error.dart';
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';

import 'widgets.dart';

/// Une el texto existente con lo dictado sin pegar palabras (igual que appendDictation de la web).
String appendDictation(String current, String dictated) {
  final piece = dictated.trim();
  if (piece.isEmpty) return current;
  if (current.trim().isEmpty) return piece[0].toUpperCase() + piece.substring(1);
  return current.endsWith(' ') || current.endsWith('\n') ? '$current$piece' : '$current $piece';
}

/// CC#40: campo de texto con "Dictar reporte". El reconocimiento de voz es el del teléfono (español
/// de México); no se guarda audio, el texto se agrega al final de lo escrito y se guarda solo con el
/// botón del formulario. El micrófono se apaga al detener o al salir de la pantalla.
class DictationTextField extends StatefulWidget {
  const DictationTextField({super.key, this.label, this.hint, this.maxLines = 3, this.onChanged, this.controller, this.dictateLabel = 'Dictar reporte'});

  final String? label;
  final String? hint;
  final int maxLines;
  final ValueChanged<String>? onChanged;
  final TextEditingController? controller;
  final String dictateLabel;

  @override
  State<DictationTextField> createState() => _DictationTextFieldState();
}

class _DictationTextFieldState extends State<DictationTextField> {
  late final TextEditingController _ctrl = widget.controller ?? TextEditingController();
  final SpeechToText _speech = SpeechToText();
  bool _listening = false;
  String _partial = '';
  String? _error;

  @override
  void dispose() {
    _speech.cancel();
    if (widget.controller == null) _ctrl.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    setState(() => _error = null);
    final ok = await _speech.initialize(
      onError: (SpeechRecognitionError e) {
        if (!mounted) return;
        setState(() {
          _listening = false;
          _error = e.errorMsg.contains('no_match') || e.errorMsg.contains('speech_timeout')
              ? 'No se detectó voz. Vuelve a intentarlo hablando cerca del micrófono.'
              : 'No se pudo transcribir. Tu texto se conserva; vuelve a intentar o escríbelo.';
        });
      },
      onStatus: (status) {
        if (!mounted) return;
        if (status == 'done' || status == 'notListening') setState(() => _listening = false);
      },
    );
    if (!ok) {
      if (mounted) setState(() => _error = 'No hay permiso para el micrófono o el teléfono no tiene reconocimiento de voz. Puedes escribir el reporte.');
      return;
    }
    final locales = await _speech.locales();
    final locale = locales.where((l) => l.localeId.toLowerCase().startsWith('es_mx') || l.localeId.toLowerCase().startsWith('es-mx')).firstOrNull ??
        locales.where((l) => l.localeId.toLowerCase().startsWith('es')).firstOrNull;
    await _speech.listen(
      listenOptions: SpeechListenOptions(
        localeId: locale?.localeId,
        partialResults: true,
        listenMode: ListenMode.dictation,
        cancelOnError: true,
        pauseFor: const Duration(seconds: 6),
        listenFor: const Duration(minutes: 3),
      ),
      onResult: (SpeechRecognitionResult r) {
        if (!mounted) return;
        if (r.finalResult) {
          final next = appendDictation(_ctrl.text, r.recognizedWords);
          _ctrl.value = TextEditingValue(text: next, selection: TextSelection.collapsed(offset: next.length));
          widget.onChanged?.call(next);
          setState(() => _partial = '');
        } else {
          setState(() => _partial = r.recognizedWords);
        }
      },
    );
    if (mounted) setState(() => _listening = true);
  }

  Future<void> _stop() async {
    await _speech.stop();
    if (mounted) setState(() => _listening = false);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      AppTextField(label: widget.label, hint: widget.hint, maxLines: widget.maxLines, controller: _ctrl, onChanged: widget.onChanged),
      const SizedBox(height: 6),
      Btn(
        _listening ? 'Escuchando… toca para detener' : widget.dictateLabel,
        icon: _listening ? Icons.stop_circle_outlined : Icons.mic_none,
        variant: _listening ? BtnVariant.danger : BtnVariant.secondary,
        small: true,
        onPressed: _listening ? _stop : _start,
      ),
      if (_listening && _partial.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 4), child: Text(_partial, style: TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: c.muted))),
      if (_listening) const MutedText('Lo dictado se agrega al final del texto. Revísalo antes de guardar.', size: 12),
      if (_error != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(_error!, style: TextStyle(fontSize: 12, color: c.dangerText))),
    ]);
  }
}
