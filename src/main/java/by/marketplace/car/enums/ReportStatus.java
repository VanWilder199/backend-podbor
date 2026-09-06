package by.marketplace.car.enums;

public enum ReportStatus {
    DRAFT("draft"),
    PENDING_REVIEW("pending_review"),
    REVISION_REQUIRED("revision_required"),
    PUBLISHED("published");

    private final String code;

    ReportStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}