package com.c21genera.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.contracts.domain.ContractTemplate.ContractInput;
import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.domain.document.ContractDocument.Block;
import com.c21genera.contracts.domain.document.ContractDocument.Paragraph;
import com.c21genera.contracts.domain.document.ContractDocument.Span;
import com.c21genera.contracts.domain.document.ContractDocument.Table;
import com.c21genera.contracts.infrastructure.DocxContractRenderer;
import com.c21genera.contracts.infrastructure.PdfContractRenderer;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.expedientes.IdDocumentType;
import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.MaritalRegime;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ContractTemplateTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

  static ParticipantView person(String role, String name, int ordinal, CivilStatus civil) {
    return new ParticipantView(
        UUID.randomUUID(), role, name, ordinal, "Mexicana", IdDocumentType.INE, "IDMEX" + ordinal + "2345678", "Instituto Nacional Electoral",
        LocalDate.of(1980, 5, 10), civil, civil == CivilStatus.CASADO ? MaritalRegime.SEPARACION_DE_BIENES : null, "PELJ800510AB" + ordinal, null,
        "persona" + ordinal + "@correo.com", "777100000" + ordinal, "Calle Uno 1, Col. Centro, Cuernavaca, Morelos, C.P. 62000");
  }

  static Map<String, Boolean> housingChecklist() {
    Map<String, Boolean> answers = new HashMap<>();
    ContractTemplate.HOUSING_CHECKLIST.keySet().forEach(k -> answers.put(k, true));
    answers.put("PLANS", false);
    return answers;
  }

  static LegalDetails deedDetails(Map<String, Boolean> checklist) {
    return new LegalDetails(
        null,
        null,
        new LegalDetails.DeedData("45,678", LocalDate.of(2010, 3, 15), "Roberto Díaz Soto", "12", "Cuernavaca, Morelos", null),
        null,
        null,
        checklist,
        "portales inmobiliarios, redes sociales y letrero en el inmueble");
  }

  static ManualClientDataView completeClientData() {
    return new ManualClientDataView(
        new BigDecimal("2500000"), LocalDate.of(2026, 10, 1), "cliente@correo.com", "7771234567", null, "Previa cita", true, false, null,
        3, 2, 2, "Bueno", "Agua, luz, drenaje, gas", "Jardín", new BigDecimal("250"), new BigDecimal("180"));
  }

  static ExpedienteSummary summary(
      PersonType personType,
      SignerCharacter signer,
      AccreditationType accreditation,
      boolean condominium,
      PropertyCaseType propertyType,
      List<ParticipantView> participants,
      LegalDetails legal) {
    String display = participants.stream().filter(ParticipantView::isOwner).map(ParticipantView::fullName).collect(Collectors.joining(" y "));
    return new ExpedienteSummary(
        UUID.randomUUID(), "EXP-2026-000123", display, ExpedienteStatus.RECEPTION_SIGNED, personType, signer, accreditation, condominium,
        propertyType, PropertyLegalStatus.LIBRE_GRAVAMEN, "Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Morelos, C.P. 62290",
        UUID.randomUUID(), participants, legal);
  }

  static ContractInput input(ExpedienteSummary summary, ManualClientDataView data) {
    return new ContractInput(
        summary,
        data,
        data.authorizedPrice() == null ? null : ContractCalculator.calculate(data.authorizedPrice(), data.contractSignatureDate()),
        List.of("Identificación oficial (INE) de Juan Pérez López", "Escritura", "Predial"),
        1,
        TODAY);
  }

  static String text(ContractDocument document) {
    StringBuilder out = new StringBuilder();
    for (Block block : document.blocks()) {
      switch (block) {
        case Paragraph p -> p.spans().forEach(s -> out.append(s.text()));
        case Table t -> t.rows().forEach(r -> r.cells().forEach(c -> c.paragraphs().forEach(ps -> ps.forEach(s -> out.append(s.text()).append(' ')))));
        case ContractDocument.SectionHeading h -> out.append(h.text());
        case ContractDocument.SignatureLines l -> out.append(String.join(" ", l.labels()));
        default -> {}
      }
      out.append('\n');
    }
    return out.toString();
  }

  @Test
  void completeSingleOwnerContractHasNoPendingDataAndFillsEveryBlank() {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(person("OWNER", "Juan Pérez López", 1, CivilStatus.CASADO)), deedDetails(housingChecklist()));

    ContractDocument doc = ContractTemplate.build(input(s, completeClientData()), false);
    String text = text(doc);

    assertThat(doc.missingItems()).isEmpty();
    assertThat(text)
        .contains("Juan Pérez López, que por su propio derecho, en su carácter de propietario")
        .contains("credencial para votar (INE)")
        .contains("tiene 46 años")
        .contains("casado(a) bajo el régimen de separación de bienes")
        .contains("Escritura pública número 45,678, otorgada el 15 de marzo de 2010")
        .contains("No aplica: el inmueble no está sujeto al régimen de propiedad en condominio")
        .contains("No aplica: el cliente comparece por su propio derecho")
        .contains("$2,500,000.00 M.N. (DOS MILLONES QUINIENTOS MIL PESOS 00/100 M.N.)")
        .contains("$125,000.00 M.N.")
        .contains("$145,000.00 M.N.")
        .contains("lo firman por duplicado el 1 de octubre de 2026")
        .contains("Décima octava. Registro del modelo de contrato de adhesión")
        .contains("En caso de vivienda.-")
        .doesNotContain("[PENDIENTE");
  }

  @Test
  void missingDataIsListedAndMarkedInTheDocument() {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(new ParticipantView(UUID.randomUUID(), "OWNER", "Juan Pérez", 1, null, null, null, null, null, null, null, null, null, null, null, null)),
            LegalDetails.empty());
    ManualClientDataView empty =
        new ManualClientDataView(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    ContractDocument doc = ContractTemplate.build(input(s, empty), true);

    assertThat(doc.missingItems())
        .anyMatch(i -> i.startsWith("Precio aproximado de mercado"))
        .anyMatch(i -> i.startsWith("Número de la escritura"))
        .anyMatch(i -> i.startsWith("Estado civil de Juan Pérez"))
        .anyMatch(i -> i.startsWith("Fecha de firma del contrato"))
        .anyMatch(i -> i.contains("mercadotécnicos"))
        .anyMatch(i -> i.startsWith("Anexo A: superficie de terreno"));
    assertThat(text(doc)).contains("[PENDIENTE: precio aproximado de mercado (precio autorizado)]");
  }

  @Test
  void coOwnersAreAllListedWithTheirOwnData() {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.COPROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(
                person("OWNER", "Juan Pérez López", 1, CivilStatus.SOLTERO),
                person("CO_OWNER", "María Gómez Ruiz", 2, CivilStatus.VIUDO),
                person("CO_OWNER", "Luis Hernández Paz", 3, CivilStatus.DIVORCIADO),
                person("CO_OWNER", "Ana Torres Vega", 4, CivilStatus.SOLTERO)),
            deedDetails(housingChecklist()));

    ContractDocument doc = ContractTemplate.build(input(s, completeClientData()), false);
    String text = text(doc);

    assertThat(doc.missingItems()).isEmpty();
    assertThat(text)
        .contains("Juan Pérez López, María Gómez Ruiz, Luis Hernández Paz y Ana Torres Vega, que por su propio derecho, en su carácter de copropietarios")
        .contains("María Gómez Ruiz: Es de nacionalidad")
        .contains("Ana Torres Vega, domicilio ubicado en")
        .contains("Son legítimos propietarios");
    assertThat(ContractTemplate.clientSignersOf(s)).hasSize(4);
  }

  @Test
  void attorneySignsForTheOwnerAndPowerDataIsRequired() {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.APODERADO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(person("OWNER", "Juan Pérez López", 1, CivilStatus.SOLTERO), person("ATTORNEY", "Carlos Ruiz Mena", 2, null)),
            deedDetails(housingChecklist()));

    ContractDocument doc = ContractTemplate.build(input(s, completeClientData()), false);

    assertThat(text(doc)).contains("representado en este acto por Carlos Ruiz Mena, en su carácter de apoderado");
    assertThat(doc.missingItems()).anyMatch(i -> i.startsWith("Número del instrumento del poder"));
    assertThat(ContractTemplate.clientSignersOf(s)).extracting(ParticipantView::fullName).containsExactly("Carlos Ruiz Mena");
  }

  @Test
  void legalEntityUsesCompanyDeclarationAndHasNoCivilStatus() {
    LegalDetails legal =
        new LegalDetails(
            new LegalDetails.CompanyData(
                "Sociedad Anónima de Capital Variable", "INM010101AB1", "1,234", LocalDate.of(2001, 1, 1), "Notario", "5", "Cuernavaca, Morelos",
                "Pedro Sol", "Cuernavaca, Morelos", "N-2001000111"),
            new LegalDetails.RepresentationData(
                "representante legal", "2,345", LocalDate.of(2020, 2, 2), "Notario", "7", "Cuernavaca, Morelos", "Laura Luna", "Cuernavaca, Morelos",
                "N-2020000222"),
            deedDetails(housingChecklist()).deed(),
            null,
            null,
            housingChecklist(),
            "portales inmobiliarios");
    ParticipantView company =
        new ParticipantView(UUID.randomUUID(), "OWNER", "Inmobiliaria Sol SA de CV", 1, null, null, null, null, null, null, null, "INM010101AB1", null, null, null,
            "Av. Siempre Viva 1, Cuernavaca, Morelos");
    ExpedienteSummary s =
        summary(
            PersonType.MORAL, SignerCharacter.REPRESENTANTE_LEGAL, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(company, person("LEGAL_REPRESENTATIVE", "Laura Méndez Ríos", 2, null)), legal);

    ContractDocument doc = ContractTemplate.build(input(s, completeClientData()), false);
    String text = text(doc);

    assertThat(doc.missingItems()).isEmpty();
    assertThat(text)
        .contains("a.2. En caso de ser persona jurídica.- Es una sociedad mercantil Sociedad Anónima de Capital Variable")
        .contains("Inmobiliaria Sol SA de CV, representada en este acto por Laura Méndez Ríos")
        .doesNotContain("a.1. En caso de ser persona física")
        .doesNotContain("estado civil");
  }

  @Test
  void privateContractCondominiumAndLandVariants() {
    Map<String, Boolean> land = new HashMap<>();
    ContractTemplate.LAND_CHECKLIST.keySet().forEach(k -> land.put(k, false));
    LegalDetails legal =
        new LegalDetails(
            null,
            null,
            null,
            new LegalDetails.PrivateContractData(
                "Pedro Venta", "Juan Pérez López", LocalDate.of(2015, 6, 1), LocalDate.of(2015, 6, 10), "la fe pública del Notario Público", "3",
                "Jiutepec, Morelos", "Sara Paz", LocalDate.of(2015, 7, 1), "Cuernavaca", "FR-998877"),
            new LegalDetails.CondominiumData("7,777", LocalDate.of(2005, 1, 1), "9", "Cuernavaca", "Hugo Mar", LocalDate.of(2005, 2, 1), "FR-555"),
            land,
            "portales inmobiliarios");
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.CONTRATO_PRIVADO, true, PropertyCaseType.RESIDENTIAL_LAND,
            List.of(person("OWNER", "Juan Pérez López", 1, CivilStatus.SOLTERO)), legal);
    ManualClientDataView data = completeClientData();

    ContractDocument doc = ContractTemplate.build(input(s, data), false);
    String text = text(doc);

    assertThat(doc.missingItems()).isEmpty();
    assertThat(text)
        .contains("Contrato privado celebrado entre Pedro Venta y Juan Pérez López")
        .contains("bajo el folio real FR-998877")
        .contains("está sujeto al régimen de propiedad en condominio; en términos de la escritura pública 7,777")
        .contains("En caso de terreno destinado a casa habitación.-")
        .contains("un terreno destinado a casa habitación")
        .doesNotContain("Escritura pública número");
  }

  @Test
  void commercialPropertyCanNeverBeSigned() {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.COMMERCIAL,
            List.of(person("OWNER", "Juan Pérez López", 1, CivilStatus.SOLTERO)), deedDetails(housingChecklist()));

    assertThat(ContractTemplate.build(input(s, completeClientData()), false).missingItems()).anyMatch(i -> i.contains("casa habitación"));
  }

  @Test
  void rendersDocxAndPdfWithTheSameContentAndMarksDrafts() throws IOException {
    ExpedienteSummary s =
        summary(
            PersonType.FISICA, SignerCharacter.COPROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, true, PropertyCaseType.DEPARTMENT,
            List.of(person("OWNER", "Juan Pérez López", 1, CivilStatus.CASADO), person("CO_OWNER", "María Gómez Ruiz", 2, CivilStatus.CASADO)),
            new LegalDetails(null, null, deedDetails(housingChecklist()).deed(), null, null, housingChecklist(), "portales inmobiliarios"));

    ContractDocument complete = ContractTemplate.build(input(s, completeClientData()), false);
    ContractDocument draft = new ContractDocument(complete.title(), true, complete.missingItems(), complete.blocks());

    byte[] pdf = new PdfContractRenderer().render(draft);
    byte[] docx = new DocxContractRenderer().render(draft);

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(5);
      String pdfText = new PDFTextStripper().getText(document);
      assertThat(pdfText).contains("Juan Pérez López").contains("PFC.B.E.7/002193-2026").contains("BORRADOR INCOMPLETO");
    }
    assertThat(docx.length).isGreaterThan(10_000);

    Path out = Path.of("target", "contract-samples");
    Files.createDirectories(out);
    Files.write(out.resolve("contrato-copropietarios-condominio-BORRADOR.pdf"), pdf);
    Files.write(out.resolve("contrato-copropietarios-condominio-BORRADOR.docx"), docx);
    Files.write(out.resolve("contrato-copropietarios-condominio.pdf"), new PdfContractRenderer().render(complete));
  }

  @Test
  void pendingSpansAreMarked() {
    assertThat(Span.pending("x").pending()).isTrue();
    assertThat(Span.pending("x").text()).isEqualTo("[PENDIENTE: x]");
  }
}
