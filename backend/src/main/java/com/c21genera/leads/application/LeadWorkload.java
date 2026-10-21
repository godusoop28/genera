package com.c21genera.leads.application;

import com.c21genera.leads.LeadStage;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.shared.staff.StaffWorkload;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Control de Cambios #25: leads abiertos de un asesor (hay que reasignarlos antes de darle de baja). */
@Component
@Transactional(readOnly = true)
class LeadWorkload implements StaffWorkload {

  private static final List<LeadStage> OPEN = Arrays.stream(LeadStage.values()).filter(LeadStage::isOpen).toList();

  private final LeadRepository leads;

  LeadWorkload(LeadRepository leads) {
    this.leads = leads;
  }

  @Override
  public List<Item> assignedTo(UUID userId) {
    return List.of(new Item("Leads abiertos", leads.countByAdvisorUserIdAndStageIn(userId, OPEN)));
  }
}
