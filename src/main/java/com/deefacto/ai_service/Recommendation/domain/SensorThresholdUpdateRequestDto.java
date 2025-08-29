package com.deefacto.ai_service.Recommendation.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorThresholdUpdateRequestDto {
    // Dashboard의 Threshold와 일치
    // zoneId와 sensorType은 어떤 센서인지 특정하기 위해 필수
    private String zoneId;
    private String sensorType;

    // 사용자가 수정 가능한 임계치 값
    private Double warningLow;
    private Double warningHigh;
    private Double alertLow;
    private Double alertHigh;

    // 절대 임계치와 비교
    public void filterSafeThreshold(AbsoluteThreshold absoluteThreshold) {
        if (warningLow < absoluteThreshold.getWarningLow()) warningLow = absoluteThreshold.getWarningLow();
        if (warningHigh > absoluteThreshold.getWarningHigh()) warningHigh = absoluteThreshold.getWarningHigh();
        if (alertLow < absoluteThreshold.getAlertLow()) alertLow = absoluteThreshold.getAlertLow();
        if (alertHigh > absoluteThreshold.getAlertHigh()) alertHigh = absoluteThreshold.getAlertHigh();
    }

}
