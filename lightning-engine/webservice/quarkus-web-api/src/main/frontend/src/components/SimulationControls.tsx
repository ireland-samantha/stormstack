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


import {
  Box,
  Tooltip,
  ButtonGroup,
  Button,
  Typography
} from '@mui/material';
import {
  PlayArrow as PlayIcon,
  Stop as StopIcon,
  SkipNext as TickIcon
} from '@mui/icons-material';
import { useContainerContext } from '../contexts/ContainerContext';
import {
  useAdvanceContainerTickMutation,
  usePlayContainerMutation,
  useStopContainerAutoAdvanceMutation,
  useGetContainerQuery
} from '../store/api/apiSlice';

export const SimulationControls: React.FC = () => {
  const { selectedContainerId } = useContainerContext();

  const { data: container } = useGetContainerQuery(selectedContainerId!, {
    skip: !selectedContainerId,
    pollingInterval: 1000
  });

  const [advanceTick, { isLoading: isTickLoading }] = useAdvanceContainerTickMutation();
  const [playContainer, { isLoading: isPlayLoading }] = usePlayContainerMutation();
  const [stopAutoAdvance, { isLoading: isStopLoading }] = useStopContainerAutoAdvanceMutation();

  const isPlaying = container?.autoAdvancing ?? false;
  const isLoading = isTickLoading || isPlayLoading || isStopLoading;

  const handleTick = async () => {
    if (selectedContainerId) {
      await advanceTick(selectedContainerId);
    }
  };

  const handlePlay = async () => {
    if (selectedContainerId) {
      await playContainer({ id: selectedContainerId, intervalMs: 16 });
    }
  };

  const handleStop = async () => {
    if (selectedContainerId) {
      await stopAutoAdvance(selectedContainerId);
    }
  };

  if (!selectedContainerId) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" color="text.secondary">
          Select a container
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <ButtonGroup variant="outlined" size="small">
        <Tooltip title="Advance one tick">
          <Button onClick={handleTick} disabled={isPlaying || isLoading}>
            <TickIcon />
          </Button>
        </Tooltip>
        {isPlaying ? (
          <Tooltip title="Stop simulation">
            <Button onClick={handleStop} color="error" disabled={isLoading}>
              <StopIcon />
            </Button>
          </Tooltip>
        ) : (
          <Tooltip title="Start simulation">
            <Button onClick={handlePlay} color="success" disabled={isLoading}>
              <PlayIcon />
            </Button>
          </Tooltip>
        )}
      </ButtonGroup>
    </Box>
  );
};

export default SimulationControls;
