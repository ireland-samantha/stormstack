/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from '../store';
import { apiSlice } from '../api/apiSlice';

interface AuthState {
  isAuthenticated: boolean;
  token: string | null;
  username: string | null;
}

const initialState: AuthState = {
  isAuthenticated: !!localStorage.getItem('authToken'),
  token: localStorage.getItem('authToken'),
  username: localStorage.getItem('username'),
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, action: PayloadAction<{ token: string; username: string }>) => {
      state.isAuthenticated = true;
      state.token = action.payload.token;
      state.username = action.payload.username;
      localStorage.setItem('authToken', action.payload.token);
      localStorage.setItem('username', action.payload.username);
    },
    logout: (state) => {
      state.isAuthenticated = false;
      state.token = null;
      state.username = null;
      localStorage.removeItem('authToken');
      localStorage.removeItem('username');
    },
  },
  extraReducers: (builder) => {
    builder.addMatcher(
      apiSlice.endpoints.login.matchFulfilled,
      (state, { payload, meta }) => {
        state.isAuthenticated = true;
        state.token = payload.token;
        state.username = meta.arg.originalArgs.username;
        localStorage.setItem('authToken', payload.token);
        localStorage.setItem('username', meta.arg.originalArgs.username);
      }
    );
  },
});

export const { setCredentials, logout } = authSlice.actions;

// Selectors
export const selectIsAuthenticated = (state: RootState) => state.auth.isAuthenticated;
export const selectToken = (state: RootState) => state.auth.token;
export const selectUsername = (state: RootState) => state.auth.username;

export default authSlice.reducer;
