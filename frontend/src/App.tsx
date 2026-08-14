import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/layout/Layout';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { EmergencyOperationsPage } from './pages/EmergencyOperationsPage';
import { PatientDashboardPage } from './pages/PatientDashboardPage';
import { MyReservationsPage } from './pages/MyReservationsPage';
import { PharmacistInventoryPage } from './pages/PharmacistInventoryPage';
import { PharmacyNetworkPage } from './pages/PharmacyNetworkPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AuditPage } from './pages/AuditPage';
import { SystemHealthPage } from './pages/SystemHealthPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode; roles?: string[] }> = ({ children, roles }) => {
  const { isAuthenticated, user } = useAuthStore();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <Layout>{children}</Layout>;
};

export const App: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <OverviewPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/operations"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <EmergencyOperationsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/search"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <PatientDashboardPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/reservations"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <MyReservationsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/pharmacies"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <PharmacyNetworkPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/inventory"
          element={
            <ProtectedRoute roles={['PHARMACIST', 'ADMIN']}>
              <PharmacistInventoryPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/ai-search"
          element={
            <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'ADMIN']}>
              <EmergencyOperationsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/analytics"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AnalyticsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/audit"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AuditPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/system-health"
          element={
            <ProtectedRoute roles={['ADMIN', 'PHARMACIST', 'PATIENT']}>
              <SystemHealthPage />
            </ProtectedRoute>
          }
        />

        <Route path="*" element={<Navigate to={user ? '/' : '/login'} replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
