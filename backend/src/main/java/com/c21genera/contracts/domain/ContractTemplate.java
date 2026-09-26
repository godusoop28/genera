package com.c21genera.contracts.domain;

import static com.c21genera.contracts.domain.document.ContractDocument.Span.b;
import static com.c21genera.contracts.domain.document.ContractDocument.Span.t;

import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.domain.document.ContractDocument.Align;
import com.c21genera.contracts.domain.document.ContractDocument.Block;
import com.c21genera.contracts.domain.document.ContractDocument.Cell;
import com.c21genera.contracts.domain.document.ContractDocument.PageBreak;
import com.c21genera.contracts.domain.document.ContractDocument.Paragraph;
import com.c21genera.contracts.domain.document.ContractDocument.Row;
import com.c21genera.contracts.domain.document.ContractDocument.SectionHeading;
import com.c21genera.contracts.domain.document.ContractDocument.SignatureLines;
import com.c21genera.contracts.domain.document.ContractDocument.Span;
import com.c21genera.contracts.domain.document.ContractDocument.Table;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.expedientes.IdDocumentType;
import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.MaritalRegime;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.SignerCharacter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Arma el contrato de intermediación a partir del modelo registrado ante
 * PROFECO (7/002193-2026, ver /reference/contrato): transcribe su texto y
 * llena cada espacio con los datos del expediente según la variante
 * (persona física o moral, con o sin apoderado, uno o varios
 * propietarios, escritura o contrato privado, condominio, vivienda o
 * terreno).
 *
 * <p>Cada dato que falta se escribe como "[PENDIENTE: ...]" y se agrega a
 * {@link ContractDocument#missingItems()}: el servicio solo permite enviar a
 * firma un contrato sin pendientes. No se reescriben cláusulas: los incisos
 * que no aplican (p. ej. condominio) se conservan con la leyenda "No aplica"
 * para no alterar la numeración del modelo registrado.
 */
public final class ContractTemplate {

  private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"));

  /** Preguntas de la declaración II.g según el tipo de inmueble (clave -> texto del modelo). */
  public static final Map<String, String> HOUSING_CHECKLIST =
      orderedMap(
          "LICENSES",
          "Autorizaciones, licencias y permisos relativos a la construcción del inmueble objeto del contrato, sus especificaciones técnicas, clase de materiales utilizados, seguridad, uso de suelo y servicios básicos",
          "PLANS",
          "Planos estructurales, arquitectónicos y de instalaciones o, en su defecto, dictamen de las condiciones estructurales del inmueble",
          "CIVIL_PROTECTION",
          "Programa Interno de Protección Civil",
          "LIENS",
          "Existencia de gravámenes que afecten la propiedad del inmueble",
          "TAXES",
          "Condiciones en las cuales se encuentren el pago de contribuciones, derechos y servicios");

  public static final Map<String, String> LAND_CHECKLIST =
      orderedMap(
          "LAND_USE",
          "Uso de suelo aplicable al terreno conforme al plan de desarrollo urbano vigente, con su respectiva interpretación y copia del documento oficial vigente que acredite la licencia de uso de suelo del terreno",
          "SERVICES_FEASIBILITY",
          "Estudio de factibilidad técnico, oficial o avalado por autoridad competente para la instalación de servicios básicos en el terreno",
          "CONSTRUCTION_RULES",
          "En su caso, Reglamento de adecuaciones o construcción aplicable al fraccionamiento, condominio o conjunto habitacional al que forme parte el bien objeto del contrato",
          "CIVIL_PROTECTION",
          "Programa Interno de Protección Civil",
          "LIENS",
          "Existencia de gravámenes que afecten la propiedad del inmueble",
          "TAXES",
          "Condiciones en las cuales se encuentren el pago de contribuciones, derechos y servicios");

  public record ContractInput(
      ExpedienteSummary expediente,
      ManualClientDataView clientData,
      ContractCalculator.Result calculations,
      List<String> deliveredDocuments,
      int versionNumber,
      LocalDate today) {}

  private final ContractInput in;
  private final LinkedHashSet<String> missing = new LinkedHashSet<>();
  private final List<Block> blocks = new ArrayList<>();

  private ContractTemplate(ContractInput input) {
    this.in = input;
  }

  public static ContractDocument build(ContractInput input, boolean draft) {
    ContractTemplate template = new ContractTemplate(input);
    template.render();
    return new ContractDocument(ContractReferenceData.TITLE, draft, List.copyOf(template.missing), List.copyOf(template.blocks));
  }

  // ---------------------------------------------------------------------

  private void render() {
    ExpedienteSummary e = in.expediente();
    blocks.add(new Paragraph(List.of(b(ContractReferenceData.TITLE)), Align.LEFT, 0));
    renderIntro();

    blocks.add(new SectionHeading("Declaraciones"));
    renderIntermediaryDeclarations();
    renderClientDeclarations();
    blocks.add(Paragraph.of(b("III. Declaran las partes que:")));
    blocks.add(Paragraph.indented(t("a. Es su voluntad celebrar el presente contrato.")));

    blocks.add(new SectionHeading("Cláusulas"));
    renderClauses();
    renderSignaturePage();

    blocks.add(new PageBreak());
    renderAnnexA();
    blocks.add(new PageBreak());
    renderAnnexB();
    blocks.add(new PageBreak());
    renderAnnexC();
    renderAnnexD();

    if (e.propertyCaseType() == PropertyCaseType.COMMERCIAL) {
      missing.add(
          "Tipo de inmueble: el modelo registrado ante PROFECO solo ampara inmuebles destinados a casa habitación; un inmueble comercial requiere otro modelo de contrato");
    }
  }

  private void renderIntro() {
    List<Span> spans = new ArrayList<>();
    spans.add(
        t("Contrato de adhesión de prestación de servicios de intermediación para la compraventa de inmueble destinado a casa habitación, al que, en lo sucesivo, se le denominará el “contrato”, que celebran, por una parte, "));
    spans.add(b(ContractReferenceData.INTERMEDIARY_LEGAL_NAME));
    spans.add(t(", quien comparece al presente acto jurídico a través de "));
    spans.add(b(ContractReferenceData.LEGAL_REPRESENTATIVE));
    spans.add(t(" en su carácter de representante legal, a quien en lo sucesivo se le denominará la “intermediaria”, y por la otra, "));
    spans.addAll(clientAppearance());
    spans.add(t(", a quien en lo sucesivo se le denominará el “cliente”, ambos sujetos contractuales que en su conjunto serán designados como “las partes”."));
    blocks.add(Paragraph.of(spans));
  }

  /** "Juan Pérez, que por su propio derecho, en su carácter de propietario" y sus variantes. */
  private List<Span> clientAppearance() {
    ExpedienteSummary e = in.expediente();
    List<Span> spans = new ArrayList<>();
    List<ParticipantView> owners = owners();
    List<ParticipantView> representatives = representatives();

    if (e.personType() == PersonType.MORAL) {
      spans.add(value(owners.isEmpty() ? null : owners.getFirst().fullName(), "razón social de la persona moral"));
      spans.add(t(", representada en este acto por "));
      spans.addAll(joinNames(representatives, "nombre del representante legal"));
      spans.add(t(", en su carácter de "));
      spans.add(value(representationCapacity("representante legal"), "carácter del representante"));
      return spans;
    }
    spans.addAll(joinNames(owners, "nombre del propietario"));
    if (e.signerCharacter() == SignerCharacter.APODERADO) {
      spans.add(t(owners.size() > 1 ? ", representados en este acto por " : ", representado en este acto por "));
      spans.addAll(joinNames(representatives, "nombre del apoderado"));
      spans.add(t(", en su carácter de "));
      spans.add(value(representationCapacity("apoderado"), "carácter del apoderado"));
    } else {
      spans.add(t(", que por su propio derecho, en su carácter de "));
      spans.add(b(owners.size() > 1 ? "copropietarios" : "propietario"));
    }
    return spans;
  }

  private void renderIntermediaryDeclarations() {
    blocks.add(Paragraph.of(b("I. Declara la intermediaria que:")));
    blocks.add(
        Paragraph.indented(
            t("a. En caso de ser persona jurídica. Es una sociedad mercantil de Responsabilidad Limitada de Capital Variable, legalmente constituida de conformidad con las Leyes de los Estados Unidos Mexicanos, según consta en el instrumento público "),
            b("número 10,186"),
            t(", "),
            b("de fecha 24 de febrero de 2026"),
            t(", otorgado ante la fe de la "),
            b("Corredora Pública número 6 del Estado de Morelos, Licenciada Norma Elvia Martel Mota"),
            t(", instrumento que consta inscrito en el Registro Público de Comercio de "),
            b("Cuernavaca, Morelos"),
            t(", bajo el folio mercantil "),
            b("N-2026010454"),
            t(", y que puede ser consultado por el cliente en el portal oficial del Registro Público de Comercio / SIGER de la Secretaría de Economía.")));
    blocks.add(
        Paragraph.indented(
            t("b. En caso de ser persona física representada o jurídica.- Su representante legal cuenta con facultades suficientes para obligarla en los términos y condiciones del presente contrato, lo cual se acredita en términos del instrumento público "),
            b("número 10,186, de fecha 24 de febrero de 2026"),
            t(", otorgado ante la fe de la "),
            b("Corredora Pública número 6 del Estado de Morelos, Licenciada Norma Elvia Martel Mota"),
            t(", mismo que consta inscrito en el Registro Público de Comercio de "),
            b("Cuernavaca, Morelos"),
            t(", bajo el folio mercantil "),
            b("N-2026010454"),
            t(", facultades que no le han sido revocadas ni modificadas en forma alguna. Tal documentación puede ser consultada por el cliente en el portal oficial del Registro Público de Comercio / SIGER de la Secretaría de Economía.")));
    blocks.add(
        Paragraph.indented(
            b("c. Su ocupación habitual y periódica / objeto social"),
            t(" versa sobre la prestación de servicios de intermediación para la compraventa de inmuebles, incluyendo aquellos destinados para casa habitación a favor de terceros, con el fin de poner en contacto a la oferta y a la demanda de dichos inmuebles para formalizar la compraventa de los mismos.")));
    blocks.add(
        Paragraph.indented(
            t("d. En caso de contar con algún certificado y/o pertenecer a alguna Cámara o Asociación.- Cuenta con autorización comercial para operar bajo el nombre comercial "),
            b(ContractReferenceData.INTERMEDIARY_COMMERCIAL_NAME.toUpperCase(Locale.ROOT)),
            t(", conforme a la documentación corporativa correspondiente; no se declara pertenencia a Cámara o Asociación adicional, salvo manifestación expresa y documental de la intermediaria.")));
    blocks.add(
        Paragraph.indented(
            t("e. Su domicilio es el ubicado en "),
            b(ContractReferenceData.INTERMEDIARY_ADDRESS),
            t(" y su Registro Federal de Contribuyentes es "),
            b(ContractReferenceData.INTERMEDIARY_RFC)));
    blocks.add(
        Paragraph.indented(
            b("f."), t(" Puso a disposición del cliente, la información y documentación especificada en los \"Anexo B y C\" del presente contrato.")));
  }

  private void renderClientDeclarations() {
    ExpedienteSummary e = in.expediente();
    LegalDetails legal = legal();
    blocks.add(Paragraph.of(b("II. Declara el cliente que:")));

    if (e.personType() == PersonType.MORAL) {
      LegalDetails.CompanyData c = legal.company();
      blocks.add(
          Paragraph.indented(
              b("a.2. En caso de ser persona jurídica.-"),
              t(" Es una sociedad mercantil "),
              value(c == null ? null : c.companyType(), "tipo de sociedad (p. ej. Sociedad Anónima de Capital Variable)"),
              t(", legalmente constituida de conformidad con las Leyes de los Estados Unidos Mexicanos, según consta en el documento público "),
              value(c == null ? null : c.instrumentNumber(), "número del instrumento de constitución"),
              t(", de fecha "),
              value(c == null ? null : date(c.instrumentDate()), "fecha del instrumento de constitución"),
              t(", otorgado ante la fe del "),
              value(c == null ? null : c.notaryTitle(), "Notario o Corredor"),
              t(" Público "),
              value(c == null ? null : c.notaryNumber(), "número de notaría o correduría"),
              t(" de "),
              value(c == null ? null : c.notaryPlace(), "lugar de la notaría o correduría"),
              t(", el Licenciado "),
              value(c == null ? null : c.notaryName(), "nombre del notario o corredor"),
              t(", instrumento que consta inscrito en el Registro Público de Comercio de "),
              value(c == null ? null : c.commerceRegistryPlace(), "lugar del Registro Público de Comercio"),
              t(" bajo el folio mercantil "),
              value(c == null ? null : c.mercantileFolio(), "folio mercantil"),
              t(".")));
    } else {
      blocks.add(Paragraph.indented(b("a.1. En caso de ser persona física.-")));
      for (ParticipantView owner : owners()) {
        String who = owner.fullName();
        List<Span> spans = new ArrayList<>();
        if (owners().size() > 1) {
          spans.add(b(who + ": "));
        }
        spans.add(t("Es de nacionalidad "));
        spans.add(value(owner.nationality(), "nacionalidad de " + who));
        spans.add(t(", acredita su identidad en términos de "));
        spans.add(value(idLabel(owner.idDocumentType()), "tipo de identificación de " + who));
        spans.add(t(" con número de folio "));
        spans.add(value(owner.idDocumentNumber(), "folio de la identificación de " + who));
        spans.add(t(" (documento oficial emitido por "));
        spans.add(value(owner.idDocumentIssuer(), "autoridad emisora de la identificación de " + who));
        spans.add(t("); tiene "));
        spans.add(value(age(owner.birthDate()), "fecha de nacimiento (edad) de " + who));
        spans.add(t(" años; y su estado civil es "));
        spans.add(value(civilStatusLabel(owner.civilStatus(), owner.maritalRegime()), "estado civil de " + who));
        spans.add(t("."));
        blocks.add(new Paragraph(spans, Align.JUSTIFY, 2));
      }
    }

    renderRepresentationDeclaration();
    renderAddressAndRfc();
    renderOwnership();

    blocks.add(
        Paragraph.indented(
            t("e. El inmueble referido en el inciso previo es "),
            b(propertyKind()),
            t(" y cuenta con las características señaladas en el \"Anexo A\" del presente contrato.")));

    renderCondominium();
    renderPropertyChecklist();
  }

  private void renderRepresentationDeclaration() {
    ExpedienteSummary e = in.expediente();
    boolean represented = e.personType() == PersonType.MORAL || e.signerCharacter() == SignerCharacter.APODERADO;
    if (!represented) {
      blocks.add(
          Paragraph.indented(
              t("b. En caso de ser persona física representada o jurídica.- "),
              b("No aplica: el cliente comparece por su propio derecho.")));
      return;
    }
    LegalDetails.RepresentationData r = legal().representation();
    String defaultCapacity = e.personType() == PersonType.MORAL ? "representante legal" : "apoderado";
    List<Span> spans = new ArrayList<>();
    spans.add(t("b. En caso de ser persona física representada o jurídica.- Su "));
    spans.add(value(representationCapacity(defaultCapacity), "carácter del representante"));
    spans.add(t(", "));
    spans.addAll(joinNames(representatives(), "nombre del " + defaultCapacity));
    spans.add(t(", cuenta con facultades suficientes para obligarlo en los términos y condiciones del presente contrato, lo cual se acredita en términos del instrumento público "));
    spans.add(value(r == null ? null : r.instrumentNumber(), "número del instrumento del poder"));
    spans.add(t(", de fecha "));
    spans.add(value(r == null ? null : date(r.instrumentDate()), "fecha del instrumento del poder"));
    spans.add(t(", otorgado ante la fe del "));
    spans.add(value(r == null ? null : r.notaryTitle(), "Notario o Corredor que otorgó el poder"));
    spans.add(t(" Público número "));
    spans.add(value(r == null ? null : r.notaryNumber(), "número de notaría del poder"));
    spans.add(t(" de "));
    spans.add(value(r == null ? null : r.notaryPlace(), "lugar de la notaría del poder"));
    spans.add(t(", el Licenciado "));
    spans.add(value(r == null ? null : r.notaryName(), "nombre del notario del poder"));
    if (r != null && notBlank(r.registryPlace()) && notBlank(r.registryFolio())) {
      spans.add(t(", mismo que consta inscrito en el Registro Público de Comercio de "));
      spans.add(b(r.registryPlace()));
      spans.add(t(" bajo el folio mercantil "));
      spans.add(b(r.registryFolio()));
    } else if (e.personType() == PersonType.MORAL) {
      spans.add(t(", mismo que consta inscrito en el Registro Público de Comercio de "));
      spans.add(value(r == null ? null : r.registryPlace(), "lugar de inscripción del poder en el Registro Público de Comercio"));
      spans.add(t(" bajo el folio mercantil "));
      spans.add(value(r == null ? null : r.registryFolio(), "folio mercantil del poder"));
    }
    spans.add(t("; facultades que no le han sido revocadas ni modificadas en forma alguna."));
    blocks.add(Paragraph.indented(spans));
  }

  private void renderAddressAndRfc() {
    List<ParticipantView> owners = owners();
    if (owners.size() <= 1) {
      ParticipantView owner = owners.isEmpty() ? null : owners.getFirst();
      blocks.add(
          Paragraph.indented(
              t("c. Su domicilio es el ubicado en "),
              value(owner == null ? null : owner.address(), "domicilio del cliente"),
              t(" y su Registro Federal de Contribuyentes es "),
              value(owner == null ? null : rfcOf(owner), "RFC del cliente")));
      return;
    }
    List<Span> spans = new ArrayList<>();
    spans.add(t("c. Sus domicilios y Registros Federales de Contribuyentes son los siguientes: "));
    for (int i = 0; i < owners.size(); i++) {
      ParticipantView owner = owners.get(i);
      spans.add(b(owner.fullName()));
      spans.add(t(", domicilio ubicado en "));
      spans.add(value(owner.address(), "domicilio de " + owner.fullName()));
      spans.add(t(" y Registro Federal de Contribuyentes "));
      spans.add(value(rfcOf(owner), "RFC de " + owner.fullName()));
      spans.add(t(i == owners.size() - 1 ? "." : "; "));
    }
    blocks.add(Paragraph.indented(spans));
  }

  private void renderOwnership() {
    ExpedienteSummary e = in.expediente();
    LegalDetails legal = legal();
    blocks.add(
        Paragraph.indented(
            t(owners().size() > 1 && e.personType() == PersonType.FISICA ? "d. Son legítimos propietarios del inmueble ubicado en " : "d. Es legítimo propietario del inmueble ubicado en "),
            value(e.propertyAddress(), "domicilio del inmueble"),
            t(", como se acredita en términos de:")));

    if (e.accreditationType() == AccreditationType.ESCRITURA_PUBLICA) {
      LegalDetails.DeedData d = legal.deed();
      List<Span> spans = new ArrayList<>();
      spans.add(t("Escritura pública número "));
      spans.add(value(d == null ? null : d.number(), "número de la escritura"));
      spans.add(t(", otorgada el "));
      spans.add(value(d == null ? null : date(d.date()), "fecha de la escritura"));
      spans.add(t(", ante la fe del Lic. "));
      spans.add(value(d == null ? null : d.notaryName(), "nombre del notario de la escritura"));
      spans.add(t(", Notario Público número "));
      spans.add(value(d == null ? null : d.notaryNumber(), "número de notaría de la escritura"));
      spans.add(t(" de "));
      spans.add(value(d == null ? null : d.notaryPlace(), "lugar de la notaría de la escritura"));
      if (d != null && notBlank(d.registryData())) {
        spans.add(t(", debidamente inscrita en el Registro Público de la Propiedad bajo los datos registrales "));
        spans.add(b(d.registryData()));
        spans.add(t("."));
      } else {
        spans.add(t(", debidamente inscrita en el Registro Público de la Propiedad bajo los datos registrales que se desprenden de la escritura correspondiente / pendientes de confirmar con certificado actualizado."));
      }
      blocks.add(new Paragraph(spans, Align.JUSTIFY, 2));
    } else {
      LegalDetails.PrivateContractData p = legal.privateContract();
      blocks.add(
          new Paragraph(
              List.of(
                  t("Contrato privado celebrado entre "),
                  value(p == null ? null : p.sellerName(), "vendedor en el contrato privado"),
                  t(" y "),
                  value(p == null ? null : p.buyerName(), "comprador en el contrato privado"),
                  t(" el "),
                  value(p == null ? null : date(p.date()), "fecha del contrato privado"),
                  t(" ratificado el "),
                  value(p == null ? null : date(p.ratificationDate()), "fecha de ratificación"),
                  t(", ante "),
                  value(p == null ? null : p.ratifiedBefore(), "ante quién se ratificó (p. ej. la fe pública del Notario Público)"),
                  t(" número "),
                  value(p == null ? null : p.notaryNumber(), "número de notaría de la ratificación"),
                  t(" de "),
                  value(p == null ? null : p.notaryPlace(), "lugar de la notaría de la ratificación"),
                  t(", el Licenciado "),
                  value(p == null ? null : p.notaryName(), "nombre del notario de la ratificación"),
                  t(", ratificación debidamente inscrita el "),
                  value(p == null ? null : date(p.registryDate()), "fecha de inscripción de la ratificación"),
                  t(" en el Registro Público de la Propiedad de "),
                  value(p == null ? null : p.registryPlace(), "Registro Público de la Propiedad donde se inscribió"),
                  t(" bajo el folio real "),
                  value(p == null ? null : p.realFolio(), "folio real"),
                  t(".")),
              Align.JUSTIFY,
              2));
    }
  }

  private void renderCondominium() {
    if (!in.expediente().condominiumRegime()) {
      blocks.add(
          Paragraph.indented(
              t("f. En caso de que el inmueble esté sujeto al régimen de propiedad en condominio.- "),
              b("No aplica: el inmueble no está sujeto al régimen de propiedad en condominio.")));
      return;
    }
    LegalDetails.CondominiumData c = legal().condominium();
    blocks.add(
        Paragraph.indented(
            t("f. En caso de que el inmueble esté sujeto al régimen de propiedad en condominio.- El inmueble indicado en el inciso previo, está sujeto al régimen de propiedad en condominio; en términos de la escritura pública "),
            value(c == null ? null : c.deedNumber(), "número de la escritura del régimen de condominio"),
            t(", otorgada en fecha "),
            value(c == null ? null : date(c.date()), "fecha de la escritura del régimen de condominio"),
            t(", ante la fe del Notario Público "),
            value(c == null ? null : c.notaryNumber(), "número de notaría del régimen de condominio"),
            t(" de "),
            value(c == null ? null : c.notaryPlace(), "lugar de la notaría del régimen de condominio"),
            t(", el Licenciado "),
            value(c == null ? null : c.notaryName(), "nombre del notario del régimen de condominio"),
            t(" y debidamente inscrita el "),
            value(c == null ? null : date(c.registryDate()), "fecha de inscripción del régimen de condominio"),
            t(" en el Registro Público de la Propiedad de localidad bajo el folio real "),
            value(c == null ? null : c.realFolio(), "folio real del régimen de condominio"),
            t(", instrumento en el cual están referidas las correspondientes áreas de uso común y porcentaje indiviso.")));
  }

  private void renderPropertyChecklist() {
    blocks.add(Paragraph.indented(t("g. Cuenta con la siguiente documentación e información relativa del inmueble:")));
    boolean land = in.expediente().propertyCaseType() == PropertyCaseType.RESIDENTIAL_LAND;
    Map<String, String> questions = land ? LAND_CHECKLIST : HOUSING_CHECKLIST;
    Map<String, Boolean> answers = legal().propertyChecklist() == null ? Map.of() : legal().propertyChecklist();

    List<Row> rows = new ArrayList<>();
    rows.add(Row.of(Cell.header(land ? "En caso de terreno destinado a casa habitación.-" : "En caso de vivienda.-", 3)));
    rows.add(Row.of(Cell.bold("Documentación/información"), new Cell(List.of(List.of(b("¿Cuenta con la documentación/información?"))), 2, false, Align.CENTER)));
    rows.add(Row.of(Cell.text(""), Cell.centered("Si"), Cell.centered("No")));
    questions.forEach(
        (key, question) -> {
          Boolean answer = answers.get(key);
          if (answer == null) {
            missing.add("Declaración II.g: indicar si cuenta con \"" + shorten(question) + "\"");
            rows.add(
                Row.of(
                    Cell.text(question),
                    new Cell(List.of(List.of(Span.pending("Sí/No"))), 2, false, Align.CENTER)));
          } else {
            rows.add(Row.of(Cell.text(question), Cell.centered(answer ? "X" : ""), Cell.centered(answer ? "" : "X")));
          }
        });
    blocks.add(new Table(List.of(70, 15, 15), rows));
  }

  // ---------------------------------------------------------------------
  // Cláusulas
  // ---------------------------------------------------------------------

  private void renderClauses() {
    ContractCalculator.Result c = in.calculations();
    blocks.add(
        Paragraph.of(
            b("Primera. Objeto.-"),
            t(" En virtud del presente contrato, la intermediaria experta en operaciones inmobiliarias, se obliga a realizar las siguientes actividades con base en las necesidades y características concretas del cliente y del inmueble destinado a casa habitación referido en la declaración II, inciso d, con el fin de conseguir un comprador del mismo:")));
    blocks.add(
        Paragraph.indented(
            b("I. Asesoría.-"),
            t(" Analizar el inmueble a fin de determinar su precio aproximado en el mercado y la forma más adecuada para enajenarlo, tomando en consideración sus características físicas, urbanas y fiscales de conformidad con la legislación aplicable; precio aproximado en el mercado que resulta cantidad de "),
            money(c == null ? null : c.price(), "precio aproximado de mercado (precio autorizado)"),
            t(". Asimismo, orientar al cliente sobre los trámites e instrumentos jurídicos necesarios para realizar la compraventa del inmueble e integrar la documentación indispensable para la escrituración de dicha compraventa ante Notario Público.")));
    blocks.add(
        Paragraph.indented(
            b("II. Publicidad.-"),
            t(" Promover a nombre del cliente el inmueble, utilizando los medios de difusión que considere apropiados conforme a su naturaleza. La publicidad será realizada a través de "),
            value(legal().advertisingMedia(), "medios de publicidad autorizados por el cliente"),
            t(", que considere adecuados conforme a la naturaleza del inmueble previamente autorizados por el cliente.")));
    blocks.add(
        Paragraph.indented(
            t("Asimismo, proporcionar toda la información sobre el inmueble y mostrarlo a los posibles compradores, cuantas veces sea necesario, para lo cual debe notificar al cliente.")));
    blocks.add(
        Paragraph.indented(
            b("III. Intermediación.-"),
            t(" Realizar labores de intermediación con los posibles compradores a nombre del cliente a fin de lograr la operación de compraventa, de conformidad con los términos de este contrato, logrando el mejor precio y condiciones de venta.")));
    blocks.add(
        Paragraph.indented(
            b("IV. Oferta.-"),
            t(" Entregar al cliente dentro de los 3 días siguientes de la recepción, todas y cada una de las ofertas que reciba de posibles compradores del inmueble con el fin de que el cliente estudie y determine la aceptación o negación de las mismas.")));
    blocks.add(
        Paragraph.indented(
            t("El cliente debe notificar la aceptación de la oferta por escrito y dentro de los 3 días hábiles siguientes a la recepción de la misma, con el fin de que la intermediaria realice los actos necesarios para proceder a la formalización de la compraventa del inmueble. En caso de que el cliente no notifique a la intermediaria la aceptación dentro del plazo establecido, dicho silencio será considerado como negación o no aceptación de la oferta correspondiente, por lo que la intermediaria continuará ofreciendo el inmueble.")));
    blocks.add(
        Paragraph.indented(
            b("V. Información.-"),
            t(" Informar por escrito al cliente sobre el desarrollo de las actividades concernientes a la prestación de los servicios y las ofertas recibidas.")));
    blocks.add(
        Paragraph.indented(
            b("VI. Instrucciones adicionales del cliente.-"),
            t(" Las visitas al inmueble deberán realizarse previa cita y confirmación del cliente. La intermediaria no podrá compartir documentación sensible del inmueble ni datos personales del cliente con terceros, salvo lo necesario para la promoción, análisis de ofertas y formalización de la compraventa.")));

    blocks.add(
        Paragraph.of(
            b("Segunda. Contraprestación económica a pagar a la intermediaria.-"),
            t(" Siempre que la intermediaria haya prestado de manera cabal las obligaciones a su cargo referidas en la cláusula previa, y únicamente en el caso de que la compraventa del inmueble destinado a casa habitación se hubiere formalizado en un contrato privado o escritura pública de compraventa, las partes acuerdan que por la prestación de los servicios de intermediación, el cliente pagará a la intermediaria:")));
    blocks.add(
        Paragraph.indented(
            b("En caso de que se trate de un porcentaje.-"),
            t(" A la fecha de firma del contrato privado o escritura pública de compraventa, el 5% sobre el precio de compraventa del inmueble que efectivamente sea pagado al cliente, es decir, la cantidad de "),
            money(c == null ? null : c.commission(), "comisión (5% del precio)"),
            t(" más el Impuesto al Valor Agregado correspondiente; suma que resulta la cantidad de "),
            money(c == null ? null : c.totalCommissionWithVat(), "comisión más IVA"),
            t(". Total a pagar por concepto de comisión más IVA: "),
            c == null || c.totalCommissionWithVat().signum() <= 0 ? Span.pending("total de comisión más IVA") : b("$" + amount(c.totalCommissionWithVat()) + " M.N."),
            t(".")));
    blocks.add(
        Paragraph.indented(
            t("El precio indicado es en Moneda Nacional, en caso de expresarse la operación en moneda extranjera, se tomará al tipo de cambio que rija en el lugar y fecha en que se realice el pago, de conformidad con la legislación aplicable.")));
    blocks.add(Paragraph.indented(t("Los conceptos de pago a cargo del cliente, deben ser cubiertos con el método de pago referido a continuación:")));
    blocks.add(
        Paragraph.indented(
            t("Mediante transferencia bancaria a la cuenta que por escrito indique la intermediaria, contra la emisión del comprobante fiscal correspondiente. La obligación de pago de la comisión surgirá únicamente una vez que la operación de compraventa quede formalizada mediante escritura pública ante notario y el vendedor haya recibido de manera efectiva los recursos correspondientes al precio de la compraventa.")));
    blocks.add(
        Paragraph.indented(
            t("Si el cliente demora en el pago del precio, se constituirá en la obligación de pagar a la intermediaria el interés moratorio del 1% mensual sobre el importe pagadero por el tiempo que medie el retraso en el pago; interés que no debe resultar inequitativo, desproporcional, abusivo, ni excesivo. Dicho interés se calcula de la siguiente manera: Dicho interés se calcula multiplicando el saldo vencido por el 1% mensual, dividiendo dicho porcentaje entre 30 días y multiplicándolo por el número de días naturales de retraso")));
    blocks.add(
        Paragraph.indented(
            t("Los pagos que realice el cliente, aún en forma extemporánea y que sean aceptados por la intermediaria, lo liberan de las obligaciones inherentes a dichos pagos.")));
    blocks.add(
        Paragraph.indented(
            t("Los importes señalados en esta cláusula, son todas las cantidades a cargo del cliente por concepto del servicio de intermediación, por lo que, la intermediaria se obliga a respetar en todo momento dicho costo")));

    List<String> delivered = in.deliveredDocuments();
    blocks.add(
        Paragraph.of(
            b("Tercera. Documentación.-"),
            t(" El cliente en este acto entrega a la intermediaria, para el cumplimiento del presente contrato, los siguientes documentos en copia simple: "),
            delivered == null || delivered.isEmpty() ? Span.pending("documentos aceptados del expediente") : b(String.join("; ", delivered)),
            t(".")));
    blocks.add(
        Paragraph.of(
            t("En caso de que la intermediaria requiera documentos adicionales, lo hará saber por escrito al cliente, de conformidad con la cláusula décima segunda del presente contrato, debiendo el cliente entregarle la documentación requerida dentro del término de 5 días hábiles siguientes a la fecha en que reciba el requerimiento en mérito.")));

    blocks.add(
        Paragraph.of(
            b("Cuarta. Relación de los derechos y obligaciones de las partes.-"),
            t(" Los derechos y obligaciones de las partes contractuales son los siguientes (listado enunciativo más no limitativo):")));
    blocks.add(
        new Table(
            List.of(50, 50),
            List.of(
                Row.of(Cell.header("Intermediaria", 2)),
                Row.of(Cell.centered("Derechos"), Cell.centered("Obligaciones")),
                Row.of(Cell.bullets(ContractReferenceData.INTERMEDIARY_RIGHTS), Cell.bullets(ContractReferenceData.INTERMEDIARY_OBLIGATIONS)))));
    blocks.add(
        new Table(
            List.of(50, 50),
            List.of(
                Row.of(Cell.header("Cliente", 2)),
                Row.of(Cell.centered("Derechos"), Cell.centered("Obligaciones")),
                Row.of(Cell.bullets(ContractReferenceData.CLIENT_RIGHTS), Cell.bullets(ContractReferenceData.CLIENT_OBLIGATIONS)))));

    ContractReferenceData.FIXED_CLAUSES_BEFORE_PENALTY.forEach(this::clause);
    blocks.add(
        Paragraph.of(
            b("Novena. Penas Convencionales.-"),
            t(" Las partes acuerdan que, para el caso de incumplimiento de cualquiera de las obligaciones contraídas en el presente contrato, se aplicará una pena convencional equivalente al 100% (cien por ciento) de la comisión pactada en la cláusula segunda, lo cual resulta la cantidad de "),
            money(c == null ? null : c.penalty(), "pena convencional (100% de la comisión)"),
            t(".")));
    ContractReferenceData.FIXED_CLAUSES_BEFORE_NOTICES.forEach(this::clause);

    blocks.add(
        Paragraph.of(
            b("Décima segunda. Notificaciones entre las partes.-"),
            t(" Todas las notificaciones, requerimientos, autorizaciones, avisos o cualquier otra comunicación que deban darse las partes conforme a este contrato, deben hacerse por escrito y considerarse como debidamente entregadas si se encuentran firmadas por la respectiva parte contractual o su representante o apoderado legal y entregadas con acuse de recibo al destinatario o confirmación de recepción en:")));
    ManualClientDataView data = in.clientData();
    String notificationAddress =
        data != null && notBlank(data.notificationAddress())
            ? data.notificationAddress()
            : owners().isEmpty() ? null : owners().getFirst().address();
    blocks.add(
        new Table(
            List.of(50, 50),
            List.of(
                Row.of(Cell.centered("Intermediaria"), Cell.centered("Cliente")),
                Row.of(
                    Cell.lines(
                        List.of(
                            List.of(b("Domicilio: "), t(ContractReferenceData.INTERMEDIARY_NOTICE_ADDRESS)),
                            List.of(b("Correo electrónico: "), t(ContractReferenceData.INTERMEDIARY_EMAIL)),
                            List.of(b("Teléfono: "), t(ContractReferenceData.INTERMEDIARY_PHONE)),
                            List.of(b("WhatsApp: "), t(ContractReferenceData.INTERMEDIARY_WHATSAPP)))),
                    Cell.lines(
                        List.of(
                            List.of(b("Domicilio: "), value(notificationAddress, "domicilio del cliente para notificaciones")),
                            List.of(b("Correo electrónico: "), value(data == null ? null : data.email(), "correo electrónico del cliente")),
                            List.of(b("Teléfono: "), value(data == null ? null : data.phone(), "teléfono del cliente"))))))));

    ContractReferenceData.FIXED_CLAUSES_AFTER_NOTICES.forEach(this::clause);
  }

  private void clause(ContractReferenceData.Clause clause) {
    blocks.add(Paragraph.of(b(clause.title()), t(" " + clause.firstParagraph())));
    for (String extra : clause.extraParagraphs()) {
      blocks.add(Paragraph.of(t(extra)));
    }
  }

  private void renderSignaturePage() {
    blocks.add(
        Paragraph.of(
            t("Leído que fue por las partes el contenido del presente contrato y sabedoras de su alcance legal, lo firman por duplicado el "),
            value(in.clientData() == null ? null : date(in.clientData().contractSignatureDate()), "fecha de firma del contrato"),
            t(", por lo que, la intermediaria está obligada a entregar un tanto del contrato y sus anexos originales y firmados al cliente.")));
    blocks.add(Paragraph.of(b("Modalidad del contrato: EXCLUSIVA por " + ContractCalculator.EXCLUSIVITY_DAYS + " días naturales.")));
    blocks.add(new SignatureLines(signatureLabels()));
    blocks.add(
        Paragraph.of(
            t("El presente contrato y sus anexos pueden signarse: de manera autógrafa original; o a través de una firma electrónica avanzada o fiable que será considerada para todos los efectos con la misma fuerza y consecuencias que la firma autógrafa original de la parte firmante.")));

    ManualClientDataView data = in.clientData();
    Boolean share = data == null ? null : data.marketingDataAuthorized();
    Boolean ads = data == null ? null : data.receiveAdsAuthorized();
    if (share == null) missing.add("Autorización del cliente para ceder su información con fines mercadotécnicos (sí/no)");
    if (ads == null) missing.add("Autorización del cliente para recibir publicidad (sí/no)");
    blocks.add(
        Paragraph.of(
            b("Autorización para la utilización de información con fines mercadotécnicos o publicitarios.-"),
            t(" El cliente si ( "),
            share == null ? Span.pending("sí/no") : b(share ? "X" : " "),
            t(" ) no ( "),
            share == null ? t(" ") : b(share ? " " : "X"),
            t(" ) acepta que la intermediaria ceda o transmita a terceros, con fines mercadotécnicos o publicitarios, la información proporcionada con motivo del presente contrato y si ( "),
            ads == null ? Span.pending("sí/no") : b(ads ? "X" : " "),
            t(" ) no ( "),
            ads == null ? t(" ") : b(ads ? " " : "X"),
            t(" ) acepta que la intermediaria le envíe publicidad sobre bienes y servicios.")));
    blocks.add(new SignatureLines(List.of("Firma del cliente", "Firma del cliente")));
    blocks.add(Paragraph.of(t(ContractReferenceData.REPEP_NOTICE)));
    blocks.add(Paragraph.of(t(ContractReferenceData.MARKETING_PROHIBITION_NOTICE)));
  }

  private List<String> signatureLabels() {
    List<String> labels = new ArrayList<>();
    labels.add("Firma de la intermediaria\n" + ContractReferenceData.LEGAL_REPRESENTATIVE + "\nRepresentante legal de " + ContractReferenceData.INTERMEDIARY_LEGAL_NAME);
    for (ParticipantView signer : clientSigners()) {
      labels.add("Firma del cliente\n" + signer.fullName() + "\n" + signerCapacity(signer));
    }
    if (clientSigners().isEmpty()) {
      labels.add("Firma del cliente");
    }
    return labels;
  }

  // ---------------------------------------------------------------------
  // Anexos
  // ---------------------------------------------------------------------

  private void renderAnnexA() {
    ManualClientDataView d = in.clientData();
    boolean land = in.expediente().propertyCaseType() == PropertyCaseType.RESIDENTIAL_LAND;
    blocks.add(new SectionHeading("Anexo A"));
    blocks.add(Paragraph.centered(b("Especificaciones del bien inmueble destinado a casa habitación")));
    blocks.add(
        Paragraph.of(
            t("El presente anexo debe contener la información relativa a las especificaciones que resulten aplicables al inmueble en cuanto a: características técnicas, de seguridad, extensión del terreno, superficie construida, tipo de estructura, instalaciones, acabados, accesorios, lugares de estacionamiento, servicios incluyendo los básicos, estado físico general, áreas de uso común con otros inmuebles, porcentaje de indiviso, así como el detalle del equipamiento urbano existente en la localidad dónde se encuentra el inmueble y los sistemas y medios de transporte existentes para llegar a él.")));
    List<Row> rows = new ArrayList<>();
    rows.add(Row.of(Cell.spans(List.of(b("Superficie de terreno"))), Cell.spans(List.of(area(d == null ? null : d.landAreaM2(), "superficie de terreno", true)))));
    rows.add(
        Row.of(
            Cell.spans(List.of(b("Superficie de construcción"))),
            Cell.spans(List.of(land ? optionalArea(d == null ? null : d.builtAreaM2()) : area(d == null ? null : d.builtAreaM2(), "superficie de construcción", true)))));
    rows.add(Row.of(Cell.spans(List.of(b("Número de recámaras"))), Cell.spans(List.of(count(d == null ? null : d.bedrooms(), "número de recámaras", !land)))));
    rows.add(Row.of(Cell.spans(List.of(b("Número de baños"))), Cell.spans(List.of(count(d == null ? null : d.bathrooms(), "número de baños", !land)))));
    rows.add(Row.of(Cell.spans(List.of(b("Estacionamientos"))), Cell.spans(List.of(count(d == null ? null : d.parkingSpots(), "estacionamientos", false)))));
    rows.add(Row.of(Cell.spans(List.of(b("Estado general de conservación"))), Cell.spans(List.of(value(d == null ? null : d.conservationStatus(), "estado general de conservación")))));
    rows.add(Row.of(Cell.spans(List.of(b("Servicios con los que cuenta"))), Cell.spans(List.of(value(d == null ? null : d.availableServices(), "servicios con los que cuenta el inmueble")))));
    rows.add(
        Row.of(
            Cell.spans(List.of(b("Características relevantes del inmueble"))),
            Cell.spans(List.of(d != null && notBlank(d.relevantFeatures()) ? t(d.relevantFeatures()) : t("Sin características adicionales declaradas.")))));
    blocks.add(new Table(List.of(35, 65), rows));
    blocks.add(new SignatureLines(List.of("Firma de la intermediaria", "Firma del cliente")));
  }

  private void renderAnnexB() {
    blocks.add(new SectionHeading("Anexo B"));
    blocks.add(Paragraph.centered(b("Información y documentación que se pone a disposición del cliente")));
    List<Row> rows = new ArrayList<>();
    rows.add(
        Row.of(
            Cell.bold("Información/documentación"),
            new Cell(List.of(List.of(b("¿Le informaron sobre/exhibieron la documentación correspondiente?"))), 2, false, Align.CENTER),
            new Cell(
                List.of(
                    List.of(b("Medio a través del cual se pone a disposición del cliente")),
                    List.of(t("(domicilio o link del sitio web en el cual está disponible la documentación para consulta)"))),
                1,
                false,
                Align.CENTER)));
    rows.add(Row.of(Cell.text(""), Cell.centered("Si"), Cell.centered("No"), Cell.text("")));
    for (String[] row : ContractReferenceData.ANNEX_B_ROWS) {
      rows.add(Row.of(Cell.text(row[0]), Cell.centered(row[1]), Cell.centered(row[2]), Cell.text(row[3])));
    }
    blocks.add(new Table(List.of(34, 8, 8, 50), rows));
    blocks.add(
        Paragraph.of(
            b("Importante para el cliente.-"),
            t(" Antes de que firme como constancia de que tuvo a su disposición la información y documentación relativa al inmueble, es importante cerciorarse de que la misma coincide con la que efectivamente le haya mostrado y/o proporcionado la intermediaria.")));
    blocks.add(new SignatureLines(List.of("Firma de la intermediaria", "Firma del cliente")));
  }

  private void renderAnnexC() {
    blocks.add(new SectionHeading("Anexo C"));
    blocks.add(Paragraph.centered(b("Carta de derechos del cliente")));
    blocks.add(new Table(List.of(100), List.of(Row.of(Cell.bullets(ContractReferenceData.CLIENT_RIGHTS_LETTER)))));
  }

  private void renderAnnexD() {
    blocks.add(new SectionHeading("Anexo D"));
    blocks.add(Paragraph.centered(b("Listado de servicios adicionales, especiales o conexos")));
    blocks.add(
        Paragraph.of(
            t("(El presente formato debe contener el listado de los servicios adicionales, especiales o conexos que el cliente puede solicitar de forma opcional por conducto de la intermediación, en concordancia con lo dispuesto en la cláusula décima primera del contrato de intermediación del cual forma parte integrante)")));
    ManualClientDataView d = in.clientData();
    blocks.add(
        Paragraph.of(
            b("Servicios adicionales solicitados por el cliente: "),
            t(d != null && notBlank(d.additionalServicesRequested()) ? d.additionalServicesRequested() : "Ninguno.")));
    blocks.add(new SignatureLines(List.of("Firma de la intermediaria", "Firma del cliente")));
  }

  // ---------------------------------------------------------------------
  // Datos derivados
  // ---------------------------------------------------------------------

  private LegalDetails legal() {
    return in.expediente().legalDetails() != null ? in.expediente().legalDetails() : LegalDetails.empty();
  }

  private List<ParticipantView> owners() {
    return in.expediente().participants().stream().filter(ParticipantView::isOwner).toList();
  }

  private List<ParticipantView> representatives() {
    return in.expediente().participants().stream().filter(ParticipantView::isRepresentative).toList();
  }

  /** Quién firma por el cliente: los titulares, o su apoderado / representante legal. */
  public static List<ParticipantView> clientSignersOf(ExpedienteSummary expediente) {
    boolean represented = expediente.personType() == PersonType.MORAL || expediente.signerCharacter() == SignerCharacter.APODERADO;
    return expediente.participants().stream().filter(p -> represented ? p.isRepresentative() : p.isOwner()).toList();
  }

  private List<ParticipantView> clientSigners() {
    return clientSignersOf(in.expediente());
  }

  public static String signerCapacityOf(ExpedienteSummary expediente, ParticipantView signer) {
    String company = expediente.personType() == PersonType.MORAL ? expediente.ownerDisplayName() : null;
    return switch (signer.role()) {
      case "LEGAL_REPRESENTATIVE" -> "Representante legal de " + (company != null ? company : "la persona moral");
      case "ATTORNEY" -> "Apoderado de " + expediente.ownerDisplayName();
      case "CO_OWNER" -> "Copropietario";
      default -> expediente.participants().stream().filter(ParticipantView::isOwner).count() > 1 ? "Copropietario" : "Propietario";
    };
  }

  private String signerCapacity(ParticipantView signer) {
    return signerCapacityOf(in.expediente(), signer);
  }

  private String representationCapacity(String fallback) {
    LegalDetails.RepresentationData r = legal().representation();
    return r != null && notBlank(r.capacity()) ? r.capacity() : fallback;
  }

  private String rfcOf(ParticipantView owner) {
    if (notBlank(owner.rfc())) {
      return owner.rfc();
    }
    LegalDetails.CompanyData company = legal().company();
    return in.expediente().personType() == PersonType.MORAL && company != null ? company.rfc() : null;
  }

  private String propertyKind() {
    return switch (in.expediente().propertyCaseType()) {
      case HOUSING -> "una casa habitación";
      case DEPARTMENT -> "un departamento destinado a casa habitación";
      case RESIDENTIAL_LAND -> "un terreno destinado a casa habitación";
      case COMMERCIAL -> "un inmueble comercial";
    };
  }

  private List<Span> joinNames(List<ParticipantView> people, String missingDescription) {
    if (people.isEmpty()) {
      missing.add(capitalize(missingDescription));
      return List.of(Span.pending(missingDescription));
    }
    List<Span> spans = new ArrayList<>();
    for (int i = 0; i < people.size(); i++) {
      if (i > 0) {
        spans.add(t(i == people.size() - 1 ? " y " : ", "));
      }
      spans.add(b(people.get(i).fullName()));
    }
    return spans;
  }

  private Span value(String value, String missingDescription) {
    if (!notBlank(value)) {
      missing.add(capitalize(missingDescription));
      return Span.pending(missingDescription);
    }
    return b(value);
  }

  private Span money(BigDecimal amount, String missingDescription) {
    if (amount == null || amount.signum() <= 0) {
      missing.add(capitalize(missingDescription) + " (el monto no puede ser cero)");
      return Span.pending(missingDescription);
    }
    return b("$" + amount(amount) + " M.N. (" + MoneyToSpanishWords.convert(amount) + ")");
  }

  private Span area(BigDecimal area, String missingDescription, boolean required) {
    if (area == null || area.signum() <= 0) {
      if (required) {
        missing.add("Anexo A: " + missingDescription);
        return Span.pending(missingDescription);
      }
      return t("No aplica");
    }
    return b(area.stripTrailingZeros().toPlainString() + " m²");
  }

  private static Span optionalArea(BigDecimal area) {
    return area == null || area.signum() <= 0 ? t("No aplica (terreno)") : b(area.stripTrailingZeros().toPlainString() + " m²");
  }

  private Span count(Integer value, String missingDescription, boolean required) {
    if (value == null) {
      if (required) {
        missing.add("Anexo A: " + missingDescription);
        return Span.pending(missingDescription);
      }
      return t("No aplica");
    }
    return b(String.valueOf(value));
  }

  private String age(LocalDate birthDate) {
    if (birthDate == null) {
      return null;
    }
    LocalDate reference = in.clientData() != null && in.clientData().contractSignatureDate() != null ? in.clientData().contractSignatureDate() : in.today();
    return String.valueOf(Period.between(birthDate, reference).getYears());
  }

  static String idLabel(IdDocumentType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case INE -> "credencial para votar (INE)";
      case PASAPORTE -> "pasaporte";
      case CEDULA_PROFESIONAL -> "cédula profesional";
      case FM2_RESIDENTE -> "tarjeta de residente";
    };
  }

  static String civilStatusLabel(CivilStatus status, MaritalRegime regime) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case SOLTERO -> "soltero(a)";
      case CASADO ->
          "casado(a)"
              + (regime == null
                  ? ""
                  : regime == MaritalRegime.SOCIEDAD_CONYUGAL ? " bajo el régimen de sociedad conyugal" : " bajo el régimen de separación de bienes");
      case UNION_LIBRE -> "unión libre";
      case DIVORCIADO -> "divorciado(a)";
      case VIUDO -> "viudo(a)";
    };
  }

  public static String date(LocalDate date) {
    return date == null ? null : LONG_DATE.format(date);
  }

  public static String amount(BigDecimal amount) {
    DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    return format.format(amount);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String shorten(String value) {
    return value.length() > 70 ? value.substring(0, 70) + "…" : value;
  }

  private static Map<String, String> orderedMap(String... keyValues) {
    Map<String, String> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return java.util.Collections.unmodifiableMap(map);
  }
}
