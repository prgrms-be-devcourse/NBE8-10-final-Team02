package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FollowupPilot40BacktestTest {

    private static final Pattern SAMPLE_ID_PATTERN = Pattern.compile("^[A-Z]{2}\\d{2}$");
    private static final List<String> GENERIC_REGEX_PRIORITY = List.of(
            "(설계|구현|개선|적용|도입|수정|분리)(했|하여|해서|했고|했습니다)?",
            "(테스트|검증|모니터링|배포 후|로그|지표)",
            "(합의|조율|기준|최종|우선순위)",
            "(대안|비교|검토|고려|\\bvs\\b)",
            "(판단|우선|적합|선택한 이유|선택 기준)",
            "(줄었|개선되|향상되|증가했|감소했|안정화됐|빨라졌)"
    );
    private static final Map<String, String> GENERIC_REGEX_REASONS = Map.of(
            "(설계|구현|개선|적용|도입|수정|분리)(했|하여|해서|했고|했습니다)?",
            "ACTION 신호가 bare 동사군에 넓게 걸려 구현/수정 서술만으로 과검출되기 쉽다.",
            "(테스트|검증|모니터링|배포 후|로그|지표)",
            "VERIFICATION 신호가 bare 로그/지표/배포 후 표현만으로도 잡혀 검증이 없는 답을 과대평가한다.",
            "(합의|조율|기준|최종|우선순위)",
            "AGREEMENT 신호가 bare 기준/최종 표현에 반응해 일반 의사결정 문장까지 잡는다.",
            "(대안|비교|검토|고려|\\bvs\\b)",
            "ALTERNATIVE 신호가 bare 비교/검토/고려 표현에 반응해 실제 대안 비교가 없는 답도 잡는다.",
            "(판단|우선|적합|선택한 이유|선택 기준)",
            "REASON 신호가 bare 판단/우선/적합에 반응해 결과 요약 문장을 이유로 오인하기 쉽다.",
            "(줄었|개선되|향상되|증가했|감소했|안정화됐|빨라졌)",
            "RESULT 신호가 개선/증가 류 표현 하나만으로 잡혀 계획이나 기대 효과 문장과 섞일 수 있다."
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FollowupRulesProperties properties;
    private TextNormalizer textNormalizer;
    private SignalExtractor signalExtractor;
    private GapResolver gapResolver;
    private FinalActionDecider finalActionDecider;
    private CandidateQuestionSelector candidateQuestionSelector;

    @BeforeEach
    void setUp() {
        properties = loadProperties();
        textNormalizer = new TextNormalizer();
        signalExtractor = new SignalExtractor(properties);
        gapResolver = new GapResolver(properties);
        finalActionDecider = new FinalActionDecider(properties);
        candidateQuestionSelector = new CandidateQuestionSelector(properties);
    }

    @Test
    void generatePilot40BacktestArtifacts() throws Exception {
        Path workbookPath = findPilotWorkbookPath();
        assumeTrue(Files.exists(workbookPath), () -> "pilot 40 workbook not found: " + workbookPath);

        List<PilotRow> pilotRows = loadPilotRows(workbookPath);
        assertThat(pilotRows).hasSize(40);

        List<BacktestRow> backtestRows = pilotRows.stream()
                .map(this::evaluate)
                .toList();

        BacktestSummary summary = BacktestSummary.from(backtestRows);
        Path outputDirectory = Paths.get("").toAbsolutePath()
                .normalize()
                .resolve("build")
                .resolve("reports")
                .resolve("followup");
        Files.createDirectories(outputDirectory);

        Path csvPath = outputDirectory.resolve("pilot40_backtest.csv");
        Path reportPath = outputDirectory.resolve("pilot40_backtest_report.md");

        writeCsv(backtestRows, csvPath);
        writeReport(summary, backtestRows, reportPath, workbookPath);

        assertThat(Files.exists(csvPath)).isTrue();
        assertThat(Files.exists(reportPath)).isTrue();
    }

    private BacktestRow evaluate(PilotRow row) {
        GoldLabel goldLabel = normalizeGoldLabel(row.questionType(), row.missingAxes());

        String normalizedAnswerText = textNormalizer.normalize(row.answerText());
        Map<GapType, List<String>> matchedRegexes = signalExtractor.extractMatchedPatterns(normalizedAnswerText);
        Map<GapType, Boolean> signals = toSignalMap(matchedRegexes);
        GapResolver.Resolution resolution = gapResolver.resolve(row.questionType(), signals);
        FinalAction predictedFinalAction = finalActionDecider.decide(row.questionType(), signals, resolution);
        List<CandidateQuestionType> predictedCandidateQuestionTypes = candidateQuestionSelector.select(
                row.questionType(),
                signals,
                resolution,
                predictedFinalAction
        );

        return new BacktestRow(
                row,
                goldLabel,
                matchedRegexes,
                signals,
                resolution,
                predictedFinalAction,
                predictedCandidateQuestionTypes,
                matchesPredictedWhitelist(row.questionType(), signals, resolution),
                matchesGoldWhitelist(row.questionType(), goldLabel)
        );
    }

    private List<PilotRow> loadPilotRows(Path workbookPath) throws IOException {
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("pilot_labeling");
            if (sheet == null) {
                throw new IllegalStateException("pilot_labeling sheet not found in " + workbookPath);
            }

            Map<String, Integer> headerIndexes = readHeaderIndexes(sheet.getRow(sheet.getFirstRowNum()), dataFormatter);
            List<PilotRow> rows = new ArrayList<>();

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null) {
                    continue;
                }

                String sampleId = readCell(excelRow, headerIndexes, "sample_id", dataFormatter);
                if (!SAMPLE_ID_PATTERN.matcher(sampleId).matches()) {
                    continue;
                }

                rows.add(new PilotRow(
                        sampleId,
                        readCell(excelRow, headerIndexes, "question_text", dataFormatter),
                        readCell(excelRow, headerIndexes, "answer_text", dataFormatter),
                        toQuestionType(readCell(excelRow, headerIndexes, "question_type", dataFormatter)),
                        readCell(excelRow, headerIndexes, "missing_axes", dataFormatter),
                        FinalAction.valueOf(readCell(excelRow, headerIndexes, "final_action", dataFormatter).trim()),
                        readCell(excelRow, headerIndexes, "note", dataFormatter)
                ));
            }

            return rows;
        }
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

    private GoldLabel normalizeGoldLabel(QuestionType questionType, String rawMissingAxes) {
        List<GapType> orderedMissingGaps = new ArrayList<>();
        boolean scopeMixed = false;

        for (String token : rawMissingAxes.split(",")) {
            String normalizedToken = token.trim();
            if (normalizedToken.isEmpty()) {
                continue;
            }

            GapType gapType = toGapType(normalizedToken);
            if (gapType == GapType.SCOPE_MIXED) {
                scopeMixed = true;
                continue;
            }
            orderedMissingGaps.add(gapType);
        }

        GapType primaryGap = orderedMissingGaps.isEmpty() ? null : orderedMissingGaps.get(0);
        GapType secondaryGap = orderedMissingGaps.size() > 1 ? orderedMissingGaps.get(1) : null;

        return new GoldLabel(
                rawMissingAxes,
                List.copyOf(orderedMissingGaps),
                primaryGap,
                secondaryGap,
                scopeMixed,
                goldMainGapSet(questionType, orderedMissingGaps)
        );
    }

    private Map<GapType, Boolean> toSignalMap(Map<GapType, List<String>> matchedRegexes) {
        Map<GapType, Boolean> signals = new LinkedHashMap<>();

        for (GapType gapType : GapType.values()) {
            signals.put(gapType, !matchedRegexes.getOrDefault(gapType, List.of()).isEmpty());
        }

        return signals;
    }

    private boolean matchesPredictedWhitelist(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution
    ) {
        return matchesWhitelist(questionType, signals, new LinkedHashSet<>(resolution.mainMissingGaps()));
    }

    private boolean matchesGoldWhitelist(QuestionType questionType, GoldLabel goldLabel) {
        Map<GapType, Boolean> assumedSignals = new EnumMap<>(GapType.class);
        for (GapType gapType : GapType.values()) {
            if (gapType == GapType.SCOPE_MIXED) {
                assumedSignals.put(gapType, goldLabel.scopeMixed());
                continue;
            }
            assumedSignals.put(gapType, !goldLabel.orderedMissingGaps().contains(gapType));
        }
        return matchesWhitelist(questionType, assumedSignals, goldLabel.mainGapSet());
    }

    private boolean matchesWhitelist(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            Set<GapType> actualMissingGaps
    ) {
        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);

        for (FollowupRulesProperties.DynamicWhitelistRule whitelistRule : typeRule.getDynamicWhitelist()) {
            Set<GapType> expectedMissingGaps = new LinkedHashSet<>(whitelistRule.getMissing());
            boolean missingMatch = properties.getRuntime().isStrictDynamicMode()
                    ? actualMissingGaps.equals(expectedMissingGaps)
                    : actualMissingGaps.containsAll(expectedMissingGaps);

            boolean requireMatch = whitelistRule.getRequire().stream()
                    .allMatch(gapType -> Boolean.TRUE.equals(signals.get(gapType)));

            if (missingMatch && requireMatch) {
                return true;
            }
        }

        return false;
    }

    private Set<GapType> goldMainGapSet(QuestionType questionType, List<GapType> orderedMissingGaps) {
        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);
        Set<GapType> mainGaps = new LinkedHashSet<>();

        for (GapType gapType : orderedMissingGaps) {
            if (typeRule.getCore().contains(gapType) || typeRule.getExtension().contains(gapType)) {
                mainGaps.add(gapType);
            }
        }

        return mainGaps;
    }

    private void writeCsv(List<BacktestRow> rows, Path csvPath) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",",
                "sample_id",
                "question_type",
                "question_text",
                "answer_text",
                "gold_missing_axes_raw",
                "gold_scope_mixed",
                "gold_primary_gap",
                "gold_secondary_gap",
                "gold_final_action",
                "gold_whitelist_hit",
                "pred_primary_gap",
                "pred_secondary_gap",
                "pred_final_action",
                "pred_whitelist_hit",
                "pred_candidate_question_types",
                "pred_ordered_missing_gaps",
                "signals_json",
                "matched_regex_json",
                "final_action_match",
                "primary_gap_match",
                "secondary_gap_match",
                "gap_pair_unordered_match",
                "note"
        ));

        for (BacktestRow row : rows) {
            lines.add(String.join(",",
                    csv(row.sampleId()),
                    csv(row.questionType().name()),
                    csv(row.questionText()),
                    csv(row.answerText()),
                    csv(row.goldLabel().rawMissingAxes()),
                    csv(Boolean.toString(row.goldLabel().scopeMixed())),
                    csv(enumName(row.goldLabel().primaryGap())),
                    csv(enumName(row.goldLabel().secondaryGap())),
                    csv(row.goldFinalAction().name()),
                    csv(Boolean.toString(row.goldWhitelistHit())),
                    csv(enumName(row.predictedPrimaryGap())),
                    csv(enumName(row.predictedSecondaryGap())),
                    csv(row.predictedFinalAction().name()),
                    csv(Boolean.toString(row.predictedWhitelistHit())),
                    csv(toJson(row.predictedCandidateQuestionTypes())),
                    csv(toJson(row.predictedOrderedMissingGaps())),
                    csv(toJson(row.signals())),
                    csv(toJson(row.matchedRegexes())),
                    csv(Boolean.toString(row.finalActionMatch())),
                    csv(Boolean.toString(row.primaryGapMatch())),
                    csv(Boolean.toString(row.secondaryGapMatch())),
                    csv(Boolean.toString(row.gapPairUnorderedMatch())),
                    csv(row.note())
            ));
        }

        Files.write(csvPath, lines);
    }

    private void writeReport(
            BacktestSummary summary,
            List<BacktestRow> rows,
            Path reportPath,
            Path workbookPath
    ) throws IOException {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# Followup Pilot 40 Backtest").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- input: `").append(workbookPath).append('`').append(System.lineSeparator());
        markdown.append("- rows: `").append(summary.totalRows()).append("`").append(System.lineSeparator());
        markdown.append("- normalization: `multi_project -> SCOPE_MIXED flag`, excluded from gold primary/secondary gap scoring").append(System.lineSeparator());
        markdown.append(System.lineSeparator());

        markdown.append("## Metrics").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| metric | value | detail |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- |").append(System.lineSeparator());
        markdown.append(metricRow("final_action_accuracy", summary.finalActionAccuracy(), summary.finalActionMatchCount(), summary.totalRows()));
        markdown.append(metricRow("primary_gap_accuracy", summary.primaryGapAccuracy(), summary.primaryGapMatchCount(), summary.totalRows()));
        markdown.append(metricRow("secondary_gap_accuracy", summary.secondaryGapAccuracy(), summary.secondaryGapMatchCount(), summary.totalRows()));
        markdown.append(metricRow("gap_pair_unordered_match_rate", summary.gapPairUnorderedMatchRate(), summary.gapPairMatchCount(), summary.totalRows()));
        markdown.append(metricRow("use_dynamic_precision", summary.useDynamicPrecision(), summary.truePositiveDynamicCount(), summary.predictedDynamicCount()));
        markdown.append(metricRow("no_follow_up_precision", summary.noFollowUpPrecision(), summary.truePositiveNoFollowUpCount(), summary.predictedNoFollowUpCount()));
        markdown.append(metricRow("dynamic_rate", summary.dynamicRate(), summary.predictedDynamicCount(), summary.totalRows()));
        markdown.append(metricRow("whitelist_pair_hit_rate", summary.whitelistPairHitRate(), summary.predictedWhitelistHitCount(), summary.totalRows()));
        markdown.append(metricRow("gold_dynamic_whitelist_coverage", summary.goldDynamicWhitelistCoverage(), summary.goldDynamicWhitelistHitCount(), summary.goldDynamicCount()));
        markdown.append(System.lineSeparator());

        markdown.append("## Confusion Matrix").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| gold \\\\ pred | NO_FOLLOW_UP | USE_CANDIDATE | USE_DYNAMIC |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- | --- |").append(System.lineSeparator());
        for (FinalAction goldAction : FinalAction.values()) {
            markdown.append("| ").append(goldAction.name()).append(" | ")
                    .append(summary.confusionCount(goldAction, FinalAction.NO_FOLLOW_UP)).append(" | ")
                    .append(summary.confusionCount(goldAction, FinalAction.USE_CANDIDATE)).append(" | ")
                    .append(summary.confusionCount(goldAction, FinalAction.USE_DYNAMIC)).append(" |")
                    .append(System.lineSeparator());
        }
        markdown.append(System.lineSeparator());

        markdown.append("## Mismatches").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| sample_id | question_type | gold_final_action | pred_final_action | gold_primary | pred_primary | gold_secondary | pred_secondary |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- | --- | --- | --- | --- | --- |").append(System.lineSeparator());
        rows.stream()
                .filter(row -> !row.finalActionMatch() || !row.gapPairUnorderedMatch())
                .forEach(row -> markdown.append("| ")
                        .append(row.sampleId()).append(" | ")
                        .append(row.questionType().name()).append(" | ")
                        .append(row.goldFinalAction().name()).append(" | ")
                        .append(row.predictedFinalAction().name()).append(" | ")
                        .append(nullSafeName(row.goldLabel().primaryGap())).append(" | ")
                        .append(nullSafeName(row.predictedPrimaryGap())).append(" | ")
                        .append(nullSafeName(row.goldLabel().secondaryGap())).append(" | ")
                        .append(nullSafeName(row.predictedSecondaryGap())).append(" |")
                        .append(System.lineSeparator()));
        markdown.append(System.lineSeparator());

        markdown.append("## Regex False Positives").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| gap | regex | false_positive_samples | sample_ids |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- | --- |").append(System.lineSeparator());
        for (RegexFalsePositiveStat stat : summary.regexFalsePositiveStats()) {
            markdown.append("| ")
                    .append(stat.gapType().name())
                    .append(" | `")
                    .append(stat.regex())
                    .append("` | ")
                    .append(stat.sampleIds().size())
                    .append(" | ")
                    .append(String.join(", ", stat.sampleIds()))
                    .append(" |")
                    .append(System.lineSeparator());
        }
        markdown.append(System.lineSeparator());

        markdown.append("## Generic Regex Remove Candidates").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| gap | regex | false_positive_samples | sample_ids | rationale |").append(System.lineSeparator());
        markdown.append("| --- | --- | --- | --- | --- |").append(System.lineSeparator());
        for (RemovalCandidate candidate : summary.removalCandidates()) {
            markdown.append("| ")
                    .append(candidate.gapType().name())
                    .append(" | `")
                    .append(candidate.regex())
                    .append("` | ")
                    .append(candidate.falsePositiveCount())
                    .append(" | ")
                    .append(String.join(", ", candidate.sampleIds()))
                    .append(" | ")
                    .append(candidate.rationale())
                    .append(" |")
                    .append(System.lineSeparator());
        }

        Files.writeString(reportPath, markdown.toString());
    }

    private String metricRow(String metricName, Double value, long numerator, long denominator) {
        String formattedValue = value == null ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
        return "| " + metricName + " | " + formattedValue + " | " + numerator + "/" + denominator + " |"
                + System.lineSeparator();
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String nullSafeName(Enum<?> value) {
        return value == null ? "-" : value.name();
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

    private QuestionType toQuestionType(String rawValue) {
        return QuestionType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private GapType toGapType(String rawValue) {
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("MULTI_PROJECT".equals(normalized) || "SCOPE_MIXED".equals(normalized)) {
            return GapType.SCOPE_MIXED;
        }
        return GapType.valueOf(normalized);
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

    private record PilotRow(
            String sampleId,
            String questionText,
            String answerText,
            QuestionType questionType,
            String missingAxes,
            FinalAction goldFinalAction,
            String note
    ) {
    }

    private record GoldLabel(
            String rawMissingAxes,
            List<GapType> orderedMissingGaps,
            GapType primaryGap,
            GapType secondaryGap,
            boolean scopeMixed,
            Set<GapType> mainGapSet
    ) {
    }

    private record BacktestRow(
            PilotRow pilotRow,
            GoldLabel goldLabel,
            Map<GapType, List<String>> matchedRegexes,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution,
            FinalAction predictedFinalAction,
            List<CandidateQuestionType> predictedCandidateQuestionTypes,
            boolean predictedWhitelistHit,
            boolean goldWhitelistHit
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

        String note() {
            return pilotRow.note();
        }

        GapType predictedPrimaryGap() {
            return resolution.primaryGap();
        }

        GapType predictedSecondaryGap() {
            return resolution.secondaryGap();
        }

        List<GapType> predictedOrderedMissingGaps() {
            return resolution.orderedMissingGaps();
        }

        boolean finalActionMatch() {
            return goldFinalAction() == predictedFinalAction;
        }

        boolean primaryGapMatch() {
            return Objects.equals(goldLabel.primaryGap(), predictedPrimaryGap());
        }

        boolean secondaryGapMatch() {
            return Objects.equals(goldLabel.secondaryGap(), predictedSecondaryGap());
        }

        boolean gapPairUnorderedMatch() {
            return unorderedGapPair(goldLabel.primaryGap(), goldLabel.secondaryGap())
                    .equals(unorderedGapPair(predictedPrimaryGap(), predictedSecondaryGap()));
        }

        private Set<GapType> unorderedGapPair(GapType first, GapType second) {
            Set<GapType> pair = new LinkedHashSet<>();
            if (first != null) {
                pair.add(first);
            }
            if (second != null) {
                pair.add(second);
            }
            return pair;
        }
    }

    private record RegexFalsePositiveStat(
            GapType gapType,
            String regex,
            List<String> sampleIds
    ) {
    }

    private record RemovalCandidate(
            GapType gapType,
            String regex,
            int falsePositiveCount,
            List<String> sampleIds,
            String rationale
    ) {
    }

    private record BacktestSummary(
            int totalRows,
            long finalActionMatchCount,
            long primaryGapMatchCount,
            long secondaryGapMatchCount,
            long gapPairMatchCount,
            long predictedDynamicCount,
            long predictedNoFollowUpCount,
            long truePositiveDynamicCount,
            long truePositiveNoFollowUpCount,
            long predictedWhitelistHitCount,
            long goldDynamicCount,
            long goldDynamicWhitelistHitCount,
            Map<FinalAction, Map<FinalAction, Integer>> confusionMatrix,
            List<RegexFalsePositiveStat> regexFalsePositiveStats,
            List<RemovalCandidate> removalCandidates
    ) {

        static BacktestSummary from(List<BacktestRow> rows) {
            long finalActionMatchCount = rows.stream().filter(BacktestRow::finalActionMatch).count();
            long primaryGapMatchCount = rows.stream().filter(BacktestRow::primaryGapMatch).count();
            long secondaryGapMatchCount = rows.stream().filter(BacktestRow::secondaryGapMatch).count();
            long gapPairMatchCount = rows.stream().filter(BacktestRow::gapPairUnorderedMatch).count();
            long predictedDynamicCount = rows.stream()
                    .filter(row -> row.predictedFinalAction() == FinalAction.USE_DYNAMIC)
                    .count();
            long predictedNoFollowUpCount = rows.stream()
                    .filter(row -> row.predictedFinalAction() == FinalAction.NO_FOLLOW_UP)
                    .count();
            long truePositiveDynamicCount = rows.stream()
                    .filter(row -> row.predictedFinalAction() == FinalAction.USE_DYNAMIC)
                    .filter(BacktestRow::finalActionMatch)
                    .count();
            long truePositiveNoFollowUpCount = rows.stream()
                    .filter(row -> row.predictedFinalAction() == FinalAction.NO_FOLLOW_UP)
                    .filter(BacktestRow::finalActionMatch)
                    .count();
            long predictedWhitelistHitCount = rows.stream()
                    .filter(BacktestRow::predictedWhitelistHit)
                    .count();
            long goldDynamicCount = rows.stream()
                    .filter(row -> row.goldFinalAction() == FinalAction.USE_DYNAMIC)
                    .count();
            long goldDynamicWhitelistHitCount = rows.stream()
                    .filter(row -> row.goldFinalAction() == FinalAction.USE_DYNAMIC)
                    .filter(BacktestRow::goldWhitelistHit)
                    .count();

            Map<FinalAction, Map<FinalAction, Integer>> confusionMatrix = new EnumMap<>(FinalAction.class);
            for (FinalAction goldAction : FinalAction.values()) {
                Map<FinalAction, Integer> confusionRow = new EnumMap<>(FinalAction.class);
                for (FinalAction predictedAction : FinalAction.values()) {
                    confusionRow.put(predictedAction, 0);
                }
                confusionMatrix.put(goldAction, confusionRow);
            }
            for (BacktestRow row : rows) {
                confusionMatrix.get(row.goldFinalAction()).merge(row.predictedFinalAction(), 1, Integer::sum);
            }

            List<RegexFalsePositiveStat> falsePositiveStats = buildRegexFalsePositiveStats(rows);
            List<RemovalCandidate> removalCandidates = falsePositiveStats.stream()
                    .filter(stat -> GENERIC_REGEX_PRIORITY.contains(stat.regex()))
                    .sorted(Comparator
                            .comparingInt((RegexFalsePositiveStat stat) -> GENERIC_REGEX_PRIORITY.indexOf(stat.regex()))
                            .thenComparing((RegexFalsePositiveStat stat) -> -stat.sampleIds().size()))
                    .limit(5)
                    .map(stat -> new RemovalCandidate(
                            stat.gapType(),
                            stat.regex(),
                            stat.sampleIds().size(),
                            stat.sampleIds(),
                            GENERIC_REGEX_REASONS.getOrDefault(stat.regex(), "pilot 40 오탐 샘플이 누적된 broad regex")
                    ))
                    .toList();

            return new BacktestSummary(
                    rows.size(),
                    finalActionMatchCount,
                    primaryGapMatchCount,
                    secondaryGapMatchCount,
                    gapPairMatchCount,
                    predictedDynamicCount,
                    predictedNoFollowUpCount,
                    truePositiveDynamicCount,
                    truePositiveNoFollowUpCount,
                    predictedWhitelistHitCount,
                    goldDynamicCount,
                    goldDynamicWhitelistHitCount,
                    confusionMatrix,
                    falsePositiveStats,
                    removalCandidates
            );
        }

        private static List<RegexFalsePositiveStat> buildRegexFalsePositiveStats(List<BacktestRow> rows) {
            Map<String, LinkedHashSet<String>> sampleIdsByKey = new LinkedHashMap<>();
            Map<String, GapType> gapByKey = new LinkedHashMap<>();
            Map<String, String> regexByKey = new LinkedHashMap<>();

            for (BacktestRow row : rows) {
                for (Map.Entry<GapType, List<String>> entry : row.matchedRegexes().entrySet()) {
                    GapType gapType = entry.getKey();
                    boolean goldSignalPresent = gapType == GapType.SCOPE_MIXED
                            ? row.goldLabel().scopeMixed()
                            : !row.goldLabel().orderedMissingGaps().contains(gapType);

                    if (goldSignalPresent) {
                        continue;
                    }

                    for (String regex : entry.getValue()) {
                        String key = gapType.name() + "::" + regex;
                        sampleIdsByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(row.sampleId());
                        gapByKey.putIfAbsent(key, gapType);
                        regexByKey.putIfAbsent(key, regex);
                    }
                }
            }

            return sampleIdsByKey.entrySet().stream()
                    .map(entry -> new RegexFalsePositiveStat(
                            gapByKey.get(entry.getKey()),
                            regexByKey.get(entry.getKey()),
                            List.copyOf(entry.getValue())
                    ))
                    .sorted(Comparator
                            .comparingInt((RegexFalsePositiveStat stat) -> stat.sampleIds().size())
                            .reversed()
                            .thenComparing(stat -> stat.gapType().name())
                            .thenComparing(RegexFalsePositiveStat::regex))
                    .toList();
        }

        Double finalActionAccuracy() {
            return rate(finalActionMatchCount, totalRows);
        }

        Double primaryGapAccuracy() {
            return rate(primaryGapMatchCount, totalRows);
        }

        Double secondaryGapAccuracy() {
            return rate(secondaryGapMatchCount, totalRows);
        }

        Double gapPairUnorderedMatchRate() {
            return rate(gapPairMatchCount, totalRows);
        }

        Double useDynamicPrecision() {
            return rate(truePositiveDynamicCount, predictedDynamicCount);
        }

        Double noFollowUpPrecision() {
            return rate(truePositiveNoFollowUpCount, predictedNoFollowUpCount);
        }

        Double dynamicRate() {
            return rate(predictedDynamicCount, totalRows);
        }

        Double whitelistPairHitRate() {
            return rate(predictedWhitelistHitCount, totalRows);
        }

        Double goldDynamicWhitelistCoverage() {
            return rate(goldDynamicWhitelistHitCount, goldDynamicCount);
        }

        int confusionCount(FinalAction goldAction, FinalAction predictedAction) {
            return confusionMatrix.get(goldAction).get(predictedAction);
        }

        private static Double rate(long numerator, long denominator) {
            if (denominator == 0) {
                return null;
            }
            return (double) numerator / denominator;
        }
    }
}
