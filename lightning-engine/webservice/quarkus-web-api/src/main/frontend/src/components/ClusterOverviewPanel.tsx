/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
  Box,
  Card,
  CardContent,
  CircularProgress,
  Grid,
  LinearProgress,
  Typography,
  Alert,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Tooltip,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  CheckCircle as HealthyIcon,
  Warning as DrainingIcon,
  Error as OfflineIcon,
} from "@mui/icons-material";
import { useGetDashboardOverviewQuery } from "../store/api/apiSlice";

const ClusterOverviewPanel: React.FC = () => {
  const {
    data: overview,
    isLoading,
    error,
    refetch,
  } = useGetDashboardOverviewQuery();

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
        Failed to load cluster overview. Make sure the control plane is running.
      </Alert>
    );
  }

  if (!overview) {
    return null;
  }

  const { clusterStatus, nodeStatuses, recentMatches } = overview;
  const saturationPercent = clusterStatus.averageSaturation * 100;

  const getNodeStatusIcon = (status: string) => {
    switch (status) {
      case "HEALTHY":
        return <HealthyIcon color="success" />;
      case "DRAINING":
        return <DrainingIcon color="warning" />;
      default:
        return <OfflineIcon color="error" />;
    }
  };

  const getMatchStatusColor = (status: string) => {
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
        <Typography variant="h5">Cluster Overview</Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={() => refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Cluster Status Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Total Nodes
              </Typography>
              <Typography variant="h3">{clusterStatus.totalNodes}</Typography>
              <Box sx={{ display: "flex", gap: 1, mt: 1 }}>
                <Chip
                  label={`${clusterStatus.healthyNodes} healthy`}
                  size="small"
                  color="success"
                />
                {clusterStatus.drainingNodes > 0 && (
                  <Chip
                    label={`${clusterStatus.drainingNodes} draining`}
                    size="small"
                    color="warning"
                  />
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Capacity Usage
              </Typography>
              <Typography variant="h3">
                {clusterStatus.usedCapacity}/{clusterStatus.totalCapacity}
              </Typography>
              <Box sx={{ mt: 1 }}>
                <LinearProgress
                  variant="determinate"
                  value={saturationPercent}
                  color={saturationPercent > 80 ? "warning" : "primary"}
                />
                <Typography variant="caption" color="text.secondary">
                  {saturationPercent.toFixed(1)}% saturated
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Healthy Nodes
              </Typography>
              <Typography variant="h3" color="success.main">
                {clusterStatus.healthyNodes}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Ready to accept containers
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Active Matches
              </Typography>
              <Typography variant="h3">
                {recentMatches.filter((m) => m.status === "RUNNING").length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Currently running
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Node Status Table */}
      <Typography variant="h6" sx={{ mb: 2 }}>
        Node Status
      </Typography>
      <TableContainer component={Paper} sx={{ mb: 4 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Node ID</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Address</TableCell>
              <TableCell>Containers</TableCell>
              <TableCell>Matches</TableCell>
              <TableCell>CPU</TableCell>
              <TableCell>Memory</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {nodeStatuses.map((node) => (
              <TableRow key={node.nodeId}>
                <TableCell>
                  <Typography fontFamily="monospace" fontSize="small">
                    {node.nodeId}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                    {getNodeStatusIcon(node.status)}
                    <Typography variant="body2">{node.status}</Typography>
                  </Box>
                </TableCell>
                <TableCell>
                  <Typography fontFamily="monospace" fontSize="small">
                    {node.advertiseAddress}
                  </Typography>
                </TableCell>
                <TableCell>
                  {node.metrics?.containerCount ?? 0}/{node.capacity.maxContainers}
                </TableCell>
                <TableCell>{node.metrics?.matchCount ?? 0}</TableCell>
                <TableCell>
                  {node.metrics ? `${node.metrics.cpuUsage.toFixed(1)}%` : "-"}
                </TableCell>
                <TableCell>
                  {node.metrics
                    ? `${node.metrics.memoryUsedMb}/${node.metrics.memoryMaxMb} MB`
                    : "-"}
                </TableCell>
              </TableRow>
            ))}
            {nodeStatuses.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography color="text.secondary">
                    No nodes registered
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Recent Matches */}
      <Typography variant="h6" sx={{ mb: 2 }}>
        Recent Matches
      </Typography>
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Match ID</TableCell>
              <TableCell>Node</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Players</TableCell>
              <TableCell>Modules</TableCell>
              <TableCell>Created</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {recentMatches.slice(0, 10).map((match) => (
              <TableRow key={match.matchId}>
                <TableCell>
                  <Typography fontFamily="monospace" fontSize="small">
                    {match.matchId}
                  </Typography>
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
                    color={getMatchStatusColor(match.status) as any}
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
                <TableCell>
                  {new Date(match.createdAt).toLocaleString()}
                </TableCell>
              </TableRow>
            ))}
            {recentMatches.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  <Typography color="text.secondary">No matches found</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ClusterOverviewPanel;
