/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.config;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.config.OAuth2Configuration;
import ca.samanthaireland.stormstack.thunder.auth.repository.ApiTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.MatchTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.RefreshTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.RoleRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.ServiceClientRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import ca.samanthaireland.stormstack.thunder.auth.service.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Quarkus CDI producer for core domain services.
 *
 * <p>This class bridges the framework-agnostic core domain services with
 * Quarkus CDI by producing singleton instances that can be injected into
 * REST resources and other framework components.
 *
 * <p>The producer pattern allows the core services to remain free of
 * framework annotations while still participating in CDI dependency injection.
 */
@ApplicationScoped
public class ServiceProducer {

    /**
     * Produces the core AuthConfiguration from Quarkus config.
     *
     * @param quarkusConfig the Quarkus configuration mapping
     * @return the core configuration interface
     */
    @Produces
    @Singleton
    public AuthConfiguration authConfiguration(QuarkusAuthConfig quarkusConfig) {
        return quarkusConfig;
    }

    /**
     * Produces the PasswordService.
     *
     * @param config the auth configuration
     * @return the password service
     */
    @Produces
    @Singleton
    public PasswordService passwordService(AuthConfiguration config) {
        return new PasswordServiceImpl(config.bcryptCost());
    }

    /**
     * Produces the RoleService.
     *
     * @param roleRepository the role repository
     * @return the role service
     */
    @Produces
    @Singleton
    public RoleService roleService(RoleRepository roleRepository) {
        return new RoleServiceImpl(roleRepository);
    }

    /**
     * Produces the ScopeService.
     *
     * @param roleRepository the role repository
     * @return the scope service
     */
    @Produces
    @Singleton
    public ScopeService scopeService(RoleRepository roleRepository) {
        return new ScopeServiceImpl(roleRepository);
    }

    /**
     * Produces the UserService.
     *
     * @param userRepository  the user repository
     * @param roleService     the role service
     * @param passwordService the password service
     * @return the user service
     */
    @Produces
    @Singleton
    public UserService userService(
            UserRepository userRepository,
            RoleService roleService,
            PasswordService passwordService) {
        return new UserServiceImpl(userRepository, roleService, passwordService);
    }

    /**
     * Produces the AuthenticationService.
     *
     * @param userRepository  the user repository
     * @param roleService     the role service
     * @param scopeService    the scope service
     * @param passwordService the password service
     * @param config          the auth configuration
     * @return the authentication service
     */
    @Produces
    @Singleton
    public AuthenticationService authenticationService(
            UserRepository userRepository,
            RoleService roleService,
            ScopeService scopeService,
            PasswordService passwordService,
            AuthConfiguration config) {
        return new AuthenticationServiceImpl(userRepository, roleService, scopeService, passwordService, config);
    }

    /**
     * Produces the ApiTokenService.
     *
     * @param apiTokenRepository the API token repository
     * @param userRepository     the user repository
     * @param passwordService    the password service
     * @param config             the auth configuration
     * @param jwtTokenService    the JWT token service for creating session tokens
     * @return the API token service
     */
    @Produces
    @Singleton
    public ApiTokenService apiTokenService(
            ApiTokenRepository apiTokenRepository,
            UserRepository userRepository,
            PasswordService passwordService,
            AuthConfiguration config,
            JwtTokenService jwtTokenService) {
        return new ApiTokenServiceImpl(apiTokenRepository, userRepository, passwordService, config, jwtTokenService);
    }

    /**
     * Produces the MatchTokenService.
     *
     * @param matchTokenRepository the match token repository
     * @param config               the auth configuration
     * @return the match token service
     */
    @Produces
    @Singleton
    public MatchTokenService matchTokenService(
            MatchTokenRepository matchTokenRepository,
            AuthConfiguration config) {
        return new MatchTokenServiceImpl(matchTokenRepository, config);
    }

    /**
     * Produces the ModuleTokenService.
     *
     * @param config the auth configuration
     * @return the module token service
     */
    @Produces
    @Singleton
    public ModuleTokenService moduleTokenService(AuthConfiguration config) {
        return new ModuleTokenServiceImpl(config);
    }

    // =========================================================================
    // OAuth2 Services
    // =========================================================================

    /**
     * Produces the OAuth2Configuration from Quarkus config.
     *
     * @param quarkusConfig the Quarkus OAuth2 configuration mapping
     * @return the OAuth2 configuration interface
     */
    @Produces
    @Singleton
    public OAuth2Configuration oauth2Configuration(QuarkusOAuth2Config quarkusConfig) {
        return quarkusConfig;
    }

    /**
     * Produces the JwtTokenService for OAuth2 token creation.
     *
     * @param config the auth configuration
     * @return the JWT token service
     */
    @Produces
    @Singleton
    public JwtTokenService jwtTokenService(AuthConfiguration config) {
        return new JwtTokenServiceImpl(config);
    }

    /**
     * Produces the ServiceClientRepository from configuration.
     *
     * @param oauth2Config    the OAuth2 configuration
     * @param passwordService the password service for hashing client secrets
     * @return the service client repository
     */
    @Produces
    @Singleton
    public ServiceClientRepository serviceClientRepository(
            OAuth2Configuration oauth2Config,
            PasswordService passwordService) {
        return new ConfigBasedServiceClientRepository(
                oauth2Config,
                passwordService::hashPassword
        );
    }

    /**
     * Produces the RefreshTokenRepository.
     *
     * <p>Uses in-memory implementation for now. For production, switch to MongoDB.
     *
     * @return the refresh token repository
     */
    @Produces
    @Singleton
    public RefreshTokenRepository refreshTokenRepository() {
        return new InMemoryRefreshTokenRepository();
    }

    /**
     * Produces the ClientCredentialsGrantHandler.
     *
     * @param jwtTokenService the JWT token service
     * @param oauth2Config    the OAuth2 configuration
     * @return the grant handler
     */
    @Produces
    @Singleton
    public ClientCredentialsGrantHandler clientCredentialsGrantHandler(
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        return new ClientCredentialsGrantHandler(jwtTokenService, oauth2Config);
    }

    /**
     * Produces the PasswordGrantHandler.
     *
     * @param userRepository         the user repository
     * @param passwordService        the password service
     * @param jwtTokenService        the JWT token service
     * @param refreshTokenRepository the refresh token repository
     * @param scopeService           the scope service
     * @param oauth2Config           the OAuth2 configuration
     * @return the grant handler
     */
    @Produces
    @Singleton
    public PasswordGrantHandler passwordGrantHandler(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtTokenService jwtTokenService,
            RefreshTokenRepository refreshTokenRepository,
            ScopeService scopeService,
            OAuth2Configuration oauth2Config) {
        return new PasswordGrantHandler(
                userRepository,
                passwordService,
                jwtTokenService,
                refreshTokenRepository,
                scopeService,
                oauth2Config
        );
    }

    /**
     * Produces the RefreshTokenGrantHandler.
     *
     * @param userRepository         the user repository
     * @param refreshTokenRepository the refresh token repository
     * @param passwordService        the password service
     * @param jwtTokenService        the JWT token service
     * @param oauth2Config           the OAuth2 configuration
     * @return the grant handler
     */
    @Produces
    @Singleton
    public RefreshTokenGrantHandler refreshTokenGrantHandler(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordService passwordService,
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        return new RefreshTokenGrantHandler(
                userRepository,
                refreshTokenRepository,
                passwordService,
                jwtTokenService,
                oauth2Config
        );
    }

    /**
     * Produces the TokenExchangeGrantHandler.
     *
     * @param apiTokenService the API token service
     * @param jwtTokenService the JWT token service
     * @param oauth2Config    the OAuth2 configuration
     * @return the grant handler
     */
    @Produces
    @Singleton
    public TokenExchangeGrantHandler tokenExchangeGrantHandler(
            ApiTokenService apiTokenService,
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        return new TokenExchangeGrantHandler(apiTokenService, jwtTokenService, oauth2Config);
    }

    /**
     * Produces the MatchTokenGrantHandler.
     *
     * @param matchTokenService the match token service
     * @return the grant handler
     */
    @Produces
    @Singleton
    public MatchTokenGrantHandler matchTokenGrantHandler(MatchTokenService matchTokenService) {
        return new MatchTokenGrantHandler(matchTokenService);
    }

    /**
     * Produces the ModuleTokenGrantHandler.
     *
     * @param moduleTokenService the module token service
     * @return the grant handler
     */
    @Produces
    @Singleton
    public ModuleTokenGrantHandler moduleTokenGrantHandler(ModuleTokenService moduleTokenService) {
        return new ModuleTokenGrantHandler(moduleTokenService);
    }

    /**
     * Produces the OAuth2TokenService.
     *
     * @param clientRepository              the service client repository
     * @param passwordService               the password service
     * @param clientCredentialsGrantHandler the client credentials grant handler
     * @param passwordGrantHandler          the password grant handler
     * @param refreshTokenGrantHandler      the refresh token grant handler
     * @param tokenExchangeGrantHandler     the token exchange grant handler
     * @param matchTokenGrantHandler        the match token grant handler
     * @param moduleTokenGrantHandler       the module token grant handler
     * @return the OAuth2 token service
     */
    @Produces
    @Singleton
    public OAuth2TokenService oauth2TokenService(
            ServiceClientRepository clientRepository,
            PasswordService passwordService,
            ClientCredentialsGrantHandler clientCredentialsGrantHandler,
            PasswordGrantHandler passwordGrantHandler,
            RefreshTokenGrantHandler refreshTokenGrantHandler,
            TokenExchangeGrantHandler tokenExchangeGrantHandler,
            MatchTokenGrantHandler matchTokenGrantHandler,
            ModuleTokenGrantHandler moduleTokenGrantHandler) {
        List<OAuth2GrantHandler> handlers = new ArrayList<>();
        handlers.add(clientCredentialsGrantHandler);
        handlers.add(passwordGrantHandler);
        handlers.add(refreshTokenGrantHandler);
        handlers.add(tokenExchangeGrantHandler);
        handlers.add(matchTokenGrantHandler);
        handlers.add(moduleTokenGrantHandler);
        return new OAuth2TokenServiceImpl(clientRepository, passwordService, handlers);
    }
}
