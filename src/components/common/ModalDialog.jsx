import React, { useEffect, useRef, useId } from 'react';
import { useTranslation } from '../../context/LanguageContext';

/**
 * ModalDialog Component (WCAG 2.1 AA Compliant Modal System)
 * 
 * Enforces:
 * - SC 2.1.2: No Keyboard Trap (Tab & Shift+Tab cycle within active boundary)
 * - SC 2.4.3: Focus Order (Initial focus on first interactive element or dialog body)
 * - Keyboard Escape dismissal listener
 * - Active element preservation and return-focus upon dialog closure
 * - Semantics: role="dialog", aria-modal="true", aria-labelledby
 */
export default function ModalDialog({
  isOpen,
  onClose,
  title,
  children,
  maxWidth = 'max-w-lg',
  ariaLabelledBy,
  ariaDescribedBy,
  showCloseButton = true,
  className = '',
}) {
  const { t } = useTranslation();
  const modalRef = useRef(null);
  const previousFocusRef = useRef(null);
  const generatedTitleId = useId();
  const titleId = ariaLabelledBy || generatedTitleId;

  // Manage focus restoration and body scroll locking
  useEffect(() => {
    if (isOpen) {
      previousFocusRef.current = document.activeElement;
      const originalOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';

      // Focus first interactive control within modal
      const timer = setTimeout(() => {
        if (!modalRef.current) return;
        const focusable = modalRef.current.querySelectorAll(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusable.length > 0) {
          focusable[0].focus();
        } else {
          modalRef.current.focus();
        }
      }, 50);

      return () => {
        clearTimeout(timer);
        document.body.style.overflow = originalOverflow;
        if (previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
          previousFocusRef.current.focus();
        }
      };
    }
  }, [isOpen]);

  // Keyboard navigation & trap handler
  const handleKeyDown = (e) => {
    if (!isOpen || !modalRef.current) return;

    if (e.key === 'Escape') {
      e.stopPropagation();
      onClose();
      return;
    }

    if (e.key === 'Tab') {
      const focusable = Array.from(
        modalRef.current.querySelectorAll(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )
      ).filter((el) => el.offsetParent !== null || el === document.activeElement);

      if (focusable.length === 0) {
        e.preventDefault();
        return;
      }

      const firstElement = focusable[0];
      const lastElement = focusable[focusable.length - 1];

      if (e.shiftKey) {
        if (document.activeElement === firstElement || document.activeElement === modalRef.current) {
          e.preventDefault();
          lastElement.focus();
        }
      } else {
        if (document.activeElement === lastElement) {
          e.preventDefault();
          firstElement.focus();
        }
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
      role="presentation"
    >
      <div
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={ariaDescribedBy}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
        className={`w-full ${maxWidth} bg-surface-container-lowest text-on-surface rounded-2xl shadow-2xl border border-outline-variant/30 flex flex-col max-h-[90vh] overflow-hidden focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${className}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/20">
          <h2 id={titleId} className="text-title-md font-title font-bold text-on-surface">
            {title}
          </h2>
          {showCloseButton && (
            <button
              type="button"
              onClick={onClose}
              aria-label={t('accessibility.closeDialog')}
              className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <span className="material-symbols-outlined text-[20px]">close</span>
            </button>
          )}
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto">
          {children}
        </div>
      </div>
    </div>
  );
}

export { ModalDialog };
