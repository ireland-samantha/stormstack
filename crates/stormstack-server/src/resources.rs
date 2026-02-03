//! Resource management for game assets.
//!
//! Provides upload/download and management of WASM modules and other game resources
//! with tenant isolation and content hashing for integrity verification.

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::PathBuf;
use std::sync::Arc;
use stormstack_core::{ResourceId, Result, StormError, TenantId};
use tokio::fs;
use tokio::io::AsyncWriteExt;
use tracing::{debug, info, warn};

/// Resource metadata stored alongside the resource data.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceMetadata {
    /// Unique resource identifier.
    pub id: ResourceId,
    /// Tenant that owns this resource.
    pub tenant_id: TenantId,
    /// Human-readable resource name.
    pub name: String,
    /// Type of resource.
    pub resource_type: ResourceType,
    /// Size of the resource in bytes.
    pub size_bytes: u64,
    /// SHA-256 hash of the content for integrity verification.
    pub content_hash: String,
    /// When the resource was created.
    pub created_at: DateTime<Utc>,
}

/// Type of resource being stored.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ResourceType {
    /// WebAssembly module for game logic.
    WasmModule,
    /// Generic game asset (textures, sounds, etc.).
    GameAsset,
    /// Configuration file.
    Configuration,
}

impl std::fmt::Display for ResourceType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ResourceType::WasmModule => write!(f, "wasm_module"),
            ResourceType::GameAsset => write!(f, "game_asset"),
            ResourceType::Configuration => write!(f, "configuration"),
        }
    }
}

impl std::str::FromStr for ResourceType {
    type Err = StormError;

    fn from_str(s: &str) -> std::result::Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "wasm_module" | "wasm" => Ok(ResourceType::WasmModule),
            "game_asset" | "asset" => Ok(ResourceType::GameAsset),
            "configuration" | "config" => Ok(ResourceType::Configuration),
            _ => Err(StormError::InvalidState(format!(
                "Unknown resource type: {}",
                s
            ))),
        }
    }
}

/// Trait for resource storage backends.
///
/// Allows swapping between file system, S3, or other storage implementations.
#[async_trait]
pub trait ResourceStorage: Send + Sync {
    /// Store a resource and return its metadata.
    ///
    /// # Arguments
    /// * `tenant_id` - Tenant that owns the resource
    /// * `name` - Human-readable name for the resource
    /// * `resource_type` - Type of resource being stored
    /// * `data` - Raw resource data
    ///
    /// # Returns
    /// Metadata for the stored resource including its generated ID and content hash.
    async fn store(
        &self,
        tenant_id: TenantId,
        name: &str,
        resource_type: ResourceType,
        data: &[u8],
    ) -> Result<ResourceMetadata>;

    /// Retrieve a resource by ID.
    ///
    /// # Arguments
    /// * `tenant_id` - Tenant that owns the resource (for isolation)
    /// * `id` - Resource identifier
    ///
    /// # Returns
    /// The resource data if found, or None if not found.
    async fn get(&self, tenant_id: TenantId, id: ResourceId) -> Result<Option<Vec<u8>>>;

    /// Retrieve only the metadata for a resource.
    ///
    /// # Arguments
    /// * `tenant_id` - Tenant that owns the resource
    /// * `id` - Resource identifier
    ///
    /// # Returns
    /// The resource metadata if found, or None if not found.
    async fn get_metadata(
        &self,
        tenant_id: TenantId,
        id: ResourceId,
    ) -> Result<Option<ResourceMetadata>>;

    /// List all resources for a tenant.
    ///
    /// # Arguments
    /// * `tenant_id` - Tenant to list resources for
    ///
    /// # Returns
    /// Vector of metadata for all resources owned by the tenant.
    async fn list(&self, tenant_id: TenantId) -> Result<Vec<ResourceMetadata>>;

    /// Delete a resource.
    ///
    /// # Arguments
    /// * `tenant_id` - Tenant that owns the resource
    /// * `id` - Resource identifier
    ///
    /// # Returns
    /// True if the resource was deleted, false if it wasn't found.
    async fn delete(&self, tenant_id: TenantId, id: ResourceId) -> Result<bool>;
}

/// File system-based resource storage.
///
/// Stores resources in a directory structure organized by tenant:
/// ```text
/// base_path/
///   <tenant_id>/
///     <resource_id>.data     # Resource content
///     <resource_id>.meta     # Resource metadata (JSON)
/// ```
pub struct FileSystemStorage {
    base_path: PathBuf,
}

impl FileSystemStorage {
    /// Create a new file system storage with the given base path.
    ///
    /// # Arguments
    /// * `base_path` - Directory where resources will be stored
    #[must_use]
    pub fn new(base_path: PathBuf) -> Self {
        Self { base_path }
    }

    /// Get the path to a tenant's resource directory.
    fn tenant_path(&self, tenant_id: TenantId) -> PathBuf {
        self.base_path.join(tenant_id.0.to_string())
    }

    /// Get the path to a resource's data file.
    fn resource_data_path(&self, tenant_id: TenantId, id: ResourceId) -> PathBuf {
        self.tenant_path(tenant_id)
            .join(format!("{}.data", id.0))
    }

    /// Get the path to a resource's metadata file.
    fn resource_meta_path(&self, tenant_id: TenantId, id: ResourceId) -> PathBuf {
        self.tenant_path(tenant_id)
            .join(format!("{}.meta", id.0))
    }

    /// Compute SHA-256 hash of data and return as hex string.
    fn compute_hash(data: &[u8]) -> String {
        let mut hasher = Sha256::new();
        hasher.update(data);
        let result = hasher.finalize();
        hex::encode(result)
    }
}

#[async_trait]
impl ResourceStorage for FileSystemStorage {
    async fn store(
        &self,
        tenant_id: TenantId,
        name: &str,
        resource_type: ResourceType,
        data: &[u8],
    ) -> Result<ResourceMetadata> {
        let id = ResourceId::new();
        let content_hash = Self::compute_hash(data);
        let size_bytes = data.len() as u64;
        let created_at = Utc::now();

        let metadata = ResourceMetadata {
            id,
            tenant_id,
            name: name.to_string(),
            resource_type,
            size_bytes,
            content_hash,
            created_at,
        };

        // Ensure tenant directory exists
        let tenant_dir = self.tenant_path(tenant_id);
        fs::create_dir_all(&tenant_dir).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!(
                "Failed to create tenant directory: {}",
                e
            ))
        })?;

        // Write data file
        let data_path = self.resource_data_path(tenant_id, id);
        let mut file = fs::File::create(&data_path).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to create data file: {}", e))
        })?;
        file.write_all(data).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to write data: {}", e))
        })?;
        file.flush().await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to flush data: {}", e))
        })?;

        // Write metadata file
        let meta_path = self.resource_meta_path(tenant_id, id);
        let meta_json = serde_json::to_string_pretty(&metadata).map_err(|e| {
            StormError::Serialization(format!("Failed to serialize metadata: {}", e))
        })?;
        fs::write(&meta_path, meta_json).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to write metadata: {}", e))
        })?;

        info!(
            resource_id = %id,
            tenant_id = %tenant_id,
            name = %name,
            resource_type = %resource_type,
            size_bytes = size_bytes,
            "Resource stored"
        );

        Ok(metadata)
    }

    async fn get(&self, tenant_id: TenantId, id: ResourceId) -> Result<Option<Vec<u8>>> {
        let data_path = self.resource_data_path(tenant_id, id);

        match fs::read(&data_path).await {
            Ok(data) => {
                debug!(resource_id = %id, tenant_id = %tenant_id, "Resource retrieved");
                Ok(Some(data))
            }
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(e) => Err(StormError::Internal(anyhow::anyhow!(
                "Failed to read resource: {}",
                e
            ))),
        }
    }

    async fn get_metadata(
        &self,
        tenant_id: TenantId,
        id: ResourceId,
    ) -> Result<Option<ResourceMetadata>> {
        let meta_path = self.resource_meta_path(tenant_id, id);

        match fs::read_to_string(&meta_path).await {
            Ok(json) => {
                let metadata: ResourceMetadata = serde_json::from_str(&json).map_err(|e| {
                    StormError::Serialization(format!("Failed to parse metadata: {}", e))
                })?;
                Ok(Some(metadata))
            }
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(e) => Err(StormError::Internal(anyhow::anyhow!(
                "Failed to read metadata: {}",
                e
            ))),
        }
    }

    async fn list(&self, tenant_id: TenantId) -> Result<Vec<ResourceMetadata>> {
        let tenant_dir = self.tenant_path(tenant_id);

        // If directory doesn't exist, return empty list
        if !tenant_dir.exists() {
            return Ok(Vec::new());
        }

        let mut entries = fs::read_dir(&tenant_dir).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to read tenant directory: {}", e))
        })?;

        let mut resources = Vec::new();

        while let Some(entry) = entries.next_entry().await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to read directory entry: {}", e))
        })? {
            let path = entry.path();
            if let Some(ext) = path.extension()
                && ext == "meta" {
                    match fs::read_to_string(&path).await {
                        Ok(json) => match serde_json::from_str::<ResourceMetadata>(&json) {
                            Ok(metadata) => resources.push(metadata),
                            Err(e) => {
                                warn!(path = %path.display(), error = %e, "Failed to parse metadata file");
                            }
                        },
                        Err(e) => {
                            warn!(path = %path.display(), error = %e, "Failed to read metadata file");
                        }
                    }
                }
        }

        // Sort by creation time (newest first)
        resources.sort_by(|a, b| b.created_at.cmp(&a.created_at));

        debug!(
            tenant_id = %tenant_id,
            count = resources.len(),
            "Listed resources"
        );

        Ok(resources)
    }

    async fn delete(&self, tenant_id: TenantId, id: ResourceId) -> Result<bool> {
        let data_path = self.resource_data_path(tenant_id, id);
        let meta_path = self.resource_meta_path(tenant_id, id);

        // Check if resource exists
        if !data_path.exists() {
            return Ok(false);
        }

        // Delete data file
        fs::remove_file(&data_path).await.map_err(|e| {
            StormError::Internal(anyhow::anyhow!("Failed to delete data file: {}", e))
        })?;

        // Delete metadata file (ignore if not found)
        if meta_path.exists() {
            fs::remove_file(&meta_path).await.map_err(|e| {
                StormError::Internal(anyhow::anyhow!("Failed to delete metadata file: {}", e))
            })?;
        }

        info!(resource_id = %id, tenant_id = %tenant_id, "Resource deleted");

        Ok(true)
    }
}

/// Shared resource storage type.
pub type SharedResourceStorage = Arc<dyn ResourceStorage>;

/// Create a shared file system storage with the given base path.
#[must_use]
pub fn shared_file_storage_with_path(base_path: PathBuf) -> SharedResourceStorage {
    Arc::new(FileSystemStorage::new(base_path))
}

/// Create a shared file system storage with the default path.
///
/// Uses the `STORMSTACK_RESOURCES_PATH` environment variable if set,
/// otherwise falls back to `./data/resources`.
#[must_use]
pub fn shared_file_storage() -> SharedResourceStorage {
    let base_path = std::env::var("STORMSTACK_RESOURCES_PATH")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("./data/resources"));
    shared_file_storage_with_path(base_path)
}

/// Verify that a resource's content matches its hash.
///
/// # Arguments
/// * `data` - Resource data to verify
/// * `expected_hash` - Expected SHA-256 hash (hex-encoded)
///
/// # Returns
/// True if the hash matches, false otherwise.
#[must_use]
pub fn verify_content_hash(data: &[u8], expected_hash: &str) -> bool {
    FileSystemStorage::compute_hash(data) == expected_hash
}

// Hex encoding utilities
mod hex {
    pub fn encode(bytes: impl AsRef<[u8]>) -> String {
        bytes
            .as_ref()
            .iter()
            .map(|b| format!("{:02x}", b))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    async fn create_test_storage() -> (TempDir, FileSystemStorage) {
        let temp_dir = TempDir::new().expect("create temp dir");
        let storage = FileSystemStorage::new(temp_dir.path().to_path_buf());
        (temp_dir, storage)
    }

    #[tokio::test]
    async fn store_and_retrieve_resource() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();
        let data = b"Hello, World!";

        // Store resource
        let metadata = storage
            .store(tenant_id, "test.txt", ResourceType::GameAsset, data)
            .await
            .expect("store resource");

        assert_eq!(metadata.tenant_id, tenant_id);
        assert_eq!(metadata.name, "test.txt");
        assert_eq!(metadata.resource_type, ResourceType::GameAsset);
        assert_eq!(metadata.size_bytes, data.len() as u64);
        assert!(!metadata.content_hash.is_empty());

        // Retrieve resource
        let retrieved = storage
            .get(tenant_id, metadata.id)
            .await
            .expect("get resource")
            .expect("resource exists");

        assert_eq!(retrieved, data);
    }

    #[tokio::test]
    async fn list_resources_for_tenant() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();

        // Store multiple resources
        storage
            .store(tenant_id, "resource1.wasm", ResourceType::WasmModule, b"wasm1")
            .await
            .expect("store 1");
        storage
            .store(tenant_id, "resource2.wasm", ResourceType::WasmModule, b"wasm2")
            .await
            .expect("store 2");
        storage
            .store(tenant_id, "config.json", ResourceType::Configuration, b"{}")
            .await
            .expect("store 3");

        // List resources
        let resources = storage.list(tenant_id).await.expect("list resources");

        assert_eq!(resources.len(), 3);
    }

    #[tokio::test]
    async fn delete_resource() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();

        // Store resource
        let metadata = storage
            .store(tenant_id, "to_delete.txt", ResourceType::GameAsset, b"data")
            .await
            .expect("store");

        // Verify it exists
        assert!(storage
            .get(tenant_id, metadata.id)
            .await
            .expect("get")
            .is_some());

        // Delete it
        let deleted = storage
            .delete(tenant_id, metadata.id)
            .await
            .expect("delete");
        assert!(deleted);

        // Verify it's gone
        assert!(storage
            .get(tenant_id, metadata.id)
            .await
            .expect("get")
            .is_none());

        // Deleting again should return false
        let deleted_again = storage
            .delete(tenant_id, metadata.id)
            .await
            .expect("delete again");
        assert!(!deleted_again);
    }

    #[tokio::test]
    async fn tenant_isolation_resources() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Store resource for tenant1
        let metadata = storage
            .store(tenant1, "secret.txt", ResourceType::GameAsset, b"tenant1 data")
            .await
            .expect("store");

        // Tenant2 should not be able to access it
        let result = storage.get(tenant2, metadata.id).await.expect("get");
        assert!(result.is_none());

        // Tenant2 should not see it in their list
        let tenant2_resources = storage.list(tenant2).await.expect("list");
        assert!(tenant2_resources.is_empty());

        // Tenant1 should see it
        let tenant1_resources = storage.list(tenant1).await.expect("list");
        assert_eq!(tenant1_resources.len(), 1);
    }

    #[tokio::test]
    async fn upload_wasm_module() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();

        // Minimal valid WASM module (magic number + version)
        let wasm_data = vec![0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00];

        let metadata = storage
            .store(tenant_id, "game.wasm", ResourceType::WasmModule, &wasm_data)
            .await
            .expect("store wasm");

        assert_eq!(metadata.resource_type, ResourceType::WasmModule);
        assert_eq!(metadata.size_bytes, 8);

        // Retrieve and verify
        let retrieved = storage
            .get(tenant_id, metadata.id)
            .await
            .expect("get wasm")
            .expect("wasm exists");

        assert_eq!(retrieved, wasm_data);
    }

    #[tokio::test]
    async fn content_hash_verification() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();
        let data = b"Test data for hashing";

        let metadata = storage
            .store(tenant_id, "hash_test.txt", ResourceType::GameAsset, data)
            .await
            .expect("store");

        // Verify hash matches
        assert!(verify_content_hash(data, &metadata.content_hash));

        // Verify different data doesn't match
        assert!(!verify_content_hash(b"Different data", &metadata.content_hash));
    }

    #[tokio::test]
    async fn get_metadata_only() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();

        let metadata = storage
            .store(tenant_id, "meta_test.txt", ResourceType::GameAsset, b"data")
            .await
            .expect("store");

        // Get metadata without content
        let retrieved_meta = storage
            .get_metadata(tenant_id, metadata.id)
            .await
            .expect("get metadata")
            .expect("metadata exists");

        assert_eq!(retrieved_meta.id, metadata.id);
        assert_eq!(retrieved_meta.name, metadata.name);
        assert_eq!(retrieved_meta.content_hash, metadata.content_hash);
    }

    #[tokio::test]
    async fn resource_type_parsing() {
        assert_eq!(
            "wasm_module".parse::<ResourceType>().unwrap(),
            ResourceType::WasmModule
        );
        assert_eq!(
            "wasm".parse::<ResourceType>().unwrap(),
            ResourceType::WasmModule
        );
        assert_eq!(
            "game_asset".parse::<ResourceType>().unwrap(),
            ResourceType::GameAsset
        );
        assert_eq!(
            "asset".parse::<ResourceType>().unwrap(),
            ResourceType::GameAsset
        );
        assert_eq!(
            "configuration".parse::<ResourceType>().unwrap(),
            ResourceType::Configuration
        );
        assert_eq!(
            "config".parse::<ResourceType>().unwrap(),
            ResourceType::Configuration
        );

        assert!("unknown".parse::<ResourceType>().is_err());
    }

    #[tokio::test]
    async fn empty_list_for_nonexistent_tenant() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();

        let resources = storage.list(tenant_id).await.expect("list");
        assert!(resources.is_empty());
    }

    #[tokio::test]
    async fn get_nonexistent_resource() {
        let (_temp_dir, storage) = create_test_storage().await;
        let tenant_id = TenantId::new();
        let fake_id = ResourceId::new();

        let result = storage.get(tenant_id, fake_id).await.expect("get");
        assert!(result.is_none());

        let meta_result = storage.get_metadata(tenant_id, fake_id).await.expect("get metadata");
        assert!(meta_result.is_none());
    }
}
