import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

describe('ForgotPasswordPage Secure Password Recovery Tests', () => {
  const pagePath = path.join(rootDir, 'src/pages/public/ForgotPasswordPage.jsx');
  assert.ok(fs.existsSync(pagePath), 'ForgotPasswordPage.jsx must exist');
  const pageContent = fs.readFileSync(pagePath, 'utf8');

  test('Page contains form submission that calls authService.forgotPassword', () => {
    assert.ok(pageContent.includes('<form'), 'ForgotPasswordPage must contain a form element');
    assert.ok(pageContent.includes('onSubmit={handleSubmit}'), 'ForgotPasswordPage must have onSubmit handler');
    assert.ok(pageContent.includes('authService.forgotPassword'), 'ForgotPasswordPage must call authService.forgotPassword');
    assert.ok(pageContent.includes('type="email"'), 'ForgotPasswordPage must contain email input');
  });

  test('Page contains no invented protocol claims or fake security keywords', () => {
    // Grepping for RFC-7519, Zero-Knowledge, or passkey must return nothing
    assert.ok(!pageContent.includes('RFC-7519'), 'Must not claim RFC-7519');
    assert.ok(!pageContent.toLowerCase().includes('zero-knowledge'), 'Must not claim Zero-Knowledge protocol');
    assert.ok(!pageContent.toLowerCase().includes('passkey'), 'Must not claim single-use cryptographic passkeys');
  });

  test('Page displays generic non-enumerating confirmation on submission', () => {
    assert.ok(
      pageContent.includes('If an account with that email exists') ||
      pageContent.includes('If an account exists with this email'),
      'Must contain generic non-enumerating confirmation message'
    );
    assert.ok(
      pageContent.includes('do not reveal whether an email is registered') ||
      pageContent.includes('instructions have been sent'),
      'Must protect against email enumeration'
    );
  });

  test('Route /forgot-password and /reset-password are registered in App.jsx', () => {
    const appPath = path.join(rootDir, 'src/App.jsx');
    const appContent = fs.readFileSync(appPath, 'utf8');
    assert.ok(
      appContent.includes('path="/forgot-password"'),
      'App.jsx must retain /forgot-password route'
    );
    assert.ok(
      appContent.includes('path="/reset-password"'),
      'App.jsx must retain /reset-password route'
    );

    const loginPath = path.join(rootDir, 'src/pages/public/LoginPage.jsx');
    const loginContent = fs.readFileSync(loginPath, 'utf8');
    assert.ok(
      loginContent.includes('to="/forgot-password"'),
      'LoginPage.jsx must link to /forgot-password'
    );
  });

  test('ResetPasswordPage exists and validates token and password constraints', () => {
    const resetPath = path.join(rootDir, 'src/pages/public/ResetPasswordPage.jsx');
    assert.ok(fs.existsSync(resetPath), 'ResetPasswordPage.jsx must exist');
    const resetContent = fs.readFileSync(resetPath, 'utf8');
    assert.ok(resetContent.includes('authService.resetPassword'), 'Must invoke authService.resetPassword');
    assert.ok(resetContent.includes('token'), 'Must handle reset token');
    assert.ok(resetContent.includes('password'), 'Must capture new password');
    assert.ok(resetContent.includes('confirmPassword'), 'Must validate confirmation password');
  });

  test('Locale dictionaries provide English and Tamil translations for password assistance', () => {
    const enPath = path.join(rootDir, 'src/locales/en.json');
    const taPath = path.join(rootDir, 'src/locales/ta.json');

    const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));
    const ta = JSON.parse(fs.readFileSync(taPath, 'utf8'));

    assert.ok(en.auth.forgotPasswordTitle, 'en.json must define auth.forgotPasswordTitle');
    assert.ok(en.auth.forgotPasswordNotice, 'en.json must define auth.forgotPasswordNotice');
    assert.ok(en.auth.forgotPasswordContact, 'en.json must define auth.forgotPasswordContact');
    assert.ok(en.auth.backToSignIn, 'en.json must define auth.backToSignIn');

    assert.ok(ta.auth.forgotPasswordTitle, 'ta.json must define auth.forgotPasswordTitle');
    assert.ok(ta.auth.forgotPasswordNotice, 'ta.json must define auth.forgotPasswordNotice');
    assert.ok(ta.auth.forgotPasswordContact, 'ta.json must define auth.forgotPasswordContact');
    assert.ok(ta.auth.backToSignIn, 'ta.json must define auth.backToSignIn');
  });
});

