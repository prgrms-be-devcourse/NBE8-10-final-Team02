package com.back.backend.domain.followup.config;

import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "followup")
public class FollowupRulesProperties {

    private String version;
    private RuntimeRule runtime = new RuntimeRule();
    private Map<QuestionType, List<String>> questionTypeFallback = new LinkedHashMap<>();
    private Map<GapType, List<String>> signals = new LinkedHashMap<>();
    private Map<QuestionType, TypeRule> types = new LinkedHashMap<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public RuntimeRule getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeRule runtime) {
        this.runtime = runtime;
    }

    public Map<QuestionType, List<String>> getQuestionTypeFallback() {
        return questionTypeFallback;
    }

    public void setQuestionTypeFallback(Map<QuestionType, List<String>> questionTypeFallback) {
        this.questionTypeFallback = questionTypeFallback;
    }

    public Map<GapType, List<String>> getSignals() {
        return signals;
    }

    public void setSignals(Map<GapType, List<String>> signals) {
        this.signals = signals;
    }

    public Map<QuestionType, TypeRule> getTypes() {
        return types;
    }

    public void setTypes(Map<QuestionType, TypeRule> types) {
        this.types = types;
    }

    public boolean hasSignalPattern(GapType gapType) {
        List<String> patterns = signals.get(gapType);
        return patterns != null && !patterns.isEmpty();
    }

    public TypeRule requiredTypeRule(QuestionType questionType) {
        TypeRule typeRule = types.get(questionType);
        if (typeRule == null) {
            throw new IllegalStateException("No followup rule configured for questionType=" + questionType);
        }
        return typeRule;
    }

    public static class RuntimeRule {

        private String inputMode = "QUESTION_TYPE_PLUS_ANSWER_TEXT";
        private boolean useQuestionTypeFallbackOnlyWhenMissing = true;
        private int maxCandidateQuestionTypes = 2;
        private boolean strictDynamicMode = true;

        public String getInputMode() {
            return inputMode;
        }

        public void setInputMode(String inputMode) {
            this.inputMode = inputMode;
        }

        public boolean isUseQuestionTypeFallbackOnlyWhenMissing() {
            return useQuestionTypeFallbackOnlyWhenMissing;
        }

        public void setUseQuestionTypeFallbackOnlyWhenMissing(boolean useQuestionTypeFallbackOnlyWhenMissing) {
            this.useQuestionTypeFallbackOnlyWhenMissing = useQuestionTypeFallbackOnlyWhenMissing;
        }

        public int getMaxCandidateQuestionTypes() {
            return maxCandidateQuestionTypes;
        }

        public void setMaxCandidateQuestionTypes(int maxCandidateQuestionTypes) {
            this.maxCandidateQuestionTypes = maxCandidateQuestionTypes;
        }

        public boolean isStrictDynamicMode() {
            return strictDynamicMode;
        }

        public void setStrictDynamicMode(boolean strictDynamicMode) {
            this.strictDynamicMode = strictDynamicMode;
        }
    }

    public static class TypeRule {

        private List<GapType> core = new ArrayList<>();
        private List<GapType> extension = new ArrayList<>();
        private List<GapType> soft = new ArrayList<>();
        private Map<GapType, CandidateQuestionType> candidateMap = new LinkedHashMap<>();
        private CandidateQuestionType coreFallback;
        private List<DynamicWhitelistRule> dynamicWhitelist = new ArrayList<>();

        public List<GapType> getCore() {
            return core;
        }

        public void setCore(List<GapType> core) {
            this.core = core;
        }

        public List<GapType> getExtension() {
            return extension;
        }

        public void setExtension(List<GapType> extension) {
            this.extension = extension;
        }

        public List<GapType> getSoft() {
            return soft;
        }

        public void setSoft(List<GapType> soft) {
            this.soft = soft;
        }

        public Map<GapType, CandidateQuestionType> getCandidateMap() {
            return candidateMap;
        }

        public void setCandidateMap(Map<GapType, CandidateQuestionType> candidateMap) {
            this.candidateMap = candidateMap;
        }

        public CandidateQuestionType getCoreFallback() {
            return coreFallback;
        }

        public void setCoreFallback(CandidateQuestionType coreFallback) {
            this.coreFallback = coreFallback;
        }

        public List<DynamicWhitelistRule> getDynamicWhitelist() {
            return dynamicWhitelist;
        }

        public void setDynamicWhitelist(List<DynamicWhitelistRule> dynamicWhitelist) {
            this.dynamicWhitelist = dynamicWhitelist;
        }
    }

    public static class DynamicWhitelistRule {

        private List<GapType> missing = new ArrayList<>();
        private List<GapType> require = new ArrayList<>();

        public List<GapType> getMissing() {
            return missing;
        }

        public void setMissing(List<GapType> missing) {
            this.missing = missing;
        }

        public List<GapType> getRequire() {
            return require;
        }

        public void setRequire(List<GapType> require) {
            this.require = require;
        }
    }
}
