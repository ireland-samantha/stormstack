/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
