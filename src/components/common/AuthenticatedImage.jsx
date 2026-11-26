import React, { useState, useEffect } from 'react';
import { auth } from '../../services/firebaseConfig';
import { resolveMediaUrl } from '../../services/complaintService';

const FALLBACK_SVG =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='225' viewBox='0 0 400 225'%3E%3Crect width='400' height='225' fill='%231e293b'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%2394a3b8' font-family='sans-serif' font-size='13'%3EImage Unavailable%3C/text%3E%3C/svg%3E";

export default function AuthenticatedImage({ src, alt, className = '', fallback = null, onError }) {
  const resolved = resolveMediaUrl(src);
  const needsAuthFetch = Boolean(
    resolved &&
      (resolved.includes('/api/complaints/photos/files/') ||
        resolved.includes('/api/complaints/evidence/files/') ||
        resolved.includes('/api/citizen/account/profile/photo/'))
  );

  const [objectUrl, setObjectUrl] = useState(needsAuthFetch ? null : resolved);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
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
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        createdUrl = URL.createObjectURL(blob);
        if (active) setObjectUrl(createdUrl);
      } catch {
        if (active) setFailed(true);
      }
    })();

    return () => {
      active = false;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [resolved, needsAuthFetch]);

  if (failed || (!objectUrl && !needsAuthFetch)) {
    return fallback || <img src={FALLBACK_SVG} alt={alt} className={className} />;
  }

  if (!objectUrl) {
    return <div className={`animate-pulse bg-surface-container ${className}`} aria-label={alt} />;
  }

  return (
    <img
      src={objectUrl}
      alt={alt}
      className={className}
      onError={(e) => {
        setFailed(true);
        if (onError) onError(e);
      }}
    />
  );
}
