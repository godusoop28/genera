package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.ContractSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSettingRepository extends JpaRepository<ContractSetting, String> {}
