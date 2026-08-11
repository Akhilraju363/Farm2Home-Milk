// India administrative location reference data (State -> District -> City) used by the
// registration Address step's cascading dropdowns. Distinct from types/location.types.ts, which
// models GPS delivery-partner location tracking - unrelated concept, deliberately named
// differently to avoid confusion between the two.
export interface IndiaState {
  id: string
  name: string
  code: string
}

export interface IndiaDistrict {
  id: string
  name: string
  code: string
}

export interface IndiaCity {
  id: string
  name: string
}
