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
  Button
} from '@mui/material';
import {
  PlayArrow as PlayIcon,
  Stop as StopIcon,
  SkipNext as TickIcon
} from '@mui/icons-material';
import { useSimulation } from '../hooks/useApi';

export const SimulationControls: React.FC = () => {
  const { playing, tick, play, stop } = useSimulation();

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <ButtonGroup variant="outlined" size="small">
        <Tooltip title="Advance one tick">
          <Button onClick={tick} disabled={playing}>
            <TickIcon />
          </Button>
        </Tooltip>
        {playing ? (
          <Tooltip title="Stop simulation">
            <Button onClick={stop} color="error">
              <StopIcon />
            </Button>
          </Tooltip>
        ) : (
          <Tooltip title="Start simulation">
            <Button onClick={() => play(16)} color="success">
              <PlayIcon />
            </Button>
          </Tooltip>
        )}
      </ButtonGroup>
    </Box>
  );
};

export default SimulationControls;
