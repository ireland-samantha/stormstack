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

import { useState, useEffect } from 'react';
import {
  Box, TextField,
  FormControlLabel, Switch, Typography, Chip, Autocomplete
} from '@mui/material';
import { CommandParameter } from '../services/api';
import { useContainerContext } from '../contexts/ContainerContext';
import { useGetPlayersInContainerQuery } from '../store/api/apiSlice';

export interface CommandFormProps {
  parameters: CommandParameter[];
  values: Record<string, unknown>;
  onChange: (values: Record<string, unknown>) => void;
  disabled?: boolean;
}

type FieldType = 'matchId' | 'playerId' | 'entityId' | 'number' | 'boolean' | 'string' | 'array' | 'object';

/**
 * Detects the field type based on parameter name and type.
 */
export function detectFieldType(param: CommandParameter): FieldType {
  const nameLower = param.name.toLowerCase();
  const typeLower = param.type.toLowerCase();

  // Check for ID fields based on name
  if (nameLower === 'matchid' || nameLower === 'match_id' || nameLower === 'match') {
    return 'matchId';
  }
  if (nameLower === 'playerid' || nameLower === 'player_id' || nameLower === 'player') {
    return 'playerId';
  }
  if (nameLower === 'entityid' || nameLower === 'entity_id' || nameLower === 'entity') {
    return 'entityId';
  }

  // Check collection types first (before primitives, since List<int> contains 'int')
  if (typeLower.includes('list') || typeLower.includes('array') || typeLower.includes('[]')) {
    return 'array';
  }
  if (typeLower.includes('map') || typeLower.includes('object')) {
    return 'object';
  }

  // Check type for primitives
  if (typeLower.includes('bool')) {
    return 'boolean';
  }
  if (typeLower.includes('int') || typeLower.includes('long') ||
      typeLower.includes('double') || typeLower.includes('float') ||
      typeLower.includes('number') || typeLower.includes('short') || typeLower.includes('byte')) {
    return 'number';
  }

  return 'string';
}

/**
 * Gets the default value for a field type.
 */
export function getDefaultValue(fieldType: FieldType): unknown {
  switch (fieldType) {
    case 'matchId':
    case 'playerId':
    case 'entityId':
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'array':
      return [];
    case 'object':
      return {};
    default:
      return '';
  }
}

/**
 * Builds initial form values from parameters.
 */
export function buildInitialValues(parameters: CommandParameter[]): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  for (const param of parameters) {
    const fieldType = detectFieldType(param);
    values[param.name] = getDefaultValue(fieldType);
  }
  return values;
}

/**
 * Form component for command parameters with smart field detection.
 * Renders appropriate input types based on parameter names and types.
 */
const CommandForm: React.FC<CommandFormProps> = ({ parameters, values, onChange, disabled }) => {
  const { matches, selectedContainerId } = useContainerContext();
  const [entities] = useState<number[]>([]);

  // Fetch players if we have a playerId field
  const hasPlayerIdField = parameters.some(p => detectFieldType(p) === 'playerId');
  const hasEntityIdField = parameters.some(p => detectFieldType(p) === 'entityId');

  // Use RTK Query to fetch players when needed
  const { data: players = [], isLoading: loadingPlayers } = useGetPlayersInContainerQuery(
    selectedContainerId!,
    { skip: !hasPlayerIdField || !selectedContainerId }
  );

  // For entity IDs, we'd need a snapshot or entity list endpoint
  // For now, provide a text field that accepts numbers
  useEffect(() => {
    if (hasEntityIdField) {
        // todo: fetch from snapshot
    }
  }, [hasEntityIdField]);

  const handleFieldChange = (name: string, value: unknown) => {
    onChange({ ...values, [name]: value });
  };

  const renderField = (param: CommandParameter) => {
    const fieldType = detectFieldType(param);
    const value = values[param.name];
    const label = param.description || param.name;
    const required = param.required;

    switch (fieldType) {
      case 'matchId':
        return (
          <Autocomplete
            key={param.name}
            options={matches}
            getOptionLabel={(m) => `Match ${m.id}`}
            value={matches.find(m => m.id === value) || null}
            onChange={(_, v) => handleFieldChange(param.name, v?.id ?? 0)}
            disabled={disabled}
            renderInput={(params) => (
              <TextField
                {...params}
                label={label}
                required={required}
                helperText="Select a match from the container"
              />
            )}
            renderOption={(props, option) => (
              <li {...props} key={option.id}>
                <Box>
                  <Typography variant="body2">Match {option.id}</Typography>
                  {option.modules && option.modules.length > 0 && (
                    <Typography variant="caption" color="text.secondary">
                      Modules: {option.modules.join(', ')}
                    </Typography>
                  )}
                </Box>
              </li>
            )}
          />
        );

      case 'playerId':
        return (
          <Autocomplete
            key={param.name}
            options={players}
            getOptionLabel={(p) => p.name ? `${p.name} (ID: ${p.id})` : `Player ${p.id}`}
            value={players.find(p => p.id === value) || null}
            onChange={(_, v) => handleFieldChange(param.name, v?.id ?? 0)}
            disabled={disabled || loadingPlayers}
            loading={loadingPlayers}
            renderInput={(params) => (
              <TextField
                {...params}
                label={label}
                required={required}
                helperText="Select a player"
              />
            )}
            renderOption={(props, option) => (
              <li {...props} key={option.id}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="body2">
                    {option.name || `Player ${option.id}`}
                  </Typography>
                  <Chip label={`ID: ${option.id}`} size="small" variant="outlined" />
                </Box>
              </li>
            )}
          />
        );

      case 'entityId':
        return (
          <Autocomplete
            key={param.name}
            options={entities}
            getOptionLabel={(e) => `Entity ${e}`}
            value={typeof value === 'number' && entities.includes(value) ? value : null}
            onChange={(_, v) => handleFieldChange(param.name, v ?? 0)}
            disabled={disabled}
            freeSolo
            renderInput={(params) => (
              <TextField
                {...params}
                label={label}
                required={required}
                type="number"
                helperText="Select or enter an entity ID"
                onChange={(e) => {
                  const num = parseInt(e.target.value, 10);
                  if (!isNaN(num)) {
                    handleFieldChange(param.name, num);
                  }
                }}
              />
            )}
          />
        );

      case 'boolean':
        return (
          <FormControlLabel
            key={param.name}
            control={
              <Switch
                checked={Boolean(value)}
                onChange={(e) => handleFieldChange(param.name, e.target.checked)}
                disabled={disabled}
              />
            }
            label={label}
          />
        );

      case 'number':
        return (
          <TextField
            key={param.name}
            label={label}
            type="number"
            value={value ?? 0}
            onChange={(e) => {
              const val = param.type.toLowerCase().includes('int') || param.type.toLowerCase().includes('long')
                ? parseInt(e.target.value, 10)
                : parseFloat(e.target.value);
              handleFieldChange(param.name, isNaN(val) ? 0 : val);
            }}
            required={required}
            disabled={disabled}
            fullWidth
            helperText={`Type: ${param.type}`}
          />
        );

      case 'array':
        return (
          <TextField
            key={param.name}
            label={label}
            value={JSON.stringify(value)}
            onChange={(e) => {
              try {
                const parsed = JSON.parse(e.target.value);
                if (Array.isArray(parsed)) {
                  handleFieldChange(param.name, parsed);
                }
              } catch {
                // Keep current value if invalid JSON
              }
            }}
            required={required}
            disabled={disabled}
            fullWidth
            multiline
            rows={2}
            helperText="Enter as JSON array, e.g., [1, 2, 3]"
            sx={{ fontFamily: 'monospace' }}
          />
        );

      case 'object':
        return (
          <TextField
            key={param.name}
            label={label}
            value={JSON.stringify(value)}
            onChange={(e) => {
              try {
                const parsed = JSON.parse(e.target.value);
                if (typeof parsed === 'object' && !Array.isArray(parsed)) {
                  handleFieldChange(param.name, parsed);
                }
              } catch {
                // Keep current value if invalid JSON
              }
            }}
            required={required}
            disabled={disabled}
            fullWidth
            multiline
            rows={2}
            helperText="Enter as JSON object, e.g., {}"
            sx={{ fontFamily: 'monospace' }}
          />
        );

      default:
        return (
          <TextField
            key={param.name}
            label={label}
            value={value ?? ''}
            onChange={(e) => handleFieldChange(param.name, e.target.value)}
            required={required}
            disabled={disabled}
            fullWidth
            helperText={param.type !== 'String' ? `Type: ${param.type}` : undefined}
          />
        );
    }
  };

  if (!parameters || parameters.length === 0) {
    return (
      <Typography color="text.secondary" sx={{ py: 2 }}>
        This command has no parameters.
      </Typography>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {parameters.map(renderField)}
    </Box>
  );
};

export default CommandForm;
