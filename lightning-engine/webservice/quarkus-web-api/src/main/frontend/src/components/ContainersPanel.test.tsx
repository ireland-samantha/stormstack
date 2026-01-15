/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { renderWithProviders } from '../test/testUtils';
import { server } from '../test/mocks/server';
import ContainersPanel from './ContainersPanel';

// Mock window.confirm
const mockConfirm = vi.spyOn(window, 'confirm');
mockConfirm.mockImplementation(() => true);

describe('ContainersPanel', () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it('renders loading state when fetching containers', () => {
    server.use(
      http.get('*/api/containers', async () => {
        await new Promise(resolve => setTimeout(resolve, 100));
        return HttpResponse.json([]);
      })
    );

    renderWithProviders(<ContainersPanel />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders empty state when no containers exist', async () => {
    server.use(
      http.get('*/api/containers', () => {
        return HttpResponse.json([]);
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByText(/no containers found/i)).toBeInTheDocument();
    });
  });

  it('renders container table when containers exist', async () => {
    server.use(
      http.get('*/api/containers', () => {
        return HttpResponse.json([
          {
            id: 1,
            name: 'game-server-1',
            status: 'RUNNING',
            currentTick: 100,
            autoAdvancing: false,
            autoAdvanceIntervalMs: 16,
            matchCount: 2,
            loadedModules: ['EntityModule']
          }
        ]);
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByText('game-server-1')).toBeInTheDocument();
    });
    expect(screen.getByText('100')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('opens create dialog and creates container', async () => {
    const user = userEvent.setup();
    let createCalled = false;

    server.use(
      http.get('*/api/containers', () => {
        return HttpResponse.json([]);
      }),
      http.post('*/api/containers', async ({ request }) => {
        createCalled = true;
        const body = await request.json() as { name: string };
        return HttpResponse.json({
          id: 1,
          name: body.name,
          status: 'CREATED',
          currentTick: 0,
          autoAdvancing: false,
          autoAdvanceIntervalMs: 16,
          matchCount: 0
        });
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /create container/i })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /create container/i }));

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText(/container name/i), 'test-container');
    await user.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(() => {
      expect(createCalled).toBe(true);
    });
  });

  it('starts container when start button is clicked', async () => {
    const user = userEvent.setup();
    let startCalled = false;

    server.use(
      http.get('*/api/containers', () => {
        return HttpResponse.json([
          {
            id: 1,
            name: 'test',
            status: 'CREATED',
            currentTick: 0,
            autoAdvancing: false,
            autoAdvanceIntervalMs: 16,
            matchCount: 0
          }
        ]);
      }),
      http.post('*/api/containers/1/start', () => {
        startCalled = true;
        return HttpResponse.json({
          id: 1,
          name: 'test',
          status: 'RUNNING',
          currentTick: 0,
          autoAdvancing: false,
          autoAdvanceIntervalMs: 16,
          matchCount: 0
        });
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByText('test')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /start/i }));

    await waitFor(() => {
      expect(startCalled).toBe(true);
    });
  });

  it('deletes container when delete button is clicked', async () => {
    const user = userEvent.setup();
    let deleteCalled = false;

    server.use(
      http.get('*/api/containers', () => {
        return HttpResponse.json([
          {
            id: 1,
            name: 'test',
            status: 'STOPPED',
            currentTick: 0,
            autoAdvancing: false,
            autoAdvanceIntervalMs: 16,
            matchCount: 0
          }
        ]);
      }),
      http.delete('*/api/containers/1', () => {
        deleteCalled = true;
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByText('test')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() => {
      expect(deleteCalled).toBe(true);
    });
  });

  it('renders error state when fetch fails', async () => {
    server.use(
      http.get('*/api/containers', () => {
        return new HttpResponse(null, { status: 500 });
      })
    );

    renderWithProviders(<ContainersPanel />);

    await waitFor(() => {
      expect(screen.getByText(/failed to fetch containers/i)).toBeInTheDocument();
    });
  });
});
