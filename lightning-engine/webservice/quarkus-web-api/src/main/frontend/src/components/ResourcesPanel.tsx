/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Delete as DeleteIcon,
    Folder as FolderIcon,
    Info as InfoIcon, Refresh as RefreshIcon
} from "@mui/icons-material";
import {
    Alert, Box, Button, Chip,
    CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, Typography
} from "@mui/material";
import { useState } from "react";
import {
    useDeleteContainerResourceMutation, useGetContainerResourcesQuery
} from "../store/api/apiSlice";
import { useAppSelector } from "../store/hooks";
import { selectSelectedContainerId } from "../store/slices/uiSlice";

const ResourcesPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(selectSelectedContainerId);

  const {
    data: resources = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetContainerResourcesQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });
  const [deleteResource] = useDeleteContainerResourceMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingResourceId, setDeletingResourceId] = useState<number | null>(
    null,
  );
  const [deletingResourceName, setDeletingResourceName] = useState<
    string | null
  >(null);

  const handleDeleteResource = async () => {
    if (deletingResourceId !== null && selectedContainerId !== null) {
      try {
        await deleteResource({
          containerId: selectedContainerId,
          resourceId: deletingResourceId,
        }).unwrap();
        setDeleteDialogOpen(false);
        setSuccess(`Resource "${deletingResourceName}" deleted`);
        setDeletingResourceId(null);
        setDeletingResourceName(null);
      } catch (err) {
        setLocalError(
          err instanceof Error ? err.message : "Failed to delete resource",
        );
      }
    }
  };

  const error = localError || (fetchError ? "Failed to fetch resources" : null);

  // Show message when no container is selected
  if (selectedContainerId === null) {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <InfoIcon sx={{ fontSize: 48, color: "text.secondary", mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view its resources.
        </Typography>
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
        }}
      >
        <Typography variant="h5">Container Resources</Typography>
        <Box sx={{ display: "flex", gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
        </Box>
      </Box>

      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          onClose={() => setLocalError(null)}
        >
          {error}
        </Alert>
      )}
      {success && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          onClose={() => setSuccess(null)}
        >
          {success}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Resource ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Type</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {resources.map((resource) => (
              <TableRow key={resource.id}>
                <TableCell>
                  <Chip label={resource.id} size="small" variant="outlined" />
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <FolderIcon color="primary" />
                    <Typography>{resource.name}</Typography>
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    label={resource.mimeType || "unknown"}
                    size="small"
                    variant="outlined"
                  />
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => {
                      setDeletingResourceId(Number(resource.id));
                      setDeletingResourceName(resource.name);
                      setDeleteDialogOpen(true);
                    }}
                  >
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {resources.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center">
                  <Typography color="text.secondary">
                    No resources in this container
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {resources.length > 0 && (
        <Box sx={{ mt: 3 }}>
          <Typography variant="h6" gutterBottom>
            Resources ({resources.length})
          </Typography>
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            {resources.map((resource) => (
              <Chip
                key={resource.id}
                icon={<FolderIcon />}
                label={resource.name}
                variant="outlined"
                color="primary"
              />
            ))}
          </Box>
        </Box>
      )}

      <Paper sx={{ mt: 3, p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          About Resources
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Resources are files stored within a container, such as textures,
          sprites, and other game assets. Each container maintains its own
          isolated set of resources that can be referenced by entities and
          modules within that container.
        </Typography>
      </Paper>

      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
      >
        <DialogTitle>Delete Resource</DialogTitle>
        <DialogContent>
          <Typography>
            Delete resource "{deletingResourceName}"? This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDeleteResource}
            color="error"
            variant="contained"
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ResourcesPanel;
