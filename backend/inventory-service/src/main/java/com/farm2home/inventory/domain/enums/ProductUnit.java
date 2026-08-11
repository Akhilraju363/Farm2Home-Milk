package com.farm2home.inventory.domain.enums;

/** Unit of sale for a customer-facing Product (e.g. "1L" of milk, "500G" of paneer) - distinct
 *  from InventoryItem's UnitType, which measures farm-supply stock (feed/medicine/equipment) and
 *  uses a different, non-overlapping set of units for that reason. */
public enum ProductUnit {
    L, ML, KG, G, PACK, DOZEN, PIECE
}
