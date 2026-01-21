/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Info as InfoIcon, Psychology as AIIcon, Refresh as RefreshIcon
} from "@mui/icons-material";
import {
    Alert, Box, Chip,
    CircularProgress, IconButton, Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, Typography
} from "@mui/material";
import { useGetContainerAIQuery } from "../store/api/apiSlice";
import { useAppSelector } from "../store/hooks";
import { selectSelectedContainerId } from "../store/slices/uiSlice";

const AIPanel: React.FC = () => {
  const selectedContainerId = useAppSelector(selectSelectedContainerId);

  const {
    data: aiNames = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetContainerAIQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  const error = fetchError ? "Failed to fetch AIs" : null;

  // Show message when no container is selected
  if (selectedContainerId === null) {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <InfoIcon sx={{ fontSize: 48, color: "text.secondary", mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No Container Selected
        </Typography>
        <Typography color="text.secondary">
          Select a container from the sidebar to view its AI.
        </Typography>
      </Box>
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
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
        }}
      >
        <Typography variant="h5">Container AI</Typography>
        <Box sx={{ display: "flex", gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>AI Name</TableCell>
              <TableCell>Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {aiNames.map((aiName) => (
              <TableRow key={aiName}>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <AIIcon color="secondary" />
                    <Typography fontWeight="medium">{aiName}</Typography>
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    label="Available"
                    size="small"
                    color="success"
                    variant="outlined"
                  />
                </TableCell>
              </TableRow>
            ))}
            {aiNames.length === 0 && (
              <TableRow>
                <TableCell colSpan={2} align="center">
                  <Typography color="text.secondary">
                    No AI installed in this container
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {aiNames.length > 0 && (
        <Box sx={{ mt: 3 }}>
          <Typography variant="h6" gutterBottom>
            Available AI ({aiNames.length})
          </Typography>
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            {aiNames.map((aiName) => (
              <Chip
                key={aiName}
                icon={<AIIcon />}
                label={aiName}
                variant="outlined"
                color="secondary"
              />
            ))}
          </Box>
        </Box>
      )}

      <Paper sx={{ mt: 3, p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          About AI
        </Typography>
        <Typography variant="body2" color="text.secondary">
          AIs are autonomous controllers that can manage game logic, spawn
          entities, and execute commands automatically during simulation. Each
          container has its own set of available AI that can be enabled when
          creating matches.
        </Typography>
      </Paper>
    </Box>
  );
};

export default AIPanel;
