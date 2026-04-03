package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FollowupPilot40CandidateQuestionBacktestTest {

    private static final Pattern SAMPLE_ID_PATTERN = Pattern.compile("^[A-Z]{2}\\d{2}$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FollowupRuleService followupRuleService;

    @BeforeEach
    void setUp() {
        FollowupRulesProperties properties = loadProperties();

        followupRuleService = new FollowupRuleService(
                new TextNormalizer(),
                new SignalExtractor(properties),
                new GapResolver(properties),
                new FinalActionDecider(properties),
                new CandidateQuestionSelector(properties)
        );
    }

    @Test
    void generatePilot40CandidateQuestionBacktestArtifacts() throws Exception {
        Path workbookPath = findPilotWorkbookPath();
        assumeTrue(Files.exists(workbookPath), () -> "pilot 40 workbook not found: " + workbookPath);

        List<CandidatePilotRow> pilotRows = loadCandidatePilotRows(workbookPath);
        assertThat(pilotRows).hasSize(16);
        assertThat(pilotRows)
                .extracting(CandidatePilotRow::expectedCandidateQuestionTypes)
                .allSatisfy(expected -> assertThat(expected).isNotEmpty());

        List<CandidateBacktestRow> backtestRows = pilotRows.stream()
                .map(this::evaluate)
                .toList();

        CandidateBacktestSummary summary = CandidateBacktestSummary.from(backtestRows);
        Path outputDirectory = Paths.get("").toAbsolutePath()
                .normalize()
                .resolve("build")
                .resolve("reports")
                .resolve("followup");
        Files.createDirectories(outputDirectory);

        Path csvPath = outputDirectory.resolve("pilot40_candidate_question_backtest.csv");
        Path reportPath = outputDirectory.resolve("pilot40_candidate_question_backtest_report.md");

        writeCsv(backtestRows, csvPath);
        writeReport(summary, backtestRows, reportPath, workbookPath);

        CandidateBacktestRow pe05 = requireRow(backtestRows, "PE05");
        CandidateBacktestRow ps03 = requireRow(backtestRows, "PS03");
        CandidateBacktestRow ps07 = requireRow(backtestRows, "PS07");
        CandidateBacktestRow ps08 = requireRow(backtestRows, "PS08");

        assertThat(Files.exists(csvPath)).isTrue();
        assertThat(Files.exists(reportPath)).isTrue();
        assertThat(summary.candidateRows()).isEqualTo(16);
        assertThat(summary.predictedCandidateActionRate()).isGreaterThanOrEqualTo(0.875);
        assertThat(summary.exactMatchRate()).isGreaterThanOrEqualTo(0.250);
        assertThat(summary.unorderedMatchRate()).isGreaterThanOrEqualTo(0.250);
        assertThat(summary.primaryCandidateMatchRate()).isGreaterThanOrEqualTo(0.437);

        assertThat(pe05.exactMatch()).isTrue();
        assertThat(ps03.exactMatch()).isTrue();
        assertThat(ps07.exactMatch()).isTrue();
        assertThat(ps08.exactMatch()).isTrue();
    }

    private CandidateBacktestRow requireRow(List<CandidateBacktestRow> rows, String sampleId) {
        return rows.stream()
                .filter(row -> row.sampleId().equals(sampleId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("pilot row not found: " + sampleId));
    }

    private CandidateBacktestRow evaluate(CandidatePilotRow row) {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                row.questionType(),
                row.answerText()
        ));

        return new CandidateBacktestRow(
                row,
                response.finalAction(),
                response.candidateQuestionTypes()
        );
    }

    private List<CandidatePilotRow> loadCandidatePilotRows(Path workbookPath) throws IOException {
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("pilot_labeling");
            if (sheet == null) {
                throw new IllegalStateException("pilot_labeling sheet not found in " + workbookPath);
            }

            Map<String, Integer> headerIndexes = readHeaderIndexes(sheet.getRow(sheet.getFirstRowNum()), dataFormatter);
            List<CandidatePilotRow> rows = new ArrayList<>();

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null) {
                    continue;
                }

                String sampleId = readCell(excelRow, headerIndexes, "sample_id", dataFormatter);
                if (!SAMPLE_ID_PATTERN.matcher(sampleId).matches()) {
                    continue;
                }

                FinalAction goldFinalAction = FinalAction.valueOf(
                        readCell(excelRow, headerIndexes, "final_action", dataFormatter).trim()
                );
                if (goldFinalAction != FinalAction.USE_CANDIDATE) {
                    continue;
                }

                List<CandidateQuestionType> expectedCandidateQuestionTypes = parseCandidateQuestionTypes(
                        readCell(excelRow, headerIndexes, "expected_candidate_question_types", dataFormatter)
                );
                if (expectedCandidateQuestionTypes.isEmpty()) {
                    throw new IllegalStateException("Missing expected_candidate_question_types for sample " + sampleId);
                }

                rows.add(new CandidatePilotRow(
                        sampleId,
                        readCell(excelRow, headerIndexes, "question_text", dataFormatter),
                        readCell(excelRow, headerIndexes, "answer_text", dataFormatter),
                        QuestionType.valueOf(readCell(excelRow, headerIndexes, "question_type", dataFormatter)
                                .trim()
                                .toUpperCase(Locale.ROOT)
                                .replace('-', '_')),
                        goldFinalAction,
                        expectedCandidateQuestionTypes,
                        readCell(excelRow, headerIndexes, "note", dataFormatter)
                ));
            }

            return rows;
        }
    }

    private List<CandidateQuestionType> parseCandidateQuestionTypes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        return List.of(rawValue.split(",")).stream()
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(CandidateQuestionType::valueOf)
                .toList();
    }

    private Map<String, Integer> readHeaderIndexes(Row headerRow, DataFormatter dataFormatter) {
        Map<String, Integer> headerIndexes = new LinkedHashMap<>();

        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String header = dataFormatter.formatCellValue(headerRow.getCell(cellIndex)).trim();
            if (!header.isEmpty()) {
                headerIndexes.put(header, cellIndex);
            }
        }

        return headerIndexes;
    }

    private String readCell(Row row, Map<String, Integer> headerIndexes, String headerName, DataFormatter dataFormatter) {
        Integer cellIndex = headerIndexes.get(headerName);
        if (cellIndex == null) {
            throw new IllegalStateException("Missing header: " + headerName);
        }

        return dataFormatter.formatCellValue(row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();
    }

    private void writeCsv(List<CandidateBacktestRow> rows, Path csvPath) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",",
                "sample_id",
                "question_type",
                "question_text",
                "answer_text",
                "gold_final_action",
                "pred_final_action",
                "gold_candidate_question_types",
                "pred_candidate_question_types",
                "candidate_action_match",
                "exact_match",
                "unordered_match",
                "primary_candidate_match",
                "note"
        ));

        for (CandidateBacktestRow row : rows) {
            lines.add(String.join(",",
                    csv(row.sampleId()),
                    csv(row.questionType().name()),
                    csv(row.questionText()),
                    csv(row.answerText()),
                    csv(row.goldFinalAction().name()),
                    csv(row.predictedFinalAction().name()),
                    csv(toJson(row.expectedCandidateQuestionTypes())),
                    csv(toJson(row.predictedCandidateQuestionTypes())),
                    csv(Boolean.toString(row.candidateActionMatch())),
                    csv(Boolean.toString(row.exactMatch())),
                    csv(Boolean.toString(row.unorderedMatch())),
                    csv(Boolean.toString(row.primaryCandidateMatch())),
                    csv(row.note())
            ));
        }

        Files.write(csvPath, lines);
    }

    private void writeReport(
            CandidateBacktestSummary summary,
            List<CandidateBacktestRow> rows,
            Path reportPath,
            Path workbookPath
    ) throws IOException {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# Followup Pilot 40 Candidate Question Backtest")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        markdown.append("- input: `").append(workbookPath).append('`').append(System.lineSeparator());
        markdown.append("- gold filter: `final_action == USE_CANDIDATE`").append(System.lineSeparator());
        markdown.append("- rows: `").append(summary.candidateRows()).append("`").append(System.lineSeparator());
        markdown.append(System.lineSeparator());

        markdown.append("## Metrics").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| metric | value | detail |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- |").append(System.lineSeparator());
        markdown.append(metricRow("candidate_final_action_gate_accuracy", summary.predictedCandidateActionRate(), summary.predictedCandidateActionCount(), summary.candidateRows()));
        markdown.append(metricRow("candidate_exact_match_accuracy", summary.exactMatchRate(), summary.exactMatchCount(), summary.candidateRows()));
        markdown.append(metricRow("candidate_unordered_match_rate", summary.unorderedMatchRate(), summary.unorderedMatchCount(), summary.candidateRows()));
        markdown.append(metricRow("candidate_primary_match_accuracy", summary.primaryCandidateMatchRate(), summary.primaryCandidateMatchCount(), summary.candidateRows()));
        markdown.append(System.lineSeparator());

        markdown.append("## Mismatches").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| sample_id | question_type | pred_final_action | gold_candidate_question_types | pred_candidate_question_types |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- | --- | --- |").append(System.lineSeparator());
        rows.stream()
                .filter(row -> !row.exactMatch())
                .forEach(row -> markdown.append("| ")
                        .append(row.sampleId()).append(" | ")
                        .append(row.questionType().name()).append(" | ")
                        .append(row.predictedFinalAction().name()).append(" | `")
                        .append(String.join(", ", row.expectedCandidateQuestionTypes().stream().map(Enum::name).toList()))
                        .append("` | `")
                        .append(String.join(", ", row.predictedCandidateQuestionTypes().stream().map(Enum::name).toList()))
                        .append("` |")
                        .append(System.lineSeparator()));

        Files.writeString(reportPath, markdown.toString());
    }

    private String metricRow(String metricName, Double value, long numerator, long denominator) {
        String formattedValue = value == null ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
        return "| " + metricName + " | " + formattedValue + " | " + numerator + "/" + denominator + " |"
                + System.lineSeparator();
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialization failed", exception);
        }
    }

    private Path findPilotWorkbookPath() {
        Path current = Paths.get("").toAbsolutePath().normalize();

        while (current != null) {
            Path candidate = current.resolve(".local").resolve("p0").resolve("pilot_labeling_sheet_v0_1_filled_40.xlsx");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        return Paths.get(".local").resolve("p0").resolve("pilot_labeling_sheet_v0_1_filled_40.xlsx").toAbsolutePath();
    }

    private FollowupRulesProperties loadProperties() {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> propertySources = loader.load(
                    "followup-rules",
                    new ClassPathResource("followup-rules-v0.2.yaml")
            );
            MutablePropertySources mutablePropertySources = new MutablePropertySources();
            propertySources.forEach(mutablePropertySources::addLast);

            return new Binder(ConfigurationPropertySources.from(mutablePropertySources))
                    .bind("followup", Bindable.of(FollowupRulesProperties.class))
                    .orElseThrow(() -> new IllegalStateException("followup rules binding failed"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record CandidatePilotRow(
            String sampleId,
            String questionText,
            String answerText,
            QuestionType questionType,
            FinalAction goldFinalAction,
            List<CandidateQuestionType> expectedCandidateQuestionTypes,
            String note
    ) {
    }

    private record CandidateBacktestRow(
            CandidatePilotRow pilotRow,
            FinalAction predictedFinalAction,
            List<CandidateQuestionType> predictedCandidateQuestionTypes
    ) {

        String sampleId() {
            return pilotRow.sampleId();
        }

        String questionText() {
            return pilotRow.questionText();
        }

        String answerText() {
            return pilotRow.answerText();
        }

        QuestionType questionType() {
            return pilotRow.questionType();
        }

        FinalAction goldFinalAction() {
            return pilotRow.goldFinalAction();
        }

        List<CandidateQuestionType> expectedCandidateQuestionTypes() {
            return pilotRow.expectedCandidateQuestionTypes();
        }

        String note() {
            return pilotRow.note();
        }

        boolean candidateActionMatch() {
            return predictedFinalAction == FinalAction.USE_CANDIDATE;
        }

        boolean exactMatch() {
            return expectedCandidateQuestionTypes().equals(predictedCandidateQuestionTypes);
        }

        boolean unorderedMatch() {
            return List.copyOf(expectedCandidateQuestionTypes()).containsAll(predictedCandidateQuestionTypes)
                    && List.copyOf(predictedCandidateQuestionTypes).containsAll(expectedCandidateQuestionTypes());
        }

        boolean primaryCandidateMatch() {
            if (expectedCandidateQuestionTypes().isEmpty() || predictedCandidateQuestionTypes.isEmpty()) {
                return false;
            }
            return Objects.equals(expectedCandidateQuestionTypes().get(0), predictedCandidateQuestionTypes.get(0));
        }
    }

    private record CandidateBacktestSummary(
            int candidateRows,
            long predictedCandidateActionCount,
            long exactMatchCount,
            long unorderedMatchCount,
            long primaryCandidateMatchCount
    ) {

        static CandidateBacktestSummary from(List<CandidateBacktestRow> rows) {
            return new CandidateBacktestSummary(
                    rows.size(),
                    rows.stream().filter(CandidateBacktestRow::candidateActionMatch).count(),
                    rows.stream().filter(CandidateBacktestRow::exactMatch).count(),
                    rows.stream().filter(CandidateBacktestRow::unorderedMatch).count(),
                    rows.stream().filter(CandidateBacktestRow::primaryCandidateMatch).count()
            );
        }

        Double predictedCandidateActionRate() {
            return rate(predictedCandidateActionCount, candidateRows);
        }

        Double exactMatchRate() {
            return rate(exactMatchCount, candidateRows);
        }

        Double unorderedMatchRate() {
            return rate(unorderedMatchCount, candidateRows);
        }

        Double primaryCandidateMatchRate() {
            return rate(primaryCandidateMatchCount, candidateRows);
        }

        private static Double rate(long numerator, long denominator) {
            if (denominator == 0) {
                return null;
            }
            return (double) numerator / denominator;
        }
    }
}
