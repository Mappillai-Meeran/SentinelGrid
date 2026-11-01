import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { PatientDashboardView } from '../components/dashboard/PatientDashboardView';
import { PharmacistDashboardView } from '../components/dashboard/PharmacistDashboardView';
import { AdminDashboardView } from '../components/dashboard/AdminDashboardView';

/**
 * OverviewPage acts as the role-aware dashboard dispatcher.
 * Strictly routes the authenticated user to their role-exclusive dedicated dashboard:
 * - PATIENT: CareLink personal healthcare dashboard
 * - PHARMACIST / PHARMACY_MANAGER: Scriptly PMS branch operational dashboard
 * - ADMIN: Co-Doctor enterprise command center
 */
export const OverviewPage: React.FC = () => {
  const { user } = useAuthStore();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  switch (user.role) {
    case 'PATIENT':
      return <PatientDashboardView />;

    case 'PHARMACIST':
    case 'PHARMACY_MANAGER':
      return <PharmacistDashboardView />;

    case 'ADMIN':
      return <AdminDashboardView />;

    default:
      return <Navigate to="/login" replace />;
  }
};

export default OverviewPage;
