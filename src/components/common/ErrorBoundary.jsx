import React from 'react';

/**
 * ErrorBoundary Component
 * Catches unhandled client-side render exceptions in child components.
 * Prevents full-screen React crashes, preserves navigation, and provides an accessible retry mechanism.
 */
export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('[CivicFix ErrorBoundary] Caught UI rendering exception:', error, errorInfo);
  }

  handleReload = () => {
    window.location.reload();
  };

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          aria-live="assertive"
          className="min-h-screen bg-surface flex items-center justify-center p-6 text-on-surface"
        >
          <div className="max-w-md w-full bg-surface-container-lowest p-8 rounded-2xl shadow-xl border border-outline-variant/30 text-center space-y-6">
            <div className="w-16 h-16 mx-auto rounded-full bg-error-container text-on-error-container flex items-center justify-center">
              <span className="material-symbols-outlined text-3xl">warning</span>
            </div>
            
            <div className="space-y-2">
              <h1 className="text-2xl font-bold font-headline tracking-tight text-on-surface">
                Something went wrong
              </h1>
              <p className="text-sm text-on-surface-variant leading-relaxed">
                An unexpected interface error occurred. Your submitted complaints and profile data are safe on the server.
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-3 justify-center pt-2">
              <button
                type="button"
                onClick={this.handleReload}
                className="px-5 py-2.5 rounded-xl bg-primary text-on-primary font-medium text-sm hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-all"
              >
                Reload Page
              </button>
              <button
                type="button"
                onClick={this.handleReset}
                className="px-5 py-2.5 rounded-xl border border-outline-variant/60 text-on-surface hover:bg-surface-container-low font-medium text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-all"
              >
                Go to Home
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
