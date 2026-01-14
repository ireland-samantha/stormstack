/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


import { useState } from 'react';
import {
  Box, Typography, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, IconButton, Button, Chip, CircularProgress, Alert,
  Dialog, DialogTitle, DialogContent, DialogActions, Stack
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon, Refresh as RefreshIcon, Dns as ContainerIcon } from '@mui/icons-material';
import { useAppSelector } from '../store/hooks';
import { selectSelectedContainerId } from '../store/slices/uiSlice';
import {
  useGetContainerQuery,
  useGetPlayersInContainerQuery,
  useCreatePlayerInContainerMutation,
  useDeletePlayerInContainerMutation,
  type PlayerData,
} from '../store/api/apiSlice';

const PlayersPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(selectSelectedContainerId);

  // RTK Query hooks
  const {
    data: selectedContainer,
    isLoading: isContainerLoading,
  } = useGetContainerQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const {
    data: players = [],
    isLoading: isPlayersLoading,
    isError,
    error,
    refetch,
  } = useGetPlayersInContainerQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const isLoading = isContainerLoading || isPlayersLoading;

  const [createPlayer] = useCreatePlayerInContainerMutation();
  const [deletePlayer] = useDeletePlayerInContainerMutation();

  const [success, setSuccess] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingPlayer, setDeletingPlayer] = useState<PlayerData | null>(null);

  const handleCreatePlayer = async () => {
    if (!selectedContainerId) return;
    try {
      await createPlayer({ containerId: selectedContainerId }).unwrap();
      setSuccess('Player created successfully');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to create player');
    }
  };

  const handleDeletePlayer = async () => {
    if (deletingPlayer && selectedContainerId) {
      try {
        await deletePlayer({ containerId: selectedContainerId, playerId: deletingPlayer.id }).unwrap();
        setDeleteDialogOpen(false);
        setDeletingPlayer(null);
        setSuccess('Player deleted');
      } catch (err) {
        setLocalError(err instanceof Error ? err.message : 'Failed to delete player');
      }
    }
  };

  if (!selectedContainerId) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <ContainerIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view and manage players
        </Typography>
      </Paper>
    );
  }

  if (isLoading || !selectedContainer) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress /></Box>;
  }

  const errorMessage = isError
    ? (error && 'data' in error ? String((error.data as { message?: string })?.message || 'Failed to fetch players') : 'Failed to fetch players')
    : localError;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Players</Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
            <Chip
              icon={<ContainerIcon />}
              label={selectedContainer.name}
              size="small"
              color="primary"
              variant="outlined"
            />
            <Chip
              label={selectedContainer.status}
              size="small"
              color={selectedContainer.status === 'RUNNING' ? 'success' : 'default'}
            />
          </Stack>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh"><RefreshIcon /></IconButton>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreatePlayer}>
            Add Player
          </Button>
        </Box>
      </Box>

      {errorMessage && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>{errorMessage}</Alert>}
      {success && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>{success}</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {players.map((player) => (
              <TableRow key={player.id}>
                <TableCell>
                  <Chip label={`Player ${player.id}`} size="small" color="primary" />
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{player.name || `Player ${player.id}`}</Typography>
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => { setDeletingPlayer(player); setDeleteDialogOpen(true); }}
                    title="Delete player"
                  >
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {players.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  <Typography color="text.secondary">No players</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Delete Player</DialogTitle>
        <DialogContent>
          <Typography>Delete player {deletingPlayer?.id}? This cannot be undone.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeletePlayer} color="error" variant="contained">Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PlayersPanel;
