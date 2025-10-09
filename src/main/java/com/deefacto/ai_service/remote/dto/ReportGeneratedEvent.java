package com.deefacto.ai_service.remote.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ReportGeneratedEvent {
    private String zoneId;
    private OffsetDateTime timestamp;
}
