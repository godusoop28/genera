import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import 'widgets.dart';

/// Recuadro para firmar con el dedo. Entrega la firma como data URL PNG
/// (igual que el SignaturePad de la web) o null si está vacío.
class SignaturePad extends StatefulWidget {
  const SignaturePad({super.key, required this.onChanged});
  final ValueChanged<String?> onChanged;

  @override
  State<SignaturePad> createState() => _SignaturePadState();
}

class _SignaturePadState extends State<SignaturePad> {
  final List<List<Offset>> strokes = [];
  Size size = Size.zero;

  Future<void> _export() async {
    if (strokes.every((s) => s.isEmpty) || size.isEmpty) {
      widget.onChanged(null);
      return;
    }
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawRect(Offset.zero & size, Paint()..color = Colors.white);
    _SignaturePainter(strokes, Colors.black).paint(canvas, size);
    final image = await recorder.endRecording().toImage(size.width.ceil(), size.height.ceil());
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    if (bytes == null) return;
    widget.onChanged('data:image/png;base64,${base64Encode(bytes.buffer.asUint8List())}');
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text('Firma', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: c.fg)),
      const SizedBox(height: 6),
      LayoutBuilder(builder: (context, box) {
        size = Size(box.maxWidth, 180);
        return Container(
          height: 180,
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border, width: 2)),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            // Reclama el gesto de inmediato para que firmar no desplace la hoja.
            child: RawGestureDetector(
              gestures: {
                _ImmediatePan: GestureRecognizerFactoryWithHandlers<_ImmediatePan>(
                  _ImmediatePan.new,
                  (r) => r
                    ..onStart = ((d) => setState(() => strokes.add([d.localPosition])))
                    ..onUpdate = ((d) => setState(() => strokes.last.add(d.localPosition)))
                    ..onEnd = ((_) => _export()),
                ),
              },
              child: CustomPaint(painter: _SignaturePainter(strokes, Colors.black), size: Size.infinite),
            ),
          ),
        );
      }),
      Row(children: [
        const Expanded(child: MutedText('Firma con el dedo dentro del recuadro.', size: 12)),
        TextButton(
          onPressed: () {
            setState(strokes.clear);
            widget.onChanged(null);
          },
          child: Text('Borrar', style: TextStyle(color: c.goldText)),
        ),
      ]),
    ]);
  }
}

class _SignaturePainter extends CustomPainter {
  _SignaturePainter(this.strokes, this.color);
  final List<List<Offset>> strokes;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 2.5
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke;
    for (final s in strokes) {
      if (s.length == 1) {
        canvas.drawCircle(s.first, 1.3, paint..style = PaintingStyle.fill);
        paint.style = PaintingStyle.stroke;
        continue;
      }
      final path = Path()..moveTo(s.first.dx, s.first.dy);
      for (final p in s.skip(1)) {
        path.lineTo(p.dx, p.dy);
      }
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _SignaturePainter oldDelegate) => true;
}

class _ImmediatePan extends PanGestureRecognizer {
  @override
  void addAllowedPointer(PointerDownEvent event) {
    super.addAllowedPointer(event);
    resolve(GestureDisposition.accepted);
  }
}
