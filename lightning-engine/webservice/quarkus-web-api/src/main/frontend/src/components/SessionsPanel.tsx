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
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, Autocomplete,
  Stack
} from '@mui/material';
import {
  Add as AddIcon, Refresh as RefreshIcon,
  LinkOff as DisconnectIcon, ExitToApp as AbandonIcon, Replay as ReconnectIcon,
  Dns as ContainerIcon
} from '@mui/icons-material';
import { useAppSelector } from '../store/hooks';
import { selectSelectedContainerId } from '../store/slices/uiSlice';
import {
  useGetContainerQuery,
  useGetContainerMatchesQuery,
  useGetPlayersInContainerQuery,
  useGetAllContainerSessionsQuery,
  useConnectSessionInContainerMutation,
  useDisconnectSessionInContainerMutation,
  useReconnectSessionInContainerMutation,
  useAbandonSessionInContainerMutation,
  type SessionData,
} from '../store/api/apiSlice';

const SessionsPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(selectSelectedContainerId);

  // RTK Query hooks
  const {
    data: selectedContainer,
    isLoading: isContainerLoading,
  } = useGetContainerQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const { data: matches = [] } = useGetContainerMatchesQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const { data: players = [] } = useGetPlayersInContainerQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const {
    data: sessions = [],
    isLoading: isSessionsLoading,
    isError,
    error,
    refetch,
  } = useGetAllContainerSessionsQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const isLoading = isContainerLoading || isSessionsLoading;

  const [connectSession] = useConnectSessionInContainerMutation();
  const [disconnectSession] = useDisconnectSessionInContainerMutation();
  const [reconnectSession] = useReconnectSessionInContainerMutation();
  const [abandonSession] = useAbandonSessionInContainerMutation();

  const [success, setSuccess] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedMatchId, setSelectedMatchId] = useState<number | null>(null);
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null);

  const handleCreateSession = async () => {
    if (selectedMatchId === null || selectedPlayerId === null || selectedContainerId === null) return;
    try {
      await connectSession({
        containerId: selectedContainerId,
        matchId: selectedMatchId,
        playerId: selectedPlayerId,
      }).unwrap();
      setCreateDialogOpen(false);
      setSelectedMatchId(null);
      setSelectedPlayerId(null);
      setSuccess('Session created successfully');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to create session');
    }
  };

  const handleDisconnect = async (session: SessionData) => {
    if (session.playerId === null || selectedContainerId === null) return;
    try {
      await disconnectSession({
        containerId: selectedContainerId,
        matchId: session.matchId,
        playerId: session.playerId,
      }).unwrap();
      setSuccess('Session disconnected');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to disconnect session');
    }
  };

  const handleReconnect = async (session: SessionData) => {
    if (session.playerId === null || selectedContainerId === null) return;
    try {
      await reconnectSession({
        containerId: selectedContainerId,
        matchId: session.matchId,
        playerId: session.playerId,
      }).unwrap();
      setSuccess('Session reconnected');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to reconnect session');
    }
  };

  const handleAbandon = async (session: SessionData) => {
    if (session.playerId === null || selectedContainerId === null) return;
    try {
      await abandonSession({
        containerId: selectedContainerId,
        matchId: session.matchId,
        playerId: session.playerId,
      }).unwrap();
      setSuccess('Session abandoned');
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to abandon session');
    }
  };

  const getMatchName = (matchId: number) => {
    const match = matches.find(m => m.id === matchId);
    return match ? `Match ${match.id}` : `Match ${matchId}`;
  };

  const getPlayerName = (playerId: number | null) => {
    if (playerId === null) return 'Unassigned';
    const player = players.find(p => p.id === playerId);
    return player?.name || `Player ${playerId}`;
  };

  const getStatusColor = (status: string | undefined): 'success' | 'warning' | 'error' | 'default' => {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'CONNECTED': return 'success';
      case 'DISCONNECTED': return 'warning';
      case 'ABANDONED':
      case 'EXPIRED': return 'error';
      default: return 'default';
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
          Select a container from the sidebar to view and manage sessions
        </Typography>
      </Paper>
    );
  }

  if (isLoading || !selectedContainer) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress /></Box>;
  }

  const errorMessage = isError
    ? (error && 'data' in error ? String((error.data as { message?: string })?.message || 'Failed to fetch sessions') : 'Failed to fetch sessions')
    : localError;

  return (
    <Box>
      {/* Header with container context */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Sessions</Typography>
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
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialogOpen(true)}
            disabled={selectedContainer.status !== 'RUNNING'}
          >
            Create Session
          </Button>
        </Box>
      </Box>

      {errorMessage && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>{errorMessage}</Alert>}
      {success && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>{success}</Alert>}

      {/* Warning if container not running */}
      {selectedContainer.status !== 'RUNNING' && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Container is not running. Start the container to create sessions.
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Match</TableCell>
              <TableCell>Player</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Connected At</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sessions.map((session, index) => (
              <TableRow key={`${session.playerId}-${session.matchId}-${index}`}>
                <TableCell>
                  <Chip label={getMatchName(session.matchId)} size="small" />
                </TableCell>
                <TableCell>
                  <Chip label={getPlayerName(session.playerId)} size="small" color="primary" />
                </TableCell>
                <TableCell>
                  <Chip
                    label={session.status || 'UNKNOWN'}
                    size="small"
                    color={getStatusColor(session.status)}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
                    {session.connectedAt ? new Date(session.connectedAt).toLocaleString() : '-'}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  {session.status === 'CONNECTED' && (
                    <IconButton
                      size="small"
                      onClick={() => handleDisconnect(session)}
                      title="Disconnect"
                      color="warning"
                    >
                      <DisconnectIcon />
                    </IconButton>
                  )}
                  {session.status === 'DISCONNECTED' && (
                    <IconButton
                      size="small"
                      onClick={() => handleReconnect(session)}
                      title="Reconnect"
                      color="success"
                    >
                      <ReconnectIcon />
                    </IconButton>
                  )}
                  {(session.status === 'CONNECTED' || session.status === 'DISCONNECTED') && (
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => handleAbandon(session)}
                      title="Abandon"
                    >
                      <AbandonIcon />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {sessions.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary">No sessions</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create Session Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Session in {selectedContainer.name}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <Autocomplete
              options={matches}
              getOptionLabel={(m) => `Match ${m.id}`}
              value={matches.find(m => m.id === selectedMatchId) || null}
              onChange={(_, v) => setSelectedMatchId(v?.id ?? null)}
              renderInput={(params) => <TextField {...params} label="Select Match" />}
            />
            <Autocomplete
              options={players}
              getOptionLabel={(p) => p.name ? `${p.name} (ID: ${p.id})` : `Player ${p.id}`}
              value={players.find(p => p.id === selectedPlayerId) || null}
              onChange={(_, v) => setSelectedPlayerId(v?.id ?? null)}
              renderInput={(params) => <TextField {...params} label="Select Player" />}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleCreateSession}
            variant="contained"
            disabled={selectedMatchId === null || selectedPlayerId === null}
          >
            Connect
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SessionsPanel;
