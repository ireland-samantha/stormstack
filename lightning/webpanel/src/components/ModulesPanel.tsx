/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Extension as ModuleIcon,
    Info as InfoIcon, Refresh as RefreshIcon
} from "@mui/icons-material";
import {
    Alert, Box, Button,
    Chip,
    CircularProgress, IconButton, LinearProgress, Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, Typography
} from "@mui/material";
import { useState } from "react";
import {
    useGetContainerModulesQuery,
    useReloadContainerModulesMutation
} from "../store/api/apiSlice";
import { useAppSelector } from "../store/hooks";
import { selectSelectedContainerId } from "../store/slices/uiSlice";

const ModulesPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(selectSelectedContainerId);

  const {
    data: moduleNames = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetContainerModulesQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });
  const [reloadModules] = useReloadContainerModulesMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [reloading, setReloading] = useState(false);

  const handleReloadModules = async () => {
    if (selectedContainerId === null) return;
    setReloading(true);
    setLocalError(null);
    try {
      await reloadModules(selectedContainerId).unwrap();
      setSuccess("Modules reloaded successfully");
      refetch();
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : "Failed to reload modules",
      );
    } finally {
      setReloading(false);
    }
  };

  const error = localError || (fetchError ? "Failed to fetch modules" : null);

  // Show message when no container is selected
  if (selectedContainerId === null) {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <InfoIcon sx={{ fontSize: 48, color: "text.secondary", mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view its modules.
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
        <Typography variant="h5">Container Modules</Typography>
        <Box sx={{ display: "flex", gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<RefreshIcon />}
            onClick={handleReloadModules}
            disabled={reloading}
          >
            Reload Modules
          </Button>
        </Box>
      </Box>

      {reloading && <LinearProgress sx={{ mb: 2 }} />}
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
              <TableCell>Module Name</TableCell>
              <TableCell>Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {moduleNames.map((moduleName) => (
              <TableRow key={moduleName}>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <ModuleIcon color="primary" />
                    <Typography fontWeight="medium">{moduleName}</Typography>
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    label="Installed"
                    size="small"
                    color="success"
                    variant="outlined"
                  />
                </TableCell>
              </TableRow>
            ))}
            {moduleNames.length === 0 && (
              <TableRow>
                <TableCell colSpan={2} align="center">
                  <Typography color="text.secondary">
                    No modules installed in this container
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {moduleNames.length > 0 && (
        <Box sx={{ mt: 3 }}>
          <Typography variant="h6" gutterBottom>
            Installed Modules ({moduleNames.length})
          </Typography>
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            {moduleNames.map((moduleName) => (
              <Chip
                key={moduleName}
                icon={<ModuleIcon />}
                label={moduleName}
                variant="outlined"
                color="primary"
              />
            ))}
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default ModulesPanel;
