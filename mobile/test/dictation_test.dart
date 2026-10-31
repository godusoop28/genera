import 'package:flutter_test/flutter_test.dart';
import 'package:genera_mobile/ui/dictation_field.dart';

void main() {
  test('CC#40: lo dictado se agrega al final sin borrar lo escrito', () {
    expect(appendDictation('Le llamé.', 'quedó el jueves'), 'Le llamé. quedó el jueves');
    expect(appendDictation('', 'visitó la casa'), 'Visitó la casa');
    expect(appendDictation('Hola', '  '), 'Hola');
  });
}
