package ca.samanthaireland.lightning.auth.quarkus.config;

import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Registers the LightningAuthConfig config mapping with SmallRye Config.
 * This is needed for non-extension Quarkus modules to have their config mappings discovered.
 */
public class LightningAuthConfigRegistrar implements SmallRyeConfigBuilderCustomizer {

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withMapping(LightningAuthConfig.class);
    }
}
