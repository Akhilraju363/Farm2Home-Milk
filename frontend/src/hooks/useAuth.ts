import { useSelector, useDispatch } from 'react-redux'
import type { RootState, AppDispatch } from '../store/store'
import { logout } from '../store/slices/authSlice'

export function useAuth() {
  const dispatch = useDispatch<AppDispatch>()
  const { user, isAuthenticated, accessToken } = useSelector((state: RootState) => state.auth)

  const logoutUser = () => {
    dispatch(logout())
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  const isAdmin = () =>
    user?.roles.some((r) => r === 'SUPER_ADMIN' || r === 'FARM_MANAGER' || r === 'DELIVERY_MANAGER') ?? false

  const isDeliveryPartner = () =>
    user?.roles.some((r) => r === 'DELIVERY_PARTNER') ?? false

  const isCustomer = () =>
    user?.roles.some((r) => r === 'CUSTOMER') ?? false

  return { user, isAuthenticated, accessToken, logoutUser, isAdmin, isDeliveryPartner, isCustomer, dispatch }
}
