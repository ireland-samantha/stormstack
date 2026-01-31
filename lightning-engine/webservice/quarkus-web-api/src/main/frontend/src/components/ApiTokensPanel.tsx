/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Add as AddIcon,
    ContentCopy as CopyIcon,
    Delete as DeleteIcon,
    Refresh as RefreshIcon,
    Block as RevokeIcon
} from "@mui/icons-material";
import {
    Alert,
    Autocomplete,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Tooltip,
    Typography
} from "@mui/material";
import { useState } from "react";
import {
    useCreateApiTokenMutation,
    useDeleteApiTokenMutation,
    useGetApiTokensQuery,
    useGetRolesQuery,
    useRevokeApiTokenMutation
} from "../store/api/apiSlice";

const ApiTokensPanel: React.FC = () => {
  const {
    data: apiTokens = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetApiTokensQuery();
  const { data: roles = [] } = useGetRolesQuery();

  const [createApiToken] = useCreateApiTokenMutation();
  const [deleteApiToken] = useDeleteApiTokenMutation();
  const [revokeApiToken] = useRevokeApiTokenMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [formData, setFormData] = useState({
    name: "",
    scopes: [] as string[],
    expiresInDays: 365,
  });

  const [newTokenDialogOpen, setNewTokenDialogOpen] = useState(false);
  const [newToken, setNewToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingTokenId, setDeletingTokenId] = useState<string | null>(null);
  const [deletingTokenName, setDeletingTokenName] = useState<string>("");

  const handleOpenDialog = () => {
    setFormData({
      name: "",
      scopes: [],
      expiresInDays: 365,
    });
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
  };

  const handleCreateToken = async () => {
    try {
      // Convert expiresInDays to ISO 8601 expiresAt
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + formData.expiresInDays);

      const result = await createApiToken({
        name: formData.name,
        scopes: formData.scopes.length > 0 ? formData.scopes : ["admin"], // Default to admin scope if none selected
        expiresAt: expiresAt.toISOString(),
      }).unwrap();

      setNewToken(result.plaintextToken);
      setNewTokenDialogOpen(true);
      handleCloseDialog();
      setSuccess("API token created successfully");
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to create API token");
    }
  };

  const handleCopyToken = async () => {
    if (newToken) {
      await navigator.clipboard.writeText(newToken);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleCloseNewTokenDialog = () => {
    setNewTokenDialogOpen(false);
    setNewToken(null);
    setCopied(false);
  };

  const handleOpenDeleteDialog = (id: string, name: string) => {
    setDeletingTokenId(id);
    setDeletingTokenName(name);
    setDeleteDialogOpen(true);
  };

  const handleDeleteToken = async () => {
    if (deletingTokenId !== null) {
      try {
        await deleteApiToken(deletingTokenId).unwrap();
        setDeleteDialogOpen(false);
        setDeletingTokenId(null);
        setDeletingTokenName("");
        setSuccess("API token deleted successfully");
      } catch (err) {
        setLocalError(err instanceof Error ? err.message : "Failed to delete API token");
      }
    }
  };

  const handleRevokeToken = async (id: string) => {
    try {
      await revokeApiToken(id).unwrap();
      setSuccess("API token revoked successfully");
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to revoke API token");
    }
  };

  const error = localError || (fetchError ? "Failed to fetch API tokens" : null);

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
        <Typography variant="h5">API Tokens</Typography>
        <Box sx={{ display: "flex", gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleOpenDialog}
          >
            Create Token
          </Button>
        </Box>
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

      {success && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          onClose={() => setSuccess(null)}
        >
          {success}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Scopes</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Created</TableCell>
              <TableCell>Expires</TableCell>
              <TableCell>Last Used</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {apiTokens.map((token) => (
              <TableRow key={token.id}>
                <TableCell>
                  <Typography fontFamily="monospace">{token.name}</Typography>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {token.scopes.map((scope) => (
                      <Chip
                        key={scope}
                        label={scope}
                        size="small"
                        color={scope === "admin" ? "primary" : "default"}
                      />
                    ))}
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    label={token.active ? "Active" : "Revoked"}
                    color={token.active ? "success" : "error"}
                    size="small"
                  />
                </TableCell>
                <TableCell>
                  {new Date(token.createdAt).toLocaleDateString()}
                </TableCell>
                <TableCell>
                  {token.expiresAt
                    ? new Date(token.expiresAt).toLocaleDateString()
                    : "Never"}
                </TableCell>
                <TableCell>
                  {token.lastUsedAt
                    ? new Date(token.lastUsedAt).toLocaleString()
                    : "Never"}
                </TableCell>
                <TableCell align="right">
                  {token.active && (
                    <Tooltip title="Revoke">
                      <IconButton
                        size="small"
                        onClick={() => handleRevokeToken(token.id)}
                        color="warning"
                      >
                        <RevokeIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Delete">
                    <IconButton
                      size="small"
                      onClick={() => handleOpenDeleteDialog(token.id, token.name)}
                      color="error"
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {apiTokens.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography color="text.secondary">
                    No API tokens found. Create one to enable programmatic access.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create Token Dialog */}
      <Dialog
        open={dialogOpen}
        onClose={handleCloseDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create API Token</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <TextField
              label="Token Name"
              value={formData.name}
              onChange={(e) =>
                setFormData({ ...formData, name: e.target.value })
              }
              fullWidth
              required
              placeholder="e.g., CI/CD Pipeline, Game Client"
              helperText="A descriptive name to identify this token"
            />
            <Autocomplete
              multiple
              options={roles.map((r) => r.name)}
              value={formData.scopes}
              onChange={(_, newValue) =>
                setFormData({ ...formData, scopes: newValue })
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Scopes"
                  helperText="Permission scopes granted to this token"
                />
              )}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => {
                  const { key, ...rest } = getTagProps({ index });
                  return (
                    <Chip key={key} label={option} size="small" {...rest} />
                  );
                })
              }
            />
            <TextField
              label="Expires In (Days)"
              type="number"
              value={formData.expiresInDays}
              onChange={(e) =>
                setFormData({
                  ...formData,
                  expiresInDays: parseInt(e.target.value) || 365,
                })
              }
              fullWidth
              inputProps={{ min: 1, max: 3650 }}
              helperText="Number of days until the token expires (1-3650)"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button
            onClick={handleCreateToken}
            variant="contained"
            disabled={!formData.name}
          >
            Create Token
          </Button>
        </DialogActions>
      </Dialog>

      {/* New Token Display Dialog */}
      <Dialog
        open={newTokenDialogOpen}
        onClose={handleCloseNewTokenDialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>API Token Created</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Copy this token now. You won't be able to see it again!
          </Alert>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              p: 2,
              bgcolor: "grey.100",
              borderRadius: 1,
              fontFamily: "monospace",
              wordBreak: "break-all",
            }}
          >
            <Typography
              sx={{ flexGrow: 1, fontFamily: "monospace", fontSize: "0.875rem" }}
            >
              {newToken}
            </Typography>
            <Tooltip title={copied ? "Copied!" : "Copy to clipboard"}>
              <IconButton onClick={handleCopyToken} color={copied ? "success" : "default"}>
                <CopyIcon />
              </IconButton>
            </Tooltip>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Use this token in the Authorization header:
          </Typography>
          <Box
            sx={{
              p: 1,
              bgcolor: "grey.50",
              borderRadius: 1,
              fontFamily: "monospace",
              fontSize: "0.75rem",
              mt: 1,
            }}
          >
            Authorization: Bearer {newToken ? newToken.substring(0, 20) + "..." : ""}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseNewTokenDialog} variant="contained">
            Done
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
      >
        <DialogTitle>Delete API Token</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete the token "{deletingTokenName}"?
            This action cannot be undone and any applications using this token
            will lose access.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeleteToken} color="error" variant="contained">
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ApiTokensPanel;
