import type { PaletteMode } from '@mui/material'

// Theme preference is per-user, not a single global browser setting - each authenticated user's
// choice is namespaced under their own stable auth-service user id (UserInfo.id), never their
// role (a user can hold multiple roles, but still has exactly one personal preference) and never
// their email/mobile/username (those can change; the id doesn't).
const STORAGE_KEY_PREFIX = 'theme_preference_'

function keyFor(userId: string): string {
  return `${STORAGE_KEY_PREFIX}${userId}`
}

export function getUserTheme(userId: string): PaletteMode | null {
  const stored = localStorage.getItem(keyFor(userId))
  return stored === 'light' || stored === 'dark' ? stored : null
}

export function setUserTheme(userId: string, mode: PaletteMode): void {
  localStorage.setItem(keyFor(userId), mode)
}

export function clearUserTheme(userId: string): void {
  localStorage.removeItem(keyFor(userId))
}
