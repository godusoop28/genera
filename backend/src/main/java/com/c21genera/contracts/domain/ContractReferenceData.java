package com.c21genera.contracts.domain;

import java.util.List;

/**
 * Texto fijo del contrato registrado ante PROFECO (ver
 * /reference/contrato/contrato-profeco-original.docx), transcrito
 * literalmente: no se reescriben cláusulas ni se inventan datos. Los
 * espacios a llenar viven en {@link ContractTemplate}.
 *
 * TODO: Confirmar dato canónico con CENTURY 21 Genera antes de producción
 * — el domicilio de la intermediaria difiere ligeramente entre este
 * contrato y el aviso de privacidad simplificado; no se normaliza aquí.
 */
public final class ContractReferenceData {

  private ContractReferenceData() {}

  public static final String TITLE =
      "Contrato de prestación de servicios de intermediación para la compraventa de inmueble destinado a casa habitación";

  public static final String PROFECO_NUMBER = "7/002193-2026";
  public static final String PROFECO_FILE_NUMBER = "PFC.B.E.7/002193-2026";
  public static final String PROFECO_REGISTRATION_DATE = "19 de marzo de 2026";

  public static final String INTERMEDIARY_LEGAL_NAME = "Grupo WILGEN y Asociados S. de R.L. de C.V.";
  public static final String INTERMEDIARY_COMMERCIAL_NAME = "CENTURY 21 Genera";
  public static final String LEGAL_REPRESENTATIVE = "Jorge Ricardo Jurado Espinal";
  public static final String INTERMEDIARY_ADDRESS = "Av Alta Tensión 403 local 1 Plaza Copa de Oro, Col Cantarranas CP 62448, Cuernavaca, Morelos";
  public static final String INTERMEDIARY_NOTICE_ADDRESS = "Av Alta Tensión 403 local 1 Plaza Copa de Oro, Col. Cantarranas, CP 62448";
  public static final String INTERMEDIARY_RFC = "GWA260224B11";
  public static final String INTERMEDIARY_EMAIL = "ventas@c21genera.com";
  public static final String INTERMEDIARY_PHONE = "7774540198";
  public static final String INTERMEDIARY_WHATSAPP = "7778005300";

  public record Clause(String title, String firstParagraph, List<String> extraParagraphs) {}

  public static final List<String> INTERMEDIARY_RIGHTS =
      List.of(
          "Recibir el pago de la contraprestación pactada al quedar formalizada la compraventa mediante escritura pública y el vendedor haya recibido de manera efectiva los recursos correspondientes al precio de la compraventa.");

  public static final List<String> INTERMEDIARY_OBLIGATIONS =
      List.of(
          "Analizar el inmueble a fin de determinar su precio aproximado en el mercado y la forma más adecuada para enajenarlo. Dicho análisis constituye una estimación orientativa con fines de comercialización y no sustituye, ni tiene los efectos de, un avalúo practicado por un perito valuador autorizado.",
          "Orientar al cliente sobre los trámites e instrumentos jurídicos necesarios para realizar la compraventa del inmueble e integrar la documentación indispensable para la escrituración de dicha compraventa ante Notario Público.",
          "Promover a nombre del cliente el inmueble.",
          "Realizar labores de intermediación con los posibles compradores a nombre del cliente a fin de lograr la operación de compraventa.",
          "Entregar al cliente de manera oportuna a su recepción, todas y cada una de las ofertas que reciba de posibles compradores.",
          "Informar por escrito al cliente sobre el desarrollo de las actividades concernientes a la prestación de los servicios y las ofertas recibidas.",
          "No condicionar la intermediación a la contratación de servicio(s) adicional(es).",
          "Respetar el derecho del cliente a cancelar la operación de consumo sin responsabilidad alguna dentro de los 5 días hábiles (plazo que no debe ser menor a 5 días hábiles contados a partir de la firma del contrato) posteriores a la firma del contrato.",
          "Responsabilizarse de los daños y perjuicios ocasionados al cliente como consecuencia de proceder con impericia, negligencia, dolo o mala fe.");

  public static final List<String> CLIENT_RIGHTS =
      List.of(
          "Recibir información veraz en la operación de intermediación.",
          "Que la prestadora del servicio realice las labores de intermediación con los posibles compradores en su nombre a fin de lograr la operación de compraventa.",
          "Recibir las ofertas de los posibles compradores.",
          "Cancelar la operación de consumo sin responsabilidad alguna dentro de los 5 días hábiles posteriores a la firma del contrato.",
          "Exigir los daños y perjuicios ocasionados en caso de que la intermediaria proceda con impericia, negligencia, dolo o mala fe.");

  public static final List<String> CLIENT_OBLIGATIONS =
      List.of(
          "Dar todas las facilidades necesarias para que el inmueble sea mostrado a los posibles compradores.",
          "No interferir durante las visitas que realicen la intermediaria y los posibles compradores a la misma.",
          "Tomar las providencias necesarias para salvaguardar en su caso los bienes muebles que se encuentren en el inmueble.",
          "Mantener el inmueble en condiciones de limpieza, higiene y seguridad, y al corriente de contribuciones fiscales, pago de servicios y demás obligaciones que se generen en relación con la mismo.",
          "No intervenir en las negociaciones hasta el momento que sea indispensable su participación para la celebración del contrato de compraventa respectivo.",
          "Pagar la contraprestación a la intermediaria a la celebración de la escritura pública de compraventa y haber recibido los recursos correspondientes.");

  /** Quinta a octava. */
  public static final List<Clause> FIXED_CLAUSES_BEFORE_PENALTY =
      List.of(
          new Clause(
              "Quinta. Vigencia.-",
              "El presente contrato tendrá una vigencia de 180 días naturales. Una vez vencido dicho plazo, la intermediaria debe devolver al cliente la documentación referida en la cláusula tercera, quedando sin efecto cualquier obligación de pago de comisión a favor de la intermediaria, salvo que durante la vigencia del presente contrato se hubiese formalizado la compraventa mediante escritura pública y el vendedor hubiese recibido efectivamente el pago correspondiente.",
              List.of()),
          new Clause(
              "Sexta. Exclusividad.-",
              "Las partes acuerdan expresamente que el presente contrato se celebra bajo la modalidad de exclusividad. Durante la vigencia del presente contrato, el cliente no podrá realizar por sí, o solicitar a persona alguna la prestación de los servicios objeto del presente contrato, en relación con el inmueble, pero sí podrá hacerlo respecto de cualquier otro inmueble que sea de su propiedad.",
              List.of(
                  "Si durante la vigencia del presente contrato, el cliente vende el inmueble por intermediación de un tercero, se obliga a pagar a la intermediaria la pena convencional dispuesta en la cláusula novena.")),
          new Clause(
              "Séptima. Revocación.-",
              "El cliente cuenta con un plazo de 5 días hábiles (plazo que no debe ser menor a 5 días hábiles contados a partir de la firma del contrato) posteriores a la firma del presente contrato para revocar su consentimiento sobre la operación sin responsabilidad alguna de su parte, mediante aviso por escrito, de conformidad con la cláusula décima segunda.",
              List.of(
                  "Para el caso de que la revocación se realice por correo certificado o registrado o servicio de mensajería, se tomará como fecha de revocación, la de recepción para su envío.",
                  "Ante la cancelación, la intermediaria se obliga a reintegrar todas las cantidades al cliente por el mismo medio en el que éste haya efectuado el pago, dentro de los 15 días hábiles siguientes a la fecha en que le sea notificada la revocación.",
                  "En caso de que no se restituyeren las cantidades al cliente dentro del plazo establecido, la intermediaria debe pagarle a su contraparte el interés moratorio del 1% mensual simple sobre la cantidad no devuelta por el tiempo que medie el retraso. Dicho interés se calcula de la siguiente manera: Dicho interés se calcula multiplicando el saldo vencido por el 1% mensual, dividiendo dicho porcentaje entre 30 días y multiplicándolo por el número de días naturales de retraso")),
          new Clause(
              "Octava. Rescisión.-",
              "Son causales de rescisión del presente contrato, cualquier incumplimiento a las obligaciones a las que se sujetan las partes en el mismo. Aunado a lo anterior, en caso de que la intermediaria actúe con impericia, negligencia, dolo o mala fe, se hará acreedora a la pena convencional establecida en la cláusula siguiente.",
              List.of(
                  "Si el incumplimiento fuera a cargo de la intermediaria, además de pagar la pena señalada en la cláusula sucesiva, debe restituir al cliente todas las cantidades pagadas por éste dentro de los 15 días hábiles siguientes a la rescisión del contrato, ya que en caso no restituir dichas cantidades dentro del plazo establecido, debe pagar al cliente el interés moratorio del 1% mensual simple sobre la cantidad no devuelta por el tiempo que medie el retraso. Dicho interés moratorio se calcula de la siguiente manera: Dicho interés se calcula multiplicando el saldo vencido por el 1% mensual, dividiendo dicho porcentaje entre 30 días y multiplicándolo por el número de días naturales de retraso.")));

  /** Décima y décima primera. */
  public static final List<Clause> FIXED_CLAUSES_BEFORE_NOTICES =
      List.of(
          new Clause(
              "Décima. Proceder en caso del fallecimiento del cliente.-",
              "En caso de fallecimiento del cliente antes del fenecimiento de la vigencia pactada en la cláusula quinta, se presume que su(s) sucesor(es) legítimo(s) lo sucede(n) en todos los derechos y obligaciones derivados del presente contrato, salvo que manifieste(n) a la intermediaria su deseo de no continuar con la intermediación, debiendo la intermediaria restituirle(s) las cantidades que le hubiere pagado el cliente con motivo del presente contrato, así como la documentación entregada; de conformidad con el Código Civil para el Estado Libre y Soberano de Morelos y demás disposiciones legales aplicables en materia sucesoria.",
              List.of()),
          new Clause(
              "Décima primera. Servicios adicionales.-",
              "En caso de que la intermediaria ofrezca servicios adicionales.- El listado de los servicios adicionales, especiales o conexos, que puede solicitar el cliente de forma opcional son detallados en cuanto a su descripción y costo en el \"Anexo D\".",
              List.of(
                  "La intermediaria sólo puede prestar servicios adicionales, especiales o conexos, si cuenta con el consentimiento escrito del cliente sobre los mismos. Las erogaciones distintas al precio de venta, deben ser aceptadas por escrito por el cliente, por lo que, la intermediaria sólo podrá hacer efectivo su pago, de manera posterior a haber recabado dicho consentimiento.",
                  "El cliente en cualquier momento podrá solicitar dar por terminada la prestación de los servicios adicionales, especiales o conexos, mediante aviso por escrito a la intermediaria, sin que ello implique la conclusión de la contratación principal.")));

  /** Décima tercera a décima octava. */
  public static final List<Clause> FIXED_CLAUSES_AFTER_NOTICES =
      List.of(
          new Clause(
              "Décima tercera. Canales de atención.-",
              "La intermediaria cuenta con los siguientes canales de atención para recibir comentarios, sugerencias y quejas del cliente: atención presencial en el domicilio de la intermediaria, WhatsApp 7778005300, teléfono 7774540198 y correo electrónico ventas@c21genera.com",
              List.of(
                  "Dichos canales están habilitados los días de Lunes a Viernes de 09:00 a 18:00 hrs y sabados de 09:00 a 13:00 hrs y el plazo de respuesta es de 72 hrs.")),
          new Clause(
              "Décima cuarta. Datos personales.-",
              "Los datos personales que se obtengan por la intermediaria deben ser tratados conforme a los principios de licitud, consentimiento, información, calidad, finalidad, lealtad, proporcionalidad y responsabilidad.",
              List.of(
                  "Para efectos de lo dispuesto en la Ley Federal de Protección de Datos Personales en Posesión de los Particulares, la intermediaria adjunta al presente contrato su Aviso de Privacidad en el \"Anexo E\", en el cual informa al titular de los datos personales, qué información recabará y con qué finalidades.",
                  "En caso de tratarse de datos personales sensibles, la intermediaria debe obtener consentimiento expreso y por escrito del titular para su tratamiento. No podrán crearse bases de datos que contengan datos personales sensibles, sin que se justifique la creación de las mismas para finalidades legítimas, concretas y acordes con las actividades o fines explícitos que persigue el sujeto regulado.",
                  "En caso de que los datos personales fueren obtenidos de manera indirecta del titular, se debe informar a los titulares de los datos personales que así lo soliciten cómo se dio la transferencia u obtención de dichos datos y se deben observar las siguientes reglas:",
                  "a. Si fueron tratados para una finalidad distinta prevista en una transferencia consentida, o si los datos fueron obtenidos de una fuente de acceso público, el aviso de privacidad se debe de dar a conocer al cliente en el primer contacto que se tenga con él.",
                  "b. Cuando la intermediaria pretenda utilizar los datos para una finalidad distinta a la consentida, el aviso de privacidad debe ser actualizado y darse a conocer al titular previo aprovechamiento de los datos personales.",
                  "La persona titular de los datos personales o su representante legal podrá solicitar a la intermediaria en cualquier momento el acceso, rectificación, cancelación u oposición respecto a sus datos personales y datos personales sensibles.")),
          new Clause(
              "Décima quinta. Competencia administrativa de la Procuraduría Federal del Consumidor (Profeco).-",
              "Ante cualquier controversia que se suscite sobre la interpretación o cumplimiento del presente contrato, el cliente puede acudir a la Profeco, la cual tiene funciones de autoridad administrativa encargada de promover y proteger los derechos e intereses de los consumidores y procurar la equidad y certeza jurídica en las relaciones de consumo, desde su ámbito competencial.",
              List.of()),
          new Clause(
              "Décima sexta. Competencia de las autoridades jurisdiccionales.-",
              "Para resolver cualquier controversia que se suscite sobre la interpretación o cumplimiento del presente contrato, las partes se someten a las autoridades jurisdiccionales competentes del Primer Distrito Judicial del Estado de Morelos, con residencia en Cuernavaca, Morelos, renunciando expresamente a cualquier otra jurisdicción que pudiera corresponderles, por razón de sus domicilios presentes o futuros o cualquier otra razón.",
              List.of()),
          new Clause(
              "Décima séptima. Plazo para ejercer la acción de responsabilidad civil.-",
              "En caso de que el incumplimiento de una de las partes al presente contrato, le ocasione a su contraparte daños y perjuicios; la segunda, podrá ejercer ante las autoridades jurisdiccionales indicadas en la cláusula décima sexta, la acción de responsabilidad civil en el plazo de diez años, contados a partir de la fecha en que la obligación pudo exigirse o el derecho pudo ejercitarse, de conformidad con los artículos 1244, 1511, 1514, 1518, 1718 y 1719 del Código Civil para el Estado Libre y Soberano de Morelos",
              List.of()),
          new Clause(
              "Décima octava. Registro del modelo de contrato de adhesión.-",
              "El presente modelo de contrato de adhesión fue inscrito el 19 marzo de 2026 en el Registro Público de Contratos de Adhesión de la Profeco bajo el número 7/002193-2026. Cualquier diferencia entre el texto del contrato de adhesión registrado ante la Procuraduría y el utilizado en perjuicio de los consumidores, se tendrá por no puesta.",
              List.of()));

  public static final String REPEP_NOTICE =
      "Todo consumidor que no desee recibir publicidad por parte de los proveedores en términos de la Ley Federal de Protección al Consumidor, puede inscribir de manera gratuita su número telefónico en el Registro Público de Consumidores (también denominado Registro Público para Evitar Publicidad) de la Profeco, a través del portal web https://repep.profeco.gob.mx/ o al 5596280000 (desde la Ciudad de México, Guadalajara y Monterrey) u 8009628000 (desde el resto de la República Mexicana).";

  public static final String MARKETING_PROHIBITION_NOTICE =
      "Queda prohibido a los proveedores que utilicen información sobre consumidores con fines mercadotécnicos o publicitarios y a sus clientes, utilizar la información relativa a los consumidores con fines diferentes a los mercadotécnicos o publicitarios, así como enviar publicidad a los consumidores que expresamente les hubieren manifestado su voluntad de no recibirla o que estén inscritos en el Registro Público de Consumidores (también denominado Registro Público para Evitar Publicidad). Los proveedores que sean objeto de publicidad son corresponsables del manejo de la información de consumidores cuando dicha publicidad la envíen a través de terceros.";

  /** Anexo B: {información, sí, no, medio}. */
  public static final List<String[]> ANNEX_B_ROWS =
      List.of(
          new String[] {
            "Personalidad del intermediario y autorización para promover la venta",
            "Sí",
            "",
            "Instrumento 10,186; RPC Cuernavaca; FME N-2026010454; autorización CENTURY 21 GENERA. Medio: físico/digital."
          },
          new String[] {"Carta de derechos", "Sí", "", "Anexo C."},
          new String[] {"Aviso de privacidad", "Sí", "", "https://century21mexico.com/avisodeprivacidad/649"},
          new String[] {"Beneficios adicionales", "No", "", "No aplica, salvo pacto escrito."},
          new String[] {"Opciones de pago, con especificación del monto a pagar en cada una de ellas", "", "", ""},
          new String[] {
            "Erogaciones del precio de venta", "", "", "Relación de gastos estimados entregada físicamente y/o enviada por correo electrónico/WhatsApp al cliente."
          },
          new String[] {"Condiciones bajo las cuales puede cancelar la operación", "Sí", "", "Cláusulas séptima y décima segunda."});

  public static final List<String> CLIENT_RIGHTS_LETTER =
      List.of(
          "Recibir información y publicidad veraz, clara y actualizada sobre la operación de consumo; de forma tal, que esté en posibilidad de tomar la mejor decisión.",
          "Firmar un contrato de adhesión bajo el modelo inscrito en la Procuraduría Federal del Consumidor, en el cual consten los términos y condiciones de la intermediación. Posterior a su firma, la intermediaria tiene la obligación de entregar una copia del contrato firmado al cliente.",
          "Contar con canales y mecanismos de atención gratuitos y accesibles para consultas, solicitudes, reclamaciones y sugerencias a la intermediaria y conocer el domicilio señalado por ésta para oír y recibir notificaciones.",
          "Tener a su disposición un Aviso de Privacidad para conocer y en su caso consentir el tratamiento que se dará a los datos personales que proporcione, que sus datos personales sean tratados conforme a la normatividad aplicable y conocer los mecanismos disponibles para realizar el ejercicio de sus derechos de acceso, rectificación, cancelación y oposición.",
          "Derecho a la protección por parte de las autoridades competentes y conforme a las leyes aplicables, incluyendo el derecho a presentar denuncias y reclamaciones ante las mismas.",
          "Los derechos previstos en esta carta, no excluyen otros derivados de tratados o convenciones internacionales de los que los Estados Unidos Mexicanos sea signatario; de la legislación interna ordinaria; o de reglamentos expedidos por las autoridades administrativas competentes.");
}
