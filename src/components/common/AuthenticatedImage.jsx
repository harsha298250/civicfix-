import React, { useState, useEffect } from 'react';
import { auth } from '../../services/firebaseConfig';
import { resolveMediaUrl } from '../../services/complaintService';
import { useTranslation } from '../../context/LanguageContext';

const buildFallbackSvg = (label = 'Image Unavailable') =>
  `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="225" viewBox="0 0 400 225"><rect width="400" height="225" fill="#1e293b"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#94a3b8" font-family="sans-serif" font-size="13">${label}</text></svg>`
  )}`;

const FALLBACK_LABELS = {
  UNAUTHORIZED: 'Access Restricted',
  NOT_FOUND: 'Image Not Found',
  ERROR: 'Image Unavailable'
};

export default function AuthenticatedImage({
  src,
  alt,
  className = '',
  fallback = null,
  onError,
  enableLightbox = false,
  downloadFilename = ''
}) {
  const { t } = useTranslation();
  const resolved = resolveMediaUrl(src);
  const needsAuthFetch = Boolean(
    resolved &&
      (resolved.includes('/api/complaints/photos/files/') ||
        resolved.includes('/api/complaints/evidence/files/') ||
        resolved.includes('/api/citizen/account/profile/photo/'))
  );

  const [objectUrl, setObjectUrl] = useState(needsAuthFetch ? null : resolved);
  const [errorReason, setErrorReason] = useState(null);
  const [lightboxOpen, setLightboxOpen] = useState(false);

  useEffect(() => {
    setErrorReason(null);
    if (!resolved) {
      setObjectUrl(null);
      return undefined;
    }
    if (!needsAuthFetch) {
      setObjectUrl(resolved);
      return undefined;
    }

    let active = true;
    let createdUrl = null;
    (async () => {
      try {
        const headers = {};
        if (auth && auth.currentUser) {
          try {
            const token = await auth.currentUser.getIdToken();
            if (token) headers['Authorization'] = `Bearer ${token}`;
          } catch {
            // Fall back to session cookie
          }
        }
        const res = await fetch(resolved, { headers, credentials: 'include' });
        if (!res.ok) {
          const reason = res.status === 401 || res.status === 403
            ? 'UNAUTHORIZED'
            : res.status === 404
            ? 'NOT_FOUND'
            : 'ERROR';
          if (active) setErrorReason(reason);
          return;
        }
        const blob = await res.blob();
        createdUrl = URL.createObjectURL(blob);
        if (active) setObjectUrl(createdUrl);
      } catch {
        if (active) setErrorReason('ERROR');
      }
    })();

    return () => {
      active = false;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [resolved, needsAuthFetch]);

  useEffect(() => {
    if (!lightboxOpen || typeof window === 'undefined') return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') setLightboxOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [lightboxOpen]);

  const handleDownload = () => {
    if (!objectUrl || typeof document === 'undefined') return;
    const inferredName =
      downloadFilename ||
      (resolved ? resolved.split('/').pop()?.split('?')[0] : '') ||
      'civicfix-evidence.jpg';
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = inferredName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  if (errorReason || (!objectUrl && !needsAuthFetch)) {
    const label = FALLBACK_LABELS[errorReason || 'ERROR'];
    return fallback || <img src={buildFallbackSvg(label)} alt={`${alt} (${label})`} className={className} />;
  }

  if (!objectUrl) {
    return <div className={`animate-pulse bg-surface-container ${className}`} aria-label={alt} />;
  }

  const imageEl = (
    <img
      src={objectUrl}
      alt={alt}
      className={className}
      onError={(e) => {
        setErrorReason('ERROR');
        if (onError) onError(e);
      }}
    />
  );

  if (!enableLightbox) {
    return imageEl;
  }

  return (
    <>
      <div className="relative w-full h-full group">
        {imageEl}
        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-colors flex items-end justify-end p-2 gap-1.5 opacity-0 group-hover:opacity-100">
          <button
            type="button"
            onClick={() => setLightboxOpen(true)}
            className="px-2 py-1 rounded-lg bg-black/75 text-white text-[11px] font-semibold flex items-center gap-1 hover:bg-black transition-colors"
            title="View full size"
          >
            <span className="material-symbols-outlined text-[14px]">zoom_in</span>
            <span>{t('common.view')}</span>
          </button>
          <button
            type="button"
            onClick={handleDownload}
            className="px-2 py-1 rounded-lg bg-black/75 text-white text-[11px] font-semibold flex items-center gap-1 hover:bg-black transition-colors"
            title={t('common.download')}
          >
            <span className="material-symbols-outlined text-[14px]">download</span>
          </button>
        </div>
      </div>

      {lightboxOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={alt || 'Full-size evidence preview'}
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4"
          onClick={() => setLightboxOpen(false)}
        >
          <div
            className="relative max-w-4xl w-full bg-surface-container-lowest rounded-2xl overflow-hidden shadow-2xl border border-outline-variant/30"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-outline-variant/30">
              <span className="text-xs font-semibold text-on-surface truncate">{alt || 'Complaint Evidence'}</span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleDownload}
                  className="px-3 py-1.5 rounded-lg bg-primary-container text-white text-xs font-semibold flex items-center gap-1.5 hover:bg-primary transition-colors"
                >
                  <span className="material-symbols-outlined text-[15px]">download</span>
                  <span>{t('common.download')}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setLightboxOpen(false)}
                  className="p-1.5 rounded-lg bg-surface-container text-on-surface hover:bg-surface-container-high transition-colors"
                  aria-label={t('common.close')}
                >
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>
            </div>
            <div className="bg-black flex items-center justify-center p-2 max-h-[80vh]">
              <img src={objectUrl} alt={alt} className="max-h-[75vh] w-auto object-contain rounded-lg" />
            </div>
          </div>
        </div>
      )}
    </>
  );
}
