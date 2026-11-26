/**
 * Shared i18n helpers for dynamic categories, error codes/messages, and notifications.
 */

const CATEGORY_KEY_MAP = {
  'Pothole & Road Hazard': 'categories.roads',
  'ROADS': 'categories.roads',
  'roads': 'categories.roads',
  'Water Supply & Leakage': 'categories.water',
  'WATER_SEWERAGE': 'categories.water',
  'water': 'categories.water',
  'Garbage & Sanitation': 'categories.garbage',
  'GARBAGE': 'categories.garbage',
  'garbage': 'categories.garbage',
  'Street Lighting': 'categories.lighting',
  'STREET_LIGHT': 'categories.lighting',
  'lighting': 'categories.lighting',
  'Drainage & Sewage': 'categories.drainage',
  'DRAINAGE': 'categories.drainage',
  'drainage': 'categories.drainage',
  'Encroachment & Illegal Parking': 'categories.encroachment',
  'ENCROACHMENT': 'categories.encroachment',
  'encroachment': 'categories.encroachment',
  'Public Health & Mosquitoes': 'categories.health',
  'PUBLIC_HEALTH': 'categories.health',
  'health': 'categories.health',
  'Others': 'categories.others',
  'OTHERS': 'categories.others',
  'others': 'categories.others'
};

export function translateCategory(rawCategory, t) {
  if (!rawCategory) return '';
  if (rawCategory === 'ALL') return t('common.allCategories');
  const key = CATEGORY_KEY_MAP[rawCategory];
  if (key) {
    const translated = t(key);
    if (translated && translated !== key) return translated;
  }
  return rawCategory;
}

export function translateErrorMessage(errOrMsg, t, fallbackKey = 'errors.generic') {
  const msg = typeof errOrMsg === 'string' ? errOrMsg : (errOrMsg?.message || '');
  const status = typeof errOrMsg === 'object' ? errOrMsg?.status : undefined;
  const lower = msg.toLowerCase();

  if (status === 401 || lower.includes('full authentication is required') || lower.includes('session expired')) {
    return t('errors.unauthorized');
  }
  if (status === 403 || lower.includes('access denied') || lower.includes('permission') || lower.includes('forbidden')) {
    return t('errors.forbidden');
  }
  if (status === 404 || lower.includes('not found')) {
    return t('errors.notFound');
  }
  if (status >= 500 || lower.includes('internal server error') || lower.includes('postgresql') || lower.includes('database')) {
    return t('errors.internalServer');
  }
  if (lower.includes('cannot reach') || lower.includes('network') || lower.includes('failed to fetch') || status === 0) {
    return t('errors.serverUnreachable');
  }
  return msg || t(fallbackKey);
}

export function translateNotificationItem(notification, t) {
  if (!notification) return { title: '', message: '' };
  const ref = notification.referenceId ? `#${notification.referenceId}` : '';
  switch (notification.type) {
    case 'COMPLAINT_CREATED':
      return {
        title: ref ? t('notifications.dynamic.complaintSubmittedTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.complaintSubmittedDesc')
      };
    case 'NEW_COMPLAINT':
      return {
        title: ref ? t('notifications.dynamic.newComplaintTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.newComplaintDesc')
      };
    case 'WORKER_ASSIGNED':
      return {
        title: ref ? t('notifications.dynamic.workerAssignedTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.workerAssignedDesc')
      };
    case 'RESOLUTION_SUBMITTED':
      return {
        title: ref ? t('notifications.dynamic.resolutionSubmittedTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.resolutionSubmittedDesc')
      };
    case 'COMPLAINT_RESOLVED':
      return {
        title: ref ? t('notifications.dynamic.complaintResolvedTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.complaintResolvedDesc')
      };
    case 'SLA_ESCALATED':
      return {
        title: ref ? t('notifications.dynamic.slaEscalatedTitle', { ref }) : notification.title,
        message: t('notifications.dynamic.slaEscalatedDesc')
      };
    default:
      return {
        title: notification.title || '',
        message: notification.message || ''
      };
  }
}
