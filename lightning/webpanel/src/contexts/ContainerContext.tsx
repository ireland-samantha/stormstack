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

/**
 * Redux-backed compatibility layer for ContainerContext.
 * This hook uses Redux + RTK Query internally but exposes the same API
 * as the original ContainerContext for backward compatibility.
 */

import { useCallback, useEffect } from "react";
import { ContainerData, MatchData } from "../services/api";
import {
    useGetContainerMatchesQuery, useGetContainersQuery
} from "../store/api/apiSlice";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import {
    selectSelectedContainerId,
    selectSelectedMatchId,
    setSelectedContainerId,
    setSelectedMatchId
} from "../store/slices/uiSlice";

interface ContainerContextType {
  containers: ContainerData[];
  selectedContainer: ContainerData | null;
  selectedContainerId: number | null;
  matches: MatchData[];
  selectedMatch: MatchData | null;
  selectedMatchId: number | null;
  loading: boolean;
  error: string | null;

  // Actions
  selectContainer: (containerId: number | null) => void;
  selectMatch: (matchId: number | null) => void;
  refreshContainers: () => Promise<void>;
  refreshMatches: () => Promise<void>;
  refreshAll: () => Promise<void>;
}

/**
 * Hook that provides the same interface as the old useContainerContext,
 * but backed by Redux and RTK Query.
 */
export const useContainerContext = (): ContainerContextType => {
  const dispatch = useAppDispatch();

  // Redux state
  const selectedContainerId = useAppSelector(selectSelectedContainerId);
  const selectedMatchId = useAppSelector(selectSelectedMatchId);

  // RTK Query - auto fetches containers
  const {
    data: containers = [],
    isLoading: isContainersLoading,
    isError: isContainersError,
    error: containersError,
    refetch: refetchContainers,
  } = useGetContainersQuery();

  // RTK Query - auto fetches matches when container is selected
  const {
    data: matches = [],
    isLoading: isMatchesLoading,
    isError: isMatchesError,
    error: matchesError,
    refetch: refetchMatches,
  } = useGetContainerMatchesQuery(selectedContainerId!, {
    skip: selectedContainerId === null,
  });

  // Derived state
  const selectedContainer =
    containers.find((c) => c.id === selectedContainerId) || null;
  const selectedMatch = matches.find((m) => m.id === selectedMatchId) || null;
  const loading = isContainersLoading || isMatchesLoading;

  // Error handling
  const error = isContainersError
    ? containersError && "data" in containersError
      ? String(
          (containersError.data as { message?: string })?.message ||
            "Failed to fetch containers",
        )
      : "Failed to fetch containers"
    : isMatchesError
      ? matchesError && "data" in matchesError
        ? String(
            (matchesError.data as { message?: string })?.message ||
              "Failed to fetch matches",
          )
        : "Failed to fetch matches"
      : null;

  // Auto-select first container if none selected
  useEffect(() => {
    if (selectedContainerId === null && containers.length > 0) {
      dispatch(setSelectedContainerId(containers[0].id));
    }
  }, [containers, selectedContainerId, dispatch]);

  // Auto-select first match if none selected
  useEffect(() => {
    if (selectedMatchId === null && matches.length > 0) {
      dispatch(setSelectedMatchId(matches[0].id));
    }
  }, [matches, selectedMatchId, dispatch]);

  // Action handlers
  const selectContainer = useCallback(
    (containerId: number | null) => {
      dispatch(setSelectedContainerId(containerId));
      dispatch(setSelectedMatchId(null)); // Reset match when container changes
    },
    [dispatch],
  );

  const selectMatch = useCallback(
    (matchId: number | null) => {
      dispatch(setSelectedMatchId(matchId));
    },
    [dispatch],
  );

  const refreshContainers = useCallback(async () => {
    await refetchContainers();
  }, [refetchContainers]);

  const refreshMatches = useCallback(async () => {
    if (selectedContainerId !== null) {
      await refetchMatches();
    }
  }, [selectedContainerId, refetchMatches]);

  const refreshAll = useCallback(async () => {
    await refetchContainers();
    if (selectedContainerId !== null) {
      await refetchMatches();
    }
  }, [refetchContainers, refetchMatches, selectedContainerId]);

  return {
    containers,
    selectedContainer,
    selectedContainerId,
    matches,
    selectedMatch,
    selectedMatchId,
    loading,
    error,
    selectContainer,
    selectMatch,
    refreshContainers,
    refreshMatches,
    refreshAll,
  };
};

/**
 * @deprecated ContainerProvider is no longer needed since Redux is the source of truth.
 * Keep this for backward compatibility - it just renders children.
 */
interface ContainerProviderProps {
  children: React.ReactNode;
}

export const ContainerProvider: React.FC<ContainerProviderProps> = ({
  children,
}) => {
  return <>{children}</>;
};

export default { useContainerContext, ContainerProvider };
