import React from 'react';
import { useTranslation } from '../../context/LanguageContext';

/**
 * SkeletonLoader Component
 * Accessible, animated placeholder elements that inform assistive technologies
 * of an active loading operation (WCAG SC 4.1.3 Status Messages).
 */
export default function SkeletonLoader({
  type = 'card',
  count = 3,
  className = '',
}) {
  const { t } = useTranslation();

  const renderSkeletonItem = (index) => {
    switch (type) {
      case 'table':
        return (
          <tr key={index} className="border-b border-outline-variant/20 animate-pulse">
            <td className="py-4 px-4"><div className="h-4 bg-outline-variant/30 rounded w-24" /></td>
            <td className="py-4 px-4"><div className="h-4 bg-outline-variant/30 rounded w-48" /></td>
            <td className="py-4 px-4"><div className="h-4 bg-outline-variant/30 rounded w-20" /></td>
            <td className="py-4 px-4"><div className="h-4 bg-outline-variant/30 rounded w-16" /></td>
            <td className="py-4 px-4"><div className="h-6 bg-outline-variant/30 rounded-full w-24" /></td>
          </tr>
        );
      case 'list':
        return (
          <div
            key={index}
            className="flex items-center gap-4 p-4 bg-surface-container-low rounded-xl border border-outline-variant/20 animate-pulse"
          >
            <div className="w-10 h-10 rounded-lg bg-outline-variant/30 shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="h-4 bg-outline-variant/30 rounded w-3/4" />
              <div className="h-3 bg-outline-variant/20 rounded w-1/2" />
            </div>
            <div className="w-16 h-6 bg-outline-variant/30 rounded-full shrink-0" />
          </div>
        );
      case 'stat':
        return (
          <div
            key={index}
            className="p-5 bg-surface-container-lowest rounded-2xl border border-outline-variant/20 animate-pulse space-y-3"
          >
            <div className="h-4 bg-outline-variant/30 rounded w-1/3" />
            <div className="h-8 bg-outline-variant/30 rounded w-1/2" />
            <div className="h-3 bg-outline-variant/20 rounded w-2/3" />
          </div>
        );
      case 'card':
      default:
        return (
          <div
            key={index}
            className="p-5 bg-surface-container-lowest rounded-2xl border border-outline-variant/20 shadow-xs animate-pulse space-y-4"
          >
            <div className="flex items-center justify-between">
              <div className="h-4 bg-outline-variant/30 rounded w-28" />
              <div className="h-5 bg-outline-variant/30 rounded-full w-20" />
            </div>
            <div className="h-5 bg-outline-variant/30 rounded w-3/4" />
            <div className="h-3 bg-outline-variant/20 rounded w-full" />
            <div className="flex items-center justify-between pt-2 border-t border-outline-variant/10">
              <div className="h-3 bg-outline-variant/20 rounded w-24" />
              <div className="h-3 bg-outline-variant/20 rounded w-16" />
            </div>
          </div>
        );
    }
  };

  return (
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      className={`w-full ${type === 'card' ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4' : 'space-y-3'} ${className}`}
    >
      <span className="sr-only">{t('accessibility.loading')}</span>
      {Array.from({ length: count }).map((_, idx) => renderSkeletonItem(idx))}
    </div>
  );
}
