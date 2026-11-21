import React from 'react';
import { useTranslation } from '../../context/LanguageContext';

/**
 * SkipToContent Component (WCAG 2.1 AA SC 2.4.1 Bypass Blocks)
 * Provides keyboard and screen-reader users with a direct bypass mechanism
 * to skip repetitive header and navigation blocks directly to #main-content.
 */
export default function SkipToContent({ targetId = 'main-content' }) {
  const { t } = useTranslation();

  const handleSkip = (e) => {
    e.preventDefault();
    const target = document.getElementById(targetId);
    if (target) {
      target.tabIndex = -1;
      target.focus();
      target.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <a
      href={`#${targetId}`}
      onClick={handleSkip}
      className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2.5 focus:bg-primary-600 focus:text-white focus:font-semibold focus:rounded-xl focus:shadow-2xl focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-primary-400 focus:border-2 focus:border-white transition-all select-none"
    >
      {t('accessibility.skipToContent')}
    </a>
  );
}
