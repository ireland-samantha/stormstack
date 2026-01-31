/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  CircularProgress,
  Alert,
  IconButton,
  Tooltip,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  CloudUpload as DistributeIcon,
  Extension as ModuleIcon,
} from "@mui/icons-material";
import { useState } from "react";
import {
  useGetClusterModulesQuery,
  useDistributeModuleMutation,
} from "../store/api/apiSlice";

const ClusterModulesPanel: React.FC = () => {
  const {
    data: modules = [],
    isLoading,
    error,
    refetch,
  } = useGetClusterModulesQuery();

  const [distributeModule] = useDistributeModuleMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [distributeDialogOpen, setDistributeDialogOpen] = useState(false);
  const [selectedModule, setSelectedModule] = useState<{
    name: string;
    versions: string[];
  } | null>(null);
  const [selectedVersion, setSelectedVersion] = useState<string>("");

  const handleDistributeClick = (module: {
    name: string;
    version: string;
    versions?: string[];
  }) => {
    setSelectedModule({
      name: module.name,
      versions: module.versions || [module.version],
    });
    setSelectedVersion(module.version);
    setDistributeDialogOpen(true);
  };

  const handleDistribute = async () => {
    if (!selectedModule || !selectedVersion) return;

    try {
      const result = await distributeModule({
        name: selectedModule.name,
        version: selectedVersion,
      }).unwrap();
      setSuccess(
        `Module ${selectedModule.name}@${selectedVersion} distributed to ${result.nodesUpdated} nodes`
      );
      setDistributeDialogOpen(false);
      setSelectedModule(null);
      setSelectedVersion("");
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : "Failed to distribute module"
      );
    }
  };

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        Failed to load cluster modules. Make sure the control plane is running.
      </Alert>
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
        <Typography variant="h5">Cluster Modules</Typography>
        <IconButton onClick={() => refetch()} title="Refresh">
          <RefreshIcon />
        </IconButton>
      </Box>

      {localError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>
          {localError}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Module</TableCell>
              <TableCell>Current Version</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Available Versions</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {modules.map((module) => (
              <TableRow key={module.name}>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <ModuleIcon color="primary" fontSize="small" />
                    <Typography fontWeight="medium">{module.name}</Typography>
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    label={module.version}
                    size="small"
                    color="primary"
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>
                  <Typography color="text.secondary" fontSize="small">
                    {module.description || "No description"}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {(module.versions || [module.version]).slice(0, 3).map((v) => (
                      <Chip
                        key={v}
                        label={v}
                        size="small"
                        variant="outlined"
                        color={v === module.version ? "primary" : "default"}
                      />
                    ))}
                    {(module.versions || []).length > 3 && (
                      <Chip
                        label={`+${(module.versions || []).length - 3}`}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Distribute to Nodes">
                    <IconButton
                      size="small"
                      onClick={() => handleDistributeClick(module)}
                      color="primary"
                    >
                      <DistributeIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {modules.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary">
                    No modules found in cluster
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Distribute Module Dialog */}
      <Dialog
        open={distributeDialogOpen}
        onClose={() => setDistributeDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Distribute Module</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <Typography>
              Distribute <strong>{selectedModule?.name}</strong> to all cluster
              nodes.
            </Typography>
            <FormControl fullWidth>
              <InputLabel>Version</InputLabel>
              <Select
                value={selectedVersion}
                label="Version"
                onChange={(e) => setSelectedVersion(e.target.value)}
              >
                {selectedModule?.versions.map((v) => (
                  <MenuItem key={v} value={v}>
                    {v}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Alert severity="info">
              This will push the selected module version to all healthy nodes in
              the cluster.
            </Alert>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDistributeDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDistribute}
            variant="contained"
            disabled={!selectedVersion}
            startIcon={<DistributeIcon />}
          >
            Distribute
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ClusterModulesPanel;
