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


import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { render } from '../test/testUtils';
import HistoryPanel from './HistoryPanel';

// Mock the useHistory hook
vi.mock('../hooks/useApi', () => ({
  useHistory: vi.fn()
}));

import { useHistory } from '../hooks/useApi';

const mockUseHistory = useHistory as ReturnType<typeof vi.fn>;

describe('HistoryPanel', () => {
  const mockFetchSummary = vi.fn();
  const mockFetchMatchSummary = vi.fn();
  const mockFetchSnapshots = vi.fn();
  const mockFetchDelta = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockUseHistory.mockReturnValue({
      summary: null,
      matchSummaries: [],
      snapshots: [],
      delta: null,
      loading: false,
      error: null,
      fetchSummary: mockFetchSummary,
      fetchMatchSummary: mockFetchMatchSummary,
      fetchSnapshots: mockFetchSnapshots,
      fetchDelta: mockFetchDelta
    });
  });

  it('renders loading state initially', () => {
    mockUseHistory.mockReturnValue({
      summary: null,
      matchSummaries: [],
      snapshots: [],
      delta: null,
      loading: true,
      error: null,
      fetchSummary: mockFetchSummary,
      fetchMatchSummary: mockFetchMatchSummary,
      fetchSnapshots: mockFetchSnapshots,
      fetchDelta: mockFetchDelta
    });

    render(<HistoryPanel />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders error state', () => {
    mockUseHistory.mockReturnValue({
      summary: null,
      matchSummaries: [],
      snapshots: [],
      delta: null,
      loading: false,
      error: 'Failed to fetch',
      fetchSummary: mockFetchSummary,
      fetchMatchSummary: mockFetchMatchSummary,
      fetchSnapshots: mockFetchSnapshots,
      fetchDelta: mockFetchDelta
    });

    render(<HistoryPanel />);

    expect(screen.getByText(/failed to fetch/i)).toBeInTheDocument();
  });

  it('renders summary data', async () => {
    mockUseHistory.mockReturnValue({
      summary: {
        totalSnapshots: 150,
        matchCount: 3,
        matchIds: [1, 2, 3]
      },
      matchSummaries: [
        { matchId: 1, snapshotCount: 50, firstTick: 0, lastTick: 49 },
        { matchId: 2, snapshotCount: 100, firstTick: 0, lastTick: 99 }
      ],
      snapshots: [],
      delta: null,
      loading: false,
      error: null,
      fetchSummary: mockFetchSummary,
      fetchMatchSummary: mockFetchMatchSummary,
      fetchSnapshots: mockFetchSnapshots,
      fetchDelta: mockFetchDelta
    });

    render(<HistoryPanel />);

    await waitFor(() => {
      expect(screen.getByText(/150 total snapshots/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/3 matches/i)).toBeInTheDocument();
  });

  it('calls fetchSummary on mount', () => {
    render(<HistoryPanel />);

    expect(mockFetchSummary).toHaveBeenCalled();
  });
});
