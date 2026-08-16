// Gates any future non-essential tracker/analytics script behind explicit consent (DPDP Act 2023
// s.6 - consent must be freely given, informed, and specific; a script that fires before the user
// has chosen would not be). As of this audit (see DPDP_PROGRESS.md) Farm2Home ships NO
// third-party trackers, analytics SDKs, or cookies at all - this is deliberately forward-looking
// scaffolding so the first tracker ever added has somewhere safe to plug into, rather than being
// wired in ungated the day someone adds one.

const STORAGE_KEY = 'f2h_tracker_consent'

interface TrackerConsentState {
  analyticsGranted: boolean
  decidedAt: string
}

function read(): TrackerConsentState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as TrackerConsentState) : null
  } catch {
    return null
  }
}

export function hasDecidedTrackerConsent(): boolean {
  return read() !== null
}

export function isAnalyticsConsentGranted(): boolean {
  return read()?.analyticsGranted ?? false
}

export function setTrackerConsent(analyticsGranted: boolean): void {
  const state: TrackerConsentState = { analyticsGranted, decidedAt: new Date().toISOString() }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

/** Call this instead of appending a <script> tag directly for any future analytics/marketing
 *  tracker - it's a no-op until the user has granted the ANALYTICS_COOKIES purpose via
 *  ConsentBanner (or the equivalent settings toggle). Loads at most once per src. */
const loadedSrcs = new Set<string>()

export function loadTrackerScript(src: string, attrs: Record<string, string> = {}): void {
  if (!isAnalyticsConsentGranted() || loadedSrcs.has(src)) return
  const script = document.createElement('script')
  script.src = src
  script.async = true
  Object.entries(attrs).forEach(([key, value]) => script.setAttribute(key, value))
  document.head.appendChild(script)
  loadedSrcs.add(src)
}
