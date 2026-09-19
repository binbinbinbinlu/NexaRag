import { defineConfig } from '@playwright/test';
export default defineConfig({ testDir:'./browser-tests', workers:1, use:{browserName:'chromium',headless:true}, reporter:'list' });
