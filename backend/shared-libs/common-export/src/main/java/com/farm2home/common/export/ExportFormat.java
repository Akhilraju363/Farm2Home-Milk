package com.farm2home.common.export;

public enum ExportFormat {

    CSV("text/csv", ".csv"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
    PDF("application/pdf", ".pdf");

    private final String contentType;
    private final String fileExtension;

    ExportFormat(String contentType, String fileExtension) {
        this.contentType = contentType;
        this.fileExtension = fileExtension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
