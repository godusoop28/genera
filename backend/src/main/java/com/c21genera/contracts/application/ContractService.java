package com.c21genera.contracts.application;

import com.c21genera.contracts.domain.ContractCalculator;
import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.infrastructure.ContractDocxBuilder;
import com.c21genera.contracts.infrastructure.ContractDocxBuilder.ContractInput;
import com.c21genera.contracts.infrastructure.ContractGenerationRepository;
import com.c21genera.contracts.infrastructure.PdfConverter;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContractService {

  private final ExpedienteLifecycleApi expedienteApi;
  private final ContractDocxBuilder docxBuilder;
  private final PdfConverter pdfConverter;
  private final FileStorage fileStorage;
  private final ContractGenerationRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ContractService(
      ExpedienteLifecycleApi expedienteApi,
      ContractDocxBuilder docxBuilder,
      PdfConverter pdfConverter,
      FileStorage fileStorage,
      ContractGenerationRepository repository,
      ObjectMapper objectMapper,
      Clock clock) {
    this.expedienteApi = expedienteApi;
    this.docxBuilder = docxBuilder;
    this.pdfConverter = pdfConverter;
    this.fileStorage = fileStorage;
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ContractCalculator.Result calculationsOf(UUID expedienteId) {
    ManualClientDataView data = expedienteApi.getManualData(expedienteId);
    LocalDate signatureDate = data.contractSignatureDate() != null ? data.contractSignatureDate() : LocalDate.now(clock);
    return ContractCalculator.calculate(data.authorizedPrice(), signatureDate);
  }

  public ContractGeneration generate(UUID expedienteId, UUID generatedByUserId) {
    ExpedienteSummary summary = expedienteApi.getSummary(expedienteId);
    ContractCalculator.Result calculations = calculationsOf(expedienteId);

    byte[] docx;
    try {
      docx =
          docxBuilder.build(
              new ContractInput(summary.ownerDisplayName(), summary.signerCharacter().name(), summary.propertyAddress(), calculations));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    int nextVersion =
        repository.findFirstByExpedienteIdOrderByVersionNumberDesc(expedienteId).map(c -> c.getVersionNumber() + 1).orElse(1);

    String docxKey = "expedientes/%s/contracts/v%d/contrato.docx".formatted(expedienteId, nextVersion);
    FileStorage.StoredObjectMetadata stored =
        fileStorage.store(
            docxKey,
            new ByteArrayInputStream(docx),
            docx.length,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    String snapshotJson = writeSnapshot(summary, calculations);

    ContractGeneration generation =
        new ContractGeneration(
            expedienteId, nextVersion, snapshotJson, stored.storageKey(), clock.instant(), generatedByUserId, sha256(snapshotJson));

    try {
      byte[] pdf = pdfConverter.toPdf(docx);
      String pdfKey = "expedientes/%s/contracts/v%d/contrato.pdf".formatted(expedienteId, nextVersion);
      fileStorage.store(pdfKey, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
      generation.attachPdf(pdfKey);
    } catch (Exception e) {
      // La conversión a PDF es opcional en este prototipo (requiere LibreOffice, ver AGENTS §79):
      // el DOCX siempre queda disponible aunque el PDF no se pueda generar.
    }

    return repository.save(generation);
  }

  @Transactional(readOnly = true)
  public List<ContractGeneration> listOf(UUID expedienteId) {
    return repository.findByExpedienteIdOrderByVersionNumberDesc(expedienteId);
  }

  @Transactional(readOnly = true)
  public ContractGeneration get(UUID contractId) {
    return repository.findById(contractId).orElseThrow(() -> new NotFoundException("Contrato", contractId));
  }

  public ContractGeneration markSigned(UUID contractId) {
    ContractGeneration contract = get(contractId);
    contract.markSigned(clock.instant());
    return contract;
  }

  public ContractGeneration markDelivered(UUID contractId, String method) {
    ContractGeneration contract = get(contractId);
    contract.markDelivered(clock.instant(), method);
    return contract;
  }

  private String writeSnapshot(ExpedienteSummary summary, ContractCalculator.Result calculations) {
    try {
      return objectMapper.writeValueAsString(Map.of("expediente", summary, "calculations", calculations));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
