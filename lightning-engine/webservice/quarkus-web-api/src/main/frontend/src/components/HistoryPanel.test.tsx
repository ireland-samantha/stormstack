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


import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { renderWithProviders } from '../test/testUtils';
import { server } from '../test/mocks/server';
import HistoryPanel from './HistoryPanel';

describe('HistoryPanel', () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it('renders loading state initially', () => {
    // Set up a delayed response to show loading
    server.use(
      http.get('*/api/history', async () => {
        await new Promise(resolve => setTimeout(resolve, 100));
        return HttpResponse.json({
          totalSnapshots: 0,
          matchCount: 0,
          matchIds: []
        });
      })
    );

    renderWithProviders(<HistoryPanel />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders error state', async () => {
    server.use(
      http.get('*/api/history', () => {
        return new HttpResponse(null, { status: 500 });
      })
    );

    renderWithProviders(<HistoryPanel />);

    await waitFor(() => {
      expect(screen.getByText(/failed to load history summary/i)).toBeInTheDocument();
    });
  });

  it('renders summary data', async () => {
    server.use(
      http.get('*/api/history', () => {
        return HttpResponse.json({
          totalSnapshots: 150,
          matchCount: 3,
          matchIds: [1, 2, 3]
        });
      }),
      http.get('*/api/history/1', () => {
        return HttpResponse.json({
          matchId: 1,
          snapshotCount: 50,
          firstTick: 0,
          lastTick: 49
        });
      }),
      http.get('*/api/history/2', () => {
        return HttpResponse.json({
          matchId: 2,
          snapshotCount: 100,
          firstTick: 0,
          lastTick: 99
        });
      }),
      http.get('*/api/history/3', () => {
        return HttpResponse.json({
          matchId: 3,
          snapshotCount: 0,
          firstTick: 0,
          lastTick: 0
        });
      })
    );

    renderWithProviders(<HistoryPanel />);

    await waitFor(() => {
      expect(screen.getByText(/150 total snapshots/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/3 matches/i)).toBeInTheDocument();
  });

  it('renders empty state when no matches', async () => {
    server.use(
      http.get('*/api/history', () => {
        return HttpResponse.json({
          totalSnapshots: 0,
          matchCount: 0,
          matchIds: []
        });
      })
    );

    renderWithProviders(<HistoryPanel />);

    await waitFor(() => {
      expect(screen.getByText(/0 total snapshots/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/no matches found/i)).toBeInTheDocument();
  });
});
