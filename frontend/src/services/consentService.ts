import axiosClient from './axiosClient'
import type { ApiResponse } from '../types/common.types'
import type { ConsentChoice, ConsentPurpose, ConsentRecord } from '../types/consent.types'

const BASE = '/customers/me/consents'

const RETRY_ATTEMPTS = 4
const RETRY_DELAY_MS = 500

/** Same async-Kafka-creation race as customerService.addAddress - the customer row this consent
 *  is scoped to may not exist yet immediately after registration, so a 404 here is retried rather
 *  than treated as a hard failure. */
async function record(choices: ConsentChoice[], attempt = 1): Promise<ConsentRecord[]> {
  try {
    const res = await axiosClient.post<ApiResponse<ConsentRecord[]>>(BASE, { consents: choices })
    return res.data.data
  } catch (err: any) {
    if (err.response?.status === 404 && attempt < RETRY_ATTEMPTS) {
      await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS))
      return record(choices, attempt + 1)
    }
    throw err
  }
}

export const consentService = {
  record,

  getAll: () =>
    axiosClient.get<ApiResponse<ConsentRecord[]>>(BASE),

  setOne: (purpose: ConsentPurpose, granted: boolean) =>
    axiosClient.put<ApiResponse<ConsentRecord>>(`${BASE}/${purpose}`, { granted }),
}
