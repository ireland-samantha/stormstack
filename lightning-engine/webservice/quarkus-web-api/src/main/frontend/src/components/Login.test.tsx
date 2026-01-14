/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/testUtils';
import Login from './Login';
import { server } from '../test/mocks/server';
import { http, HttpResponse } from 'msw';

describe('Login', () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it('renders login form', () => {
    renderWithProviders(<Login />);

    expect(screen.getByText('Lightning Engine')).toBeInTheDocument();
    expect(screen.getByText('Admin Dashboard')).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('disables submit button when fields are empty', () => {
    renderWithProviders(<Login />);

    const submitButton = screen.getByRole('button', { name: /sign in/i });
    expect(submitButton).toBeDisabled();
  });

  it('enables submit button when both fields are filled', () => {
    renderWithProviders(<Login />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });

    const submitButton = screen.getByRole('button', { name: /sign in/i });
    expect(submitButton).not.toBeDisabled();
  });

  it('updates auth state after successful login', async () => {
    const { store } = renderWithProviders(<Login />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'admin' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(store.getState().auth.isAuthenticated).toBe(true);
    });
  });

  it('displays error message on login failure', async () => {
    // Override handler to return error
    server.use(
      http.post('/api/auth/login', () => {
        return HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 });
      })
    );

    renderWithProviders(<Login />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText(/login failed/i)).toBeInTheDocument();
    });
  });

  it('shows loading state during login', async () => {
    // Add delay to handler
    server.use(
      http.post('/api/auth/login', async () => {
        await new Promise(resolve => setTimeout(resolve, 500));
        return HttpResponse.json({ token: 'test-token' });
      })
    );

    renderWithProviders(<Login />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });
});
