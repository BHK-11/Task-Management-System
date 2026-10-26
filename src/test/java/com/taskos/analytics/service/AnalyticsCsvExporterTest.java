package com.taskos.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnalyticsCsvExporterTest {
    @Test
    void csvCellsEscapeQuotesAndNeutralizeSpreadsheetFormulas() {
        assertEquals("\"'=2+2\"", AnalyticsCsvExporter.quote("=2+2"));
        assertEquals("\"'  @SUM(A1:A2)\"", AnalyticsCsvExporter.quote("  @SUM(A1:A2)"));
        assertEquals("\"a\"\"b,c\"", AnalyticsCsvExporter.quote("a\"b,c"));
    }
}
