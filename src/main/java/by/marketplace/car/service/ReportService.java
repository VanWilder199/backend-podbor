package by.marketplace.car.service;

import by.marketplace.car.dto.ReportDto;
import by.marketplace.car.dto.UpdateConclusionRequest;
import by.marketplace.car.dto.UpdatePaintMeasurementsRequest;
import by.marketplace.car.dto.UpdateSectionRequest;

import java.util.UUID;

public interface ReportService {
  UUID createReport(UUID inspectorId, UUID carId);

  void updateSection(UUID reportid, UUID sectionId, UUID inspectorId, UpdateSectionRequest request);
  void updatePaintMeasurements(UUID reportid, UUID inspectorId, UpdatePaintMeasurementsRequest request);
  void updateConclusion(UUID reportid, UUID inspectorId, UpdateConclusionRequest request);
  void submitForModeration(UUID reportid, UUID inspectorId);

  ReportDto getReport(UUID reportId, UUID inspectorId);
}
