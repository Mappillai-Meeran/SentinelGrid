import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/layout/Layout';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { PatientDashboardPage } from './pages/PatientDashboardPage';
import { MyReservationsPage } from './pages/MyReservationsPage';
import { PharmacistInventoryPage } from './pages/PharmacistInventoryPage';
import { PharmacyNetworkPage } from './pages/PharmacyNetworkPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AuditPage } from './pages/AuditPage';
import { SystemHealthPage } from './pages/SystemHealthPage';
import { PharmacyManagerPage } from './pages/PharmacyManagerPage';
import { SecuredMedicinePage } from './pages/SecuredMedicinePage';
import { PharmacyPartnershipPage } from './pages/PharmacyPartnershipPage';
import { ToastProvider } from './components/ui/Toast';

/**
 * Strict Role-Based Protected Route Guard.
 * If unauthenticated: redirects to /login.
 * If authenticated but lacking required role: redirects directly to / (role home) without leaking content.
 */
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
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          {/* Root Route: Resolves to role-dedicated dashboard (CareLink / Scriptly / Co-Doctor) */}
          <Route
            path="/"
            element={
              <ProtectedRoute roles={['PATIENT', 'PHARMACIST', 'PHARMACY_MANAGER', 'ADMIN']}>
                <OverviewPage />
              </ProtectedRoute>
            }
          />

          {/* Patient-Exclusive Routes (CareLink Personal Health Portal) */}
          <Route
            path="/search"
            element={
              <ProtectedRoute roles={['PATIENT']}>
                <PatientDashboardPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/reservations"
            element={
              <ProtectedRoute roles={['PATIENT']}>
                <MyReservationsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/secured/:id"
            element={
              <ProtectedRoute roles={['PATIENT']}>
                <SecuredMedicinePage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/pharmacies"
            element={
              <ProtectedRoute roles={['PATIENT']}>
                <PharmacyNetworkPage />
              </ProtectedRoute>
            }
          />

          {/* Pharmacist & Manager Operational Routes (Scriptly Branch Desk) */}
          <Route
            path="/inventory"
            element={
              <ProtectedRoute roles={['PHARMACIST', 'PHARMACY_MANAGER']}>
                <PharmacistInventoryPage />
              </ProtectedRoute>
            }
          />

          {/* Manager-Exclusive Route: Branch Staff Scheduling & Operating Rules */}
          <Route
            path="/manager"
            element={
              <ProtectedRoute roles={['PHARMACY_MANAGER']}>
                <PharmacyManagerPage />
              </ProtectedRoute>
            }
          />

          {/* Admin-Exclusive Governance & Command Center Routes (Co-Doctor Enterprise) */}
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
              <ProtectedRoute roles={['ADMIN']}>
                <SystemHealthPage />
              </ProtectedRoute>
            }
          />

          {/* Public & Authenticated Route: Pharmacy Partnership Onboarding */}
          <Route
            path="/pharmacy-partnership"
            element={
              user ? (
                <Layout>
                  <PharmacyPartnershipPage />
                </Layout>
              ) : (
                <PharmacyPartnershipPage isPublic />
              )
            }
          />

          {/* Deprecated & Redundant Demo Routes: Safely redirect to native home */}
          <Route path="/operations" element={<Navigate to="/" replace />} />
          <Route path="/ai-search" element={<Navigate to="/search" replace />} />
          <Route path="/demo-health" element={<Navigate to="/" replace />} />

          {/* Wildcard Catch-all */}
          <Route path="*" element={<Navigate to={user ? '/' : '/login'} replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
};

export default App;
