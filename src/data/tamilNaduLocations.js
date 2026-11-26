import gccZonesData from './gis/chennai_gcc_zones.json';

export const TAMIL_NADU_DISTRICTS_BILINGUAL = [
  { name: 'Ariyalur', taName: 'அரியலூர்' },
  { name: 'Chengalpattu', taName: 'செங்கல்பட்டு' },
  { name: 'Chennai', taName: 'சென்னை' },
  { name: 'Coimbatore', taName: 'கோயம்புத்தூர்' },
  { name: 'Cuddalore', taName: 'கடலூர்' },
  { name: 'Dharmapuri', taName: 'தருமபுரி' },
  { name: 'Dindigul', taName: 'திண்டுக்கல்' },
  { name: 'Erode', taName: 'ஈரோடு' },
  { name: 'Kallakurichi', taName: 'கள்ளக்குறிச்சி' },
  { name: 'Kancheepuram', taName: 'காஞ்சிபுரம்' },
  { name: 'Kanyakumari', taName: 'கன்னியாகுமரி' },
  { name: 'Karur', taName: 'கரூர்' },
  { name: 'Krishnagiri', taName: 'கிருஷ்ணகிரி' },
  { name: 'Madurai', taName: 'மதுரை' },
  { name: 'Mayiladuthurai', taName: 'மயிலாடுதுறை' },
  { name: 'Nagapattinam', taName: 'நாகப்பட்டினம்' },
  { name: 'Namakkal', taName: 'நாமக்கல்' },
  { name: 'Nilgiris', taName: 'நீலகிரி' },
  { name: 'Perambalur', taName: 'பெரம்பலூர்' },
  { name: 'Pudukkottai', taName: 'புதுக்கோட்டை' },
  { name: 'Ramanathapuram', taName: 'ராமநாதபுரம்' },
  { name: 'Ranipet', taName: 'ராணிப்பேட்டை' },
  { name: 'Salem', taName: 'சேலம்' },
  { name: 'Sivaganga', taName: 'சிவகங்கை' },
  { name: 'Tenkasi', taName: 'தென்காசி' },
  { name: 'Thanjavur', taName: 'தஞ்சாவூர்' },
  { name: 'Theni', taName: 'தேனி' },
  { name: 'Thoothukudi', taName: 'தூத்துக்குடி' },
  { name: 'Tiruchirappalli', taName: 'திருச்சிராப்பள்ளி' },
  { name: 'Tirunelveli', taName: 'திருநெல்வேலி' },
  { name: 'Tirupathur', taName: 'திருப்பத்தூர்' },
  { name: 'Tiruppur', taName: 'திருப்பூர்' },
  { name: 'Tiruvallur', taName: 'திருவள்ளூர்' },
  { name: 'Tiruvannamalai', taName: 'திருவண்ணாமலை' },
  { name: 'Tiruvarur', taName: 'திருவாரூர்' },
  { name: 'Vellore', taName: 'வேலூர்' },
  { name: 'Viluppuram', taName: 'விழுப்புரம்' },
  { name: 'Virudhunagar', taName: 'விருதுநகர்' }
];

const GCC_ZONE_TAMIL_NAMES = {
  Thiruvottiyur: 'திருவொற்றியூர்',
  Manali: 'மணலி',
  Madhavaram: 'மாதவரம்',
  Tondiarpet: 'தண்டையார்பேட்டை',
  Royapuram: 'ராயபுரம்',
  'Thiru. Vi. Ka. Nagar': 'திரு.வி.க. நகர்',
  Ambattur: 'அம்பத்தூர்',
  'Anna Nagar': 'அண்ணா நகர்',
  Teynampet: 'தேனாம்பேட்டை',
  Kodambakkam: 'கோடம்பாக்கம்',
  Valasaravakkam: 'வளசரவாக்கம்',
  Alandur: 'ஆலந்தூர்',
  Adyar: 'அடையாறு',
  Perungudi: 'பெருங்குடி',
  Sholinganallur: 'சோழிங்கநல்லூர்'
};

export const CHENNAI_GCC_ZONES = (gccZonesData?.features || []).map((f) => {
  const p = f.properties || {};
  const prefix = p.wardPrefix || '';
  const name = p.zoneName || '';
  const taName = GCC_ZONE_TAMIL_NAMES[name] || name;
  return {
    zoneNumber: p.zoneNumber,
    wardPrefix: prefix,
    zoneName: name,
    taZoneName: taName,
    value: `Zone ${prefix}, ${name}, Chennai`,
    taLabel: `மண்டலம் ${prefix}, ${taName}, சென்னை`,
    headquarters: p.headquarters || '',
    coveredWards: Array.isArray(p.coveredWards) ? p.coveredWards : []
  };
});

export function getDistrictDisplayLabel(districtName, isTamil = false) {
  if (!districtName) return '';
  const found = TAMIL_NADU_DISTRICTS_BILINGUAL.find(
    (d) => d.name.toLowerCase() === String(districtName).toLowerCase()
  );
  if (!found) return districtName;
  return isTamil ? `${found.taName} (${found.name})` : found.name;
}

export function getZoneDisplayLabel(zoneValueOrName, isTamil = false) {
  if (!zoneValueOrName) return '';
  const found = CHENNAI_GCC_ZONES.find(
    (z) =>
      z.value.toLowerCase() === String(zoneValueOrName).toLowerCase() ||
      z.zoneName.toLowerCase() === String(zoneValueOrName).toLowerCase()
  );
  if (!found) return zoneValueOrName;
  return isTamil ? found.taLabel : found.value;
}
