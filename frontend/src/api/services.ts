import api from './client';
import type {
  InventoryItem,
  Reservation,
  DashboardSummary,
  LowStockItem,
  AuditLog,
  TopMedicine,
  TopPharmacy,
  DailyReservation,
  ManagerProfile,
  ManagerRules,
  ManagerStaff,
  ManagerWorkspace,
  Observability,
  RoutingEstimateResponse,
} from '../types';

/**
 * Normalizes backend InventoryDto into a flat InventoryItem structure.
 */
export const mapInventoryDto = (dto: any): InventoryItem => {
  return {
    id: dto.id,
    pharmacyId: dto.pharmacy?.id ?? dto.pharmacyId,
    pharmacyName: dto.pharmacy?.name ?? dto.pharmacyName,
    pharmacyAddress: dto.pharmacy?.address ?? dto.pharmacyAddress,
    pharmacyCity: dto.pharmacy?.city ?? dto.pharmacyCity,
    pharmacyLatitude: dto.pharmacy?.latitude ?? dto.pharmacyLatitude,
    pharmacyLongitude: dto.pharmacy?.longitude ?? dto.pharmacyLongitude,
    pharmacyContactNumber: dto.pharmacy?.contactNumber ?? dto.pharmacyContactNumber,
    pharmacyOpeningTime: dto.pharmacy?.openingTime ?? dto.pharmacyOpeningTime,
    pharmacyClosingTime: dto.pharmacy?.closingTime ?? dto.pharmacyClosingTime,
    pharmacyIs24Seven: dto.pharmacy?.is24Seven ?? dto.pharmacyIs24Seven,
    pharmacyTimezone: dto.pharmacy?.timezone ?? dto.pharmacyTimezone,
    pharmacyOperatingHours: dto.pharmacy?.operatingHours ?? dto.pharmacyOperatingHours,
    pharmacyStatus: dto.pharmacy?.status ?? dto.pharmacyStatus ?? 'UNKNOWN',
    pharmacyIsOpen: dto.pharmacy?.isOpen ?? dto.pharmacyIsOpen ?? null,
    medicineId: dto.medicine?.id ?? dto.medicineId,
    medicineName: dto.medicine?.name ?? dto.medicineName,
    medicineCategory: dto.medicine?.category ?? dto.medicineCategory ?? 'EMERGENCY',
    medicineDosage: dto.medicine?.dosage ?? dto.medicineDosage,
    medicineBrand: dto.medicine?.brand ?? dto.medicineBrand,
    requiresPrescription: dto.medicine?.requiresPrescription ?? dto.requiresPrescription ?? false,
    quantity: dto.totalQuantity !== undefined ? dto.totalQuantity : dto.quantity ?? 0,
    reservedQuantity: dto.reservedQuantity ?? 0,
    availableQuantity: dto.availableQuantity ?? 0,
  };
};

export const medicineService = {
  async searchMedicines(
    name?: string,
    city?: string,
    latitude?: number | null,
    longitude?: number | null,
    radiusKm?: number
  ): Promise<InventoryItem[]> {
    const apiCity = city && (city.toLowerCase() === 'bengaluru' || city.toLowerCase() === 'bangalore')
      ? 'Bangalore'
      : city;
    const params: Record<string, any> = { name, city: apiCity };
    if (latitude != null && longitude != null) {
      params.latitude = latitude;
      params.longitude = longitude;
      if (radiusKm != null && radiusKm > 0) {
        params.radiusKm = radiusKm;
      }
    }
    const res = await api.get('/medicines/search', { params });
    const data = res.data.data || [];
    return data.map(mapInventoryDto);
  },
};

export const aiService = {
  async searchAssist(query: string, city: string) {
    const apiCity = city && (city.toLowerCase() === 'bengaluru' || city.toLowerCase() === 'bangalore')
      ? 'Bangalore'
      : city;
    const res = await api.post('/ai/search-assist', { prompt: query, query, city: apiCity });
    return res.data.data;
  },
};

export const reservationService = {
  async createReservation(
    pharmacyId: number,
    medicineId: number,
    quantity: number = 1,
    prescriptionDetails?: {
      prescriptionNumber?: string;
      doctorName?: string;
      doctorRegNumber?: string;
    },
    idempotencyKey?: string
  ): Promise<Reservation> {
    const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `key-${Date.now()}-${Math.random()}`);
    const payload = {
      pharmacyId,
      medicineId,
      quantity,
      ...(prescriptionDetails || {}),
    };
    const res = await api.post('/reservations', payload, {
      headers: {
        'Idempotency-Key': key,
      },
    });
    return res.data.data;
  },

  async verifyPrescription(id: number): Promise<Reservation> {
    const res = await api.post(`/reservations/${id}/verify-prescription`);
    return res.data.data;
  },

  async getMyReservations(status?: string): Promise<Reservation[]> {
    const url = !status || status === 'ALL' ? '/reservations/my' : `/reservations/my?status=${status}`;
    const res = await api.get(url);
    return res.data.data || [];
  },

  async getReservation(id: number): Promise<Reservation> {
    const res = await api.get(`/reservations/${id}`);
    return res.data.data;
  },

  async confirmPickup(id: number): Promise<Reservation> {
    const res = await api.post(`/reservations/${id}/confirm`);
    return res.data.data;
  },

  async cancelReservation(id: number): Promise<Reservation> {
    const res = await api.post(`/reservations/${id}/cancel`);
    return res.data.data;
  },
};

export const pharmacyService = {
  async getPharmacyReservations(pharmacyId: number, status?: string): Promise<Reservation[]> {
    const url = !status || status === 'ALL'
      ? `/pharmacies/${pharmacyId}/reservations`
      : `/pharmacies/${pharmacyId}/reservations?status=${status}`;
    const res = await api.get(url);
    return res.data.data || [];
  },

  async getPharmacyInventory(pharmacyId: number): Promise<InventoryItem[]> {
    const res = await api.get(`/pharmacies/${pharmacyId}/inventory`);
    const data = res.data.data || [];
    return data.map(mapInventoryDto);
  },

  async addInventory(pharmacyId: number, medicineId: number, quantity: number): Promise<InventoryItem> {
    const res = await api.post(`/pharmacies/${pharmacyId}/inventory`, { medicineId, quantity });
    return mapInventoryDto(res.data.data);
  },

  async adjustInventory(pharmacyId: number, inventoryId: number, delta: number): Promise<InventoryItem> {
    const res = await api.patch(`/pharmacies/${pharmacyId}/inventory/${inventoryId}/adjust`, { delta });
    return mapInventoryDto(res.data.data);
  },

  async deleteInventory(pharmacyId: number, inventoryId: number): Promise<void> {
    await api.delete(`/pharmacies/${pharmacyId}/inventory/${inventoryId}`);
  },
};

export const dashboardService = {
  async getSummary(): Promise<DashboardSummary> {
    const res = await api.get('/admin/dashboard/summary');
    return res.data.data;
  },

  async getLowStock(threshold: number = 10): Promise<LowStockItem[]> {
    const res = await api.get(`/admin/dashboard/inventory/low-stock?threshold=${threshold}`);
    return res.data.data || [];
  },

  async getTopMedicines(limit: number = 5): Promise<TopMedicine[]> {
    const res = await api.get(`/admin/dashboard/top-medicines?limit=${limit}`);
    return res.data.data || [];
  },

  async getTopPharmacies(limit: number = 5): Promise<TopPharmacy[]> {
    const res = await api.get(`/admin/dashboard/pharmacies/top?limit=${limit}`);
    return res.data.data || [];
  },

  async getDailyTrend(days: number = 7): Promise<DailyReservation[]> {
    const res = await api.get(`/admin/dashboard/reservations/daily?days=${days}`);
    return res.data.data || [];
  },

  async getAuditLogs(): Promise<AuditLog[]> {
    const res = await api.get('/admin/dashboard/audit-logs');
    return res.data.data || [];
  },
};

export const managerService = {
  async getWorkspace(): Promise<ManagerWorkspace> {
    const res = await api.get('/manager/workspace');
    return res.data.data;
  },

  async updateProfile(profile: ManagerProfile): Promise<ManagerProfile> {
    const res = await api.put('/manager/workspace/profile', profile);
    return res.data.data;
  },

  async updateRules(rules: ManagerRules): Promise<ManagerRules> {
    const res = await api.put('/manager/workspace/rules', rules);
    return res.data.data;
  },

  async saveStaff(staff: ManagerStaff): Promise<ManagerStaff> {
    const res = await api.post('/manager/workspace/staff', staff);
    return res.data.data;
  },

  async updateStaffStatus(id: number, status: string): Promise<ManagerStaff> {
    const res = await api.put(`/manager/workspace/staff/${id}/status/${status}`);
    return res.data.data;
  },

  async deleteStaff(id: number): Promise<void> {
    await api.delete(`/manager/workspace/staff/${id}`);
  },
};

export const systemService = {
  async getObservability(): Promise<Observability> {
    const res = await api.get('/system/observability');
    return res.data.data;
  },
};

export const routingService = {
  async getRouteEstimates(
    origin: { latitude: number; longitude: number },
    destinations: Array<{ id: number; latitude: number; longitude: number }>
  ): Promise<RoutingEstimateResponse> {
    const res = await api.post('/routing/estimate', { origin, destinations });
    return res.data.data;
  },
};
