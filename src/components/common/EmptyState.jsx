import React from 'react';
import { Link } from 'react-router-dom';

/**
 * EmptyState Component
 * Consistent and accessible empty status presentation across queues and lists.
 */
export default function EmptyState({
  icon = 'inbox',
  title,
  description,
  actionText,
  actionLink,
  onAction,
  className = '',
}) {
  return (
    <div
      role="region"
      aria-label={title}
      className={`flex flex-col items-center justify-center p-8 text-center bg-surface-container-low/50 rounded-2xl border border-outline-variant/30 ${className}`}
    >
      <div className="w-14 h-14 rounded-2xl bg-surface-container-high/60 text-on-surface-variant flex items-center justify-center mb-4 shadow-xs">
        <span className="material-symbols-outlined text-[28px]">{icon}</span>
      </div>
      <h3 className="text-title-sm font-bold text-on-surface mb-1">
        {title}
      </h3>
      {description && (
        <p className="text-body-sm text-on-surface-variant max-w-md mb-6 leading-relaxed">
          {description}
        </p>
      )}
      {actionText && (
        <div>
          {actionLink ? (
            <Link
              to={actionLink}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-primary text-white text-label-md font-semibold rounded-xl hover:bg-primary-hover shadow-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-primary"
            >
              <span>{actionText}</span>
            </Link>
          ) : (
            <button
              type="button"
              onClick={onAction}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-primary text-white text-label-md font-semibold rounded-xl hover:bg-primary-hover shadow-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-primary"
            >
              <span>{actionText}</span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}
