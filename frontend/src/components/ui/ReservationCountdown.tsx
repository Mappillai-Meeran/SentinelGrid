import React, { useEffect, useMemo, useState } from 'react';

interface ReservationCountdownProps {
  expiresAt?: string;
  remainingSeconds?: number;
  className?: string;
  onExpire?: () => void;
}

const getRemainingSeconds = (expiresAt?: string, fallbackSeconds: number = 1800) => {
  if (!expiresAt) return fallbackSeconds;
  const parsedExpiry = new Date(expiresAt).getTime();
  if (Number.isNaN(parsedExpiry)) return fallbackSeconds;
  return Math.max(0, Math.ceil((parsedExpiry - Date.now()) / 1000));
};

export const ReservationCountdown: React.FC<ReservationCountdownProps> = ({
  expiresAt,
  remainingSeconds,
  className = '',
  onExpire,
}) => {
  const initialSeconds = useMemo(
    () => getRemainingSeconds(expiresAt, remainingSeconds ?? 1800),
    [expiresAt, remainingSeconds]
  );
  const [secondsLeft, setSecondsLeft] = useState(initialSeconds);

  useEffect(() => {
    setSecondsLeft(getRemainingSeconds(expiresAt, remainingSeconds ?? 1800));
  }, [expiresAt, remainingSeconds]);

  useEffect(() => {
    if (secondsLeft <= 0) {
      onExpire?.();
      return;
    }
    const timer = window.setInterval(() => {
      setSecondsLeft((current) => Math.max(0, current - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [secondsLeft, onExpire]);

  const minutes = Math.floor(secondsLeft / 60);
  const seconds = String(secondsLeft % 60).padStart(2, '0');

  return <span className={`tabular-nums ${className}`}>{minutes}:{seconds}</span>;
};
