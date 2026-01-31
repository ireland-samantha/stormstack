/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
    Analytics as MetricsIcon, Bolt as BoltIcon, BugReport as LogsIcon, CameraAlt as SnapshotIcon,
    Cloud as ControlPlaneIcon, Code as CommandsIcon, Dashboard as DashboardIcon, Dns as NodesIcon,
    ExpandLess, ExpandMore, Extension as ModulesIcon, Folder as ResourcesIcon, History as HistoryIcon,
    Hub as ClusterIcon, Key as ApiKeyIcon, Lock as AuthIcon, Logout as LogoutIcon, Menu as MenuIcon,
    Memory as EngineIcon, People as PeopleIcon, PersonOutline as PlayersIcon, Psychology as AIIcon,
    RocketLaunch as DeployIcon, Security as SecurityIcon, Speed as TickIcon,
    SportsEsports as MatchesIcon, Tune as AutoscalerIcon, VpnKey as SessionsIcon
} from "@mui/icons-material";
import {
    alpha, AppBar, Avatar, Box, Button, Chip, Collapse, Divider, Drawer, FormControl, IconButton, InputLabel, List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    ListItemText, MenuItem, Select, SelectChangeEvent, Toolbar,
    Typography, useMediaQuery,
    useTheme
} from "@mui/material";
import { useEffect } from "react";
import AIPanel from "./components/AIPanel";
import ApiTokensPanel from "./components/ApiTokensPanel";
import AutoscalerPanel from "./components/AutoscalerPanel";
import ClusterMatchesPanel from "./components/ClusterMatchesPanel";
import ClusterModulesPanel from "./components/ClusterModulesPanel";
import ClusterNodesPanel from "./components/ClusterNodesPanel";
import ClusterOverviewPanel from "./components/ClusterOverviewPanel";
import CommandsPanel from "./components/CommandsPanel";
import DeploymentsPanel from "./components/DeploymentsPanel";
import ContainerDashboard from "./components/ContainerDashboard";
import HistoryPanel from "./components/HistoryPanel";
import Login from "./components/Login";
import LogsPanel from "./components/LogsPanel";
import MatchesPanel from "./components/MatchesPanel";
import MetricsPanel from "./components/MetricsPanel";
import ModulesPanel from "./components/ModulesPanel";
import NotificationProvider from "./components/NotificationProvider";
import PlayersPanel from "./components/PlayersPanel";
import ResourcesPanel from "./components/ResourcesPanel";
import RolesPanel from "./components/RolesPanel";
import SessionsPanel from "./components/SessionsPanel";
import SnapshotPanel from "./components/SnapshotPanel";
import UsersPanel from "./components/UsersPanel";
import { useContainerErrors } from "./hooks/useContainerErrors";
import {
    useGetContainerMatchesQuery, useGetContainersQuery
} from "./store/api/apiSlice";
import { useAppDispatch, useAppSelector } from "./store/hooks";
import { logout, selectIsAuthenticated } from "./store/slices/authSlice";
import {
    selectActivePanel, selectAuthMenuOpen, selectControlPlaneMenuOpen, selectEngineMenuOpen,
    selectSelectedContainerId, selectSelectedMatchId, selectSidebarOpen, setActivePanel,
    setSelectedContainerId, setSelectedMatchId, setSidebarOpen, toggleAuthMenu,
    toggleControlPlaneMenu, toggleEngineMenu, type PanelType
} from "./store/slices/uiSlice";

const drawerWidth = 280;

const AppContent: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const dispatch = useAppDispatch();

  // Redux state
  const sidebarOpen = useAppSelector(selectSidebarOpen);
  const activePanel = useAppSelector(selectActivePanel);
  const controlPlaneMenuOpen = useAppSelector(selectControlPlaneMenuOpen);
  const engineMenuOpen = useAppSelector(selectEngineMenuOpen);
  const authMenuOpen = useAppSelector(selectAuthMenuOpen);
  const selectedContainerId = useAppSelector(selectSelectedContainerId);
  const selectedMatchId = useAppSelector(selectSelectedMatchId);

  // RTK Query - auto fetches containers
  const { data: containers = [] } = useGetContainersQuery();

  // RTK Query - auto fetches matches when container is selected
  const { data: matches = [] } = useGetContainerMatchesQuery(
    selectedContainerId!,
    {
      skip: selectedContainerId === null,
    },
  );

  // Auto-subscribe to error stream for selected container
  useContainerErrors();

  // Derived state
  const selectedContainer =
    containers.find((c) => c.id === selectedContainerId) || null;

  // Auto-select first container if none selected
  useEffect(() => {
    if (selectedContainerId === null && containers.length > 0) {
      dispatch(setSelectedContainerId(containers[0].id));
    }
  }, [containers, selectedContainerId, dispatch]);

  // Auto-select first match if none selected
  useEffect(() => {
    if (selectedMatchId === null && matches.length > 0) {
      dispatch(setSelectedMatchId(matches[0].id));
    }
  }, [matches, selectedMatchId, dispatch]);

  const handleDrawerToggle = () => {
    dispatch(setSidebarOpen(!sidebarOpen));
  };

  const handleContainerChange = (event: SelectChangeEvent<number>) => {
    dispatch(setSelectedContainerId(event.target.value as number));
  };

  const handleMatchChange = (event: SelectChangeEvent<number>) => {
    dispatch(setSelectedMatchId(event.target.value as number));
  };

  const handlePanelChange = (panel: PanelType) => {
    dispatch(setActivePanel(panel));
    if (isMobile) {
      dispatch(setSidebarOpen(false));
    }
  };

  const handleLogout = () => {
    dispatch(logout());
    window.location.reload();
  };

  // Lightning Control Plane menu items
  const controlPlaneMenuItems = [
    { id: "clusterOverview" as const, label: "Overview", icon: <DashboardIcon /> },
    { id: "clusterNodes" as const, label: "Nodes", icon: <NodesIcon /> },
    { id: "clusterMatches" as const, label: "Matches", icon: <MatchesIcon /> },
    { id: "clusterModules" as const, label: "Modules", icon: <ModulesIcon /> },
    { id: "deployments" as const, label: "Deployments", icon: <DeployIcon /> },
    { id: "autoscaler" as const, label: "Autoscaler", icon: <AutoscalerIcon /> },
  ];

  // Lightning Engine (Node) menu items
  const engineMenuItems = [
    { id: "dashboard" as const, label: "Overview", icon: <DashboardIcon /> },
    { id: "matches" as const, label: "Matches", icon: <MatchesIcon /> },
    { id: "players" as const, label: "Players", icon: <PlayersIcon /> },
    { id: "sessions" as const, label: "Sessions", icon: <SessionsIcon /> },
    { id: "commands" as const, label: "Commands", icon: <CommandsIcon /> },
    { id: "snapshot" as const, label: "Live Snapshot", icon: <SnapshotIcon /> },
    { id: "history" as const, label: "History", icon: <HistoryIcon /> },
    { id: "logs" as const, label: "Logs", icon: <LogsIcon /> },
    { id: "metrics" as const, label: "Metrics", icon: <MetricsIcon /> },
    { id: "modules" as const, label: "Modules", icon: <ModulesIcon /> },
    { id: "ai" as const, label: "AI", icon: <AIIcon /> },
    { id: "resources" as const, label: "Resources", icon: <ResourcesIcon /> },
  ];

  // Authentication menu items
  const authMenuItems = [
    { id: "users" as const, label: "Users", icon: <PeopleIcon /> },
    { id: "roles" as const, label: "Roles", icon: <SecurityIcon /> },
    { id: "apiTokens" as const, label: "API Tokens", icon: <ApiKeyIcon /> },
  ];

  const drawer = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Toolbar sx={{ px: 2 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Avatar sx={{ bgcolor: "warning.main", width: 36, height: 36 }}>
            <BoltIcon />
          </Avatar>
          <Box>
            <Typography variant="subtitle1" fontWeight={700} noWrap>
              Thunder
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Control Panel
            </Typography>
          </Box>
        </Box>
      </Toolbar>
      <Divider />

      <List sx={{ flexGrow: 1, pt: 0 }}>
        {/* Lightning Control Plane Section */}
        <ListItemButton onClick={() => dispatch(toggleControlPlaneMenu())}>
          <ListItemIcon>
            <ControlPlaneIcon />
          </ListItemIcon>
          <ListItemText primary="Control Plane" />
          {controlPlaneMenuOpen ? <ExpandLess /> : <ExpandMore />}
        </ListItemButton>
        <Collapse in={controlPlaneMenuOpen} timeout="auto" unmountOnExit>
          <List component="div" disablePadding>
            {controlPlaneMenuItems.map((item) => (
              <ListItem key={item.id} disablePadding>
                <ListItemButton
                  sx={{ pl: 4 }}
                  selected={activePanel === item.id}
                  onClick={() => handlePanelChange(item.id)}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Collapse>

        {/* Lightning Engine Section */}
        <ListItemButton onClick={() => dispatch(toggleEngineMenu())}>
          <ListItemIcon>
            <EngineIcon />
          </ListItemIcon>
          <ListItemText primary="Engine (Select Node)" />
          {engineMenuOpen ? <ExpandLess /> : <ExpandMore />}
        </ListItemButton>
        <Collapse in={engineMenuOpen} timeout="auto" unmountOnExit>
          {/* Container Selector in Engine submenu */}
          {containers.length > 0 && (
            <Box sx={{ px: 2, py: 1.5 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="sidebar-container-label">
                  Active Container
                </InputLabel>
                <Select
                  labelId="sidebar-container-label"
                  value={selectedContainerId || ""}
                  label="Active Container"
                  onChange={handleContainerChange}
                >
                  {containers.map((container) => (
                    <MenuItem key={container.id} value={container.id}>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <Chip
                          size="small"
                          label={container.status}
                          color={
                            container.status === "RUNNING" ? "success" : "default"
                          }
                          sx={{ height: 20, fontSize: "0.7rem" }}
                        />
                        {container.name}
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>
          )}
          <List component="div" disablePadding>
            {engineMenuItems.map((item) => (
              <ListItem key={item.id} disablePadding>
                <ListItemButton
                  sx={{ pl: 4 }}
                  selected={activePanel === item.id}
                  onClick={() => handlePanelChange(item.id)}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Collapse>

        {/* Authentication Section */}
        <ListItemButton onClick={() => dispatch(toggleAuthMenu())}>
          <ListItemIcon>
            <AuthIcon />
          </ListItemIcon>
          <ListItemText primary="Authentication" />
          {authMenuOpen ? <ExpandLess /> : <ExpandMore />}
        </ListItemButton>
        <Collapse in={authMenuOpen} timeout="auto" unmountOnExit>
          <List component="div" disablePadding>
            {authMenuItems.map((item) => (
              <ListItem key={item.id} disablePadding>
                <ListItemButton
                  sx={{ pl: 4 }}
                  selected={activePanel === item.id}
                  onClick={() => handlePanelChange(item.id)}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Collapse>
      </List>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Button
          fullWidth
          variant="outlined"
          startIcon={<LogoutIcon />}
          onClick={handleLogout}
        >
          Logout
        </Button>
      </Box>
    </Box>
  );

  const renderPanel = () => {
    switch (activePanel) {
      // Control Plane panels
      case "clusterOverview":
        return <ClusterOverviewPanel />;
      case "clusterNodes":
        return <ClusterNodesPanel />;
      case "clusterMatches":
        return <ClusterMatchesPanel />;
      case "clusterModules":
        return <ClusterModulesPanel />;
      case "deployments":
        return <DeploymentsPanel />;
      case "autoscaler":
        return <AutoscalerPanel />;
      // Engine panels
      case "dashboard":
        return <ContainerDashboard />;
      case "snapshot":
        return (
          <SnapshotPanel
            containerId={selectedContainerId || 1}
            matchId={selectedMatchId || 1}
          />
        );
      case "history":
        return <HistoryPanel />;
      case "logs":
        return <LogsPanel />;
      case "metrics":
        return <MetricsPanel />;
      case "matches":
        return <MatchesPanel />;
      case "players":
        return <PlayersPanel />;
      case "sessions":
        return <SessionsPanel />;
      case "commands":
        return <CommandsPanel />;
      case "modules":
        return <ModulesPanel />;
      case "ai":
        return <AIPanel />;
      case "resources":
        return <ResourcesPanel />;
      // Authentication panels
      case "users":
        return <UsersPanel />;
      case "roles":
        return <RolesPanel />;
      case "apiTokens":
        return <ApiTokensPanel />;
      default:
        return null;
    }
  };

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          bgcolor: "background.paper",
          color: "text.primary",
          borderBottom: 1,
          borderColor: "divider",
        }}
        elevation={0}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { md: "none" } }}
          >
            <MenuIcon />
          </IconButton>

          {/* Section indicator and context info */}
          {["clusterOverview", "clusterNodes", "clusterMatches", "clusterModules", "deployments", "autoscaler"].includes(activePanel) ? (
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 2,
                flexGrow: 1,
              }}
            >
              <Chip
                icon={<ClusterIcon />}
                label="Lightning Control Plane"
                color="primary"
                variant="outlined"
              />
            </Box>
          ) : ["users", "roles", "apiTokens"].includes(activePanel) ? (
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 2,
                flexGrow: 1,
              }}
            >
              <Chip
                icon={<AuthIcon />}
                label="Authentication"
                color="primary"
                variant="outlined"
              />
            </Box>
          ) : selectedContainer ? (
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 2,
                flexGrow: 1,
              }}
            >
              <Chip
                icon={<NodesIcon />}
                label={selectedContainer.name}
                color="primary"
                variant="outlined"
              />
              <Chip
                label={selectedContainer.status}
                color={
                  selectedContainer.status === "RUNNING" ? "success" : "default"
                }
                size="small"
              />
              <Chip
                icon={<TickIcon />}
                label={`Tick: ${selectedContainer.currentTick}`}
                size="small"
                variant="outlined"
                sx={{ fontFamily: "monospace" }}
              />
            </Box>
          ) : (
            <Box sx={{ flexGrow: 1 }} />
          )}

          {/* Match Selector (context-aware, only for engine panels) */}
          {matches.length > 0 &&
            (activePanel === "snapshot" || activePanel === "history") && (
              <FormControl size="small" sx={{ minWidth: 150, ml: 2 }}>
                <InputLabel id="match-select-label">Match</InputLabel>
                <Select
                  labelId="match-select-label"
                  value={selectedMatchId || ""}
                  label="Match"
                  onChange={handleMatchChange}
                >
                  {matches.map((match) => (
                    <MenuItem key={match.id} value={match.id}>
                      Match {match.id}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
        </Toolbar>
      </AppBar>

      <Box
        component="nav"
        sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}
      >
        {/* Mobile drawer */}
        <Drawer
          variant="temporary"
          open={sidebarOpen && isMobile}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", md: "none" },
            "& .MuiDrawer-paper": {
              boxSizing: "border-box",
              width: drawerWidth,
            },
          }}
        >
          {drawer}
        </Drawer>

        {/* Desktop drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: "none", md: "block" },
            "& .MuiDrawer-paper": {
              boxSizing: "border-box",
              width: drawerWidth,
              borderRight: 1,
              borderColor: "divider",
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { md: `calc(100% - ${drawerWidth}px)` },
          mt: "64px",
          bgcolor: alpha(theme.palette.background.default, 0.5),
          minHeight: "calc(100vh - 64px)",
        }}
      >
        {renderPanel()}
      </Box>
    </Box>
  );
};

const App: React.FC = () => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);

  return (
    <NotificationProvider>
      {isAuthenticated ? <AppContent /> : <Login />}
    </NotificationProvider>
  );
};

export default App;
