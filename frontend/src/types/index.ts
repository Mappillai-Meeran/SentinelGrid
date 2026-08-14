export interface User {
  id: number;
  username: string;
  email: string;
  role: 'PATIENT' | 'PHARMACIST' | 'ADMIN';
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
  role: 'PATIENT' | 'PHARMACIST' | 'ADMIN';
}

export interface Pharmacy {
  id: number;
  name: string;
  address: string;
  city: string;
  latitude: number;
  longitude: number;
  contactNumber: string;
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
  medicineId: number;
  medicineName: string;
  medicineCategory?: string;
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
}

export interface DashboardSummary {
  totalUsers: number;
  totalPharmacies: number;
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
