/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/testUtils';
import SessionsPanel from './SessionsPanel';
import { server } from '../test/mocks/server';
import { http, HttpResponse, delay } from 'msw';

describe('SessionsPanel', () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it('renders the panel', async () => {
    renderWithProviders(<SessionsPanel />, {
      preloadedState: { ui: { selectedContainerId: 1, selectedMatchId: null, activePanel: 'sessions', sidebarOpen: true, containerMenuOpen: true, adminMenuOpen: false, iamMenuOpen: false } },
    });

    await waitFor(() => {
      expect(screen.getByText('Sessions')).toBeInTheDocument();
    });
  });

  it('shows loading state initially', () => {
    // Override handler to never resolve
    server.use(
      http.get('/api/containers/:id/sessions', async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      })
    );

    renderWithProviders(<SessionsPanel />, {
      preloadedState: { ui: { selectedContainerId: 1, selectedMatchId: null, activePanel: 'sessions', sidebarOpen: true, containerMenuOpen: true, adminMenuOpen: false, iamMenuOpen: false } },
    });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows empty state when no sessions', async () => {
    server.use(
      http.get('/api/containers/:id/sessions', async () => {
        await delay(50);
        return HttpResponse.json([]);
      })
    );

    renderWithProviders(<SessionsPanel />, {
      preloadedState: { ui: { selectedContainerId: 1, selectedMatchId: null, activePanel: 'sessions', sidebarOpen: true, containerMenuOpen: true, adminMenuOpen: false, iamMenuOpen: false } },
    });

    await waitFor(() => {
      expect(screen.getByText(/no sessions/i)).toBeInTheDocument();
    });
  });

  it('displays error on fetch failure', async () => {
    server.use(
      http.get('/api/containers/:id/sessions', async () => {
        await delay(50);
        return HttpResponse.json({ message: 'Failed to fetch' }, { status: 500 });
      })
    );

    renderWithProviders(<SessionsPanel />, {
      preloadedState: { ui: { selectedContainerId: 1, selectedMatchId: null, activePanel: 'sessions', sidebarOpen: true, containerMenuOpen: true, adminMenuOpen: false, iamMenuOpen: false } },
    });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });

  it('shows no container selected message when no container', async () => {
    renderWithProviders(<SessionsPanel />, {
      preloadedState: { ui: { selectedContainerId: null, selectedMatchId: null, activePanel: 'sessions', sidebarOpen: true, containerMenuOpen: true, adminMenuOpen: false, iamMenuOpen: false } },
    });

    expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
  });
});
