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


import { useState, useCallback } from 'react';
import apiClient, {
  MatchData,
  HistorySummary,
  MatchHistorySummary,
  SnapshotData,
  DeltaSnapshotData
} from '../services/api';

export function useMatches() {
  const [matches, setMatches] = useState<MatchData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchMatches = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiClient.getMatches();
      setMatches(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch matches');
    } finally {
      setLoading(false);
    }
  }, []);

  const createMatch = useCallback(async (modules: string[]) => {
    setLoading(true);
    setError(null);
    try {
      const match = await apiClient.createMatch(modules);
      setMatches(prev => [...prev, match]);
      return match;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create match');
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { matches, loading, error, fetchMatches, createMatch };
}

export function useSimulation() {
  const [playing, setPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tick = useCallback(async () => {
    try {
      await apiClient.tick();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to tick');
    }
  }, []);

  const play = useCallback(async (tickRateMs: number = 16) => {
    try {
      await apiClient.play(tickRateMs);
      setPlaying(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to play');
    }
  }, []);

  const stop = useCallback(async () => {
    try {
      await apiClient.stop();
      setPlaying(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to stop');
    }
  }, []);

  return { playing, error, tick, play, stop };
}

export function useHistory() {
  const [summary, setSummary] = useState<HistorySummary | null>(null);
  const [matchSummaries, setMatchSummaries] = useState<MatchHistorySummary[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotData[]>([]);
  const [delta, setDelta] = useState<DeltaSnapshotData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSummary = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiClient.getHistorySummary();
      setSummary(data);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch history summary');
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchMatchSummary = useCallback(async (matchId: number) => {
    try {
      const data = await apiClient.getMatchHistorySummary(matchId);
      setMatchSummaries(prev => {
        const filtered = prev.filter(s => s.matchId !== matchId);
        return [...filtered, data];
      });
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch match summary');
      return null;
    }
  }, []);

  const fetchSnapshots = useCallback(async (matchId: number, limit: number = 100) => {
    setLoading(true);
    try {
      const data = await apiClient.getHistorySnapshots(matchId, { limit });
      setSnapshots(data);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch snapshots');
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchDelta = useCallback(async (matchId: number, fromTick: number, toTick: number) => {
    try {
      const data = await apiClient.getDelta(matchId, fromTick, toTick);
      setDelta(data);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch delta');
      return null;
    }
  }, []);

  return {
    summary,
    matchSummaries,
    snapshots,
    delta,
    loading,
    error,
    fetchSummary,
    fetchMatchSummary,
    fetchSnapshots,
    fetchDelta
  };
}
