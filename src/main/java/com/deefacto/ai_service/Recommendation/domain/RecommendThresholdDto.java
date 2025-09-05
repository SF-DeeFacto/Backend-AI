package com.deefacto.ai_service.Recommendation.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendThresholdDto {
    // Dashboard의 Threshold와 일치
    // zoneId와 sensorType은 어떤 센서인지 특정하기 위해 필수
    @NotBlank(message = "zoneId는 필수입니다.")
    private String zoneId;
    @NotBlank(message = "sensorType은 필수입니다.")
    private String sensorType;

    private String reasonTitle;
    private String reasonContent;

    // 사용자가 수정 가능한 임계치 값
    private Double warningLow;
    private Double warningHigh;
    private Double alertLow;
    private Double alertHigh;

    @Override
    public String toString() {
        return "SensorThresholdUpdateRequestDto{" +
                "zoneId='" + zoneId + '\'' +
                ", sensorType='" + sensorType + '\'' +
                ", reasonTitle='" + reasonTitle + '\'' +
                ", reasonContent='" + reasonContent + '\'' +
                ", warningLow=" + warningLow +
                ", warningHigh=" + warningHigh +
                ", alertLow=" + alertLow +
                ", alertHigh=" + alertHigh +
                '}';
    }

}
