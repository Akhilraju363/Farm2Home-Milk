export interface InventoryItem {
  id: string
  itemName: string
  itemType: ItemType
  quantity: number
  unit: UnitType
  reorderLevel: number
  belowReorderLevel: boolean
  unitPrice?: number
  supplier?: string
  createdAt: string
  createdBy: string
}

export interface StockTransaction {
  id: string
  itemId: string
  itemName: string
  txnType: 'IN' | 'OUT'
  quantity: number
  reason?: string
  referenceId?: string
  transactedAt: string
  createdBy: string
}

export type ItemType = 'FEED' | 'MEDICINE' | 'EQUIPMENT'
export type UnitType = 'KG' | 'LITRE' | 'PIECE' | 'BOX'
