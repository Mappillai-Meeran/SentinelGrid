import React, { createContext, useContext, useState, useCallback } from 'react';
import { X, CheckCircle, AlertTriangle, AlertCircle, Info } from 'lucide-react';
import { AnimatePresence, motion } from 'framer-motion';

type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
  showError: (err: any, fallbackMessage?: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, type: ToastType = 'info') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, type, message }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const showError = useCallback((err: any, fallbackMessage: string = 'Action failed') => {
    let msg = fallbackMessage;
    if (err.response?.data?.message) {
      msg = err.response.data.message;
    } else if (err.message) {
      msg = err.message;
    }
    
    // Check specific HTTP status responses
    const status = err.response?.status;
    if (status === 401) {
      msg = 'Your session has expired. Please sign in again.';
    } else if (status === 403) {
      msg = "You don't have permission to perform this action.";
    } else if (status === 409) {
      msg = 'This record was modified by another user. Please refresh the inventory page and try again.';
    } else if (status === 500) {
      msg = 'SentinelGrid is temporarily unable to complete this request.';
    }
    
    showToast(msg, 'error');
  }, [showToast]);

  const removeToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ showToast, showError }}>
      {children}
      {/* Toast Portal Area */}
      <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-3 max-w-sm w-full pointer-events-none">
        <AnimatePresence>
          {toasts.map((t) => {
            let bg = 'bg-white border-slate-205 text-slate-800';
            let Icon = Info;
            let iconColor = 'text-blue-500';
            
            if (t.type === 'success') {
              bg = 'bg-emerald-50 border-emerald-200 text-emerald-800';
              Icon = CheckCircle;
              iconColor = 'text-emerald-600';
            } else if (t.type === 'error') {
              bg = 'bg-red-50 border-red-250 text-red-800';
              Icon = AlertCircle;
              iconColor = 'text-red-600';
            } else if (t.type === 'warning') {
              bg = 'bg-amber-50 border-amber-250 text-amber-800';
              Icon = AlertTriangle;
              iconColor = 'text-amber-605';
            }

            return (
              <motion.div
                key={t.id}
                initial={{ opacity: 0, y: 20, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, scale: 0.9, y: -10 }}
                transition={{ duration: 0.2 }}
                className={`pointer-events-auto p-4 rounded-2xl border flex items-start gap-3 shadow-lg ${bg}`}
              >
                <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${iconColor}`} />
                <div className="flex-1 text-xs font-semibold leading-relaxed">
                  {t.message}
                </div>
                <button
                  onClick={() => removeToast(t.id)}
                  className="text-slate-400 hover:text-slate-600 p-0.5 rounded-lg transition-colors shrink-0"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
