/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/testUtils';
import ResourcesPanel from './ResourcesPanel';

// Test with a selected container (container-scoped resources)
const withSelectedContainer = {
  preloadedState: {
    ui: { selectedContainerId: 1 },
  },
};

describe('ResourcesPanel', () => {
  it('renders the panel title', async () => {
    renderWithProviders(<ResourcesPanel />, withSelectedContainer);

    await waitFor(() => {
      expect(screen.getByText('Container Resources')).toBeInTheDocument();
    });
  });

  it('shows no container message when none selected', async () => {
    renderWithProviders(<ResourcesPanel />);

    await waitFor(() => {
      expect(screen.getByText('No Container Selected')).toBeInTheDocument();
    });
  });

  it('displays resources from API', async () => {
    renderWithProviders(<ResourcesPanel />, withSelectedContainer);

    await waitFor(() => {
      // Resource names appear in both table and chip list
      expect(screen.getAllByText('sprite.png').length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText('sound.wav').length).toBeGreaterThan(0);
  });

  it('shows resource IDs', async () => {
    renderWithProviders(<ResourcesPanel />, withSelectedContainer);

    await waitFor(() => {
      expect(screen.getByText('res-1')).toBeInTheDocument();
    });
    expect(screen.getByText('res-2')).toBeInTheDocument();
  });

  it('shows mime type for resources', async () => {
    renderWithProviders(<ResourcesPanel />, withSelectedContainer);

    await waitFor(() => {
      expect(screen.getByText('image/png')).toBeInTheDocument();
    });
    expect(screen.getByText('audio/wav')).toBeInTheDocument();
  });
});
