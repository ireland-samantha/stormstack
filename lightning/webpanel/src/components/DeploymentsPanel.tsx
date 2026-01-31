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
  FormControlLabel,
  Switch,
  Card,
  CardContent,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  Delete as DeleteIcon,
  RocketLaunch as DeployIcon,
  OpenInNew as OpenIcon,
  ContentCopy as CopyIcon,
} from "@mui/icons-material";
import { useState } from "react";
import {
  useGetClusterMatchesQuery,
  useGetClusterModulesQuery,
  useGetClusterNodesQuery,
  useDeployMutation,
  useUndeployMutation,
  useGetDeploymentQuery,
} from "../store/api/apiSlice";

const DeploymentsPanel: React.FC = () => {
  const {
    data: matches = [],
    isLoading,
    error,
    refetch,
  } = useGetClusterMatchesQuery(undefined);

  const { data: modules = [] } = useGetClusterModulesQuery();
  const { data: nodes = [] } = useGetClusterNodesQuery();

  const [deploy] = useDeployMutation();
  const [undeploy] = useUndeployMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [deployDialogOpen, setDeployDialogOpen] = useState(false);
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [preferredNode, setPreferredNode] = useState<string>("");
  const [autoStart, setAutoStart] = useState(true);

  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false);
  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null);

  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [matchToUndeploy, setMatchToUndeploy] = useState<string | null>(null);

  const { data: deploymentDetails } = useGetDeploymentQuery(selectedMatchId!, {
    skip: !selectedMatchId,
  });

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

  const handleDeploy = async () => {
    try {
      const result = await deploy({
        modules: selectedModules,
        preferredNodeId: preferredNode || undefined,
        autoStart,
      }).unwrap();
      setSuccess(
        `Deployment created: Match ${result.matchId} on node ${result.nodeId}`
      );
      setDeployDialogOpen(false);
      setSelectedModules([]);
      setPreferredNode("");
      setAutoStart(true);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to deploy");
    }
  };

  const handleViewDetails = (matchId: string) => {
    setSelectedMatchId(matchId);
    setDetailsDialogOpen(true);
  };

  const handleUndeployClick = (matchId: string) => {
    setMatchToUndeploy(matchId);
    setConfirmDialogOpen(true);
  };

  const handleUndeploy = async () => {
    if (!matchToUndeploy) return;

    try {
      await undeploy(matchToUndeploy).unwrap();
      setSuccess(`Match ${matchToUndeploy} has been undeployed`);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to undeploy");
    }

    setConfirmDialogOpen(false);
    setMatchToUndeploy(null);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setSuccess("Copied to clipboard");
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
        Failed to load deployments. Make sure the control plane is running.
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
        <Typography variant="h5">Deployments</Typography>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<DeployIcon />}
            onClick={() => setDeployDialogOpen(true)}
          >
            New Deployment
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
              <TableCell>Modules</TableCell>
              <TableCell>Players</TableCell>
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
                    color={getStatusColor(match.status) as "success" | "error" | "info" | "default"}
                  />
                </TableCell>
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
                <TableCell>{match.playerCount}</TableCell>
                <TableCell>{new Date(match.createdAt).toLocaleString()}</TableCell>
                <TableCell align="right">
                  <Tooltip title="View Details">
                    <IconButton
                      size="small"
                      onClick={() => handleViewDetails(match.matchId)}
                    >
                      <OpenIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Undeploy">
                    <IconButton
                      size="small"
                      onClick={() => handleUndeployClick(match.matchId)}
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
                    No deployments found
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* New Deployment Dialog */}
      <Dialog
        open={deployDialogOpen}
        onClose={() => setDeployDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>New Deployment</DialogTitle>
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
                  helperText="Select modules to enable for this deployment"
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
            <FormControlLabel
              control={
                <Switch
                  checked={autoStart}
                  onChange={(e) => setAutoStart(e.target.checked)}
                />
              }
              label="Auto-start match after deployment"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeployDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDeploy}
            variant="contained"
            disabled={selectedModules.length === 0}
            startIcon={<DeployIcon />}
          >
            Deploy
          </Button>
        </DialogActions>
      </Dialog>

      {/* Deployment Details Dialog */}
      <Dialog
        open={detailsDialogOpen}
        onClose={() => setDetailsDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Deployment Details</DialogTitle>
        <DialogContent>
          {deploymentDetails ? (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Match ID
                  </Typography>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <Typography fontFamily="monospace">
                      {deploymentDetails.matchId}
                    </Typography>
                    <IconButton
                      size="small"
                      onClick={() => copyToClipboard(deploymentDetails.matchId)}
                    >
                      <CopyIcon fontSize="small" />
                    </IconButton>
                  </Box>
                </CardContent>
              </Card>

              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Endpoints
                  </Typography>
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="body2">
                      <strong>HTTP:</strong>{" "}
                      <code>{deploymentDetails.endpoints.http}</code>
                      <IconButton
                        size="small"
                        onClick={() =>
                          copyToClipboard(deploymentDetails.endpoints.http)
                        }
                      >
                        <CopyIcon fontSize="small" />
                      </IconButton>
                    </Typography>
                    <Typography variant="body2">
                      <strong>WebSocket:</strong>{" "}
                      <code>{deploymentDetails.endpoints.websocket}</code>
                      <IconButton
                        size="small"
                        onClick={() =>
                          copyToClipboard(deploymentDetails.endpoints.websocket)
                        }
                      >
                        <CopyIcon fontSize="small" />
                      </IconButton>
                    </Typography>
                    <Typography variant="body2">
                      <strong>Commands:</strong>{" "}
                      <code>{deploymentDetails.endpoints.commands}</code>
                      <IconButton
                        size="small"
                        onClick={() =>
                          copyToClipboard(deploymentDetails.endpoints.commands)
                        }
                      >
                        <CopyIcon fontSize="small" />
                      </IconButton>
                    </Typography>
                  </Box>
                </CardContent>
              </Card>

              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Modules
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mt: 1 }}>
                    {deploymentDetails.modules.map((mod) => (
                      <Chip key={mod} label={mod} size="small" />
                    ))}
                  </Box>
                </CardContent>
              </Card>
            </Box>
          ) : (
            <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
              <CircularProgress />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Undeploy Dialog */}
      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>Undeploy Match</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to undeploy match{" "}
            <strong>{matchToUndeploy}</strong>? This will stop the match and
            release all resources.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleUndeploy} color="error" variant="contained">
            Undeploy
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default DeploymentsPanel;
