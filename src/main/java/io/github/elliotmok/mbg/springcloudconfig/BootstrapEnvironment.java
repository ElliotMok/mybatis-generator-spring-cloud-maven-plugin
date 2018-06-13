package io.github.elliotmok.mbg.springcloudconfig;

import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/**
 * 特定于"spring-cloud-config"的"bootstrap文件"的Environment
 *
 * @author molibin
 * @date 2018-01-17
 * @since 1.1.0
 */
public class BootstrapEnvironment extends PropertySourcesPropertyResolver implements Environment {
    /**
     * Create a new resolver against the given property sources.
     *
     * @param propertySources the set of {@link PropertySource} objects to use
     */
    public BootstrapEnvironment(PropertySources propertySources) {
        super(propertySources);
    }

    @Override
    public String[] getActiveProfiles() {
        return new String[]{"dev", "prd", "test"};
    }

    @Override
    public String[] getDefaultProfiles() {
        return new String[]{"dev"};
    }

    @Override
    public boolean acceptsProfiles(String... profiles) {
        return false;
    }
}