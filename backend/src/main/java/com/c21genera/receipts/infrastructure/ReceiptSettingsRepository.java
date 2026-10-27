package com.c21genera.receipts.infrastructure;

import com.c21genera.receipts.domain.ReceiptSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptSettingsRepository extends JpaRepository<ReceiptSettings, Short> {}
