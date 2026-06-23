import { useSelector, useDispatch } from 'react-redux'
import type { RootState, AppDispatch } from '../store/store'

export function useAuth() {
  const dispatch = useDispatch<AppDispatch>()
  // TODO: connect to auth slice once implemented
  const isAuthenticated = false
  const user = null

  return { isAuthenticated, user, dispatch }
}
