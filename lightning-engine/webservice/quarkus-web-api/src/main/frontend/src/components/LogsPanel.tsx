/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Box, Typography, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, IconButton, Chip, CircularProgress, Alert,
  TextField, Autocomplete, FormControlLabel, Switch, Collapse, Stack, Button
} from '@mui/material';
import {
  Refresh as RefreshIcon, Delete as DeleteIcon, Error as ErrorIcon,
  Warning as WarningIcon, Info as InfoIcon, ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon, PlayArrow as PlayIcon, Stop as StopIcon,
  Dns as ContainerIcon
} from '@mui/icons-material';
import { GameError, buildPlayerErrorWebSocketUrl } from '../services/api';
import {
  useGetContainerQuery,
  useGetContainerMatchesQuery,
  useGetPlayersInContainerQuery,
} from '../store/api/apiSlice';
import { useAppSelector } from '../store/hooks';

const LogsPanel: React.FC = () => {
  const selectedContainerId = useAppSelector((state) => state.ui.selectedContainerId);

  const { data: selectedContainer } = useGetContainerQuery(selectedContainerId!, {
    skip: !selectedContainerId,
  });

  const { data: matches = [], refetch: refetchMatches } = useGetContainerMatchesQuery(
    selectedContainerId!,
    { skip: !selectedContainerId }
  );

  const { data: players = [], isLoading, error: fetchError } = useGetPlayersInContainerQuery(
    selectedContainerId!,
    { skip: !selectedContainerId }
  );

  const [errors, setErrors] = useState<GameError[]>([]);
  const [localError, setLocalError] = useState<string | null>(null);

  const [selectedMatchId, setSelectedMatchId] = useState<number | null>(null);
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const [expandedErrorId, setExpandedErrorId] = useState<string | null>(null);

  const wsRef = useRef<WebSocket | null>(null);
  const tableEndRef = useRef<HTMLDivElement>(null);

  const fetchData = useCallback(async () => {
    await refetchMatches();
  }, [refetchMatches]);

  useEffect(() => {
    if (autoScroll && tableEndRef.current) {
      tableEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [errors, autoScroll]);

  // Cleanup WebSocket on unmount
  useEffect(() => {
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, []);

  const startStreaming = useCallback(() => {
    if (!selectedMatchId || !selectedPlayerId) {
      setLocalError('Please select a match and player');
      return;
    }

    if (wsRef.current) {
      wsRef.current.close();
    }

    const wsUrl = buildPlayerErrorWebSocketUrl(selectedMatchId, selectedPlayerId);
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      setIsStreaming(true);
      setLocalError(null);
    };

    ws.onmessage = (event) => {
      try {
        const gameError: GameError = JSON.parse(event.data);
        setErrors(prev => [...prev.slice(-999), gameError]); // Keep last 1000 errors
      } catch (e) {
        console.error('Failed to parse error:', e);
      }
    };

    ws.onerror = () => {
      setLocalError('WebSocket connection error');
      setIsStreaming(false);
    };

    ws.onclose = () => {
      setIsStreaming(false);
    };

    wsRef.current = ws;
  }, [selectedMatchId, selectedPlayerId]);

  const stopStreaming = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setIsStreaming(false);
  }, []);

  const clearLogs = () => {
    setErrors([]);
  };

  const getErrorIcon = (type: string) => {
    switch (type) {
      case 'COMMAND':
        return <ErrorIcon fontSize="small" color="error" />;
      case 'SYSTEM':
        return <WarningIcon fontSize="small" color="warning" />;
      default:
        return <InfoIcon fontSize="small" color="info" />;
    }
  };

  const getErrorTypeColor = (type: string): 'error' | 'warning' | 'info' => {
    switch (type) {
      case 'COMMAND': return 'error';
      case 'SYSTEM': return 'warning';
      default: return 'info';
    }
  };

  const formatTimestamp = (timestamp: string) => {
    try {
      return new Date(timestamp).toLocaleTimeString();
    } catch {
      return timestamp;
    }
  };

  const error = localError || (fetchError ? 'Failed to fetch data' : null);

  if (!selectedContainerId || !selectedContainer) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <ContainerIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view logs
        </Typography>
      </Paper>
    );
  }

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress /></Box>;
  }

  return (
    <Box>
      {/* Header with container context */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Logs</Typography>
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
          <IconButton onClick={fetchData} title="Refresh matches/players"><RefreshIcon /></IconButton>
          <IconButton onClick={clearLogs} title="Clear logs" color="error"><DeleteIcon /></IconButton>
        </Box>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>{error}</Alert>}

      {/* Connection Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>Error Stream Connection</Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          <Autocomplete
            sx={{ minWidth: 200 }}
            options={matches}
            getOptionLabel={(m) => `Match ${m.id}`}
            value={matches.find(m => m.id === selectedMatchId) || null}
            onChange={(_, v) => setSelectedMatchId(v?.id || null)}
            renderInput={(params) => <TextField {...params} label="Match" size="small" />}
            disabled={isStreaming}
          />
          <Autocomplete
            sx={{ minWidth: 200 }}
            options={players}
            getOptionLabel={(p) => `Player ${p.id}${p.name ? ` (${p.name})` : ''}`}
            value={players.find(p => p.id === selectedPlayerId) || null}
            onChange={(_, v) => setSelectedPlayerId(v?.id || null)}
            renderInput={(params) => <TextField {...params} label="Player" size="small" />}
            disabled={isStreaming}
          />
          {!isStreaming ? (
            <Button
              variant="contained"
              color="primary"
              startIcon={<PlayIcon />}
              onClick={startStreaming}
              disabled={!selectedMatchId || !selectedPlayerId}
            >
              Start Streaming
            </Button>
          ) : (
            <Button
              variant="contained"
              color="error"
              startIcon={<StopIcon />}
              onClick={stopStreaming}
            >
              Stop Streaming
            </Button>
          )}
          <FormControlLabel
            control={<Switch checked={autoScroll} onChange={(e) => setAutoScroll(e.target.checked)} />}
            label="Auto-scroll"
          />
          {isStreaming && (
            <Chip label="Connected" color="success" size="small" />
          )}
        </Box>
      </Paper>

      {/* Error Log Table */}
      <TableContainer component={Paper} sx={{ maxHeight: 500 }}>
        <Table stickyHeader size="small">
          <TableHead>
            <TableRow>
              <TableCell width={40}></TableCell>
              <TableCell width={100}>Time</TableCell>
              <TableCell width={100}>Type</TableCell>
              <TableCell width={150}>Source</TableCell>
              <TableCell>Message</TableCell>
              <TableCell width={80}>Match</TableCell>
              <TableCell width={80}>Player</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {errors.map((err) => (
              <>
                <TableRow
                  key={err.id}
                  hover
                  onClick={() => setExpandedErrorId(expandedErrorId === err.id ? null : err.id)}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell>
                    <IconButton size="small">
                      {expandedErrorId === err.id ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </IconButton>
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                    {formatTimestamp(err.timestamp)}
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={getErrorIcon(err.type)}
                      label={err.type}
                      size="small"
                      color={getErrorTypeColor(err.type)}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                    {err.source}
                  </TableCell>
                  <TableCell sx={{
                    maxWidth: 300,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}>
                    {err.message}
                  </TableCell>
                  <TableCell>{err.matchId || '-'}</TableCell>
                  <TableCell>{err.playerId || '-'}</TableCell>
                </TableRow>
                <TableRow key={`${err.id}-details`}>
                  <TableCell colSpan={7} sx={{ p: 0 }}>
                    <Collapse in={expandedErrorId === err.id}>
                      <Box sx={{ p: 2, bgcolor: 'grey.50' }}>
                        <Typography variant="subtitle2" gutterBottom>Message</Typography>
                        <Typography variant="body2" sx={{ mb: 2 }}>{err.message}</Typography>
                        {err.details && (
                          <>
                            <Typography variant="subtitle2" gutterBottom>Stack Trace</Typography>
                            <Paper sx={{ p: 1, bgcolor: 'grey.100', maxHeight: 200, overflow: 'auto' }}>
                              <Typography
                                variant="body2"
                                component="pre"
                                sx={{
                                  fontFamily: 'monospace',
                                  fontSize: '0.7rem',
                                  whiteSpace: 'pre-wrap',
                                  wordBreak: 'break-all',
                                  m: 0
                                }}
                              >
                                {err.details}
                              </Typography>
                            </Paper>
                          </>
                        )}
                      </Box>
                    </Collapse>
                  </TableCell>
                </TableRow>
              </>
            ))}
            {errors.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography color="text.secondary" sx={{ py: 4 }}>
                    {isStreaming
                      ? 'Waiting for errors... (This is good!)'
                      : 'No errors logged. Start streaming to receive errors.'}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <div ref={tableEndRef} />
      </TableContainer>

      <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          {errors.length} error{errors.length !== 1 ? 's' : ''} logged
        </Typography>
      </Box>
    </Box>
  );
};

export default LogsPanel;
