import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { InventoryItem } from '../types';
import { Plus, Trash2, ShieldCheck } from 'lucide-react';

export const PharmacistInventoryPage: React.FC = () => {
  const pharmacyId = 1;
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  // Form Modal States
  const [medicineId, setMedicineId] = useState<number>(1);
  const [quantity, setQuantity] = useState<number>(50);
  const [showAddModal, setShowAddModal] = useState(false);

  // Confirm Pickup Modal State
  const [confirmResId, setConfirmResId] = useState<string>('');
  const [confirmMsg, setConfirmMsg] = useState<string>('');

  const fetchInventory = async () => {
    setLoading(true);
    try {
      const res = await api.get(`/pharmacies/${pharmacyId}/inventory`);
      setInventory(res.data.data || []);
    } catch (err) {
      console.error('Failed to fetch inventory', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInventory();
  }, [pharmacyId]);

  const handleAddStock = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post(`/pharmacies/${pharmacyId}/inventory`, {
        medicineId: Number(medicineId),
        quantity: Number(quantity),
      });
      setMessage('Stock record created successfully!');
      setShowAddModal(false);
      fetchInventory();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to add stock');
    }
  };

  const handleAdjustDelta = async (inventoryId: number, delta: number) => {
    try {
      await api.patch(`/pharmacies/${pharmacyId}/inventory/${inventoryId}/adjust`, { delta });
      setMessage(`Inventory #${inventoryId} adjusted by ${delta > 0 ? '+' : ''}${delta}`);
      fetchInventory();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to adjust stock');
    }
  };

  const handleDeleteInventory = async (inventoryId: number) => {
    if (!confirm('Are you sure you want to delete this stock entry?')) return;
    try {
      await api.delete(`/pharmacies/${pharmacyId}/inventory/${inventoryId}`);
      setMessage(`Inventory #${inventoryId} deleted.`);
      fetchInventory();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to delete inventory');
    }
  };

  const handleConfirmPickup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!confirmResId) return;
    try {
      await api.post(`/reservations/${confirmResId}/confirm`);
      setConfirmMsg(`Pickup confirmed for Reservation #${confirmResId}! Stock updated.`);
      setConfirmResId('');
      fetchInventory();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to confirm pickup');
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Pharmacy Inventory Management</h2>
          <p className="text-xs text-slate-500">Manage live stock, adjust availability, and verify patient pickups</p>
        </div>

        <div className="flex gap-3">
          <button
            onClick={() => setShowAddModal(true)}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl text-xs flex items-center gap-2 shadow-xs"
          >
            <Plus className="w-4 h-4" /> Add Stock Record
          </button>
        </div>
      </div>

      {message && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-xl text-xs font-semibold">
          {message}
        </div>
      )}

      {/* Quick Pickup Verification Widget */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
        <h3 className="text-sm font-bold text-slate-800 mb-3 flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-blue-600" />
          Patient Pickup Counter Verification
        </h3>
        <form onSubmit={handleConfirmPickup} className="flex gap-3 max-w-md">
          <input
            type="number"
            required
            value={confirmResId}
            onChange={(e) => setConfirmResId(e.target.value)}
            placeholder="Enter Reservation ID (e.g. 1)"
            className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500"
          />
          <button
            type="submit"
            className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl text-xs shadow-xs"
          >
            Confirm Pickup &amp; Deduct Stock
          </button>
        </form>
        {confirmMsg && <p className="text-xs font-bold text-emerald-600 mt-2">{confirmMsg}</p>}
      </div>

      {/* Stock Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex justify-between items-center">
          <h3 className="font-bold text-slate-800 text-sm">Live Inventory Stock</h3>
          <span className="text-xs text-slate-500">Pharmacy ID #{pharmacyId}</span>
        </div>

        {loading ? (
          <div className="p-8 text-center text-xs text-slate-500 font-medium">Loading pharmacy stock...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200">
                <tr>
                  <th className="p-4">ID</th>
                  <th className="p-4">Medicine Name</th>
                  <th className="p-4">Total Stock</th>
                  <th className="p-4">Reserved Stock</th>
                  <th className="p-4">Available</th>
                  <th className="p-4 text-right">Quick Stock Adjustment</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                {inventory.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50">
                    <td className="p-4 font-bold text-slate-900">#{item.id}</td>
                    <td className="p-4 font-semibold text-slate-800">{item.medicineName}</td>
                    <td className="p-4">{item.quantity} units</td>
                    <td className="p-4 text-amber-600 font-bold">{item.reservedQuantity} reserved</td>
                    <td className="p-4 font-bold text-emerald-600">{item.availableQuantity} available</td>
                    <td className="p-4 text-right space-x-2">
                      <button
                        onClick={() => handleAdjustDelta(item.id, +10)}
                        className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded"
                      >
                        +10
                      </button>
                      <button
                        onClick={() => handleAdjustDelta(item.id, -5)}
                        className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded"
                      >
                        -5
                      </button>
                      <button
                        onClick={() => handleDeleteInventory(item.id)}
                        className="px-2 py-1 bg-red-50 hover:bg-red-100 text-red-600 rounded"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Stock Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl p-6 max-w-md w-full border border-slate-200 shadow-2xl space-y-4">
            <h3 className="font-bold text-slate-900 text-base">Add New Inventory Stock</h3>
            <form onSubmit={handleAddStock} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Medicine ID</label>
                <select
                  value={medicineId}
                  onChange={(e) => setMedicineId(Number(e.target.value))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-800"
                >
                  <option value={1}>1 - Remdesivir 100mg Injection</option>
                  <option value={2}>2 - Tocilizumab 400mg</option>
                  <option value={3}>3 - Enoxaparin 40mg Injection</option>
                  <option value={4}>4 - Favipiravir 400mg</option>
                  <option value={5}>5 - Oxygen Cylinder 10L</option>
                  <option value={6}>6 - Anti-Venom Polyvalent Injection</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Initial Quantity</label>
                <input
                  type="number"
                  min={1}
                  required
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-800"
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 bg-slate-100 text-slate-600 rounded-xl text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-semibold shadow-xs"
                >
                  Save Stock Record
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
