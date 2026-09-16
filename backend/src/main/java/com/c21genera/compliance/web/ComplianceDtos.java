package com.c21genera.compliance.web;

import com.c21genera.compliance.domain.ComplianceChecklist;
import com.c21genera.compliance.domain.ComplianceChecklist.ComplianceCheckResult;
import java.util.List;
import java.util.UUID;

public final class ComplianceDtos {

  private ComplianceDtos() {}

  public record CheckItemResponse(String code, boolean passed, String detail) {

    static CheckItemResponse from(ComplianceCheckResult result) {
      return new CheckItemResponse(result.code().name(), result.passed(), result.detail());
    }
  }

  public record ChecklistResponse(UUID expedienteId, boolean allPassed, List<CheckItemResponse> items) {

    public static ChecklistResponse from(ComplianceChecklist checklist) {
      return new ChecklistResponse(
          checklist.expedienteId(), checklist.allPassed(), checklist.items().stream().map(CheckItemResponse::from).toList());
    }
  }
}
