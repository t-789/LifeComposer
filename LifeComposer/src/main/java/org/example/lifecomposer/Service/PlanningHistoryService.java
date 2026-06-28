package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.PlanningHistory;
import org.example.lifecomposer.Repository.PlanningHistoryRepository;
import org.example.lifecomposer.dto.PlanningHistoryDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlanningHistoryService {

    private final PlanningHistoryRepository planningHistoryRepository;

    public PlanningHistoryService(PlanningHistoryRepository planningHistoryRepository) {
        this.planningHistoryRepository = planningHistoryRepository;
    }

    public Long appendRecord(Long userId, String type, String requestJson,
                             String responseJson, String provider, String model,
                             String status, String errorMessage) {
        PlanningHistory record = new PlanningHistory();
        record.setUserId(userId);
        record.setType(type);
        record.setRequestJson(requestJson);
        record.setResponseJson(responseJson);
        record.setProvider(provider);
        record.setModel(model);
        record.setStatus(status != null ? status : "MOCKED");
        record.setErrorMessage(errorMessage);
        return planningHistoryRepository.insert(record);
    }

    public PlanningHistoryDto getRecord(Long id, Long userId) {
        PlanningHistory record = planningHistoryRepository.findById(id);
        if (record == null) {
            throw new IllegalStateException("规划记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new IllegalStateException("无权访问此记录");
        }
        return toDto(record);
    }

    public List<PlanningHistoryDto> listRecords(Long userId) {
        return planningHistoryRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private PlanningHistoryDto toDto(PlanningHistory record) {
        if (record == null) {
            return null;
        }
        PlanningHistoryDto dto = new PlanningHistoryDto();
        dto.setId(record.getId());
        dto.setType(record.getType());
        dto.setRequestJson(record.getRequestJson());
        dto.setResponseJson(record.getResponseJson());
        dto.setProvider(record.getProvider());
        dto.setModel(record.getModel());
        dto.setStatus(record.getStatus());
        dto.setErrorMessage(record.getErrorMessage());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }
}
