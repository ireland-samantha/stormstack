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
  TextField,
  Autocomplete,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  Add as AddIcon,
  Stop as FinishIcon,
  Delete as DeleteIcon,
  OpenInNew as OpenIcon,
} from "@mui/icons-material";
import { useState } from "react";
import {
  useGetClusterMatchesQuery,
  useCreateClusterMatchMutation,
  useFinishClusterMatchMutation,
  useDeleteClusterMatchMutation,
  useGetClusterModulesQuery,
  useGetClusterNodesQuery,
} from "../store/api/apiSlice";

const ClusterMatchesPanel: React.FC = () => {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const {
    data: matches = [],
    isLoading,
    error,
    refetch,
  } = useGetClusterMatchesQuery(statusFilter || undefined);

  const { data: modules = [] } = useGetClusterModulesQuery();
  const { data: nodes = [] } = useGetClusterNodesQuery();

  const [createMatch] = useCreateClusterMatchMutation();
  const [finishMatch] = useFinishClusterMatchMutation();
  const [deleteMatch] = useDeleteClusterMatchMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [preferredNode, setPreferredNode] = useState<string>("");

  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{
    type: "finish" | "delete";
    matchId: string;
  } | null>(null);

  const getStatusColor = (status: string) => {
    switch (status) {
      case "RUNNING":
        return "success";
      case "CREATING":
        return "info";
      case "FINISHED":
        return "default";
      case "ERROR":
        return "error";
      default:
        return "default";
    }
  };

  const handleCreateMatch = async () => {
    try {
      const result = await createMatch({
        moduleNames: selectedModules,
        preferredNodeId: preferredNode || undefined,
      }).unwrap();
      setSuccess(`Match ${result.matchId} created on node ${result.nodeId}`);
      setCreateDialogOpen(false);
      setSelectedModules([]);
      setPreferredNode("");
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to create match");
    }
  };

  const handleFinishClick = (matchId: string) => {
    setConfirmAction({ type: "finish", matchId });
    setConfirmDialogOpen(true);
  };

  const handleDeleteClick = (matchId: string) => {
    setConfirmAction({ type: "delete", matchId });
    setConfirmDialogOpen(true);
  };

  const handleConfirm = async () => {
    if (!confirmAction) return;

    try {
      if (confirmAction.type === "finish") {
        await finishMatch(confirmAction.matchId).unwrap();
        setSuccess(`Match ${confirmAction.matchId} has been finished`);
      } else {
        await deleteMatch(confirmAction.matchId).unwrap();
        setSuccess(`Match ${confirmAction.matchId} has been deleted`);
      }
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : `Failed to ${confirmAction.type} match`
      );
    }

    setConfirmDialogOpen(false);
    setConfirmAction(null);
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
        Failed to load cluster matches. Make sure the control plane is running.
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
        <Typography variant="h5">Cluster Matches</Typography>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Status</InputLabel>
            <Select
              value={statusFilter}
              label="Status"
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="CREATING">Creating</MenuItem>
              <MenuItem value="RUNNING">Running</MenuItem>
              <MenuItem value="FINISHED">Finished</MenuItem>
              <MenuItem value="ERROR">Error</MenuItem>
            </Select>
          </FormControl>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialogOpen(true)}
          >
            Create Match
          </Button>
        </Box>
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
              <TableCell>Match ID</TableCell>
              <TableCell>Node</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Players</TableCell>
              <TableCell>Modules</TableCell>
              <TableCell>Created</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {matches.map((match) => (
              <TableRow key={match.matchId}>
                <TableCell>
                  <Typography fontFamily="monospace">{match.matchId}</Typography>
                </TableCell>
                <TableCell>
                  <Typography fontFamily="monospace" fontSize="small">
                    {match.nodeId}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={match.status}
                    size="small"
                    color={getStatusColor(match.status) as any}
                  />
                </TableCell>
                <TableCell>{match.playerCount}</TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {match.moduleNames.slice(0, 3).map((mod) => (
                      <Chip key={mod} label={mod} size="small" variant="outlined" />
                    ))}
                    {match.moduleNames.length > 3 && (
                      <Chip
                        label={`+${match.moduleNames.length - 3}`}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </TableCell>
                <TableCell>{new Date(match.createdAt).toLocaleString()}</TableCell>
                <TableCell align="right">
                  {match.websocketUrl && (
                    <Tooltip title="Open WebSocket URL">
                      <IconButton
                        size="small"
                        onClick={() => window.open(match.websocketUrl, "_blank")}
                      >
                        <OpenIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                  {match.status === "RUNNING" && (
                    <Tooltip title="Finish Match">
                      <IconButton
                        size="small"
                        onClick={() => handleFinishClick(match.matchId)}
                        color="warning"
                      >
                        <FinishIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Delete Match">
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteClick(match.matchId)}
                      color="error"
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {matches.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography color="text.secondary">
                    No matches found
                    {statusFilter && ` with status "${statusFilter}"`}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create Match Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create Match</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <Autocomplete
              multiple
              options={modules.map((m) => m.name)}
              value={selectedModules}
              onChange={(_, newValue) => setSelectedModules(newValue)}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Modules"
                  helperText="Select modules to enable for this match"
                />
              )}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => {
                  const { key, ...rest } = getTagProps({ index });
                  return <Chip key={key} label={option} size="small" {...rest} />;
                })
              }
            />
            <FormControl fullWidth>
              <InputLabel>Preferred Node (Optional)</InputLabel>
              <Select
                value={preferredNode}
                label="Preferred Node (Optional)"
                onChange={(e) => setPreferredNode(e.target.value)}
              >
                <MenuItem value="">Auto-select</MenuItem>
                {nodes
                  .filter((n) => n.status === "HEALTHY")
                  .map((node) => (
                    <MenuItem key={node.nodeId} value={node.nodeId}>
                      {node.nodeId} ({node.advertiseAddress})
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleCreateMatch}
            variant="contained"
            disabled={selectedModules.length === 0}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Dialog */}
      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>
          {confirmAction?.type === "finish" ? "Finish Match" : "Delete Match"}
        </DialogTitle>
        <DialogContent>
          <Typography>
            {confirmAction?.type === "finish" ? (
              <>
                Are you sure you want to finish match{" "}
                <strong>{confirmAction?.matchId}</strong>? The match will be marked as
                complete.
              </>
            ) : (
              <>
                Are you sure you want to delete match{" "}
                <strong>{confirmAction?.matchId}</strong>? This action cannot be undone.
              </>
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleConfirm}
            color={confirmAction?.type === "finish" ? "warning" : "error"}
            variant="contained"
          >
            {confirmAction?.type === "finish" ? "Finish" : "Delete"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ClusterMatchesPanel;
