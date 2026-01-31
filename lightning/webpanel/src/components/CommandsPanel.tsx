/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Cable as WebSocketIcon, Code as CodeIcon,
    Dns as ContainerIcon,
    FilterList as FilterIcon, Refresh as RefreshIcon, Send as SendIcon
} from "@mui/icons-material";
import {
    Alert, Autocomplete, Box, Button,
    Chip,
    CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControl, FormControlLabel, IconButton, InputLabel, MenuItem, Paper, Select, Stack, Switch, Tab, Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, Tabs, TextField, Tooltip, Typography
} from "@mui/material";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useCommandWebSocket } from "../hooks/useCommandWebSocket";
import { CommandParameter } from "../services/api";
import { CommandResponse } from "../services/commandWebSocket";
import {
    useGetContainerCommandsQuery, useGetContainerQuery, useSendCommandToContainerMutation
} from "../store/api/apiSlice";
import { useAppSelector } from "../store/hooks";
import CommandForm, {
    buildInitialValues,
    detectFieldType,
    getDefaultValue
} from "./CommandForm";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
    </div>
  );
}

interface CommandData {
  name: string;
  description?: string;
  module?: string;
  parameters?: CommandParameter[];
}

/**
 * Generates a JSON template from command parameters.
 * Creates placeholder values based on parameter types.
 */
export function generateParameterTemplate(
  parameters?: CommandParameter[],
): string {
  if (!parameters || parameters.length === 0) {
    return "{}";
  }

  const template: Record<string, unknown> = {};
  for (const param of parameters) {
    const fieldType = detectFieldType(param);
    template[param.name] = getDefaultValue(fieldType);
  }
  return JSON.stringify(template, null, 2);
}

const CommandsPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(
    (state) => state.ui.selectedContainerId,
  );

  const { data: selectedContainer } = useGetContainerQuery(
    selectedContainerId!,
    {
      skip: !selectedContainerId,
    },
  );

  const {
    data: commands = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetContainerCommandsQuery(selectedContainerId!, {
    skip: !selectedContainerId,
  });

  const [sendCommandRest] = useSendCommandToContainerMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [tabValue, setTabValue] = useState(0);

  // WebSocket mode toggle
  const [useWebSocket, setUseWebSocket] = useState(true);

  // WebSocket command hook
  const handleWsResponse = useCallback((response: CommandResponse) => {
    if (response.status === "ACCEPTED") {
      setSuccess(`Command "${response.commandName}" sent via WebSocket`);
    } else if (response.status === "ERROR") {
      setLocalError(`WebSocket error: ${response.message}`);
    }
  }, []);

  const handleWsError = useCallback((error: Error) => {
    setLocalError(`WebSocket error: ${error.message}`);
  }, []);

  const {
    isConnected: wsConnected,
    isConnecting: wsConnecting,
    error: wsError,
    sendCommand: sendCommandWs,
    connect: connectWs,
    disconnect: disconnectWs,
  } = useCommandWebSocket({
    containerId: selectedContainerId,
    onResponse: handleWsResponse,
    onError: handleWsError,
  });

  // Auto-connect WebSocket when container is selected and WebSocket mode is enabled
  useEffect(() => {
    if (
      useWebSocket &&
      selectedContainerId &&
      selectedContainer?.status === "RUNNING" &&
      !wsConnected &&
      !wsConnecting
    ) {
      connectWs();
    }
  }, [
    useWebSocket,
    selectedContainerId,
    selectedContainer?.status,
    wsConnected,
    wsConnecting,
    connectWs,
  ]);

  // Disconnect when switching to REST mode
  useEffect(() => {
    if (!useWebSocket && wsConnected) {
      disconnectWs();
    }
  }, [useWebSocket, wsConnected, disconnectWs]);

  // Module filter
  const [selectedModule, setSelectedModule] = useState<string>("all");

  // Send dialog state
  const [sendDialogOpen, setSendDialogOpen] = useState(false);
  const [selectedCommand, setSelectedCommand] = useState<CommandData | null>(
    null,
  );
  const [formValues, setFormValues] = useState<Record<string, unknown>>({});

  // Extract unique modules from commands
  const modules = useMemo(() => {
    const moduleSet = new Set<string>();
    commands.forEach((cmd) => {
      if (cmd.module) {
        moduleSet.add(cmd.module);
      }
    });
    return Array.from(moduleSet).sort();
  }, [commands]);

  // Filter commands by selected module
  const filteredCommands = useMemo(() => {
    if (selectedModule === "all") {
      return commands;
    }
    return commands.filter((cmd) => cmd.module === selectedModule);
  }, [commands, selectedModule]);

  const handleOpenSendDialog = (command: CommandData) => {
    setSelectedCommand(command);
    setFormValues(buildInitialValues(command.parameters || []));
    setSendDialogOpen(true);
  };

  const handleCommandSelect = (command: CommandData | null) => {
    setSelectedCommand(command);
    if (command) {
      setFormValues(buildInitialValues(command.parameters || []));
    } else {
      setFormValues({});
    }
  };

  const handleSendCommand = async () => {
    if (!selectedCommand || !selectedContainerId) return;

    try {
      if (useWebSocket && wsConnected) {
        // Send via WebSocket
        const matchId =
          typeof formValues.matchId === "number" ? formValues.matchId : 0;
        const playerId =
          typeof formValues.playerId === "number" ? formValues.playerId : 0;
        await sendCommandWs(
          selectedCommand.name,
          matchId,
          playerId,
          formValues,
        );
        // Success is handled by the WebSocket response listener
      } else {
        // Send via REST API
        await sendCommandRest({
          containerId: selectedContainerId,
          body: { commandName: selectedCommand.name, parameters: formValues },
        }).unwrap();
        setSuccess(
          `Command "${selectedCommand.name}" sent to container ${selectedContainer?.name}`,
        );
      }
      setSendDialogOpen(false);
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : "Failed to send command",
      );
    }
  };

  const getParameterSummary = (params?: CommandParameter[]) => {
    if (!params || params.length === 0) return "No parameters";
    return params.map((p) => `${p.name}: ${p.type}`).join(", ");
  };

  const error = localError || (fetchError ? "Failed to fetch commands" : null);

  if (!selectedContainerId || !selectedContainer) {
    return (
      <Paper sx={{ p: 4, textAlign: "center" }}>
        <ContainerIcon sx={{ fontSize: 48, color: "text.secondary", mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view and send commands
        </Typography>
      </Paper>
    );
  }

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      {/* Header with container context */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          mb: 3,
        }}
      >
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Commands
          </Typography>
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
              color={
                selectedContainer.status === "RUNNING" ? "success" : "default"
              }
            />
            <Chip
              label={`${commands.length} commands`}
              size="small"
              variant="outlined"
            />
            <Tooltip
              title={
                useWebSocket
                  ? wsConnected
                    ? "WebSocket connected"
                    : wsConnecting
                      ? "Connecting..."
                      : "WebSocket disconnected"
                  : "Using REST API"
              }
            >
              <Chip
                icon={<WebSocketIcon />}
                label={
                  useWebSocket
                    ? wsConnected
                      ? "WS"
                      : wsConnecting
                        ? "..."
                        : "WS Off"
                    : "REST"
                }
                size="small"
                color={useWebSocket && wsConnected ? "success" : "default"}
                variant="outlined"
              />
            </Tooltip>
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <FormControlLabel
            control={
              <Switch
                checked={useWebSocket}
                onChange={(e) => setUseWebSocket(e.target.checked)}
                size="small"
              />
            }
            label="WebSocket"
            sx={{ mr: 1 }}
          />
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
        </Stack>
      </Box>

      {wsError && useWebSocket && (
        <Alert severity="warning" sx={{ mb: 2 }} onClose={() => {}}>
          WebSocket: {wsError}. Commands will be sent via REST API.
        </Alert>
      )}

      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          onClose={() => setLocalError(null)}
        >
          {error}
        </Alert>
      )}
      {success && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          onClose={() => setSuccess(null)}
        >
          {success}
        </Alert>
      )}

      {/* Warning if container not running */}
      {selectedContainer.status !== "RUNNING" && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Container is not running. Start the container to send commands.
        </Alert>
      )}

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)} sx={{ mb: 2 }}>
        <Tab label="Available Commands" />
        <Tab label="Quick Send" />
      </Tabs>

      <TabPanel value={tabValue} index={0}>
        {/* Module Filter */}
        <Box sx={{ mb: 2, display: "flex", alignItems: "center", gap: 2 }}>
          <FilterIcon color="action" />
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>Filter by Module</InputLabel>
            <Select
              value={selectedModule}
              label="Filter by Module"
              onChange={(e) => setSelectedModule(e.target.value)}
            >
              <MenuItem value="all">All Modules ({commands.length})</MenuItem>
              <Divider />
              {modules.map((mod) => (
                <MenuItem key={mod} value={mod}>
                  {mod} ({commands.filter((c) => c.module === mod).length})
                </MenuItem>
              ))}
              {commands.some((c) => !c.module) && (
                <MenuItem value="">
                  No Module ({commands.filter((c) => !c.module).length})
                </MenuItem>
              )}
            </Select>
          </FormControl>
          {selectedModule !== "all" && (
            <Button size="small" onClick={() => setSelectedModule("all")}>
              Clear Filter
            </Button>
          )}
        </Box>

        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Parameters</TableCell>
                <TableCell>Module</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredCommands.map((command) => (
                <TableRow key={command.name} hover>
                  <TableCell>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                      <CodeIcon fontSize="small" color="primary" />
                      <Box>
                        <Typography
                          sx={{ fontFamily: "monospace", fontWeight: 500 }}
                        >
                          {command.name}
                        </Typography>
                        {command.description && (
                          <Typography variant="caption" color="text.secondary">
                            {command.description}
                          </Typography>
                        )}
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ fontFamily: "monospace", fontSize: "0.75rem" }}
                    >
                      {getParameterSummary(command.parameters)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {command.module ? (
                      <Chip
                        label={command.module}
                        size="small"
                        variant="outlined"
                        onClick={() => setSelectedModule(command.module!)}
                        sx={{ cursor: "pointer" }}
                      />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        -
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      variant="contained"
                      startIcon={<SendIcon />}
                      onClick={() => handleOpenSendDialog(command)}
                      disabled={selectedContainer.status !== "RUNNING"}
                    >
                      Send
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {filteredCommands.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    <Typography color="text.secondary" sx={{ py: 2 }}>
                      {commands.length === 0
                        ? "No commands available"
                        : `No commands found for module "${selectedModule}"`}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </TabPanel>

      <TabPanel value={tabValue} index={1}>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Send Command to Container
          </Typography>

          {/* Module filter for command selection */}
          <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel>Module</InputLabel>
              <Select
                value={selectedModule}
                label="Module"
                onChange={(e) => {
                  setSelectedModule(e.target.value);
                  setSelectedCommand(null);
                  setFormValues({});
                }}
              >
                <MenuItem value="all">All</MenuItem>
                {modules.map((mod) => (
                  <MenuItem key={mod} value={mod}>
                    {mod}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Autocomplete
              options={filteredCommands}
              getOptionLabel={(c) => c.name}
              value={selectedCommand}
              onChange={(_, v) => handleCommandSelect(v)}
              sx={{ flex: 1 }}
              renderInput={(params) => (
                <TextField {...params} label="Command" size="small" />
              )}
              renderOption={(props, option) => (
                <li {...props} key={option.name}>
                  <Box>
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: "monospace" }}
                    >
                      {option.name}
                    </Typography>
                    {option.description && (
                      <Typography variant="caption" color="text.secondary">
                        {option.description}
                      </Typography>
                    )}
                  </Box>
                </li>
              )}
            />
          </Box>

          {selectedCommand && (
            <>
              <Divider sx={{ my: 2 }} />
              <Typography
                variant="subtitle2"
                color="text.secondary"
                gutterBottom
              >
                Parameters for {selectedCommand.name}
              </Typography>
              <CommandForm
                parameters={selectedCommand.parameters || []}
                values={formValues}
                onChange={setFormValues}
                disabled={selectedContainer.status !== "RUNNING"}
              />
              <Box sx={{ mt: 3 }}>
                <Button
                  variant="contained"
                  startIcon={<SendIcon />}
                  onClick={handleSendCommand}
                  disabled={selectedContainer.status !== "RUNNING"}
                  fullWidth
                >
                  Send {selectedCommand.name} to {selectedContainer.name}
                </Button>
              </Box>
            </>
          )}

          {!selectedCommand && (
            <Typography
              color="text.secondary"
              sx={{ py: 2, textAlign: "center" }}
            >
              Select a command to configure and send
            </Typography>
          )}
        </Paper>
      </TabPanel>

      {/* Send Command Dialog */}
      <Dialog
        open={sendDialogOpen}
        onClose={() => setSendDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ pb: 1 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <CodeIcon color="primary" />
            <Typography
              variant="h6"
              component="span"
              sx={{ fontFamily: "monospace" }}
            >
              {selectedCommand?.name}
            </Typography>
          </Box>
          {selectedCommand?.description && (
            <Typography variant="body2" color="text.secondary">
              {selectedCommand.description}
            </Typography>
          )}
        </DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Command will be sent to container "{selectedContainer.name}" and
            executed on the next tick.
          </Alert>

          <CommandForm
            parameters={selectedCommand?.parameters || []}
            values={formValues}
            onChange={setFormValues}
            disabled={selectedContainer.status !== "RUNNING"}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSendDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleSendCommand}
            variant="contained"
            startIcon={<SendIcon />}
            disabled={selectedContainer.status !== "RUNNING"}
          >
            Send Command
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CommandsPanel;
