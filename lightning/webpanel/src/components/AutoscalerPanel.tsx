/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import {
  Box,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Alert,
  IconButton,
  Chip,
  Button,
  Grid,
  Paper,
  Divider,
} from "@mui/material";
import {
  Refresh as RefreshIcon,
  TrendingUp as ScaleUpIcon,
  TrendingDown as ScaleDownIcon,
  HorizontalRule as NoActionIcon,
  CheckCircle as AcknowledgeIcon,
  Timer as CooldownIcon,
  Info as InfoIcon,
} from "@mui/icons-material";
import { useState } from "react";
import {
  useGetAutoscalerStatusQuery,
  useGetAutoscalerRecommendationQuery,
  useAcknowledgeScalingActionMutation,
} from "../store/api/apiSlice";

const AutoscalerPanel: React.FC = () => {
  const {
    data: status,
    isLoading: statusLoading,
    error: statusError,
    refetch: refetchStatus,
  } = useGetAutoscalerStatusQuery();

  const {
    data: recommendation,
    isLoading: recommendationLoading,
    error: recommendationError,
    refetch: refetchRecommendation,
  } = useGetAutoscalerRecommendationQuery();

  const [acknowledgeAction] = useAcknowledgeScalingActionMutation();

  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleRefresh = () => {
    refetchStatus();
    refetchRecommendation();
  };

  const handleAcknowledge = async () => {
    try {
      await acknowledgeAction().unwrap();
      setSuccess("Scaling action acknowledged. Cooldown period started.");
    } catch (err) {
      setLocalError(
        err instanceof Error ? err.message : "Failed to acknowledge scaling action"
      );
    }
  };

  const getActionIcon = (action: string) => {
    switch (action) {
      case "SCALE_UP":
        return <ScaleUpIcon color="success" sx={{ fontSize: 48 }} />;
      case "SCALE_DOWN":
        return <ScaleDownIcon color="warning" sx={{ fontSize: 48 }} />;
      default:
        return <NoActionIcon color="disabled" sx={{ fontSize: 48 }} />;
    }
  };

  const getActionColor = (action: string): "success" | "warning" | "default" => {
    switch (action) {
      case "SCALE_UP":
        return "success";
      case "SCALE_DOWN":
        return "warning";
      default:
        return "default";
    }
  };

  const getActionLabel = (action: string) => {
    switch (action) {
      case "SCALE_UP":
        return "Scale Up Recommended";
      case "SCALE_DOWN":
        return "Scale Down Recommended";
      default:
        return "No Scaling Needed";
    }
  };

  const isLoading = statusLoading || recommendationLoading;
  const error = statusError || recommendationError;

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        Failed to load autoscaler status. Make sure the control plane is running.
      </Alert>
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
        <Typography variant="h5">Autoscaler</Typography>
        <IconButton onClick={handleRefresh} title="Refresh">
          <RefreshIcon />
        </IconButton>
      </Box>

      {localError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>
          {localError}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Current Status Card */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Status
              </Typography>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
                {status?.inCooldown ? (
                  <>
                    <CooldownIcon color="warning" />
                    <Chip label="In Cooldown" color="warning" size="small" />
                  </>
                ) : (
                  <>
                    <CheckCircle color="success" />
                    <Chip label="Active" color="success" size="small" />
                  </>
                )}
              </Box>
              {status?.inCooldown && (
                <Alert severity="info" icon={<InfoIcon />}>
                  The autoscaler is in cooldown mode to prevent rapid scaling.
                  It will resume normal operation after the cooldown period.
                </Alert>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Current Recommendation Card */}
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Current Recommendation
              </Typography>
              {recommendation ? (
                <Box>
                  <Box
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      gap: 2,
                      mb: 2,
                    }}
                  >
                    {getActionIcon(recommendation.action)}
                    <Box>
                      <Chip
                        label={getActionLabel(recommendation.action)}
                        color={getActionColor(recommendation.action)}
                        sx={{ mb: 1 }}
                      />
                      <Typography variant="body2" color="text.secondary">
                        {recommendation.reason}
                      </Typography>
                    </Box>
                  </Box>
                  <Divider sx={{ my: 2 }} />
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Current Nodes
                      </Typography>
                      <Typography variant="h4">
                        {recommendation.currentNodes}
                      </Typography>
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Recommended Nodes
                      </Typography>
                      <Typography
                        variant="h4"
                        color={
                          recommendation.recommendedNodes > recommendation.currentNodes
                            ? "success.main"
                            : recommendation.recommendedNodes < recommendation.currentNodes
                            ? "warning.main"
                            : "text.primary"
                        }
                      >
                        {recommendation.recommendedNodes}
                      </Typography>
                    </Grid>
                  </Grid>
                  {recommendation.action !== "NO_ACTION" && (
                    <Box sx={{ mt: 3 }}>
                      <Button
                        variant="contained"
                        startIcon={<AcknowledgeIcon />}
                        onClick={handleAcknowledge}
                        disabled={status?.inCooldown}
                      >
                        Acknowledge & Start Cooldown
                      </Button>
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ display: "block", mt: 1 }}
                      >
                        Acknowledging indicates you have taken or will take the
                        recommended action. This starts the cooldown timer.
                      </Typography>
                    </Box>
                  )}
                </Box>
              ) : (
                <Typography color="text.secondary">
                  No recommendation available
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Last Recommendation History */}
        {status?.lastRecommendation && (
          <Grid size={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Last Acknowledged Recommendation
              </Typography>
              <Box sx={{ display: "flex", gap: 4, alignItems: "center" }}>
                <Box>
                  <Chip
                    label={status.lastRecommendation.action}
                    color={getActionColor(status.lastRecommendation.action)}
                    size="small"
                  />
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Reason
                  </Typography>
                  <Typography variant="body1">
                    {status.lastRecommendation.reason}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Nodes
                  </Typography>
                  <Typography variant="body1">
                    {status.lastRecommendation.currentNodes} →{" "}
                    {status.lastRecommendation.recommendedNodes}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Time
                  </Typography>
                  <Typography variant="body1">
                    {new Date(status.lastRecommendation.timestamp).toLocaleString()}
                  </Typography>
                </Box>
              </Box>
            </Paper>
          </Grid>
        )}
      </Grid>
    </Box>
  );
};

// Helper component for CheckCircle since we're using it without the import
const CheckCircle: React.FC<{ color: "success" | "error" | "warning" }> = ({
  color,
}) => (
  <Box
    component="span"
    sx={{
      display: "inline-flex",
      color: `${color}.main`,
    }}
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
    </svg>
  </Box>
);

export default AutoscalerPanel;
