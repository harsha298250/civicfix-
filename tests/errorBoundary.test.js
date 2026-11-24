import { test, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

describe('Phase 8 — ErrorBoundary Component & Reliability Tests', () => {
  const rootDir = process.cwd();
  const errorBoundaryPath = path.join(rootDir, 'src', 'components', 'common', 'ErrorBoundary.jsx');
  const mainPath = path.join(rootDir, 'src', 'main.jsx');

  it('1. ErrorBoundary.jsx exists and defines class component lifecycle methods', () => {
    assert.ok(fs.existsSync(errorBoundaryPath), 'ErrorBoundary.jsx must exist');
    const content = fs.readFileSync(errorBoundaryPath, 'utf-8');

    assert.ok(content.includes('class ErrorBoundary extends React.Component'), 'Must be a React class component');
    assert.ok(content.includes('getDerivedStateFromError(error)'), 'Must implement getDerivedStateFromError');
    assert.ok(content.includes('componentDidCatch(error, errorInfo)'), 'Must implement componentDidCatch');
    assert.ok(content.includes('{ hasError: true, error }'), 'Must update hasError state on caught error');
  });

  it('2. ErrorBoundary implements WCAG 2.1 AA alert role and accessible error messaging', () => {
    const content = fs.readFileSync(errorBoundaryPath, 'utf-8');

    assert.ok(content.includes('role="alert"'), 'Must declare role="alert" for screen readers');
    assert.ok(content.includes('aria-live="assertive"'), 'Must declare aria-live="assertive"');
    assert.ok(content.includes('Something went wrong'), 'Must present accessible user-friendly headline');
  });

  it('3. ErrorBoundary provides recovery actions (reload and reset navigation)', () => {
    const content = fs.readFileSync(errorBoundaryPath, 'utf-8');

    assert.ok(content.includes('window.location.reload()'), 'Must provide page reload recovery');
    assert.ok(content.includes("window.location.href = '/'"), 'Must provide home navigation fallback');
    assert.ok(content.includes('Reload Page'), 'Must provide visible reload button');
    assert.ok(content.includes('Go to Home'), 'Must provide visible go to home button');
  });

  it('4. ErrorBoundary does not expose raw stack trace or internal paths in user UI', () => {
    const content = fs.readFileSync(errorBoundaryPath, 'utf-8');

    assert.ok(!content.includes('{this.state.error?.stack}'), 'Must not render raw error stack in DOM');
    assert.ok(!content.includes('{error?.stack}'), 'Must not render raw error stack in DOM');
  });

  it('5. main.jsx wraps root application in ErrorBoundary', () => {
    assert.ok(fs.existsSync(mainPath), 'main.jsx must exist');
    const content = fs.readFileSync(mainPath, 'utf-8');

    assert.ok(content.includes('ErrorBoundary') && content.includes('./components/common/ErrorBoundary'), 'Must import ErrorBoundary');
    assert.ok(content.includes('<ErrorBoundary>'), 'Must wrap application tree with <ErrorBoundary>');
    assert.ok(content.includes('</ErrorBoundary>'), 'Must close </ErrorBoundary>');
  });
});
