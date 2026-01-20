/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { useState, useEffect } from 'react';
import {
  Box, Typography, Card, CardContent, CardActions, Grid, Chip, IconButton,
  Button, LinearProgress, Tooltip, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, Skeleton, Alert, Paper, Divider,
  Stack, Avatar, FormControl, InputLabel, Select, MenuItem, OutlinedInput,
  Checkbox, ListItemText
} from '@mui/material';
import {
  PlayArrow as PlayIcon,
  Pause as PauseIcon,
  Stop as StopIcon,
  Refresh as RefreshIcon,
  Add as AddIcon,
  SkipNext as TickIcon,
  Speed as SpeedIcon,
  SportsEsports as MatchIcon,
  Extension as ModulesIcon,
  Dns as ContainerIcon,
  Delete as DeleteIcon,
  CheckCircle as RunningIcon,
  RadioButtonUnchecked as StoppedIcon,
  HourglassEmpty as CreatedIcon,
  PowerSettingsNew as StartIcon,
  Memory as MemoryIcon,
  Storage as StorageIcon,
  People as EntitiesIcon
} from '@mui/icons-material';
import {
  useGetContainersQuery,
  useGetContainerQuery,
  useGetContainerMatchesQuery,
  useGetContainerTickQuery,
  useGetContainerStatsQuery,
  useGetModulesQuery,
  useGetAIsQuery,
  useCreateContainerMutation,
  useStartContainerMutation,
  useStopContainerMutation,
  useAdvanceContainerTickMutation,
  usePlayContainerMutation,
  useStopContainerAutoAdvanceMutation,
  useDeleteContainerMutation,
  ContainerData,
} from '../store/api/apiSlice';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { setSelectedContainerId } from '../store/slices/uiSlice';

const ContainerDashboard: React.FC = () => {
  const dispatch = useAppDispatch();
  const selectedContainerId = useAppSelector((state) => state.ui.selectedContainerId);

  const { data: containers = [], isLoading: containersLoading, error: containersError, refetch: refetchContainers } = useGetContainersQuery(undefined, {
    pollingInterval: 2000,
  });

  const { data: selectedContainer } = useGetContainerQuery(selectedContainerId!, {
    skip: !selectedContainerId,
  });

  const { data: matches = [] } = useGetContainerMatchesQuery(selectedContainerId!, {
    skip: !selectedContainerId,
  });

  const { data: tickData } = useGetContainerTickQuery(selectedContainerId!, {
    skip: !selectedContainerId || selectedContainer?.status !== 'RUNNING',
    pollingInterval: 500,
  });

  const { data: containerStats } = useGetContainerStatsQuery(selectedContainerId!, {
    skip: !selectedContainerId,
    pollingInterval: 2000,
  });

  // Global modules and AI for selection
  const { data: globalModules = [] } = useGetModulesQuery();
  const { data: globalAIs = [] } = useGetAIsQuery();

  const [createContainer, { isLoading: isCreating }] = useCreateContainerMutation();
  const [startContainer] = useStartContainerMutation();
  const [stopContainer] = useStopContainerMutation();
  const [advanceContainerTick] = useAdvanceContainerTickMutation();
  const [playContainer] = usePlayContainerMutation();
  const [stopContainerAutoAdvance] = useStopContainerAutoAdvanceMutation();
  const [deleteContainer] = useDeleteContainerMutation();

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newContainerName, setNewContainerName] = useState('');
  const [newContainerMaxMemoryMb, setNewContainerMaxMemoryMb] = useState<number | ''>('');
  const [selectedModuleNames, setSelectedModuleNames] = useState<string[]>([]);
  const [selectedAINames, setSelectedAINames] = useState<string[]>([]);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [tick, setTick] = useState<number>(0);

  // Update tick from query
  useEffect(() => {
    if (tickData?.tick !== undefined) {
      setTick(tickData.tick);
    }
  }, [tickData]);

  const selectContainer = (id: number | null) => {
    dispatch(setSelectedContainerId(id));
  };

  const handleCreateContainer = async () => {
    if (!newContainerName.trim()) return;
    try {
      const request: {
        name: string;
        maxMemoryMb?: number;
        moduleNames?: string[];
        aiNames?: string[];
      } = { name: newContainerName.trim() };
      if (newContainerMaxMemoryMb !== '' && newContainerMaxMemoryMb > 0) {
        request.maxMemoryMb = newContainerMaxMemoryMb;
      }
      if (selectedModuleNames.length > 0) {
        request.moduleNames = selectedModuleNames;
      }
      if (selectedAINames.length > 0) {
        request.aiNames = selectedAINames;
      }
      await createContainer(request).unwrap();
      setCreateDialogOpen(false);
      setNewContainerName('');
      setNewContainerMaxMemoryMb('');
      setSelectedModuleNames([]);
      setSelectedAINames([]);
      setSuccess('Container created successfully');
    } catch (err) {
      // Close dialog and show error in main panel so it doesn't block subsequent interactions
      setCreateDialogOpen(false);
      setNewContainerName('');
      setNewContainerMaxMemoryMb('');
      setSelectedModuleNames([]);
      setSelectedAINames([]);
      setLocalError(err instanceof Error ? err.message : 'Failed to create container');
    }
  };

  const handleStartContainer = async (id: number) => {
    setActionLoading(`start-${id}`);
    try {
      await startContainer(id).unwrap();
      setSuccess('Container started');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to start container');
    } finally {
      setActionLoading(null);
    }
  };

  const handleStopContainer = async (id: number) => {
    setActionLoading(`stop-${id}`);
    try {
      await stopContainer(id).unwrap();
      setSuccess('Container stopped');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to stop container');
    } finally {
      setActionLoading(null);
    }
  };

  const handleAdvanceTick = async () => {
    if (!selectedContainerId) return;
    setActionLoading('tick');
    try {
      const response = await advanceContainerTick(selectedContainerId).unwrap();
      setTick(response.tick);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to advance tick');
    } finally {
      setActionLoading(null);
    }
  };

  const handlePlay = async (intervalMs: number = 16) => {
    if (!selectedContainerId) return;
    setActionLoading('play');
    try {
      await playContainer({ id: selectedContainerId, intervalMs }).unwrap();
      setSuccess(`Auto-advance started at ${Math.round(1000 / intervalMs)} FPS`);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to start auto-advance');
    } finally {
      setActionLoading(null);
    }
  };

  const handleStopAuto = async () => {
    if (!selectedContainerId) return;
    setActionLoading('stopAuto');
    try {
      await stopContainerAutoAdvance(selectedContainerId).unwrap();
      setSuccess('Auto-advance stopped');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to stop auto-advance');
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteContainer = async (id: number) => {
    setActionLoading(`delete-${id}`);
    try {
      await deleteContainer(id).unwrap();
      setSuccess('Container deleted');
      if (selectedContainerId === id) {
        selectContainer(null);
      }
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to delete container');
    } finally {
      setActionLoading(null);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'RUNNING': return 'success';
      case 'PAUSED': return 'warning';
      case 'STOPPED': return 'error';
      case 'CREATED': return 'default';
      default: return 'default';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'RUNNING': return <RunningIcon color="success" />;
      case 'PAUSED': return <PauseIcon color="warning" />;
      case 'STOPPED': return <StoppedIcon color="error" />;
      case 'CREATED': return <CreatedIcon color="disabled" />;
      default: return <StoppedIcon />;
    }
  };

  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const renderContainerCard = (container: ContainerData) => {
    const isSelected = selectedContainerId === container.id;
    const isRunning = container.status === 'RUNNING';
    const isCardLoading = actionLoading?.startsWith(`start-${container.id}`) ||
      actionLoading?.startsWith(`stop-${container.id}`) ||
      actionLoading?.startsWith(`delete-${container.id}`);

    return (
      <Grid size={{ xs: 12, sm: 6, md: 4 }} key={container.id}>
        <Card
          elevation={isSelected ? 8 : 2}
          sx={{
            cursor: 'pointer',
            transition: 'all 0.2s ease-in-out',
            border: isSelected ? 2 : 0,
            borderColor: 'primary.main',
            '&:hover': {
              elevation: 4,
              transform: 'translateY(-2px)',
              boxShadow: 4
            },
            position: 'relative',
            overflow: 'visible'
          }}
          onClick={() => selectContainer(container.id)}
        >
          {isCardLoading && <LinearProgress sx={{ position: 'absolute', top: 0, left: 0, right: 0 }} />}

          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Avatar
                  sx={{
                    bgcolor: isRunning ? 'success.light' : 'grey.300',
                    width: 40,
                    height: 40
                  }}
                >
                  <ContainerIcon />
                </Avatar>
                <Box>
                  <Typography variant="h6" component="div" fontWeight={600}>
                    {container.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    ID: {container.id}
                  </Typography>
                </Box>
              </Box>
              <Chip
                icon={getStatusIcon(container.status)}
                label={container.status}
                size="small"
                color={getStatusColor(container.status) as 'success' | 'warning' | 'error' | 'default'}
                variant="outlined"
              />
            </Box>

            <Stack direction="row" spacing={2} sx={{ mb: 1 }}>
              <Tooltip title="Current Tick">
                <Chip
                  icon={<SpeedIcon />}
                  label={`Tick: ${isSelected && isRunning ? tick : container.currentTick || 0}`}
                  size="small"
                  variant="outlined"
                />
              </Tooltip>
              {container.matchCount !== undefined && (
                <Tooltip title="Matches">
                  <Chip
                    icon={<MatchIcon />}
                    label={`${container.matchCount} matches`}
                    size="small"
                    variant="outlined"
                  />
                </Tooltip>
              )}
            </Stack>

            {container.moduleCount !== undefined && (
              <Tooltip title="Loaded Modules">
                <Chip
                  icon={<ModulesIcon />}
                  label={`${container.moduleCount} modules`}
                  size="small"
                  variant="outlined"
                  sx={{ mt: 1 }}
                />
              </Tooltip>
            )}
          </CardContent>

          <Divider />

          <CardActions sx={{ justifyContent: 'space-between', px: 2 }}>
            <Box>
              {container.status === 'CREATED' || container.status === 'STOPPED' ? (
                <Tooltip title="Start Container">
                  <IconButton
                    size="small"
                    color="success"
                    onClick={(e) => { e.stopPropagation(); handleStartContainer(container.id); }}
                    disabled={isCardLoading}
                  >
                    <StartIcon />
                  </IconButton>
                </Tooltip>
              ) : (
                <Tooltip title="Stop Container">
                  <IconButton
                    size="small"
                    color="error"
                    onClick={(e) => { e.stopPropagation(); handleStopContainer(container.id); }}
                    disabled={isCardLoading}
                  >
                    <StopIcon />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
            <Tooltip title="Delete Container">
              <span>
                <IconButton
                  size="small"
                  color="error"
                  onClick={(e) => { e.stopPropagation(); handleDeleteContainer(container.id); }}
                  disabled={isCardLoading || isRunning}
                >
                  <DeleteIcon />
                </IconButton>
              </span>
            </Tooltip>
          </CardActions>
        </Card>
      </Grid>
    );
  };

  const error = localError || (containersError ? 'Failed to fetch containers' : null);

  if (containersLoading && containers.length === 0) {
    return (
      <Box sx={{ p: 3 }}>
        <Grid container spacing={3}>
          {[1, 2, 3].map((i) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={i}>
              <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 2 }} />
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Execution Containers
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Isolated runtime environments with independent classloaders and game loops
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <IconButton onClick={() => refetchContainers()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialogOpen(true)}
          >
            New Container
          </Button>
        </Box>
      </Box>

      {/* Alerts */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>
          {error}
        </Alert>
      )}
      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      {/* Container Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {containers.map(renderContainerCard)}
        {containers.length === 0 && (
          <Grid size={12}>
            <Paper sx={{ p: 4, textAlign: 'center' }}>
              <ContainerIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="h6" color="text.secondary" gutterBottom>
                No Containers
              </Typography>
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                Create your first container to start running game simulations
              </Typography>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setCreateDialogOpen(true)}
              >
                Create Container
              </Button>
            </Paper>
          </Grid>
        )}
      </Grid>

      {/* Selected Container Control Panel */}
      {selectedContainer && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar sx={{ bgcolor: 'primary.main' }}>
                <ContainerIcon />
              </Avatar>
              <Box>
                <Typography variant="h6" fontWeight={600}>
                  {selectedContainer.name}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Tick Control & Simulation
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Chip
                label={`Tick: ${tick}`}
                color="primary"
                sx={{ fontFamily: 'monospace', fontWeight: 600 }}
              />
              <Chip
                label={selectedContainer.status}
                color={getStatusColor(selectedContainer.status) as 'success' | 'warning' | 'error' | 'default'}
              />
            </Box>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Tooltip title="Advance one tick">
              <span>
                <Button
                  variant="outlined"
                  startIcon={<TickIcon />}
                  onClick={handleAdvanceTick}
                  disabled={selectedContainer.status !== 'RUNNING' || actionLoading !== null}
                >
                  Step
                </Button>
              </span>
            </Tooltip>

            <Tooltip title="Start auto-advance at 60 FPS">
              <span>
                <Button
                  variant="contained"
                  color="success"
                  startIcon={<PlayIcon />}
                  onClick={() => handlePlay(16)}
                  disabled={selectedContainer.status !== 'RUNNING' || actionLoading !== null}
                >
                  Play (60 FPS)
                </Button>
              </span>
            </Tooltip>

            <Tooltip title="Start auto-advance at 30 FPS">
              <span>
                <Button
                  variant="outlined"
                  color="success"
                  startIcon={<PlayIcon />}
                  onClick={() => handlePlay(33)}
                  disabled={selectedContainer.status !== 'RUNNING' || actionLoading !== null}
                >
                  Play (30 FPS)
                </Button>
              </span>
            </Tooltip>

            <Tooltip title="Stop auto-advance">
              <span>
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<PauseIcon />}
                  onClick={handleStopAuto}
                  disabled={selectedContainer.status !== 'RUNNING' || actionLoading !== null}
                >
                  Pause
                </Button>
              </span>
            </Tooltip>
          </Box>

          {/* Matches in this container */}
          <Box sx={{ mt: 3 }}>
            <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
              Matches in this container ({matches.length})
            </Typography>
            {matches.length > 0 ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {matches.map((match) => (
                  <Chip
                    key={match.id}
                    icon={<MatchIcon />}
                    label={`Match ${match.id}`}
                    variant="outlined"
                    size="small"
                  />
                ))}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                No matches. Create a match from the Matches panel.
              </Typography>
            )}
          </Box>

          {/* Container Statistics */}
          {containerStats && (
            <Box sx={{ mt: 3 }}>
              <Divider sx={{ mb: 2 }} />
              <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 2 }}>
                Container Statistics
              </Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                    <EntitiesIcon color="primary" sx={{ fontSize: 32, mb: 1 }} />
                    <Typography variant="h6" fontWeight={600}>
                      {containerStats.entityCount.toLocaleString()}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Entities ({containerStats.maxEntities.toLocaleString()} max)
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={Math.min((containerStats.entityCount / containerStats.maxEntities) * 100, 100)}
                      sx={{ mt: 1 }}
                    />
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                    <MemoryIcon color="secondary" sx={{ fontSize: 32, mb: 1 }} />
                    <Typography variant="h6" fontWeight={600}>
                      {formatBytes(containerStats.usedMemoryBytes)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      ECS Memory ({containerStats.maxMemoryBytes > 0 ? formatBytes(containerStats.maxMemoryBytes) + ' limit' : 'unlimited'})
                    </Typography>
                    {containerStats.maxMemoryBytes > 0 && (
                      <LinearProgress
                        variant="determinate"
                        value={Math.min((containerStats.usedMemoryBytes / containerStats.maxMemoryBytes) * 100, 100)}
                        sx={{ mt: 1 }}
                      />
                    )}
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                    <StorageIcon color="info" sx={{ fontSize: 32, mb: 1 }} />
                    <Typography variant="h6" fontWeight={600}>
                      {formatBytes(containerStats.jvmUsedMemoryBytes)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      JVM Heap ({formatBytes(containerStats.jvmMaxMemoryBytes)} max)
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={Math.min((containerStats.jvmUsedMemoryBytes / containerStats.jvmMaxMemoryBytes) * 100, 100)}
                      color="info"
                      sx={{ mt: 1 }}
                    />
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                    <ModulesIcon color="success" sx={{ fontSize: 32, mb: 1 }} />
                    <Typography variant="h6" fontWeight={600}>
                      {containerStats.moduleCount}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Loaded Modules
                    </Typography>
                  </Paper>
                </Grid>
              </Grid>
            </Box>
          )}
        </Paper>
      )}

      {/* Create Container Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create New Container</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Each container has its own isolated classloader, entity store, and game loop.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            label="Container Name"
            value={newContainerName}
            onChange={(e) => setNewContainerName(e.target.value)}
            placeholder="e.g., game-server-1"
            helperText="A descriptive name for this container"
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            label="Max Memory (MB)"
            type="number"
            value={newContainerMaxMemoryMb}
            onChange={(e) => setNewContainerMaxMemoryMb(e.target.value === '' ? '' : parseInt(e.target.value, 10))}
            placeholder="e.g., 512"
            helperText="Maximum memory allocation in MB (0 or empty = unlimited, bounded by JVM heap)"
            inputProps={{ min: 0 }}
            sx={{ mb: 2 }}
          />

          {/* Module Selection */}
          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel id="modules-select-label">Modules</InputLabel>
            <Select
              labelId="modules-select-label"
              multiple
              value={selectedModuleNames}
              onChange={(e) => setSelectedModuleNames(e.target.value as string[])}
              input={<OutlinedInput label="Modules" />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((value) => (
                    <Chip key={value} label={value} size="small" />
                  ))}
                </Box>
              )}
            >
              {globalModules.map((module) => (
                <MenuItem key={module.name} value={module.name}>
                  <Checkbox checked={selectedModuleNames.indexOf(module.name) > -1} />
                  <ListItemText primary={module.name} secondary={module.description} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* AI Selection */}
          <FormControl fullWidth>
            <InputLabel id="ai-select-label">AI</InputLabel>
            <Select
              labelId="ai-select-label"
              multiple
              value={selectedAINames}
              onChange={(e) => setSelectedAINames(e.target.value as string[])}
              input={<OutlinedInput label="AI" />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((value) => (
                    <Chip key={value} label={value} size="small" />
                  ))}
                </Box>
              )}
            >
              {globalAIs.map((ai) => (
                <MenuItem key={ai.name} value={ai.name}>
                  <Checkbox checked={selectedAINames.indexOf(ai.name) > -1} />
                  <ListItemText primary={ai.name} secondary={ai.description} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleCreateContainer}
            variant="contained"
            disabled={!newContainerName.trim() || isCreating}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContainerDashboard;
