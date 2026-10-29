package com.example.platform.payment.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {}
