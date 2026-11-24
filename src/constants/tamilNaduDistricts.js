/**
 * Official 38 Administrative Districts of Tamil Nadu, India.
 * Complete with centroid coordinates, default map zoom levels, and regional classifications.
 */

export const TN_BOUNDS = {
  minLat: 8.08,
  maxLat: 13.55,
  minLng: 76.24,
  maxLng: 80.35
};

export const TN_CENTER = {
  lat: 11.1271,
  lng: 78.6569,
  zoom: 7
};

export const TAMIL_NADU_DISTRICTS = [
  { name: 'Ariyalur', lat: 11.1401, lng: 79.0786, zoom: 12, region: 'Central' },
  { name: 'Chengalpattu', lat: 12.6819, lng: 79.9888, zoom: 12, region: 'North' },
  { name: 'Chennai', lat: 13.0827, lng: 80.2707, zoom: 12, region: 'North', isMajor: true },
  { name: 'Coimbatore', lat: 11.0168, lng: 76.9558, zoom: 12, region: 'West', isMajor: true },
  { name: 'Cuddalore', lat: 11.7480, lng: 79.7714, zoom: 12, region: 'Central' },
  { name: 'Dharmapuri', lat: 12.1211, lng: 78.1582, zoom: 12, region: 'North-West' },
  { name: 'Dindigul', lat: 10.3673, lng: 77.9803, zoom: 12, region: 'South' },
  { name: 'Erode', lat: 11.3410, lng: 77.7172, zoom: 12, region: 'West', isMajor: true },
  { name: 'Kallakurichi', lat: 11.7384, lng: 78.9639, zoom: 12, region: 'North' },
  { name: 'Kancheepuram', lat: 12.8342, lng: 79.7036, zoom: 12, region: 'North' },
  { name: 'Kanyakumari', lat: 8.0883, lng: 77.5385, zoom: 12, region: 'South' },
  { name: 'Karur', lat: 10.9601, lng: 78.0766, zoom: 12, region: 'Central' },
  { name: 'Krishnagiri', lat: 12.5186, lng: 78.2137, zoom: 12, region: 'North-West' },
  { name: 'Madurai', lat: 9.9252, lng: 78.1198, zoom: 12, region: 'South', isMajor: true },
  { name: 'Mayiladuthurai', lat: 11.1075, lng: 79.6523, zoom: 12, region: 'Delta' },
  { name: 'Nagapattinam', lat: 10.7672, lng: 79.8449, zoom: 12, region: 'Delta' },
  { name: 'Namakkal', lat: 11.2189, lng: 78.1674, zoom: 12, region: 'West' },
  { name: 'Nilgiris', lat: 11.4102, lng: 76.6950, zoom: 12, region: 'West' },
  { name: 'Perambalur', lat: 11.2342, lng: 78.8820, zoom: 12, region: 'Central' },
  { name: 'Pudukkottai', lat: 10.3797, lng: 78.8208, zoom: 12, region: 'Central' },
  { name: 'Ramanathapuram', lat: 9.3639, lng: 78.8395, zoom: 12, region: 'South' },
  { name: 'Ranipet', lat: 12.9274, lng: 79.3330, zoom: 12, region: 'North' },
  { name: 'Salem', lat: 11.6643, lng: 78.1460, zoom: 12, region: 'West', isMajor: true },
  { name: 'Sivaganga', lat: 9.8433, lng: 78.4809, zoom: 12, region: 'South' },
  { name: 'Tenkasi', lat: 8.9594, lng: 77.3152, zoom: 12, region: 'South' },
  { name: 'Thanjavur', lat: 10.7870, lng: 79.1378, zoom: 12, region: 'Delta' },
  { name: 'Theni', lat: 10.0104, lng: 77.4768, zoom: 12, region: 'South' },
  { name: 'Thoothukudi', lat: 8.7642, lng: 78.1348, zoom: 12, region: 'South' },
  { name: 'Tiruchirappalli', lat: 10.7905, lng: 78.7047, zoom: 12, region: 'Central', isMajor: true },
  { name: 'Tirunelveli', lat: 8.7139, lng: 77.7567, zoom: 12, region: 'South', isMajor: true },
  { name: 'Tirupathur', lat: 12.4926, lng: 78.5678, zoom: 12, region: 'North' },
  { name: 'Tiruppur', lat: 11.1085, lng: 77.3411, zoom: 12, region: 'West' },
  { name: 'Tiruvallur', lat: 13.1432, lng: 79.9074, zoom: 12, region: 'North' },
  { name: 'Tiruvannamalai', lat: 12.2253, lng: 79.0747, zoom: 12, region: 'North' },
  { name: 'Tiruvarur', lat: 10.7725, lng: 79.6365, zoom: 12, region: 'Delta' },
  { name: 'Vellore', lat: 12.9165, lng: 79.1325, zoom: 12, region: 'North', isMajor: true },
  { name: 'Viluppuram', lat: 11.9401, lng: 79.4861, zoom: 12, region: 'North' },
  { name: 'Virudhunagar', lat: 9.5680, lng: 77.9624, zoom: 12, region: 'South' }
];

export const MAJOR_TN_DISTRICTS = TAMIL_NADU_DISTRICTS.filter(d => d.isMajor);

/**
 * Find district object by case-insensitive name.
 */
export function findDistrictByName(name) {
  if (!name) return null;
  const clean = name.trim().toLowerCase();
  return TAMIL_NADU_DISTRICTS.find(d => d.name.toLowerCase() === clean) || null;
}
