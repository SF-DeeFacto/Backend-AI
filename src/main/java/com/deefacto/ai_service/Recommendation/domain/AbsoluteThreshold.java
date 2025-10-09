package com.deefacto.ai_service.Recommendation.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AbsoluteThreshold {
    private String sensorType;        // 센서 타입
    private Double warningLow;        // 경고 하한
    private Double warningHigh;       // 경고 상한
    private Double alertLow;          // 알림 하한
    private Double alertHigh;         // 알림 상한

    // 클린룸 기준 임계치
    public static Map<String, AbsoluteThreshold> defaultThresholds() {
        return Map.of(
                "temperature", new AbsoluteThreshold("temperature", 20.0, 22.0, 19.0, 24.0),
                "humidity", new AbsoluteThreshold("humidity", 40.0, 50.0, 32.0, 52.0),
                "electrostatic", new AbsoluteThreshold("electrostatic", null, 80.0, null, 100.0),
                "winddirection", new AbsoluteThreshold("winddirection", -14.0, 14.0, -20.0, 20.0),
                "particle_0_1um", new AbsoluteThreshold("particle_0_1um", null, 1000.0, null, 1045.0),
                "particle_0_3um", new AbsoluteThreshold("particle_0_3um", null, 102.0, null, 108.0),
                "particle_0_5um", new AbsoluteThreshold("particle_0_5um", null, 35.0, null, 39.0)
        );
    }
}
