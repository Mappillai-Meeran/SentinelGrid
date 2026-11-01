export interface User {
  id: number;
  username: string;
  email: string;
  role: 'PATIENT' | 'PHARMACIST' | 'PHARMACY_MANAGER' | 'ADMIN';
  pharmacyId?: number | null;
  pharmacyName?: string | null;
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
  role: 'PATIENT' | 'PHARMACIST' | 'PHARMACY_MANAGER' | 'ADMIN';
  pharmacyId?: number | null;
  pharmacyName?: string | null;
}

export interface Pharmacy {
  id: number;
  name: string;
  address: string;
  city: string;
  latitude: number;
  longitude: number;
  contactNumber: string;
  openingTime?: string;
  closingTime?: string;
  is24Seven?: boolean;
  timezone?: string;
  operatingHours?: string;
  status?: 'OPEN' | 'CLOSED' | 'UNKNOWN';
  isOpen?: boolean | null;
}

export interface Medicine {
  id: number;
  name: string;
  brand: string;
  category: string;
  description: string;
  dosage: string;
  requiresPrescription: boolean;
}

export interface InventoryItem {
  id: number;
  pharmacyId: number;
  pharmacyName: string;
  pharmacyAddress?: string;
  pharmacyCity?: string;
  pharmacyLatitude?: number;
  pharmacyLongitude?: number;
  pharmacyContactNumber?: string;
  pharmacyOpeningTime?: string;
  pharmacyClosingTime?: string;
  pharmacyIs24Seven?: boolean;
  pharmacyTimezone?: string;
  pharmacyOperatingHours?: string;
  pharmacyStatus?: 'OPEN' | 'CLOSED' | 'UNKNOWN';
  pharmacyIsOpen?: boolean | null;
  roadDistanceKm?: number | null;
  estimatedMinutes?: number | null;
  distanceKm?: number | null;
  routingStatus?: 'SUCCESS' | 'UNAVAILABLE' | 'FALLBACK';
  medicineId: number;
  medicineName: string;
  medicineCategory?: string;
  medicineDosage?: string;
  medicineBrand?: string;
  requiresPrescription?: boolean;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
}

export interface Reservation {
  id: number;
  patientId: number;
  patientUsername: string;
  pharmacy: Pharmacy;
  medicine: Medicine;
  quantity: number;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
  expiresAt: string;
  remainingSeconds?: number;
  remainingMinutes?: number;
  expired?: boolean;
  confirmedAt?: string;
  cancelledAt?: string;
  createdAt: string;
  version: number;
  prescriptionNumber?: string | null;
  doctorName?: string | null;
  doctorRegNumber?: string | null;
  prescriptionVerified?: boolean;
  prescriptionVerifiedBy?: string | null;
  prescriptionVerifiedAt?: string | null;
}

export interface DashboardSummary {
  totalUsers: number;
  totalPharmacies: number;
  twentyFourSevenPharmacies?: number;
  standardHoursPharmacies?: number;
  totalMedicines: number;
  totalReservations: number;
  pendingReservations: number;
  confirmedReservations: number;
  expiredReservations: number;
  cancelledReservations: number;
  totalInventoryUnits: number;
}

export interface TopMedicine {
  medicineName: string;
  reservationCount: number;
}

export interface TopPharmacy {
  pharmacyName: string;
  reservationCount: number;
  completedCount?: number;
  concludedCount?: number;
  fulfillmentRate?: number | null;
}

export interface DailyReservation {
  date: string;
  count: number;
}

export interface LowStockItem {
  inventoryId: number;
  pharmacyName: string;
  medicineName: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
}

export interface AuditLog {
  id: number;
  entityName: string;
  entityId: number;
  action: string;
  performedBy: string;
  details: string;
  timestamp: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
  errors?: Record<string, string>;
}

export interface ManagerProfile {
  name: string;
  address: string;
  city: string;
  phone: string;
  hours: string;
  coverage: string;
  verificationStatus: string;
}

export interface ManagerRules {
  holdExpiryMinutes: number;
  lowStockThreshold: number;
  maxEmergencyHoldQuantity: number;
}

export interface ManagerStaff {
  id?: number;
  username: string;
  role: string;
  shift: string;
  status: string;
  reservationsHandled: number;
}

export interface ManagerWorkspace {
  profile: ManagerProfile;
  rules: ManagerRules;
  staff: ManagerStaff[];
  reports: {
    stockRisks: Array<{ medicine: string; available: number; threshold: number }>;
    reservationOutcomes: Array<{ name: string; value: number; color: string }>;
  };
}

export interface ObservabilityMetric {
  name: string;
  value: string;
  detail: string;
  status: 'AVAILABLE' | 'UNAVAILABLE' | string;
}

export interface Observability {
  healthStatus: string;
  metrics: ObservabilityMetric[];
}

export interface RouteEstimate {
  pharmacyId: number;
  distanceKm?: number | null;
  durationMinutes?: number | null;
  status: 'SUCCESS' | 'UNAVAILABLE';
}

export interface RoutingEstimateResponse {
  routes: RouteEstimate[];
}
