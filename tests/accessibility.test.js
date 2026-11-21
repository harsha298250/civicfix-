import test, { describe, it } from 'node:test';
import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';

describe('Phase 2.2 — WCAG 2.1 AA Accessibility & Component Tests', () => {

  const rootDir = process.cwd();

  it('1. SkipToContent component defines accessible bypass link targeting #main-content', () => {
    const filePath = path.join(rootDir, 'src', 'components', 'common', 'SkipToContent.jsx');
    assert.ok(fs.existsSync(filePath), 'SkipToContent.jsx must exist');
    const content = fs.readFileSync(filePath, 'utf-8');

    assert.ok(content.includes('href={`#${targetId}`}'), 'Must link to target id');
    assert.ok(content.includes('sr-only focus:not-sr-only'), 'Must use sr-only with focus:not-sr-only for keyboard users');
    assert.ok(content.includes('target.focus()'), 'Must focus target element on click');
    assert.ok(content.includes('focus-visible:ring-2'), 'Must provide visible focus ring');
  });

  it('2. All six application layout shells mount <SkipToContent /> and define #main-content', () => {
    const layouts = [
      'PublicLayout.jsx',
      'CitizenLayout.jsx',
      'OfficerLayout.jsx',
      'WorkerLayout.jsx',
      'SupervisorLayout.jsx',
      'AdminLayout.jsx',
    ];

    for (const layout of layouts) {
      const filePath = path.join(rootDir, 'src', 'components', 'layout', layout);
      assert.ok(fs.existsSync(filePath), `${layout} must exist`);
      const content = fs.readFileSync(filePath, 'utf-8');

      assert.ok(content.includes('<SkipToContent'), `${layout} must mount <SkipToContent />`);
      assert.ok(content.includes('id="main-content"'), `${layout} must define main content area with id="main-content"`);
      assert.ok(content.includes('tabIndex="-1"'), `${layout} main content area must have tabIndex="-1" for focusability`);
    }
  });

  it('3. ModalDialog implements WCAG 2.1 AA keyboard trapping, Escape dismissal, and ARIA roles', () => {
    const filePath = path.join(rootDir, 'src', 'components', 'common', 'ModalDialog.jsx');
    assert.ok(fs.existsSync(filePath), 'ModalDialog.jsx must exist');
    const content = fs.readFileSync(filePath, 'utf-8');

    assert.ok(content.includes('role="dialog"'), 'Must declare role="dialog"');
    assert.ok(content.includes('aria-modal="true"'), 'Must declare aria-modal="true"');
    assert.ok(content.includes("e.key === 'Escape'"), 'Must handle Escape key to close');
    assert.ok(content.includes("e.key === 'Tab'"), 'Must handle Tab key for focus trapping');
    assert.ok(content.includes('previousFocusRef.current'), 'Must restore focus to previously active element on unmount');
    assert.ok(content.includes('overflow = \'hidden\''), 'Must lock document body scroll while dialog is open');
  });

  it('4. SkeletonLoader provides role="status", aria-busy="true", and localized screen-reader text', () => {
    const filePath = path.join(rootDir, 'src', 'components', 'common', 'SkeletonLoader.jsx');
    assert.ok(fs.existsSync(filePath), 'SkeletonLoader.jsx must exist');
    const content = fs.readFileSync(filePath, 'utf-8');

    assert.ok(content.includes('role="status"'), 'Must declare role="status"');
    assert.ok(content.includes('aria-busy="true"'), 'Must declare aria-busy="true"');
    assert.ok(content.includes('aria-live="polite"'), 'Must declare aria-live="polite"');
    assert.ok(content.includes('sr-only'), 'Must include screen-reader accessible description');
    assert.ok(content.includes("case 'table':"), 'Must support table variant');
    assert.ok(content.includes("case 'list':"), 'Must support list variant');
  });

  it('5. EmptyState defines semantic region with customizable icon and action button', () => {
    const filePath = path.join(rootDir, 'src', 'components', 'common', 'EmptyState.jsx');
    assert.ok(fs.existsSync(filePath), 'EmptyState.jsx must exist');
    const content = fs.readFileSync(filePath, 'utf-8');

    assert.ok(content.includes('role="region"'), 'Must declare role="region"');
    assert.ok(content.includes('aria-label={title}'), 'Must announce title via aria-label');
    assert.ok(content.includes('focus-visible:ring-2'), 'Action buttons must have visible focus rings');
  });

  it('6. LoginPage binds labels to inputs via htmlFor and includes aria-describedby for errors', () => {
    const filePath = path.join(rootDir, 'src', 'pages', 'public', 'LoginPage.jsx');
    const content = fs.readFileSync(filePath, 'utf-8');

    assert.ok(content.includes('htmlFor="login-identifier"'), 'Must bind identifier label to input');
    assert.ok(content.includes('id="login-identifier"'), 'Must define login-identifier input');
    assert.ok(content.includes('htmlFor="login-password"'), 'Must bind password label to input');
    assert.ok(content.includes('id="login-password"'), 'Must define login-password input');
    assert.ok(content.includes('role="alert"'), 'Error banner must declare role="alert"');
    assert.ok(content.includes('aria-live="assertive"'), 'Error banner must declare aria-live="assertive"');
    assert.ok(content.includes('aria-describedby'), 'Inputs must reference error banner or hint via aria-describedby');
  });

  it('7. RegisterPage and Password Recovery pages bind labels and announce error alerts', () => {
    const regContent = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'public', 'RegisterPage.jsx'), 'utf-8');
    assert.ok(regContent.includes('htmlFor="reg-name"'), 'RegisterPage must bind name label');
    assert.ok(regContent.includes('id="reg-name"'), 'RegisterPage must define reg-name');
    assert.ok(regContent.includes('role="alert"'), 'RegisterPage must declare role="alert" on errors');

    const forgotContent = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'public', 'ForgotPasswordPage.jsx'), 'utf-8');
    assert.ok(forgotContent.includes('role="alert"'), 'ForgotPasswordPage must declare role="alert" on error');
    assert.ok(forgotContent.includes('htmlFor="recovery-email"'), 'ForgotPasswordPage must bind email label');

    const resetContent = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'public', 'ResetPasswordPage.jsx'), 'utf-8');
    assert.ok(resetContent.includes('role="alert"'), 'ResetPasswordPage must declare role="alert" on error');
    assert.ok(resetContent.includes('htmlFor="new-pass"'), 'ResetPasswordPage must bind new-pass label');
    assert.ok(resetContent.includes('htmlFor="confirm-pass"'), 'ResetPasswordPage must bind confirm-pass label');
  });

  it('8. Offline banner in App.jsx provides an accessible retry button and localized notice', () => {
    const appContent = fs.readFileSync(path.join(rootDir, 'src', 'App.jsx'), 'utf-8');
    assert.ok(appContent.includes('role="alert"'), 'App.jsx offline banner must declare role="alert"');
    assert.ok(appContent.includes('aria-live="assertive"'), 'App.jsx offline banner must declare aria-live="assertive"');
    assert.ok(appContent.includes('handleRetryConnection'), 'App.jsx offline banner must have retry button handler');
    assert.ok(appContent.includes('focus-visible:ring-2'), 'App.jsx offline retry button must have focus-visible styling');
  });

});
