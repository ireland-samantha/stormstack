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

package ca.samanthaireland.lightning.controlplane.module.service;

import ca.samanthaireland.lightning.controlplane.config.ModuleStorageConfiguration;
import ca.samanthaireland.lightning.controlplane.module.exception.ModuleNotFoundException;
import ca.samanthaireland.lightning.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.lightning.controlplane.module.repository.ModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ModuleRegistryService.
 *
 * <p>This is a pure domain implementation with no framework dependencies.
 * Dependencies are provided via constructor injection.
 *
 * <p>Note: Module distribution is handled by {@link ModuleDistributionService}
 * following the Interface Segregation Principle.
 */
public class ModuleRegistryServiceImpl implements ModuleRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistryServiceImpl.class);

    private final ModuleRepository moduleRepository;
    private final ModuleStorageConfiguration config;

    /**
     * Creates a new ModuleRegistryServiceImpl.
     *
     * @param moduleRepository the module repository
     * @param config           the module storage configuration
     */
    public ModuleRegistryServiceImpl(
            ModuleRepository moduleRepository,
            ModuleStorageConfiguration config
    ) {
        this.moduleRepository = moduleRepository;
        this.config = config;
    }

    @Override
    public ModuleMetadata uploadModule(
            String name,
            String version,
            String description,
            String fileName,
            byte[] jarData,
            String uploadedBy
    ) {
        // Validate file size
        if (jarData.length > config.maxFileSize()) {
            throw new IllegalArgumentException(
                    "File size " + jarData.length + " exceeds maximum " + config.maxFileSize()
            );
        }

        // Calculate checksum
        String checksum = calculateChecksum(jarData);

        // Create metadata
        ModuleMetadata metadata = ModuleMetadata.create(
                name, version, description, fileName, jarData.length, checksum, uploadedBy
        );

        // Save to repository
        ModuleMetadata saved = moduleRepository.save(metadata, jarData);

        log.info("Uploaded module {}:{} ({} bytes, checksum: {})",
                name, version, jarData.length, checksum.substring(0, 16) + "...");

        return saved;
    }

    @Override
    public Optional<ModuleMetadata> getModule(String name, String version) {
        return moduleRepository.findByNameAndVersion(name, version);
    }

    @Override
    public List<ModuleMetadata> getModuleVersions(String name) {
        return moduleRepository.findByName(name);
    }

    @Override
    public List<ModuleMetadata> listAllModules() {
        return moduleRepository.findAll();
    }

    @Override
    public InputStream downloadModule(String name, String version) {
        return moduleRepository.getJarFile(name, version)
                .orElseThrow(() -> new ModuleNotFoundException(name, version));
    }

    @Override
    public void deleteModule(String name, String version) {
        if (!moduleRepository.exists(name, version)) {
            throw new ModuleNotFoundException(name, version);
        }

        moduleRepository.delete(name, version);
        log.info("Deleted module {}:{}", name, version);
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
