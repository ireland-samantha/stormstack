/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/testUtils';
import ModulesPanel from './ModulesPanel';

// Test with a selected container (container-scoped modules)
const withSelectedContainer = {
  preloadedState: {
    ui: { selectedContainerId: 1 },
  },
};

describe('ModulesPanel', () => {
  it('renders the panel title', async () => {
    renderWithProviders(<ModulesPanel />, withSelectedContainer);

    await waitFor(() => {
      expect(screen.getByText('Container Modules')).toBeInTheDocument();
    });
  });

  it('shows no container message when none selected', async () => {
    renderWithProviders(<ModulesPanel />);

    await waitFor(() => {
      expect(screen.getByText('No Container Selected')).toBeInTheDocument();
    });
  });

  it('displays module names when loaded', async () => {
    renderWithProviders(<ModulesPanel />, withSelectedContainer);

    await waitFor(() => {
      // Module names appear in both table and chip list
      expect(screen.getAllByText('EntityModule').length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText('RigidBodyModule').length).toBeGreaterThan(0);
  });

  it('has reload button', async () => {
    renderWithProviders(<ModulesPanel />, withSelectedContainer);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /reload modules/i })).toBeInTheDocument();
    });
  });

  it('shows module status', async () => {
    renderWithProviders(<ModulesPanel />, withSelectedContainer);

    await waitFor(() => {
      // Status column shows chips for each module
      const statusCells = screen.getAllByText('Installed');
      expect(statusCells.length).toBeGreaterThan(0);
    });
  });
});
