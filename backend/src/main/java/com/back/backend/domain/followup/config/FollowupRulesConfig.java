package com.back.backend.domain.followup.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Configuration
public class FollowupRulesConfig {

    private static final String FOLLOWUP_RULES_PREFIX = "followup";
    private static final String FOLLOWUP_RULES_RESOURCE_PATH = "followup-rules-v0.2.yaml";

    @Bean
    public FollowupRulesProperties followupRulesProperties() {
        return loadRules(new ClassPathResource(FOLLOWUP_RULES_RESOURCE_PATH));
    }

    public static FollowupRulesProperties loadRules(Resource resource) {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> propertySources = loader.load(FOLLOWUP_RULES_PREFIX, resource);
            MutablePropertySources mutablePropertySources = new MutablePropertySources();
            propertySources.forEach(mutablePropertySources::addLast);

            return new Binder(ConfigurationPropertySources.from(mutablePropertySources))
                    .bind(FOLLOWUP_RULES_PREFIX, Bindable.of(FollowupRulesProperties.class))
                    .orElseThrow(() -> new IllegalStateException(
                            "followup rules binding failed for resource=" + resource.getDescription()
                    ));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to load followup rules from resource=" + resource.getDescription(),
                    exception
            );
        }
    }
}
