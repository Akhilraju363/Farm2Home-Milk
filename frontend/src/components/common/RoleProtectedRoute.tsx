import { Outlet } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { PermissionDenied } from './PermissionDenied'
import type { Role } from '../../types/auth.types'

interface Props {
  allowedRoles: Role[]
}

/** Nest inside the existing <ProtectedRoute> (which already redirects unauthenticated visitors to
 *  /login) - by the time this runs the user IS authenticated, so it only needs to check role.
 *  Route-level gating like this is a UX convenience only; the backend's own @PreAuthorize checks
 *  remain the actual authority (see each service's SecurityConfig). */
export function RoleProtectedRoute({ allowedRoles }: Props) {
  const { user } = useAuth()
  const allowed = user?.roles.some((r) => allowedRoles.includes(r)) ?? false
  return allowed ? <Outlet /> : <PermissionDenied />
}
