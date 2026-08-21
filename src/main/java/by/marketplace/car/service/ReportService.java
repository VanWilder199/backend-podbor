package by.marketplace.car.service;

import java.util.UUID;

public interface ReportService {
  UUID createReport(UUID inspectorId, UUID carId);
}
