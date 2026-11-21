package com.civicfix.ai.triage;

import java.util.*;

/**
 * Domain vocabulary and severity weighting matrix for civic infrastructure triage.
 * Tuned for Indian municipal and urban road terminology with bilingual (English & Tamil) support.
 * 
 * Note: Tamil lexicon translations and hazard terminology require review by a native Tamil speaker.
 */
public final class CivicLexicon {

    private CivicLexicon() {}

    // Canonical category keys matching CivicFix backend / UI standards
    public static final String CAT_ROADS = "ROADS";
    public static final String CAT_WATER = "WATER_SEWERAGE";
    public static final String CAT_STREET_LIGHT = "STREET_LIGHT";
    public static final String CAT_GARBAGE = "GARBAGE";
    public static final String CAT_DRAINAGE = "DRAINAGE";
    public static final String CAT_PUBLIC_TRANSPORT = "PUBLIC_TRANSPORT";
    public static final String CAT_PARKS = "PARKS";

    public static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        // ROADS / தார் சாலைகள் & குழிகள்
        CATEGORY_KEYWORDS.put(CAT_ROADS, List.of(
                "pothole", "potholes", "pothol", "pot hole", "road", "roads", "asphalt", "tar", "fissure", "crater", "pavement",
                "divider", "curb", "bitumen", "speed breaker", "speedbreaker", "road cave", "footpath", "sidewalk",
                "cobblestone", "street damage", "flyover", "underpass", "road cut", "manhole surrounding", "tar road", "damaged road",
                "salai", "குழி", "சாலை", "தார் சாலை", "பள்ளம்", "சாலை சேதம்", "நடைபாதை", "வேகத்தடை",
                "மேடு பள்ளம்", "சாக்கடை பாலம்", "ரோடு", "சாலைப்பணி", "பள்ளங்கள்", "தார்"
        ));

        // WATER_SEWERAGE / குடிநீர் & கழிவுநீர்
        CATEGORY_KEYWORDS.put(CAT_WATER, List.of(
                "pipeline", "pipe burst", "pipe leak", "water pipe", "water leak", "water supply", "drinking water",
                "drinking pipe", "contamination", "sewage", "sewer", "sewage leak", "sewage overflow", "water main",
                "flange", "valve", "pump", "borewell", "overhead tank", "water pressure", "drainage overflow", "gutter overflow",
                "kudineer", "thanneer", "குடிநீர்", "தண்ணீர் கசிவு", "குழாய் உடைப்பு", "தண்ணீர் குழாய்", "கழிவுநீர்", "சாக்கடை",
                "தண்ணீர் விநியோகம்", "நீர்க்கசிவு", "குழாய்", "தண்ணீர்", "குடிநீர் தட்டுப்பாடு", "குழாய் வெடிப்பு", "கழிவுநீர் தேக்கம்"
        ));

        // STREET_LIGHT / தெருவிளக்குகள் & மின்சாரம்
        CATEGORY_KEYWORDS.put(CAT_STREET_LIGHT, List.of(
                "street light", "streetlight", "streetlights", "lamppost", "lamp post", "light pole", "bulb",
                "dark street", "dark road", "cable", "wire", "transformer", "fuse", "driver unit",
                "sodium light", "led light", "led lamp", "pole damaged", "light broken", "street illumination",
                "dark stretch", "illumination", "blackout", "theru vilakku",
                "தெருவிளக்கு", "தெரு விளக்கு", "விளக்கு கம்பம்", "மின்விளக்கு", "விளக்கு எரியவில்லை",
                "இருட்டாக உள்ளது", "மின் கம்பி", "மின்மாற்றி", "விளக்கு", "மின்சாரம் இல்லை", "விளக்கு மாற்று"
        ));

        // GARBAGE / திடக்கழிவு & குப்பை
        CATEGORY_KEYWORDS.put(CAT_GARBAGE, List.of(
                "garbage", "garbag", "waste", "trash", "dump", "dumping", "debris", "uncollected", "litter",
                "dustbin", "bin", "solid waste", "plastic waste", "stench", "foul smell", "animal carcass",
                "commercial waste", "rubbish", "compost", "dumping yard", "overflowing bin", "rotting garbage",
                "kuppai", "குப்பை", "குப்பைக் கூளம்", "கழிவு", "குப்பை தொட்டி", "துர்நாற்றம்",
                "குப்பை அள்ளப்படவில்லை", "பிளாஸ்டிக் கழிவு", "அழுக்கு", "துப்புரவு", "குப்பை மேடு", "கழிவு மேலாண்மை"
        ));

        // DRAINAGE / மழைநீர் வடிகால் & கால்வாய்
        CATEGORY_KEYWORDS.put(CAT_DRAINAGE, List.of(
                "storm drain", "stormdrain", "drain", "drains", "canal", "culvert", "silt", "clogged drain",
                "rainwater", "stagnation", "stagnant water", "flooding", "flood", "manhole", "open drain",
                "catch basin", "runoff", "waterlogging", "waterlog", "blocked culvert", "monsoon drain",
                "drain overflow", "storm water", "vadikaal",
                "வடிகால்", "மழைநீர் வடிகால்", "கால்வாய்", "அடைப்பு", "கழிவுநீர் கால்வாய்",
                "தேங்கிய நீர்", "வெள்ளம்", "பாதாள சாக்கடை", "சாக்கடை அடைப்பு", "நீர் தேக்கம்", "வடிகால் தூர்வாருதல்", "மழைநீர் கால்வாய் அடைப்பு"
        ));

        // PUBLIC_TRANSPORT / பேருந்து & பொதுப்போக்குவரத்து
        CATEGORY_KEYWORDS.put(CAT_PUBLIC_TRANSPORT, List.of(
                "bus stop", "bus shelter", "bus bay", "transit", "depot", "mtc", "passenger",
                "metro station", "bus route", "commuter shelter", "bus stop shed", "bus shelter damaged", "bus terminus",
                "perunthu", "பேருந்து நிறுத்தம்", "பேருந்து நிழற்குடை", "பேருந்து", "அரசு பேருந்து",
                "மெட்ரோ நிலையம்", "பஸ் ஸ்டாப்", "பஸ்", "பஸ் நிறுத்தம்"
        ));

        // PARKS / பூங்காக்கள் & மரங்கள்
        CATEGORY_KEYWORDS.put(CAT_PARKS, List.of(
                "tree", "trees", "branch", "branches", "fallen tree", "overhanging branch", "park", "parks", "playground",
                "garden", "overgrown", "root", "horticulture", "lawn", "shrub", "fallen branch", "uprooted tree", "children park",
                "maram", "மரம்", "விழுந்த மரம்", "மரக்கிளை", "கிளை முறிந்தது", "பூங்கா", "தோட்டம்", "புல்வெளி", "செடி", "பூங்கா பராமரிப்பு", "மரத்தை வெட்ட"
        ));
    }

    // Critical Hazard Triggers (Recommend CRITICAL / P1 Priority)
    // Note: Tamil lexicon translations and hazard terminology require review by a native Tamil speaker.
    public static final List<HazardRule> CRITICAL_HAZARDS = List.of(
            new HazardRule("Live / exposed electrical wiring", List.of(
                    "live wire", "exposed wire", "electric shock", "sparking", "transformer fire", "power line snapped",
                    "அறுந்த மின்கம்பி", "மின்கசிவு", "மின் அதிர்ச்சி", "தீப்பொறி"
            )),
            new HazardRule("Open manhole or unsecured shaft", List.of(
                    "open manhole", "missing manhole cover", "open drain shaft", "uncovered manhole", "open shaft", "manhole open",
                    "திறந்த மேன்ஹோல்", "மூடப்படாத சாக்கடை", "திறந்த கழிவுநீர் தொட்டி"
            )),
            new HazardRule("Severe roadway or structural collapse", List.of(
                    "road collapse", "deep cave-in", "cave in", "sinkhole", "bridge collapse", "culvert collapse", "structural slab failure",
                    "சாலை இடிவு", "ஆழமான பள்ளம்", "பாலம் இடிவு"
            )),
            new HazardRule("Acute toxic or contaminated water hazard", List.of(
                    "gas leak", "chemical leak", "drinking water contamination", "toxic sewage residential",
                    "நச்சு வாயு", "குடிநீர் மாசடைந்தது"
            ))
    );

    // High Hazard Triggers (Recommend HIGH / P2 Priority)
    public static final List<HazardRule> HIGH_HAZARDS = List.of(
            new HazardRule("Arterial transit route obstruction", List.of(
                    "transit bus lane", "arterial road", "hospital route", "school zone", "major intersection blocked",
                    "போக்குவரத்து நெரிசல்", "முக்கிய சாலை அடைப்பு"
            )),
            new HazardRule("Major utility line rupture", List.of(
                    "water main burst", "pipeline burst", "arterial water main", "flange burst",
                    "முக்கிய குழாய் உடைப்பு", "பெரிய குடிநீர் கசிவு"
            )),
            new HazardRule("Significant depth or collision risk", List.of(
                    "depth > 10cm", "deep crater", "severe fissure", "two-wheeler spillover", "heavy bike traffic risk",
                    "ஆழமான குழி", "இருசக்கர வாகனம் விபத்து"
            )),
            new HazardRule("Fallen tree or branch blocking thoroughfare", List.of(
                    "fallen tree across road", "branch blocking road", "dangling branch",
                    "சாலையின் குறுக்கே விழுந்த மரம்", "சாலையில் மரம் விழுந்தது", "மரக்கிளை விழுந்தது"
            ))
    );

    // Medium Hazard Triggers (Recommend MEDIUM / P3 Priority)
    public static final List<HazardRule> MEDIUM_HAZARDS = List.of(
            new HazardRule("Standard infrastructure defect", List.of(
                    "pothole", "clogged gutter", "dark lamppost", "garbage heap", "uncollected waste", "street light out", "pavement damaged",
                    "சாலையில் குழி", "தெருவிளக்கு பழுது", "குப்பை தேக்கம்", "வடிகால் அடைப்பு"
            ))
    );

    public record HazardRule(String label, List<String> triggers) {}
}
