package com.c21genera.contracts.domain;

import java.util.List;

/**
 * Referencia textual del contrato real registrado ante PROFECO (ver
 * /reference/contrato/contrato-profeco-original.docx). Transcribe
 * fielmente los apartados del documento fuente para la vista previa de
 * prototipo (ver AGENTS §46/§72): no se reescriben cláusulas ni se
 * inventan datos.
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
  public static final String PROFECO_REGISTRATION_DATE = "19 de marzo de 2026";

  public static final String INTERMEDIARY_LEGAL_NAME = "Grupo WILGEN y Asociados S. de R.L. de C.V.";
  public static final String INTERMEDIARY_COMMERCIAL_NAME = "CENTURY 21 GENERA";
  public static final String LEGAL_REPRESENTATIVE = "Jorge Ricardo Jurado Espinal";
  public static final String INTERMEDIARY_ADDRESS =
      "Av Alta Tensión 403 local 1, Plaza Copa de Oro, Col. Cantarranas, C.P. 62448, Cuernavaca, Morelos";
  public static final String INTERMEDIARY_RFC = "GWA260224B11";

  public record Clause(String id, String title, String text) {}

  public static final List<Clause> CLAUSES =
      List.of(
          new Clause(
              "primera",
              "Primera. Objeto",
              "La intermediaria se obliga a realizar asesoría, publicidad, intermediación, entrega de ofertas e"
                  + " información al cliente con el fin de conseguir un comprador del inmueble destinado a casa"
                  + " habitación referido en la declaración II, inciso d."),
          new Clause(
              "segunda",
              "Segunda. Contraprestación económica",
              "Formalizada la compraventa, el cliente pagará a la intermediaria el 5% sobre el precio de"
                  + " compraventa efectivamente pagado, más el Impuesto al Valor Agregado correspondiente."),
          new Clause(
              "quinta",
              "Quinta. Vigencia",
              "180 días naturales. Vencido el plazo sin formalizarse la compraventa, la intermediaria debe"
                  + " devolver la documentación y no procede comisión."),
          new Clause(
              "sexta",
              "Sexta. Exclusividad",
              "El contrato se celebra bajo modalidad de exclusividad respecto del inmueble. Si el cliente vende"
                  + " por intermediación de un tercero durante la vigencia, se obliga a pagar la pena convencional"
                  + " de la cláusula novena."),
          new Clause(
              "septima",
              "Séptima. Revocación",
              "El cliente cuenta con 5 días hábiles posteriores a la firma para revocar su consentimiento sin"
                  + " responsabilidad, mediante aviso por escrito."),
          new Clause(
              "novena",
              "Novena. Penas convencionales",
              "Pena convencional equivalente al 100% de la comisión pactada en la cláusula segunda, para el caso"
                  + " de incumplimiento de cualquiera de las obligaciones contraídas."),
          new Clause(
              "decima-octava",
              "Décima octava. Registro del modelo de contrato de adhesión",
              "Inscrito el " + PROFECO_REGISTRATION_DATE + " en el Registro Público de Contratos de Adhesión de"
                  + " la PROFECO bajo el número " + PROFECO_NUMBER + "."));

  public static final List<String> ANNEX_A_FIELDS =
      List.of(
          "Superficie de terreno",
          "Superficie de construcción",
          "Número de recámaras",
          "Número de baños",
          "Estacionamientos",
          "Estado general de conservación",
          "Servicios con los que cuenta",
          "Características relevantes del inmueble");
}
