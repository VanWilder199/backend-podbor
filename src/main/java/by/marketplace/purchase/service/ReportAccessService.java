package by.marketplace.purchase.service;

import by.marketplace.purchase.dto.ReportViewDto;

public interface ReportAccessService {
    ReportViewDto getReportByToken(String rawToken);
}
