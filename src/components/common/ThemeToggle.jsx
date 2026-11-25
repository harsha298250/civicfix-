import React from 'react';
import { useTheme } from '../../context/ThemeContext';

const THEME_LABELS = {
  light: 'Light Mode',
  dark: 'Dark Mode',
  system: 'System Theme'
};

const THEME_ICONS = {
  light: 'light_mode',
  dark: 'dark_mode',
  system: 'contrast'
};

export default function ThemeToggle({ className = '', variant = 'button' }) {
  const { theme, resolvedTheme, setTheme, cycleTheme } = useTheme();

  if (variant === 'selector') {
    const options = [
      { id: 'light', label: 'Light', icon: 'light_mode' },
      { id: 'dark', label: 'Dark', icon: 'dark_mode' },
      { id: 'system', label: 'System', icon: 'contrast' }
    ];
    return (
      <div
        role="radiogroup"
        aria-label="Color theme preference"
        className={`inline-flex items-center p-1 rounded-xl bg-surface-container-low border border-outline-variant/40 gap-1 ${className}`}
      >
        {options.map((opt) => {
          const active = theme === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => setTheme(opt.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                active
                  ? 'bg-primary-container text-white shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
              }`}
            >
              <span className="material-symbols-outlined text-[15px]">{opt.icon}</span>
              <span>{opt.label}</span>
            </button>
          );
        })}
      </div>
    );
  }

  const label = THEME_LABELS[theme] || 'System Theme';
  const icon = theme === 'system'
    ? (resolvedTheme === 'dark' ? 'dark_mode' : 'light_mode')
    : (THEME_ICONS[theme] || 'light_mode');

  return (
    <button
      type="button"
      onClick={cycleTheme}
      aria-label={`Theme: ${label}. Click to switch theme`}
      title={`Theme: ${label} (click to cycle Light / Dark / System)`}
      data-testid="theme-toggle-button"
      className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-low hover:bg-surface-container text-on-surface text-xs font-semibold transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${className}`}
    >
      <span className="material-symbols-outlined text-[17px] text-primary-container" aria-hidden="true">
        {icon}
      </span>
      <span className="hidden xl:inline">{label}</span>
    </button>
  );
}
