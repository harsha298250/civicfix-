import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';

export const THEME_STORAGE_KEY = 'civicfix_theme';
export const VALID_THEMES = ['light', 'dark', 'system'];

/**
 * Resolves a theme preference ('light' | 'dark' | 'system') to an effective 'light' | 'dark' mode.
 */
export function resolveTheme(theme, prefersDark = false) {
  if (theme === 'dark') return 'dark';
  if (theme === 'light') return 'light';
  return prefersDark ? 'dark' : 'light';
}

export function getSystemPrefersDark() {
  try {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
  } catch {
    // Ignore matchMedia errors
  }
  return false;
}

export function readStoredTheme() {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
      if (VALID_THEMES.includes(saved)) {
        return saved;
      }
    }
  } catch {
    // Storage access blocked
  }
  return 'system';
}

export function applyThemeToDocument(effectiveTheme) {
  if (typeof document === 'undefined' || !document.documentElement) return;
  const root = document.documentElement;
  if (effectiveTheme === 'dark') {
    root.classList.add('dark');
    root.style.colorScheme = 'dark';
  } else {
    root.classList.remove('dark');
    root.style.colorScheme = 'light';
  }
}

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => readStoredTheme());
  const [systemPrefersDark, setSystemPrefersDark] = useState(() => getSystemPrefersDark());

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e) => setSystemPrefersDark(Boolean(e.matches));
    setSystemPrefersDark(Boolean(mql.matches));
    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', handleChange);
      return () => mql.removeEventListener('change', handleChange);
    }
    if (typeof mql.addListener === 'function') {
      mql.addListener(handleChange);
      return () => mql.removeListener(handleChange);
    }
    return undefined;
  }, []);

  const resolvedTheme = useMemo(
    () => resolveTheme(theme, systemPrefersDark),
    [theme, systemPrefersDark]
  );

  useEffect(() => {
    applyThemeToDocument(resolvedTheme);
  }, [resolvedTheme]);

  const setTheme = useCallback((nextTheme) => {
    const valid = VALID_THEMES.includes(nextTheme) ? nextTheme : 'system';
    setThemeState(valid);
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(THEME_STORAGE_KEY, valid);
      }
    } catch {
      // Ignore storage errors
    }
    applyThemeToDocument(resolveTheme(valid, getSystemPrefersDark()));
  }, []);

  const cycleTheme = useCallback(() => {
    const order = ['light', 'dark', 'system'];
    const idx = order.indexOf(theme);
    const next = order[(idx + 1) % order.length];
    setTheme(next);
  }, [theme, setTheme]);

  const value = useMemo(
    () => ({
      theme,
      resolvedTheme,
      isDark: resolvedTheme === 'dark',
      setTheme,
      cycleTheme
    }),
    [theme, resolvedTheme, setTheme, cycleTheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    return {
      theme: 'light',
      resolvedTheme: 'light',
      isDark: false,
      setTheme: () => {},
      cycleTheme: () => {}
    };
  }
  return ctx;
}
