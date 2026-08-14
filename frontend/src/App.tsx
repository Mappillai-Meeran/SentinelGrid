import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/layout/Layout';
import { LoginPage } from './pages/LoginPage';
import { PatientDashboardPage } from './pages/PatientDashboardPage';
import { MyReservationsPage } from './pages/MyReservationsPage';
import { PharmacistInventoryPage } from './pages/PharmacistInventoryPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode; roles?: string[] }> = ({ children, roles }) => {
  const { isAuthenticated, user } = useAuthStore();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/login" replace />;
  }

  return <Layout>{children}</Layout>;
};

export const App: React.FC = () => {
  const { user } = useAuthStore();

  const getDefaultRedirect = () => {
    if (!user) return '/login';
    if (user.role === 'ADMIN') return '/admin-dashboard';
    if (user.role === 'PHARMACIST') return '/inventory';
    return '/patient-dashboard';
  };

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/patient-dashboard"
          element={
            <ProtectedRoute roles={['PATIENT', 'ADMIN']}>
              <PatientDashboardPage />
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
          path="/my-reservations"
          element={
            <ProtectedRoute roles={['PATIENT', 'ADMIN']}>
              <MyReservationsPage />
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
          path="/admin-dashboard"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminDashboardPage />
            </ProtectedRoute>
          }
        />

        <Route path="*" element={<Navigate to={getDefaultRedirect()} replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
