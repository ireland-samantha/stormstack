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
  LinearProgress,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  CheckCircle as HealthyIcon,
  Warning as DrainingIcon,
  Error as OfflineIcon,
  PauseCircle as DrainIcon,
  Delete as DeleteIcon,
} from "@mui/icons-material";
import { useState } from "react";
import {
  useGetClusterNodesQuery,
  useDrainNodeMutation,
  useDeregisterNodeMutation,
} from "../store/api/apiSlice";

const ClusterNodesPanel: React.FC = () => {
  const {
    data: nodes = [],
    isLoading,
    error,
    refetch,
  } = useGetClusterNodesQuery();

  const [drainNode] = useDrainNodeMutation();
  const [deregisterNode] = useDeregisterNodeMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{
    type: "drain" | "deregister";
    nodeId: string;
  } | null>(null);

  const getNodeStatusIcon = (status: string) => {
    switch (status) {
      case "HEALTHY":
        return <HealthyIcon color="success" fontSize="small" />;
      case "DRAINING":
        return <DrainingIcon color="warning" fontSize="small" />;
      default:
        return <OfflineIcon color="error" fontSize="small" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "HEALTHY":
        return "success";
      case "DRAINING":
        return "warning";
      default:
        return "error";
    }
  };

  const handleDrainClick = (nodeId: string) => {
    setConfirmAction({ type: "drain", nodeId });
    setConfirmDialogOpen(true);
  };

  const handleDeregisterClick = (nodeId: string) => {
    setConfirmAction({ type: "deregister", nodeId });
    setConfirmDialogOpen(true);
  };

  const handleConfirm = async () => {
    if (!confirmAction) return;

    try {
      if (confirmAction.type === "drain") {
        await drainNode(confirmAction.nodeId).unwrap();
        setSuccess(`Node ${confirmAction.nodeId} is now draining`);
      } else {
        await deregisterNode(confirmAction.nodeId).unwrap();
        setSuccess(`Node ${confirmAction.nodeId} has been deregistered`);
      }
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : `Failed to ${confirmAction.type} node`
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
        Failed to load cluster nodes. Make sure the control plane is running.
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
        <Typography variant="h5">Cluster Nodes</Typography>
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
              <TableCell>Node ID</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Address</TableCell>
              <TableCell>Capacity</TableCell>
              <TableCell>CPU</TableCell>
              <TableCell>Memory</TableCell>
              <TableCell>Last Heartbeat</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {nodes.map((node) => {
              const containerUsage = node.metrics
                ? (node.metrics.containerCount / node.capacity.maxContainers) * 100
                : 0;

              return (
                <TableRow key={node.nodeId}>
                  <TableCell>
                    <Typography fontFamily="monospace">{node.nodeId}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getNodeStatusIcon(node.status)}
                      label={node.status}
                      size="small"
                      color={getStatusColor(node.status) as any}
                    />
                  </TableCell>
                  <TableCell>
                    <Typography fontFamily="monospace" fontSize="small">
                      {node.advertiseAddress}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ minWidth: 100 }}>
                      <Box sx={{ display: "flex", justifyContent: "space-between" }}>
                        <Typography variant="body2">
                          {node.metrics?.containerCount ?? 0}/{node.capacity.maxContainers}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {containerUsage.toFixed(0)}%
                        </Typography>
                      </Box>
                      <LinearProgress
                        variant="determinate"
                        value={containerUsage}
                        color={containerUsage > 80 ? "warning" : "primary"}
                        sx={{ mt: 0.5 }}
                      />
                    </Box>
                  </TableCell>
                  <TableCell>
                    {node.metrics ? (
                      <Box sx={{ minWidth: 80 }}>
                        <Typography variant="body2">
                          {node.metrics.cpuUsage.toFixed(1)}%
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={node.metrics.cpuUsage}
                          color={node.metrics.cpuUsage > 80 ? "warning" : "primary"}
                          sx={{ mt: 0.5 }}
                        />
                      </Box>
                    ) : (
                      "-"
                    )}
                  </TableCell>
                  <TableCell>
                    {node.metrics ? (
                      <Box sx={{ minWidth: 100 }}>
                        <Typography variant="body2">
                          {node.metrics.memoryUsedMb}/{node.metrics.memoryMaxMb} MB
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={
                            (node.metrics.memoryUsedMb / node.metrics.memoryMaxMb) * 100
                          }
                          color={
                            node.metrics.memoryUsedMb / node.metrics.memoryMaxMb > 0.8
                              ? "warning"
                              : "primary"
                          }
                          sx={{ mt: 0.5 }}
                        />
                      </Box>
                    ) : (
                      "-"
                    )}
                  </TableCell>
                  <TableCell>
                    {new Date(node.lastHeartbeat).toLocaleString()}
                  </TableCell>
                  <TableCell align="right">
                    {node.status === "HEALTHY" && (
                      <Tooltip title="Drain Node">
                        <IconButton
                          size="small"
                          onClick={() => handleDrainClick(node.nodeId)}
                          color="warning"
                        >
                          <DrainIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                    <Tooltip title="Deregister Node">
                      <IconButton
                        size="small"
                        onClick={() => handleDeregisterClick(node.nodeId)}
                        color="error"
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              );
            })}
            {nodes.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography color="text.secondary">
                    No nodes registered in the cluster
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Confirm Dialog */}
      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>
          {confirmAction?.type === "drain" ? "Drain Node" : "Deregister Node"}
        </DialogTitle>
        <DialogContent>
          <Typography>
            {confirmAction?.type === "drain" ? (
              <>
                Are you sure you want to drain node <strong>{confirmAction?.nodeId}</strong>?
                The node will stop accepting new containers but existing containers will
                continue running.
              </>
            ) : (
              <>
                Are you sure you want to deregister node{" "}
                <strong>{confirmAction?.nodeId}</strong>? This will remove the node from
                the cluster.
              </>
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleConfirm}
            color={confirmAction?.type === "drain" ? "warning" : "error"}
            variant="contained"
          >
            {confirmAction?.type === "drain" ? "Drain" : "Deregister"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ClusterNodesPanel;
