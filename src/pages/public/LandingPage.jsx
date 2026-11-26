import React, { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';

export const LandingPage = () => {
  const { t } = useTranslation();
  const location = useLocation();

  // Handle cross-page anchor navigation (e.g. from /register or /legal/terms to /#how-it-works)
  useEffect(() => {
    const hash = window.location.hash.replace('#', '');
    if (hash) {
      const timer = setTimeout(() => {
        const el = document.getElementById(hash);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [location]);
  return (
    <div className="w-full flex flex-col items-center">
      {/* Hero Section */}
      <section className="relative w-full overflow-hidden pt-8 pb-16 md:py-20 bg-surface">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          {/* Left Content Column */}
          <div className="lg:col-span-7 flex flex-col gap-6 z-10">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-surface-container-high w-fit shadow-sm">
              <span className="w-2 h-2 rounded-full bg-primary-container animate-pulse" />
              <span className="font-label-sm text-label-sm text-on-surface-variant font-medium">
                {t('landing.badge')}
              </span>
            </div>

            <h1 className="font-display text-4xl sm:text-5xl lg:text-6xl text-on-surface tracking-tight leading-tight font-extrabold">
              {t('landing.heroTitle')}
            </h1>

            <p className="font-body-lg text-body-lg text-on-surface-variant max-w-xl leading-relaxed">
              {t('landing.heroDesc')}
            </p>

            {/* CTA Cluster */}
            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Link
                to="/citizen/complaints/new"
                className="inline-flex items-center gap-2 bg-primary-container hover:bg-primary text-white font-semibold px-6 h-12 rounded-xl shadow-md transition-all hover:shadow-lg hover:-translate-y-0.5 active:translate-y-0"
              >
                <span className="material-symbols-outlined text-[20px]">near_me</span>
                <span>{t('landing.reportAnIssue')}</span>
              </Link>
              <Link
                to="/login"
                className="inline-flex items-center gap-2 bg-surface-container-lowest hover:bg-surface-container text-on-surface font-semibold px-6 h-12 rounded-xl shadow-sm border border-outline-variant/40 transition-all hover:shadow"
              >
                <span className="material-symbols-outlined text-[20px]">lock_open</span>
                <span>{t('landing.signInPortal')}</span>
              </Link>
              <Link
                to="/map"
                className="inline-flex items-center gap-2 bg-secondary-fixed text-on-secondary-fixed hover:bg-secondary-fixed-dim font-semibold px-5 h-12 rounded-xl shadow-sm transition-all"
              >
                <span className="material-symbols-outlined text-[20px]">map</span>
                <span>{t('landing.liveCityMap')}</span>
              </Link>
            </div>

            {/* Trust micro-badges */}
            <div className="pt-4 flex flex-wrap items-center gap-6 text-on-surface-variant font-label-sm text-label-sm">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-primary-container text-[18px]">verified</span>
                <span>{t('landing.gisValidation')}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-primary-container text-[18px]">history_toggle_off</span>
                <span>{t('landing.realTimeAudit')}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-secondary text-[18px]">rule</span>
                <span>{t('landing.ruleTriagePipeline')}</span>
              </div>
            </div>
          </div>

          {/* Right Visual Column with Contextual Streetscape & Floating Card */}
          <div className="lg:col-span-5 relative">
            <div className="relative w-full rounded-2xl overflow-hidden shadow-2xl aspect-[4/3] bg-surface-container border border-outline-variant/40">
              <img
                src="https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&fm=webp&w=800&q=75"
                srcSet="https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&fm=webp&w=480&q=75 480w, https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&fm=webp&w=800&q=75 800w, https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&fm=webp&w=1000&q=75 1000w"
                sizes="(max-width: 1024px) 100vw, 42vw"
                width={800}
                height={600}
                decoding="async"
                fetchPriority="high"
                alt="Modern city infrastructure with organized streets"
                className="w-full h-full object-cover"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#111c28]/70 via-transparent to-transparent" />
              <div className="absolute top-4 left-4 bg-[#151D26]/80 backdrop-blur-md text-white text-xs px-3 py-1.5 rounded-full flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                <span>{t('landing.sensorGrid')}</span>
              </div>
            </div>

            {/* Floating UI Resolution Preview Card */}
            <div className="relative sm:absolute sm:-bottom-8 sm:-left-8 mt-4 sm:mt-0 max-w-sm w-full bg-surface-container-lowest/95 backdrop-blur-md rounded-2xl p-4 shadow-2xl border border-outline-variant/40 transition-transform hover:-translate-y-1">
              <div className="flex items-start justify-between gap-2 mb-2">
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-700 font-label-sm text-xs font-semibold">
                  <span className="material-symbols-outlined text-[14px]">check_circle</span>
                  {t('landing.resolvedIn48h')}
                </span>
                <span className="font-code text-xs text-on-surface-variant font-semibold">#CF-2026-00124</span>
              </div>
              <h4 className="font-title text-sm text-on-surface font-bold">{t('landing.sampleTitle')}</h4>
              <p className="font-body-sm text-xs text-on-surface-variant flex items-center gap-1 mt-1">
                <span className="material-symbols-outlined text-[15px] text-tertiary">location_on</span>
                {t('landing.sampleLoc')}
              </p>
              <div className="mt-3 pt-2 flex items-center justify-between bg-surface-container-low rounded-xl p-2.5 text-xs">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-primary-container text-white flex items-center justify-center">
                    <span className="material-symbols-outlined text-[18px]">engineering</span>
                  </div>
                  <div>
                    <p className="font-semibold text-on-surface">{t('landing.sampleUnit')}</p>
                    <p className="text-[10px] text-tertiary">{t('landing.sampleUnitDesc')}</p>
                  </div>
                </div>
                <Link
                  to="/citizen/complaints/CF-2026-00124"
                  className="text-primary-container font-semibold hover:underline flex items-center gap-0.5"
                >
                  <span>{t('landing.track')}</span>
                  <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How It Works Section */}
      <section id="how-it-works" className="scroll-mt-20 w-full py-16 bg-surface-container-low border-y border-outline-variant/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <span className="text-secondary font-semibold text-xs uppercase tracking-wider">
              {t('landing.transparentOps')}
            </span>
            <h2 className="font-headline-lg text-3xl font-bold text-on-surface mt-1">
              {t('landing.howCivicFixResolves')}
            </h2>
            <p className="text-on-surface-variant text-sm mt-2">
              {t('landing.howCivicFixSubtitle')}
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {[
              {
                step: '01',
                title: t('landing.step1Title'),
                desc: t('landing.step1Desc'),
                icon: 'photo_camera',
                color: 'bg-primary-container text-white'
              },
              {
                step: '02',
                title: t('landing.step2Title'),
                desc: t('landing.step2Desc'),
                icon: 'rule',
                color: 'bg-secondary text-white'
              },
              {
                step: '03',
                title: t('landing.step3Title'),
                desc: t('landing.step3Desc'),
                icon: 'send_and_archive',
                color: 'bg-amber-600 text-white'
              },
              {
                step: '04',
                title: t('landing.step4Title'),
                desc: t('landing.step4Desc'),
                icon: 'verified',
                color: 'bg-emerald-600 text-white'
              }
            ].map((s) => (
              <div
                key={s.step}
                className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-outline-variant/40 flex flex-col justify-between hover:shadow-md transition-shadow"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="font-code text-sm font-bold text-tertiary">{t('landing.stage')} {s.step}</span>
                    <div className={`w-10 h-10 rounded-xl ${s.color} flex items-center justify-center shadow-sm`}>
                      <span className="material-symbols-outlined text-[20px]">{s.icon}</span>
                    </div>
                  </div>
                  <h3 className="font-title text-base font-bold text-on-surface mb-2">{s.title}</h3>
                  <p className="text-body-sm text-xs text-on-surface-variant leading-relaxed">{s.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Feature Showcase Grid */}
      <section id="features" className="scroll-mt-20 w-full py-16 bg-surface">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-4">
            <div>
              <span className="text-primary-container font-semibold text-xs uppercase tracking-wider">
                {t('landing.fullStackArch')}
              </span>
              <h2 className="font-headline-lg text-3xl font-bold text-on-surface mt-1">
                {t('landing.engineeredCityScale')}
              </h2>
              <p className="text-on-surface-variant text-sm mt-1">
                {t('landing.engineeredSubtitle')}
              </p>
            </div>
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-primary-container font-semibold hover:underline text-sm"
            >
              <span>{t('landing.exploreAll4Workspaces')}</span>
              <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
            </Link>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              {
                role: t('landing.citizenPortal'),
                path: '/citizen/dashboard',
                icon: 'person',
                desc: t('landing.citizenPortalDesc'),
                badge: t('landing.citizenBadge')
              },
              {
                role: t('landing.municipalOfficer'),
                path: '/officer/dashboard',
                icon: 'account_balance',
                desc: t('landing.municipalOfficerDesc'),
                badge: t('landing.officerBadge')
              },
              {
                role: t('landing.fieldWorkerCompanion'),
                path: '/worker/dashboard',
                icon: 'engineering',
                desc: t('landing.fieldWorkerDesc'),
                badge: t('landing.workerBadge')
              },
              {
                role: t('landing.adminSuite'),
                path: '/admin/dashboard',
                icon: 'admin_panel_settings',
                desc: t('landing.adminDesc'),
                badge: t('landing.adminBadge')
              }
            ].map((card) => (
              <div
                key={card.role}
                className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-outline-variant/40 hover:border-primary-container/40 flex flex-col justify-between hover:shadow-md transition-all group"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <div className="w-10 h-10 rounded-xl bg-surface-container-low group-hover:bg-primary-container/10 group-hover:text-primary-container transition-colors flex items-center justify-center text-on-surface">
                      <span className="material-symbols-outlined text-[22px]">{card.icon}</span>
                    </div>
                    <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface text-[10px] font-semibold">
                      {card.badge}
                    </span>
                  </div>
                  <h3 className="font-title text-base font-bold text-on-surface mb-2">{card.role}</h3>
                  <p className="text-body-sm text-xs text-on-surface-variant leading-relaxed">{card.desc}</p>
                </div>
                <div className="mt-6 pt-4 border-t border-outline-variant/30">
                  <Link
                    to={card.path}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-primary-container hover:text-primary transition-colors"
                  >
                    <span>{t('landing.launchWorkspace')}</span>
                    <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Impact & Metrics Section */}
      <section id="impact" className="scroll-mt-20 w-full py-16 bg-surface-container-low border-t border-outline-variant/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-2xl mx-auto mb-8">
            <span className="text-secondary font-semibold text-xs uppercase tracking-wider">
              {t('landing.telemetryBadge')}
            </span>
            <h2 className="font-headline-lg text-2xl font-bold text-on-surface mt-1">
              {t('landing.telemetryTitle')}
            </h2>
            <p className="text-xs text-tertiary mt-1">
              {t('landing.telemetrySubtitle')}
            </p>
          </div>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-6 text-center">
            <div className="p-6 bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/40">
              <p className="text-4xl font-extrabold text-primary-container font-display">14,820+</p>
              <p className="text-sm font-semibold text-on-surface mt-1">{t('landing.verifiedFixes')}</p>
              <p className="text-xs text-tertiary mt-0.5">{t('landing.verifiedFixesSub')}</p>
            </div>
            <div className="p-6 bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/40">
              <p className="text-4xl font-extrabold text-secondary font-display">&lt; 14m</p>
              <p className="text-sm font-semibold text-on-surface mt-1">{t('landing.triageSpeed')}</p>
              <p className="text-xs text-tertiary mt-0.5">{t('landing.triageSpeedSub')}</p>
            </div>
            <div className="p-6 bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/40">
              <p className="text-4xl font-extrabold text-primary-container font-display">99.4%</p>
              <p className="text-sm font-semibold text-on-surface mt-1">{t('landing.routingAccuracy')}</p>
              <p className="text-xs text-tertiary mt-0.5">{t('landing.routingAccuracySub')}</p>
            </div>
            <div className="p-6 bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/40">
              <p className="text-4xl font-extrabold text-emerald-600 font-display">48h</p>
              <p className="text-sm font-semibold text-on-surface mt-1">{t('landing.p1Sla')}</p>
              <p className="text-xs text-tertiary mt-0.5">{t('landing.p1SlaSub')}</p>
            </div>
          </div>
        </div>
      </section>

      {/* Bottom CTA Banner */}
      <section className="w-full py-16 bg-[#151D26] text-white">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 text-center space-y-6">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-xs font-semibold text-primary-fixed">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span>{t('landing.bottomGridOnline')}</span>
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight">
            {t('landing.bottomTitle')}
          </h2>
          <p className="text-gray-300 max-w-2xl mx-auto text-sm">
            {t('landing.bottomSubtitle')}
          </p>
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            <Link
              to="/citizen/dashboard"
              className="bg-primary-container hover:bg-primary text-white font-semibold px-6 py-3 rounded-xl shadow-md transition-all"
            >
              {t('landing.startCitizen')}
            </Link>
            <Link
              to="/officer/dashboard"
              className="bg-white/10 hover:bg-white/20 text-white font-semibold px-6 py-3 rounded-xl border border-white/20 transition-all"
            >
              {t('landing.startOfficer')}
            </Link>
            <Link
              to="/login"
              className="bg-white text-[#151D26] hover:bg-gray-100 font-semibold px-6 py-3 rounded-xl shadow-md transition-all"
            >
              {t('landing.unifiedSignIn')}
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
};
export default LandingPage;
