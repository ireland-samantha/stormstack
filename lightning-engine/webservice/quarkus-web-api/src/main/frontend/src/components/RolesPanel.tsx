/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Add as AddIcon, Delete as DeleteIcon, Edit as EditIcon, Refresh as RefreshIcon
} from "@mui/icons-material";
import {
    Alert, Autocomplete, Box, Button,
    Chip,
    CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow, TextField, Typography
} from "@mui/material";
import { useState } from "react";
import {
    RoleData, useCreateRoleMutation, useDeleteRoleMutation, useGetRolesQuery, useUpdateRoleDescriptionMutation,
    useUpdateRoleIncludesMutation, useUpdateRoleScopesMutation
} from "../store/api/apiSlice";

// Common scopes for autocomplete suggestions
const COMMON_SCOPES = [
  // Auth service scopes
  "auth.user.read",
  "auth.user.create",
  "auth.user.update",
  "auth.user.delete",
  "auth.user.*",
  "auth.role.read",
  "auth.role.create",
  "auth.role.update",
  "auth.role.delete",
  "auth.role.*",
  "auth.token.read",
  "auth.token.create",
  "auth.token.revoke",
  "auth.token.*",
  "auth.*",
  // Engine service scopes
  "engine.container.read",
  "engine.container.create",
  "engine.container.update",
  "engine.container.delete",
  "engine.container.*",
  "engine.match.read",
  "engine.match.create",
  "engine.match.update",
  "engine.match.delete",
  "engine.match.*",
  "engine.command.submit",
  "engine.command.*",
  "engine.snapshot.view",
  "engine.snapshot.*",
  "engine.module.read",
  "engine.module.install",
  "engine.module.*",
  "engine.*",
  // Control plane scopes
  "controlplane.cluster.read",
  "controlplane.node.read",
  "controlplane.deploy",
  "controlplane.undeploy",
  "controlplane.autoscaler.read",
  "controlplane.autoscaler.acknowledge",
  "controlplane.*",
  // Wildcard
  "*",
];

const RolesPanel: React.FC = () => {
  const {
    data: roles = [],
    isLoading,
    error: fetchError,
    refetch,
  } = useGetRolesQuery();

  const [createRole] = useCreateRoleMutation();
  const [updateRoleDescription] = useUpdateRoleDescriptionMutation();
  const [updateRoleIncludes] = useUpdateRoleIncludesMutation();
  const [updateRoleScopes] = useUpdateRoleScopesMutation();
  const [deleteRole] = useDeleteRoleMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleData | null>(null);
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    includedRoles: [] as string[],
    scopes: [] as string[],
  });

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingRole, setDeletingRole] = useState<RoleData | null>(null);

  const handleOpenDialog = (role?: RoleData) => {
    if (role) {
      setEditingRole(role);
      setFormData({
        name: role.name,
        description: role.description || "",
        includedRoles: role.includedRoles || [],
        scopes: role.scopes || [],
      });
    } else {
      setEditingRole(null);
      setFormData({
        name: "",
        description: "",
        includedRoles: [],
        scopes: [],
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingRole(null);
  };

  const handleSaveRole = async () => {
    try {
      if (editingRole) {
        // Update description if changed
        if (editingRole.description !== formData.description) {
          await updateRoleDescription({
            roleId: editingRole.id,
            description: formData.description,
          }).unwrap();
        }
        // Update includes if changed
        if (
          JSON.stringify(editingRole.includedRoles || []) !==
          JSON.stringify(formData.includedRoles)
        ) {
          await updateRoleIncludes({
            roleId: editingRole.id,
            includes: formData.includedRoles,
          }).unwrap();
        }
        // Update scopes if changed
        if (
          JSON.stringify(editingRole.scopes || []) !==
          JSON.stringify(formData.scopes)
        ) {
          await updateRoleScopes({
            roleId: editingRole.id,
            scopes: formData.scopes,
          }).unwrap();
        }
        setSuccess("Role updated successfully");
      } else {
        await createRole({
          name: formData.name,
          description: formData.description || undefined,
          includedRoles:
            formData.includedRoles.length > 0
              ? formData.includedRoles
              : undefined,
          scopes:
            formData.scopes.length > 0
              ? formData.scopes
              : undefined,
        }).unwrap();
        setSuccess("Role created successfully");
      }
      handleCloseDialog();
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "Failed to save role");
    }
  };

  const handleOpenDeleteDialog = (role: RoleData) => {
    setDeletingRole(role);
    setDeleteDialogOpen(true);
  };

  const handleDeleteRole = async () => {
    if (deletingRole) {
      try {
        await deleteRole(deletingRole.id).unwrap();
        setDeleteDialogOpen(false);
        setDeletingRole(null);
        setSuccess("Role deleted successfully");
      } catch (err) {
        setLocalError(
          err instanceof Error ? err.message : "Failed to delete role",
        );
      }
    }
  };

  const getRoleColor = (roleName: string): "error" | "primary" | "default" => {
    if (roleName === "admin") return "error";
    if (roleName.includes("manager") || roleName.includes("command"))
      return "primary";
    return "default";
  };

  const error = localError || (fetchError ? "Failed to fetch roles" : null);

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
        <Typography variant="h5">Role Management</Typography>
        <Box sx={{ display: "flex", gap: 1 }}>
          <IconButton onClick={() => refetch()} title="Refresh">
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => handleOpenDialog()}
          >
            Add Role
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
              <TableCell>Description</TableCell>
              <TableCell>Includes Roles</TableCell>
              <TableCell>Scopes</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {roles.map((role) => (
              <TableRow key={role.id}>
                <TableCell>
                  <Chip
                    label={role.name}
                    color={getRoleColor(role.name)}
                    size="small"
                  />
                </TableCell>
                <TableCell>{role.description || "-"}</TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {(role.includedRoles || []).map((inc) => (
                      <Chip
                        key={inc}
                        label={inc}
                        size="small"
                        variant="outlined"
                      />
                    ))}
                    {(!role.includedRoles ||
                      role.includedRoles.length === 0) && (
                      <Typography color="text.secondary" variant="body2">
                        None
                      </Typography>
                    )}
                  </Box>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {(role.scopes || []).slice(0, 3).map((scope) => (
                      <Chip
                        key={scope}
                        label={scope}
                        size="small"
                        color="info"
                        variant="outlined"
                      />
                    ))}
                    {(role.scopes || []).length > 3 && (
                      <Chip
                        label={`+${(role.scopes || []).length - 3}`}
                        size="small"
                        variant="outlined"
                      />
                    )}
                    {(!role.scopes || role.scopes.length === 0) && (
                      <Typography color="text.secondary" variant="body2">
                        None
                      </Typography>
                    )}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    onClick={() => handleOpenDialog(role)}
                    title="Edit"
                  >
                    <EditIcon />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={() => handleOpenDeleteDialog(role)}
                    title="Delete"
                    color="error"
                    disabled={role.name === "admin"}
                  >
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {roles.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary">No roles found</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Add/Edit Role Dialog */}
      <Dialog
        open={dialogOpen}
        onClose={handleCloseDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{editingRole ? "Edit Role" : "Add Role"}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <TextField
              label="Name"
              value={formData.name}
              onChange={(e) =>
                setFormData({ ...formData, name: e.target.value })
              }
              fullWidth
              required
              disabled={!!editingRole}
            />
            <TextField
              label="Description"
              value={formData.description}
              onChange={(e) =>
                setFormData({ ...formData, description: e.target.value })
              }
              fullWidth
              multiline
              rows={2}
            />
            <Autocomplete
              multiple
              options={roles
                .map((r) => r.name)
                .filter((n) => n !== formData.name)}
              value={formData.includedRoles}
              onChange={(_, newValue) =>
                setFormData({ ...formData, includedRoles: newValue })
              }
              renderInput={(params) => (
                <TextField {...params} label="Includes Roles" />
              )}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => {
                  const { key, ...rest } = getTagProps({ index });
                  return (
                    <Chip
                      key={key}
                      label={option}
                      size="small"
                      variant="outlined"
                      {...rest}
                    />
                  );
                })
              }
            />
            <Autocomplete
              multiple
              freeSolo
              options={COMMON_SCOPES}
              value={formData.scopes}
              onChange={(_, newValue) =>
                setFormData({ ...formData, scopes: newValue })
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Scopes"
                  helperText="Permission scopes (e.g., auth.user.read, engine.command.*)"
                />
              )}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => {
                  const { key, ...rest } = getTagProps({ index });
                  return (
                    <Chip
                      key={key}
                      label={option}
                      size="small"
                      color="info"
                      variant="outlined"
                      {...rest}
                    />
                  );
                })
              }
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button
            onClick={handleSaveRole}
            variant="contained"
            disabled={!formData.name}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
      >
        <DialogTitle>Delete Role</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete role "{deletingRole?.name}"? This
            action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeleteRole} color="error" variant="contained">
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default RolesPanel;
