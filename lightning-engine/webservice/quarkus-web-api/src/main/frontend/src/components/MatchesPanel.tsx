/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { useState } from 'react';
import {
  Box, Typography, IconButton, Button, Chip, CircularProgress, Alert,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, Autocomplete,
  Card, CardContent, Grid, Paper, Avatar, Stack, Tooltip
} from '@mui/material';
import {
  Add as AddIcon, Delete as DeleteIcon, Refresh as RefreshIcon,
  SportsEsports as MatchIcon, Extension as ModulesIcon, Dns as ContainerIcon
} from '@mui/icons-material';
import {
  useGetContainerMatchesQuery,
  useCreateMatchInContainerMutation,
  useDeleteMatchFromContainerMutation,
  useGetModulesQuery,
  useGetContainerQuery,
} from '../store/api/apiSlice';
import { useAppSelector } from '../store/hooks';

const MatchesPanel: React.FC = () => {
  const selectedContainerId = useAppSelector((state) => state.ui.selectedContainerId);

  const { data: selectedContainer } = useGetContainerQuery(selectedContainerId!, {
    skip: !selectedContainerId,
  });

  const { data: matches = [], isLoading, error: fetchError, refetch } = useGetContainerMatchesQuery(
    selectedContainerId!,
    { skip: !selectedContainerId }
  );

  const { data: modules = [] } = useGetModulesQuery();

  const [createMatch, { isLoading: isCreating }] = useCreateMatchInContainerMutation();
  const [deleteMatch, { isLoading: isDeleting }] = useDeleteMatchFromContainerMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingMatchId, setDeletingMatchId] = useState<number | null>(null);

  const handleCreateMatch = async () => {
    if (!selectedContainerId) {
      setLocalError('No container selected');
      return;
    }

    try {
      const matchId = Date.now();
      await createMatch({
        containerId: selectedContainerId,
        body: { id: matchId, enabledModuleNames: selectedModules }
      }).unwrap();
      setDialogOpen(false);
      setSelectedModules([]);
      setSuccess('Match created successfully');
    } catch (err) {
      // Close dialog and show error in main panel so it doesn't block subsequent interactions
      setDialogOpen(false);
      setSelectedModules([]);
      setLocalError(err instanceof Error ? err.message : 'Failed to create match');
    }
  };

  const handleDeleteMatch = async () => {
    if (!deletingMatchId || !selectedContainerId) return;

    try {
      await deleteMatch({ containerId: selectedContainerId, matchId: deletingMatchId }).unwrap();
      setDeleteDialogOpen(false);
      setDeletingMatchId(null);
      setSuccess('Match deleted successfully');
    } catch (err) {
      // Close dialog and show error in main panel so it doesn't block subsequent interactions
      setDeleteDialogOpen(false);
      setDeletingMatchId(null);
      setLocalError(err instanceof Error ? err.message : 'Failed to delete match');
    }
  };

  const error = localError || (fetchError ? 'Failed to fetch matches' : null);

  if (!selectedContainerId || !selectedContainer) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <ContainerIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view and manage matches
        </Typography>
      </Paper>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Matches</Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
            <Chip icon={<ContainerIcon />} label={selectedContainer.name} size="small" color="primary" variant="outlined" />
            <Chip label={selectedContainer.status} size="small" color={selectedContainer.status === 'RUNNING' ? 'success' : 'default'} />
          </Stack>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh" disabled={isLoading}>
            <RefreshIcon />
          </IconButton>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)} disabled={selectedContainer.status !== 'RUNNING'}>
            Create Match
          </Button>
        </Box>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>{error}</Alert>}
      {success && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>{success}</Alert>}

      {selectedContainer.status !== 'RUNNING' && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Container is not running. Start the container to create matches and interact with them.
        </Alert>
      )}

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {!isLoading && (
        <Grid container spacing={2}>
          {matches.map((match) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={match.id}>
              <Card elevation={2} sx={{ height: '100%' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar sx={{ bgcolor: 'primary.light', width: 36, height: 36 }}>
                        <MatchIcon />
                      </Avatar>
                      <Box>
                        <Typography variant="h6" fontWeight={600}>Match {match.id}</Typography>
                        <Typography variant="caption" color="text.secondary">Tick: {match.tick ?? 0}</Typography>
                      </Box>
                    </Box>
                    <Tooltip title="Delete Match">
                      <IconButton size="small" color="error" onClick={() => { setDeletingMatchId(match.id); setDeleteDialogOpen(true); }}>
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1 }}>
                      <ModulesIcon sx={{ fontSize: 14 }} /> Enabled Modules
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      {(match.modules || []).length > 0 ? (
                        (match.modules || []).map((mod) => (
                          <Chip key={mod} label={mod} size="small" variant="outlined" />
                        ))
                      ) : (
                        <Typography variant="body2" color="text.secondary">No modules</Typography>
                      )}
                    </Stack>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
          {matches.length === 0 && (
            <Grid size={12}>
              <Paper sx={{ p: 4, textAlign: 'center' }}>
                <MatchIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
                <Typography variant="h6" color="text.secondary" gutterBottom>No Matches</Typography>
                <Typography color="text.secondary" sx={{ mb: 2 }}>
                  {selectedContainer.status === 'RUNNING' ? 'Create a match to start running game simulations' : 'Start the container first, then create matches'}
                </Typography>
                {selectedContainer.status === 'RUNNING' && (
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
                    Create Match
                  </Button>
                )}
              </Paper>
            </Grid>
          )}
        </Grid>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Match in {selectedContainer.name}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Select which modules to enable for this match. Modules provide commands and components.
          </Typography>
          <Autocomplete
            multiple
            options={modules.map(m => m.name)}
            value={selectedModules}
            onChange={(_, v) => setSelectedModules(v)}
            renderInput={(params) => <TextField {...params} label="Modules" />}
            renderTags={(value, getTagProps) => value.map((opt, i) => {
              const { key, ...rest } = getTagProps({ index: i });
              return <Chip key={key} label={opt} size="small" {...rest} />;
            })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleCreateMatch} variant="contained" disabled={isCreating}>
            {isCreating ? <CircularProgress size={20} /> : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Delete Match</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to delete Match {deletingMatchId}? This action cannot be undone.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeleteMatch} color="error" variant="contained" disabled={isDeleting}>
            {isDeleting ? <CircularProgress size={20} /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MatchesPanel;
