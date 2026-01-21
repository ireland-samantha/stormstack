/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { CssBaseline, ThemeProvider } from "@mui/material";
import { render, RenderOptions } from "@testing-library/react";
import { PropsWithChildren, ReactElement } from "react";
import { Provider } from "react-redux";
import type { PanelType } from "../store/slices/uiSlice";
import { AppStore, RootState, setupStore } from "../store/store";
import { theme } from "../theme";

// Type for the ui state
interface UiState {
  selectedContainerId: number | null;
  selectedMatchId: number | null;
  activePanel: PanelType;
  sidebarOpen: boolean;
  containerMenuOpen: boolean;
  adminMenuOpen: boolean;
  iamMenuOpen: boolean;
}

// Default UI state for tests
const defaultUiState: UiState = {
  selectedContainerId: null,
  selectedMatchId: null,
  activePanel: "dashboard",
  sidebarOpen: true,
  containerMenuOpen: true,
  adminMenuOpen: false,
  iamMenuOpen: false,
};

// Type for partial preloaded state that allows partial ui state
interface PartialPreloadedState {
  ui?: Partial<UiState>;
  auth?: RootState["auth"];
  api?: RootState["api"];
}

// Extended render options for Redux testing
interface ExtendedRenderOptions extends Omit<RenderOptions, "queries"> {
  preloadedState?: PartialPreloadedState;
  store?: AppStore;
}

/**
 * Render with all providers including Redux store.
 * Creates a fresh store per test to prevent state leakage.
 */
export function renderWithProviders(
  ui: ReactElement,
  { preloadedState = {}, store, ...renderOptions }: ExtendedRenderOptions = {},
) {
  // Merge partial ui state with defaults
  const mergedState: Partial<RootState> = {
    ...preloadedState,
    ui: preloadedState.ui
      ? { ...defaultUiState, ...preloadedState.ui }
      : undefined,
  };

  // Remove undefined values
  if (!mergedState.ui) {
    delete mergedState.ui;
  }

  const storeToUse = store ?? setupStore(mergedState);

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <Provider store={storeToUse}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          {children}
        </ThemeProvider>
      </Provider>
    );
  }

  return {
    store: storeToUse,
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
  };
}

// Simple render without Redux (for components that don't need it)
const AllTheProviders = ({ children }: PropsWithChildren) => {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  );
};

const customRender = (
  ui: ReactElement,
  options?: Omit<RenderOptions, "wrapper">,
) => render(ui, { wrapper: AllTheProviders, ...options });

// Re-export everything from testing-library
export * from "@testing-library/react";
export { customRender as render };
