package com.deefacto.ai_service.Recommendation.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class BedrockResponseDto {
    private String text;
    private List<Map<String, Object>> data;
}
