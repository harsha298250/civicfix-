import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';

describe('Supervisor Authentication, Role Isolation & Workspace Routing Tests', () => {
  const rootDir = process.cwd();

  async function loadRoleRouteWithUser(currentUser) {
    const outFile = path.join(rootDir, 'node_modules', '.cache', `role-route-${currentUser?.role || 'anon'}.mjs`);
    fs.mkdirSync(path.dirname(outFile), { recursive: true });

    const stubPlugin = {
      name: 'stub-role-route',
      setup(build) {
        build.onResolve({ filter: /context\/AppContext$/ }, () => ({
          path: 'app-ctx-stub',
          namespace: 'stub'
        }));
        build.onLoad({ filter: /^app-ctx-stub$/, namespace: 'stub' }, () => ({
          contents: `export const useApp = () => ({ currentUser: ${JSON.stringify(currentUser)}, showToast: () => {} });`,
          loader: 'js'
        }));
      }
    };

    await esbuild.build({
      entryPoints: [path.join(rootDir, 'src', 'components', 'common', 'RoleRoute.jsx')],
      outfile: outFile,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react', 'react-dom', 'react-router-dom'],
      plugins: [stubPlugin],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });

    const mod = await import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);
    return mod.RoleRoute;
  }

  it('1. Supervisor can access Supervisor workspace routes and SupervisorLayout links only to /supervisor/* routes', () => {
    const appSrc = fs.readFileSync(path.join(rootDir, 'src', 'App.jsx'), 'utf-8');
    const layoutSrc = fs.readFileSync(path.join(rootDir, 'src', 'components', 'layout', 'SupervisorLayout.jsx'), 'utf-8');

    assert.ok(appSrc.includes("allowedRoles={['SUPERVISOR', 'ADMIN']}"));
    assert.ok(appSrc.includes('<Route path="escalations"'));
    assert.ok(appSrc.includes('<Route path="analytics"'));
    assert.ok(appSrc.includes('<Route path="map"'));
    assert.ok(appSrc.includes('<Route path="notifications"'));

    assert.ok(layoutSrc.includes("'/supervisor/escalations'"));
    assert.ok(layoutSrc.includes("'/supervisor/analytics'"));
    assert.ok(layoutSrc.includes("'/supervisor/map'"));
    assert.ok(layoutSrc.includes("'/supervisor/notifications'"));
    assert.ok(!layoutSrc.includes("'/officer/"), 'SupervisorLayout must not link to /officer/* routes');
  });

  it('2. RoleRoute enforces strict workspace isolation across SUPERVISOR, ADMIN, OFFICER, WORKER, and CITIZEN', async () => {
    const SupervisorRoleRoute = await loadRoleRouteWithUser({ id: '3', role: 'SUPERVISOR' });
    const OfficerRoleRoute = await loadRoleRouteWithUser({ id: '2', role: 'OFFICER' });
    const WorkerRoleRoute = await loadRoleRouteWithUser({ id: '4', role: 'WORKER' });
    const CitizenRoleRoute = await loadRoleRouteWithUser({ id: '5', role: 'CITIZEN' });

    const renderGuard = (Guard, allowedRoles) =>
      renderToString(
        React.createElement(
          MemoryRouter,
          { initialEntries: ['/protected'] },
          React.createElement(
            Guard,
            { allowedRoles },
            React.createElement('div', null, 'AUTHORIZED_CONTENT')
          )
        )
      );

    // Supervisor can access supervisor routes, but NOT admin-only or officer-only routes
    assert.ok(renderGuard(SupervisorRoleRoute, ['SUPERVISOR', 'ADMIN']).includes('AUTHORIZED_CONTENT'));
    assert.ok(!renderGuard(SupervisorRoleRoute, ['ADMIN']).includes('AUTHORIZED_CONTENT'));
    assert.ok(!renderGuard(SupervisorRoleRoute, ['OFFICER', 'ADMIN']).includes('AUTHORIZED_CONTENT'));

    // Officer, Worker, Citizen cannot access supervisor-only routes
    assert.ok(!renderGuard(OfficerRoleRoute, ['SUPERVISOR', 'ADMIN']).includes('AUTHORIZED_CONTENT'));
    assert.ok(!renderGuard(WorkerRoleRoute, ['SUPERVISOR', 'ADMIN']).includes('AUTHORIZED_CONTENT'));
    assert.ok(!renderGuard(CitizenRoleRoute, ['SUPERVISOR', 'ADMIN']).includes('AUTHORIZED_CONTENT'));
    assert.ok(!renderGuard(CitizenRoleRoute, ['OFFICER', 'ADMIN']).includes('AUTHORIZED_CONTENT'));
  });

  it('3. LoginPage routes strictly by backend-returned user.role regardless of selectedRole tab', () => {
    const loginSrc = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'public', 'LoginPage.jsx'), 'utf-8');
    assert.ok(loginSrc.includes("else if (user.role === 'SUPERVISOR') navigate('/supervisor/escalations');"));
    assert.ok(!loginSrc.includes("selectedRole === 'CITIZEN') navigate('/citizen/dashboard')"));
    assert.ok(loginSrc.includes('Municipal staff account detected'));
  });
});
