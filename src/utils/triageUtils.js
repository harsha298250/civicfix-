/**
 * Client-Side Multilingual Civic Triage & Keyword Assistant
 * Provides instant category suggestion from bilingual text (English & Tamil)
 * and Web Speech API language configuration.
 * 
 * Note: Tamil lexicon translations require review by a native Tamil speaker.
 */

export const CATEGORY_LEXICON = [
  {
    id: 'roads',
    keywords: [
      'pothole', 'potholes', 'pothol', 'pot hole', 'road', 'roads', 'asphalt', 'tar', 'crater', 'fissure', 'pavement', 'curb', 'divider',
      'speed breaker', 'speedbreaker', 'footpath', 'sidewalk', 'cobblestone', 'tar road', 'salai',
      'குழி', 'சாலை', 'தார் சாலை', 'பள்ளம்', 'சாலை சேதம்', 'நடைபாதை', 'வேகத்தடை', 'ரோடு', 'மேடு பள்ளம்', 'சாலைப்பணி', 'பள்ளங்கள்', 'தார்'
    ]
  },
  {
    id: 'water',
    keywords: [
      'water', 'pipeline', 'pipe burst', 'pipe leak', 'water pipe', 'water leak', 'leakage', 'drinking water',
      'contamination', 'sewage', 'sewer', 'flange', 'borewell', 'water main', 'drainage', 'drain',
      'clogged', 'stagnant', 'flood', 'kudineer', 'thanneer', 'vadikaal',
      'குடிநீர்', 'தண்ணீர் கசிவு', 'குழாய் உடைப்பு', 'தண்ணீர் குழாய்', 'கழிவுநீர்', 'சாக்கடை',
      'தண்ணீர் விநியோகம்', 'நீர்க்கசிவு', 'குழாய்', 'தண்ணீர்', 'குடிநீர் தட்டுப்பாடு', 'குழாய் வெடிப்பு', 'கழிவுநீர் தேக்கம்',
      'வடிகால்', 'மழைநீர் வடிகால்', 'மழைநீர்', 'கால்வாய்', 'அடைப்பு', 'வெள்ளம்', 'நீர் தேக்கம்', 'தேங்கிய நீர்', 'வடிகால் தூர்வாருதல்'
    ]
  },
  {
    id: 'lighting',
    keywords: [
      'street light', 'streetlight', 'streetlights', 'lamppost', 'lamp post', 'light pole', 'bulb', 'dark street',
      'transformer', 'cable', 'wire', 'illumination', 'blackout', 'led lamp', 'light broken', 'theru vilakku',
      'தெருவிளக்கு', 'தெரு விளக்கு', 'விளக்கு கம்பம்', 'மின்விளக்கு', 'விளக்கு எரியவில்லை',
      'இருட்டாக உள்ளது', 'மின் கம்பி', 'மின்மாற்றி', 'விளக்கு', 'மின்சாரம் இல்லை', 'விளக்கு மாற்று'
    ]
  },
  {
    id: 'waste',
    keywords: [
      'garbage', 'garbag', 'waste', 'trash', 'dump', 'dumping', 'debris', 'litter', 'dustbin', 'bin',
      'stench', 'foul smell', 'plastic waste', 'compost', 'dumping yard', 'overflowing bin', 'kuppai',
      'குப்பை', 'குப்பைக் கூளம்', 'கழிவு', 'குப்பை தொட்டி', 'துர்நாற்றம்',
      'குப்பை அள்ளப்படவில்லை', 'பிளாஸ்டிக் கழிவு', 'அழுக்கு', 'துப்புரவு', 'குப்பை மேடு'
    ]
  },
  {
    id: 'traffic',
    keywords: [
      'bus stop', 'bus shelter', 'bus bay', 'transit', 'depot', 'traffic', 'signal', 'metro station', 'perunthu',
      'பேருந்து நிறுத்தம்', 'பேருந்து நிழற்குடை', 'பேருந்து', 'அரசு பேருந்து', 'மெட்ரோ நிலையம்', 'பஸ் ஸ்டாப்', 'போக்குவரத்து', 'பஸ் நிறுத்தம்'
    ]
  },
  {
    id: 'parks',
    keywords: [
      'tree', 'trees', 'branch', 'branches', 'fallen tree', 'park', 'parks', 'playground', 'garden', 'horticulture', 'lawn', 'maram',
      'மரம்', 'விழுந்த மரம்', 'மரக்கிளை', 'கிளை முறிந்தது', 'பூங்கா', 'தோட்டம்', 'புல்வெளி', 'பூங்கா பராமரிப்பு'
    ]
  }
];

export function mapBackendCategoryToFrontendId(cat) {
  if (!cat) return 'roads';
  const c = String(cat).toUpperCase();
  if (c.includes('ROAD') || c.includes('POTHOLE')) return 'roads';
  if (c.includes('WATER') || c.includes('SEWER')) return 'water';
  if (c.includes('LIGHT') || c.includes('LAMP')) return 'lighting';
  if (c.includes('GARBAGE') || c.includes('WASTE')) return 'waste';
  if (c.includes('DRAIN')) return 'water';
  if (c.includes('TRANSPORT') || c.includes('BUS') || c.includes('TRAFFIC')) return 'traffic';
  if (c.includes('PARK') || c.includes('TREE')) return 'parks';
  return 'roads';
}

/**
 * Suggests the best defect category given user-entered title and narrative.
 * Evaluates both English and Tamil keywords.
 * Returns category id string or null if no confident keyword match.
 */
export function suggestCategoryFromText(text) {
  if (!text || typeof text !== 'string') return null;
  const lower = text.toLowerCase();

  let bestCat = null;
  let maxMatches = 0;

  for (const cat of CATEGORY_LEXICON) {
    let matchCount = 0;
    for (const kw of cat.keywords) {
      if (lower.includes(kw.toLowerCase())) {
        matchCount += kw.includes(' ') ? 3 : 2;
      }
    }
    if (matchCount > maxMatches) {
      maxMatches = matchCount;
      bestCat = cat.id;
    }
  }

  return maxMatches > 0 ? bestCat : null;
}

/**
 * Checks if the Web Speech API is supported in the current environment
 */
export function isSpeechRecognitionSupported() {
  if (typeof window === 'undefined') return false;
  return Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
}

/**
 * Gets the BCP 47 language tag for Speech Recognition
 */
export function getSpeechLanguage(currentLang) {
  return currentLang === 'ta' ? 'ta-IN' : 'en-IN';
}
