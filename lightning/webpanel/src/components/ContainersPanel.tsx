/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Add as AddIcon,
    Delete as DeleteIcon, Pause as PauseIcon, PlayArrow as PlayIcon, Refresh as RefreshIcon,
    SkipNext as TickIcon, Stop as StopIcon
} from "@mui/icons-material";
import {
    Alert, Box, Button, Chip,
    CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Stack, Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, TextField, Tooltip, Typography
} from "@mui/material";
import { useState } from "react";
import {
    useAdvanceContainerTickMutation, useCreateContainerMutation,
    useDeleteContainerMutation, useGetContainersQuery, usePauseContainerMutation, usePlayContainerMutation, useResumeContainerMutation, useStartContainerMutation, useStopContainerAutoAdvanceMutation, useStopContainerMutation
} from "../store/api/apiSlice";

const ContainersPanel: React.FC = () => {
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newContainerName, setNewContainerName] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);

  // RTK Query hooks
  const {
    data: containers = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetContainersQuery(undefined, {
    pollingInterval: 2000, // Auto-refresh every 2 seconds
  });

  const [createContainer, { isLoading: isCreating }] =
    useCreateContainerMutation();
  const [deleteContainer] = useDeleteContainerMutation();
  const [startContainer] = useStartContainerMutation();
  const [stopContainer] = useStopContainerMutation();
  const [pauseContainer] = usePauseContainerMutation();
  const [resumeContainer] = useResumeContainerMutation();
  const [advanceTick] = useAdvanceContainerTickMutation();
  const [playContainer] = usePlayContainerMutation();
  const [stopAutoAdvance] = useStopContainerAutoAdvanceMutation();

  const handleError = (err: unknown, action: string) => {
    const message = err instanceof Error ? err.message : `Failed to ${action}`;
    setLocalError(message);
  };

  const handleCreateContainer = async () => {
    if (!newContainerName.trim()) return;

    try {
      await createContainer({ name: newContainerName.trim() }).unwrap();
      setCreateDialogOpen(false);
      setNewContainerName("");
    } catch (err) {
      handleError(err, "create container");
    }
  };

  const handleDeleteContainer = async (containerId: number) => {
    if (!confirm("Are you sure you want to delete this container?")) return;

    try {
      await deleteContainer(containerId).unwrap();
    } catch (err) {
      handleError(err, "delete container");
    }
  };

  const handleStartContainer = async (containerId: number) => {
    try {
      await startContainer(containerId).unwrap();
    } catch (err) {
      handleError(err, "start container");
    }
  };

  const handleStopContainer = async (containerId: number) => {
    try {
      await stopContainer(containerId).unwrap();
    } catch (err) {
      handleError(err, "stop container");
    }
  };

  const handlePauseContainer = async (containerId: number) => {
    try {
      await pauseContainer(containerId).unwrap();
    } catch (err) {
      handleError(err, "pause container");
    }
  };

  const handleResumeContainer = async (containerId: number) => {
    try {
      await resumeContainer(containerId).unwrap();
    } catch (err) {
      handleError(err, "resume container");
    }
  };

  const handlePlayContainer = async (containerId: number) => {
    try {
      await playContainer({ id: containerId, intervalMs: 16 }).unwrap();
    } catch (err) {
      handleError(err, "play container");
    }
  };

  const handleStopAutoAdvance = async (containerId: number) => {
    try {
      await stopAutoAdvance(containerId).unwrap();
    } catch (err) {
      handleError(err, "stop auto-advance");
    }
  };

  const handleTickContainer = async (containerId: number) => {
    try {
      await advanceTick(containerId).unwrap();
    } catch (err) {
      handleError(err, "advance tick");
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "RUNNING":
        return "success";
      case "PAUSED":
        return "warning";
      case "STOPPED":
      case "STOPPING":
        return "error";
      case "CREATED":
      case "STARTING":
        return "info";
      default:
        return "default";
    }
  };

  const error =
    localError || (fetchError ? "Failed to fetch containers" : null);

  if (isLoading && containers.length === 0) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
        }}
      >
        <Typography variant="h5">Execution Containers</Typography>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialogOpen(true)}
          >
            Create Container
          </Button>
        </Stack>
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

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Tick</TableCell>
              <TableCell>Auto-Advance</TableCell>
              <TableCell>Matches</TableCell>
              <TableCell>Modules</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {containers.map((container) => (
              <TableRow key={container.id}>
                <TableCell>{container.id}</TableCell>
                <TableCell>
                  {container.name}
                  {container.id === 0 && (
                    <Chip
                      label="default"
                      size="small"
                      sx={{ ml: 1 }}
                      color="primary"
                      variant="outlined"
                    />
                  )}
                </TableCell>
                <TableCell>
                  <Chip
                    label={container.status}
                    color={
                      getStatusColor(container.status) as
                        | "success"
                        | "warning"
                        | "error"
                        | "info"
                        | "default"
                    }
                    size="small"
                  />
                </TableCell>
                <TableCell>{container.currentTick}</TableCell>
                <TableCell>
                  {container.autoAdvancing ? (
                    <Chip
                      label={`${container.autoAdvanceIntervalMs}ms`}
                      color="success"
                      size="small"
                    />
                  ) : (
                    <Chip label="Off" size="small" variant="outlined" />
                  )}
                </TableCell>
                <TableCell>{container.matchCount}</TableCell>
                <TableCell>
                  <Tooltip
                    title={container.loadedModules?.join(", ") || "No modules"}
                  >
                    <span>{container.loadedModules?.length ?? 0}</span>
                  </Tooltip>
                </TableCell>
                <TableCell align="right">
                  <Stack
                    direction="row"
                    spacing={0.5}
                    justifyContent="flex-end"
                  >
                    {container.status === "CREATED" && (
                      <Tooltip title="Start">
                        <IconButton
                          size="small"
                          onClick={() => handleStartContainer(container.id)}
                          color="success"
                        >
                          <PlayIcon />
                        </IconButton>
                      </Tooltip>
                    )}

                    {container.status === "RUNNING" && (
                      <>
                        {container.autoAdvancing ? (
                          <Tooltip title="Stop Auto-Advance">
                            <IconButton
                              size="small"
                              onClick={() =>
                                handleStopAutoAdvance(container.id)
                              }
                              color="warning"
                            >
                              <PauseIcon />
                            </IconButton>
                          </Tooltip>
                        ) : (
                          <>
                            <Tooltip title="Play (Auto-Advance)">
                              <IconButton
                                size="small"
                                onClick={() =>
                                  handlePlayContainer(container.id)
                                }
                                color="success"
                              >
                                <PlayIcon />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Advance One Tick">
                              <IconButton
                                size="small"
                                onClick={() =>
                                  handleTickContainer(container.id)
                                }
                                color="primary"
                              >
                                <TickIcon />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                        <Tooltip title="Pause">
                          <IconButton
                            size="small"
                            onClick={() => handlePauseContainer(container.id)}
                            color="warning"
                          >
                            <PauseIcon />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}

                    {container.status === "PAUSED" && (
                      <Tooltip title="Resume">
                        <IconButton
                          size="small"
                          onClick={() => handleResumeContainer(container.id)}
                          color="success"
                        >
                          <PlayIcon />
                        </IconButton>
                      </Tooltip>
                    )}

                    {(container.status === "RUNNING" ||
                      container.status === "PAUSED") && (
                      <Tooltip title="Stop">
                        <IconButton
                          size="small"
                          onClick={() => handleStopContainer(container.id)}
                          color="error"
                        >
                          <StopIcon />
                        </IconButton>
                      </Tooltip>
                    )}

                    {container.id !== 0 && container.status === "STOPPED" && (
                      <Tooltip title="Delete">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteContainer(container.id)}
                          color="error"
                        >
                          <DeleteIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {containers.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography color="text.secondary">
                    No containers found
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create Container Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
      >
        <DialogTitle>Create New Container</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Container Name"
            fullWidth
            value={newContainerName}
            onChange={(e) => setNewContainerName(e.target.value)}
            disabled={isCreating}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setCreateDialogOpen(false)}
            disabled={isCreating}
          >
            Cancel
          </Button>
          <Button
            onClick={handleCreateContainer}
            variant="contained"
            disabled={isCreating || !newContainerName.trim()}
          >
            {isCreating ? <CircularProgress size={20} /> : "Create"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContainersPanel;
