import axios from 'axios'
import { tokenStorage } from './tokenStorage'

const axiosClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

axiosClient.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      tokenStorage.clear()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default axiosClient
