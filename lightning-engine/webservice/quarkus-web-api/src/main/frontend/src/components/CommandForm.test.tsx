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

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { render } from '../test/testUtils';
import CommandForm, { detectFieldType, getDefaultValue, buildInitialValues } from './CommandForm';
import { apiClient, CommandParameter } from '../services/api';

vi.mock('../services/api', () => ({
  apiClient: {
    getPlayers: vi.fn()
  }
}));

vi.mock('../contexts/ContainerContext', () => ({
  useContainerContext: () => ({
    containers: [{ id: 1, name: 'test-container', status: 'RUNNING' }],
    selectedContainer: { id: 1, name: 'test-container', status: 'RUNNING' },
    selectedContainerId: 1,
    matches: [
      { id: 1, enabledModules: ['EntityModule'] },
      { id: 2, enabledModules: ['MovementModule'] }
    ],
    selectedMatch: null,
    selectedMatchId: null,
    loading: false,
    error: null,
    selectContainer: vi.fn(),
    selectMatch: vi.fn(),
    refreshContainers: vi.fn(),
    refreshMatches: vi.fn(),
    refreshAll: vi.fn()
  })
}));

describe('detectFieldType', () => {
  it('detects matchId field by name', () => {
    expect(detectFieldType({ name: 'matchId', type: 'long', required: true })).toBe('matchId');
    expect(detectFieldType({ name: 'match_id', type: 'long', required: true })).toBe('matchId');
    expect(detectFieldType({ name: 'match', type: 'long', required: true })).toBe('matchId');
    expect(detectFieldType({ name: 'MATCHID', type: 'long', required: true })).toBe('matchId');
  });

  it('detects playerId field by name', () => {
    expect(detectFieldType({ name: 'playerId', type: 'long', required: true })).toBe('playerId');
    expect(detectFieldType({ name: 'player_id', type: 'long', required: true })).toBe('playerId');
    expect(detectFieldType({ name: 'player', type: 'long', required: true })).toBe('playerId');
  });

  it('detects entityId field by name', () => {
    expect(detectFieldType({ name: 'entityId', type: 'long', required: true })).toBe('entityId');
    expect(detectFieldType({ name: 'entity_id', type: 'long', required: true })).toBe('entityId');
    expect(detectFieldType({ name: 'entity', type: 'long', required: true })).toBe('entityId');
  });

  it('detects boolean type', () => {
    expect(detectFieldType({ name: 'active', type: 'boolean', required: true })).toBe('boolean');
    expect(detectFieldType({ name: 'enabled', type: 'Boolean', required: true })).toBe('boolean');
  });

  it('detects number type', () => {
    expect(detectFieldType({ name: 'x', type: 'int', required: true })).toBe('number');
    expect(detectFieldType({ name: 'y', type: 'Integer', required: true })).toBe('number');
    expect(detectFieldType({ name: 'z', type: 'long', required: true })).toBe('number');
    expect(detectFieldType({ name: 'speed', type: 'double', required: true })).toBe('number');
    expect(detectFieldType({ name: 'rate', type: 'Float', required: true })).toBe('number');
    expect(detectFieldType({ name: 'count', type: 'short', required: true })).toBe('number');
    expect(detectFieldType({ name: 'value', type: 'byte', required: true })).toBe('number');
  });

  it('detects array type', () => {
    expect(detectFieldType({ name: 'items', type: 'List<String>', required: true })).toBe('array');
    expect(detectFieldType({ name: 'ids', type: 'int[]', required: true })).toBe('array');
    expect(detectFieldType({ name: 'values', type: 'Array', required: true })).toBe('array');
  });

  it('detects object type', () => {
    expect(detectFieldType({ name: 'data', type: 'Map<String, Object>', required: true })).toBe('object');
    expect(detectFieldType({ name: 'config', type: 'Object', required: true })).toBe('object');
  });

  it('defaults to string for unknown types', () => {
    expect(detectFieldType({ name: 'name', type: 'String', required: true })).toBe('string');
    expect(detectFieldType({ name: 'custom', type: 'CustomType', required: true })).toBe('string');
  });
});

describe('getDefaultValue', () => {
  it('returns 0 for matchId', () => {
    expect(getDefaultValue('matchId')).toBe(0);
  });

  it('returns 0 for playerId', () => {
    expect(getDefaultValue('playerId')).toBe(0);
  });

  it('returns 0 for entityId', () => {
    expect(getDefaultValue('entityId')).toBe(0);
  });

  it('returns 0 for number', () => {
    expect(getDefaultValue('number')).toBe(0);
  });

  it('returns false for boolean', () => {
    expect(getDefaultValue('boolean')).toBe(false);
  });

  it('returns empty array for array', () => {
    expect(getDefaultValue('array')).toEqual([]);
  });

  it('returns empty object for object', () => {
    expect(getDefaultValue('object')).toEqual({});
  });

  it('returns empty string for string', () => {
    expect(getDefaultValue('string')).toBe('');
  });
});

describe('buildInitialValues', () => {
  it('builds values for all parameters', () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true },
      { name: 'name', type: 'String', required: true },
      { name: 'active', type: 'boolean', required: false }
    ];

    const values = buildInitialValues(params);

    expect(values).toEqual({
      x: 0,
      name: '',
      active: false
    });
  });

  it('returns empty object for empty parameters', () => {
    expect(buildInitialValues([])).toEqual({});
  });

  it('handles special ID fields', () => {
    const params: CommandParameter[] = [
      { name: 'matchId', type: 'long', required: true },
      { name: 'playerId', type: 'long', required: true },
      { name: 'entityId', type: 'long', required: true }
    ];

    const values = buildInitialValues(params);

    expect(values).toEqual({
      matchId: 0,
      playerId: 0,
      entityId: 0
    });
  });
});

describe('CommandForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (apiClient.getPlayers as ReturnType<typeof vi.fn>).mockResolvedValue([]);
  });

  it('shows no parameters message when empty', () => {
    const onChange = vi.fn();
    render(<CommandForm parameters={[]} values={{}} onChange={onChange} />);

    expect(screen.getByText(/no parameters/i)).toBeInTheDocument();
  });

  it('renders text field for string parameters', () => {
    const params: CommandParameter[] = [
      { name: 'name', type: 'String', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ name: '' }} onChange={onChange} />);

    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
  });

  it('renders number field for numeric parameters', () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ x: 0 }} onChange={onChange} />);

    const input = screen.getByLabelText(/x/i);
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'number');
  });

  it('renders switch for boolean parameters', () => {
    const params: CommandParameter[] = [
      { name: 'active', type: 'boolean', required: false }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ active: false }} onChange={onChange} />);

    // MUI Switch uses an internal checkbox, find by text label
    expect(screen.getByText(/active/i)).toBeInTheDocument();
  });

  it('renders autocomplete for matchId field', () => {
    const params: CommandParameter[] = [
      { name: 'matchId', type: 'long', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ matchId: 0 }} onChange={onChange} />);

    // Should render autocomplete with match options
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText(/select a match/i)).toBeInTheDocument();
  });

  it('calls onChange when text field value changes', async () => {
    const params: CommandParameter[] = [
      { name: 'name', type: 'String', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ name: '' }} onChange={onChange} />);

    const input = screen.getByLabelText(/name/i);
    fireEvent.change(input, { target: { value: 'test' } });

    expect(onChange).toHaveBeenCalledWith({ name: 'test' });
  });

  it('calls onChange when number field value changes', async () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ x: 0 }} onChange={onChange} />);

    const input = screen.getByLabelText(/x/i);
    fireEvent.change(input, { target: { value: '42' } });

    expect(onChange).toHaveBeenCalledWith({ x: 42 });
  });

  it('calls onChange when boolean field value changes', async () => {
    const params: CommandParameter[] = [
      { name: 'active', type: 'boolean', required: false }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ active: false }} onChange={onChange} />);

    // MUI Switch - find the label and click it to toggle
    const switchLabel = screen.getByText(/active/i);
    fireEvent.click(switchLabel);

    expect(onChange).toHaveBeenCalledWith({ active: true });
  });

  it('renders multiple fields correctly', () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true },
      { name: 'y', type: 'int', required: true },
      { name: 'name', type: 'String', required: true },
      { name: 'active', type: 'boolean', required: false }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ x: 0, y: 0, name: '', active: false }} onChange={onChange} />);

    // Find all number inputs
    const numberInputs = screen.getAllByRole('spinbutton');
    expect(numberInputs).toHaveLength(2);

    // Find string input
    const textInputs = screen.getAllByRole('textbox');
    expect(textInputs.length).toBeGreaterThanOrEqual(1);

    // Find boolean switch
    expect(screen.getByText(/active/i)).toBeInTheDocument();
  });

  it('disables fields when disabled prop is true', () => {
    const params: CommandParameter[] = [
      { name: 'name', type: 'String', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ name: '' }} onChange={onChange} disabled />);

    expect(screen.getByLabelText(/name/i)).toBeDisabled();
  });

  it('shows helper text with type for non-string fields', () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ x: 0 }} onChange={onChange} />);

    expect(screen.getByText(/type: int/i)).toBeInTheDocument();
  });

  it('renders textarea for array type parameters', () => {
    const params: CommandParameter[] = [
      { name: 'items', type: 'List<String>', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ items: [] }} onChange={onChange} />);

    expect(screen.getByLabelText(/items/i)).toBeInTheDocument();
    expect(screen.getByText(/enter as json array/i)).toBeInTheDocument();
  });

  it('renders textarea for object type parameters', () => {
    const params: CommandParameter[] = [
      { name: 'config', type: 'Object', required: true }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ config: {} }} onChange={onChange} />);

    expect(screen.getByLabelText(/config/i)).toBeInTheDocument();
    expect(screen.getByText(/enter as json object/i)).toBeInTheDocument();
  });

  it('fetches players when playerId field is present', async () => {
    const params: CommandParameter[] = [
      { name: 'playerId', type: 'long', required: true }
    ];
    const onChange = vi.fn();
    (apiClient.getPlayers as ReturnType<typeof vi.fn>).mockResolvedValue([
      { id: 1, name: 'Player 1' },
      { id: 2, name: 'Player 2' }
    ]);

    render(<CommandForm parameters={params} values={{ playerId: 0 }} onChange={onChange} />);

    await waitFor(() => {
      expect(apiClient.getPlayers).toHaveBeenCalled();
    });
  });

  it('uses description as label when provided', () => {
    const params: CommandParameter[] = [
      { name: 'x', type: 'int', required: true, description: 'X Coordinate' }
    ];
    const onChange = vi.fn();

    render(<CommandForm parameters={params} values={{ x: 0 }} onChange={onChange} />);

    expect(screen.getByLabelText(/x coordinate/i)).toBeInTheDocument();
  });
});
