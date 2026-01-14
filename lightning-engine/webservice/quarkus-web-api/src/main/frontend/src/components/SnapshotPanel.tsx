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


import { useEffect, useState } from 'react';
import {
  Card,
  CardContent,
  CardHeader,
  Typography,
  Box,
  Chip,
  CircularProgress,
  Alert,
  IconButton,
  Tooltip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  Circle as CircleIcon
} from '@mui/icons-material';
import { useSnapshot } from '../hooks/useSnapshot';

interface SnapshotPanelProps {
  containerId: number;
  matchId: number;
}

export const SnapshotPanel: React.FC<SnapshotPanelProps> = ({ containerId, matchId }) => {
  const { snapshot, connected, error, requestSnapshot } = useSnapshot(containerId, matchId);
  const [expandedModules, setExpandedModules] = useState<Set<string>>(new Set());

  useEffect(() => {
    // Expand first module by default
    if (snapshot && expandedModules.size === 0) {
      const modules = Object.keys(snapshot.data);
      if (modules.length > 0) {
        setExpandedModules(new Set([modules[0]]));
      }
    }
  }, [snapshot]);

  const handleModuleToggle = (moduleName: string) => {
    setExpandedModules(prev => {
      const newSet = new Set(prev);
      if (newSet.has(moduleName)) {
        newSet.delete(moduleName);
      } else {
        newSet.add(moduleName);
      }
      return newSet;
    });
  };

  const getEntityCount = (): number => {
    if (!snapshot?.data) return 0;
    const modules = Object.values(snapshot.data);
    if (modules.length === 0) return 0;
    const firstModule = modules[0];
    const firstComponent = Object.values(firstModule)[0];
    return Array.isArray(firstComponent) ? firstComponent.length : 0;
  };

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardHeader
        title="Live Snapshot"
        subheader={`Match ${matchId}`}
        action={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Tooltip title={connected ? 'Connected' : 'Disconnected'}>
              <CircleIcon
                sx={{
                  fontSize: 12,
                  color: connected ? 'success.main' : 'error.main'
                }}
              />
            </Tooltip>
            <Tooltip title="Refresh">
              <IconButton onClick={requestSnapshot} size="small">
                <RefreshIcon />
              </IconButton>
            </Tooltip>
          </Box>
        }
      />
      <CardContent sx={{ flexGrow: 1, overflow: 'auto' }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {!snapshot && !error && (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {snapshot && (
          <>
            <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
              <Chip
                label={`Tick ${snapshot.tick}`}
                color="primary"
                variant="outlined"
              />
              <Chip
                label={`${getEntityCount()} Entities`}
                color="secondary"
                variant="outlined"
              />
              <Chip
                label={`${Object.keys(snapshot.data).length} Modules`}
                variant="outlined"
              />
            </Box>

            {Object.entries(snapshot.data).map(([moduleName, moduleData]) => (
              <Accordion
                key={moduleName}
                expanded={expandedModules.has(moduleName)}
                onChange={() => handleModuleToggle(moduleName)}
                sx={{ mb: 1 }}
              >
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="subtitle1" fontWeight="medium">
                    {moduleName}
                  </Typography>
                  <Chip
                    size="small"
                    label={`${Object.keys(moduleData).length} components`}
                    sx={{ ml: 2 }}
                  />
                </AccordionSummary>
                <AccordionDetails>
                  <TableContainer component={Paper} variant="outlined">
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Component</TableCell>
                          <TableCell align="right">Count</TableCell>
                          <TableCell>Sample Values</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {Object.entries(moduleData).map(([componentName, values]) => (
                          <TableRow key={componentName}>
                            <TableCell component="th" scope="row">
                              <Typography variant="body2" fontFamily="monospace">
                                {componentName}
                              </Typography>
                            </TableCell>
                            <TableCell align="right">
                              {Array.isArray(values) ? values.length : 0}
                            </TableCell>
                            <TableCell>
                              <Typography
                                variant="body2"
                                fontFamily="monospace"
                                sx={{
                                  maxWidth: 200,
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap'
                                }}
                              >
                                {Array.isArray(values)
                                  ? values.slice(0, 3).join(', ') + (values.length > 3 ? '...' : '')
                                  : String(values)}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>
            ))}
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default SnapshotPanel;
