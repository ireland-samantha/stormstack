package ca.samanthaireland.stormstack.thunder.engine.quarkus.api.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Registers the LightningAuthConfig config mapping with SmallRye Config.
 * This is needed because the auth adapter is not a Quarkus extension.
 */
@StaticInitSafe
public class AuthConfigRegistrar implements SmallRyeConfigBuilderCustomizer {

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withMapping(ca.samanthaireland.stormstack.thunder.auth.quarkus.config.LightningAuthConfig.class);
    }
}
