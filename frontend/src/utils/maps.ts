type DirectionTarget = {
  name?: string;
  address?: string;
  city?: string;
  latitude?: number | null;
  longitude?: number | null;
};

type UserLocation = {
  latitude?: number | null;
  longitude?: number | null;
};

export const calculateDistanceKm = (
  lat1?: number | null,
  lon1?: number | null,
  lat2?: number | null,
  lon2?: number | null
): number | null => {
  if (
    typeof lat1 !== 'number' ||
    !Number.isFinite(lat1) ||
    typeof lon1 !== 'number' ||
    !Number.isFinite(lon1) ||
    typeof lat2 !== 'number' ||
    !Number.isFinite(lat2) ||
    typeof lon2 !== 'number' ||
    !Number.isFinite(lon2)
  ) {
    return null;
  }

  const R = 6371; // Earth's radius in kilometers
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distance = R * c;

  if (isNaN(distance) || !Number.isFinite(distance)) {
    return null;
  }

  return Math.round(distance * 10) / 10;
};

export const buildGoogleMapsDirectionsUrl = (
  target: DirectionTarget,
  userLocation?: UserLocation | null
) => {
  const hasCoordinates =
    typeof target.latitude === 'number' &&
    Number.isFinite(target.latitude) &&
    typeof target.longitude === 'number' &&
    Number.isFinite(target.longitude);

  const destination = hasCoordinates
    ? `${target.latitude},${target.longitude}`
    : [target.name, target.address, target.city].filter(Boolean).join(', ');

  const hasUserCoordinates =
    typeof userLocation?.latitude === 'number' &&
    Number.isFinite(userLocation.latitude) &&
    typeof userLocation?.longitude === 'number' &&
    Number.isFinite(userLocation.longitude);

  const originParam = hasUserCoordinates
    ? `&origin=${userLocation!.latitude},${userLocation!.longitude}`
    : '';

  return `https://www.google.com/maps/dir/?api=1${originParam}&destination=${encodeURIComponent(destination)}&travelmode=driving`;
};

