export interface MilkProduction {
  id: string
  cowId: string
  collectionDate: string
  session: MilkSession
  quantityLiters: number
  fatPercentage?: number
  snfPercentage?: number
  qualityGrade?: QualityGrade
  collectedBy?: string
  notes?: string
  createdAt: string
  createdBy: string
}

export interface DailySummary {
  date: string
  totalLiters: number
  recordCount: number
}

export type MilkSession = 'MORNING' | 'EVENING'
export type QualityGrade = 'A' | 'B' | 'C'
