import type { InventoryItem } from '../types';

export interface RecommendationResult {
  recommendedItem: InventoryItem | null;
  reason: string;
  hasValidStockAndOpen: boolean;
}

/**
 * Pure, deterministic function to rank pharmacy inventory search results
 * using emergency factors:
 * 1. Available Stock (in stock > out of stock)
 * 2. Operating Status (OPEN > UNKNOWN > CLOSED) -- UNKNOWN is never treated as OPEN
 * 3. Shortest Estimated Driving Time (estimatedMinutes via ORS)
 * 4. Shortest Road Distance (roadDistanceKm via ORS)
 * 5. Shortest Haversine Distance Fallback (distanceKm)
 * 6. Original list order as deterministic tie-breaker
 */
export const rankPharmacies = (
  items: InventoryItem[],
  userLocation?: { latitude: number; longitude: number } | null
): InventoryItem[] => {
  if (!items || items.length === 0) return [];

  return [...items].sort((a, b) => {
    // Factor 1: Available stock (in stock > out of stock)
    const aInStock = a.availableQuantity > 0 ? 1 : 0;
    const bInStock = b.availableQuantity > 0 ? 1 : 0;
    if (aInStock !== bInStock) return bInStock - aInStock;

    // Factor 2: Operating status (OPEN > UNKNOWN > CLOSED)
    const statusScore = (status?: string) =>
      status === 'OPEN' ? 3 : status === 'UNKNOWN' ? 2 : 1;
    const aStatusScore = statusScore(a.pharmacyStatus);
    const bStatusScore = statusScore(b.pharmacyStatus);
    if (aStatusScore !== bStatusScore) return bStatusScore - aStatusScore;

    // Factors 3-5: Travel duration / Road distance / Haversine distance (if patient location active)
    if (userLocation && userLocation.latitude != null && userLocation.longitude != null) {
      // Travel Time (estimatedMinutes)
      if (a.estimatedMinutes != null && b.estimatedMinutes != null) {
        if (a.estimatedMinutes !== b.estimatedMinutes) return a.estimatedMinutes - b.estimatedMinutes;
      }
      if (a.estimatedMinutes != null && b.estimatedMinutes == null) return -1;
      if (a.estimatedMinutes == null && b.estimatedMinutes != null) return 1;

      // Road Distance (roadDistanceKm)
      if (a.roadDistanceKm != null && b.roadDistanceKm != null) {
        if (a.roadDistanceKm !== b.roadDistanceKm) return a.roadDistanceKm - b.roadDistanceKm;
      }
      if (a.roadDistanceKm != null && b.roadDistanceKm == null) return -1;
      if (a.roadDistanceKm == null && b.roadDistanceKm != null) return 1;

      // Haversine Fallback Distance (distanceKm)
      if (a.distanceKm != null && b.distanceKm != null) {
        if (a.distanceKm !== b.distanceKm) return a.distanceKm - b.distanceKm;
      }
      if (a.distanceKm != null && b.distanceKm == null) return -1;
      if (a.distanceKm == null && b.distanceKm != null) return 1;
    }

    // Factor 6: Original list order as deterministic tie-breaker
    return 0;
  });
};

/**
 * Deterministically evaluates pre-ranked or unranked inventory search results and returns
 * the recommended pharmacy along with a factual, clear explanation of why it was selected.
 */
export const getRecommendedPharmacy = (
  sortedItems: InventoryItem[]
): RecommendationResult => {
  if (!sortedItems || sortedItems.length === 0) {
    return {
      recommendedItem: null,
      reason: 'No pharmacies matching search criteria.',
      hasValidStockAndOpen: false,
    };
  }

  // Filter items that have stock available (> 0)
  const inStockItems = sortedItems.filter((item) => item.availableQuantity > 0);

  if (inStockItems.length === 0) {
    return {
      recommendedItem: null,
      reason: 'No currently matching pharmacy has verified stock available.',
      hasValidStockAndOpen: false,
    };
  }

  // Look for top in-stock item that is OPEN
  const openInStockItems = inStockItems.filter((item) => item.pharmacyStatus === 'OPEN');

  if (openInStockItems.length > 0) {
    const top = openInStockItems[0];
    let reason = 'In stock and currently open';

    if (top.estimatedMinutes != null) {
      reason = 'Fastest estimated drive among open pharmacies with stock';
    } else if (top.roadDistanceKm != null) {
      reason = 'Nearest open pharmacy with available stock (road distance)';
    } else if (top.distanceKm != null) {
      reason = 'Nearest open pharmacy with available stock (approximate distance)';
    } else if (top.pharmacyIs24Seven) {
      reason = 'In stock at 24x7 emergency pharmacy';
    }

    return {
      recommendedItem: top,
      reason,
      hasValidStockAndOpen: true,
    };
  }

  // Fallback to top in-stock item with UNKNOWN hours
  const unknownHoursInStock = inStockItems.filter((item) => item.pharmacyStatus === 'UNKNOWN');
  if (unknownHoursInStock.length > 0) {
    const top = unknownHoursInStock[0];
    let reason = 'In stock (operating schedule unverified)';
    if (top.estimatedMinutes != null) {
      reason = 'In stock with shortest estimated drive (hours unverified)';
    } else if (top.distanceKm != null) {
      reason = 'In stock at nearest pharmacy (hours unverified)';
    }
    return {
      recommendedItem: top,
      reason,
      hasValidStockAndOpen: false,
    };
  }

  // If all in-stock pharmacies are CLOSED, do not recommend a closed pharmacy as best option
  return {
    recommendedItem: null,
    reason: 'Pharmacies with stock are currently closed according to configured schedules.',
    hasValidStockAndOpen: false,
  };
};

