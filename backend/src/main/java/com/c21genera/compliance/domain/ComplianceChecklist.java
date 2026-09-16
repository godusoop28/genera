package com.c21genera.compliance.domain;

import java.util.List;

public record ComplianceChecklist(java.util.UUID expedienteId, List<ComplianceCheckResult> items, boolean allPassed) {

  public record ComplianceCheckResult(ComplianceCheckCode code, boolean passed, String detail) {}
}
