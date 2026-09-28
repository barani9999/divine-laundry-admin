const DEFAULT_IMAGE = '/assets/service-images/default-laundry.svg';

const normalize = value => String(value ?? '')
  .trim()
  .toLowerCase()
  .replace(/[^a-z0-9]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const normalizeCode = value => String(value ?? '').trim().toUpperCase();

const WOMEN_SERVICE_CODES = new Set([
  'BLAZER_DRY_W', 'BLOUSE_DRY_W', 'BRASSIERES_DRY_W', 'CAPRI_DRY_W',
  'DANGREE_DRY_W', 'DRESS_DRY_W', 'DUPATTA_DRY_W', 'JACKET_DRY_W',
  'JEANS_DRY_W', 'JUMPER_DRY_W', 'KURTI_KAMEEZ_DRY_W', 'LEGGINGS_DRY_W',
  'LEHENGA_DRY_W', 'LONGCOAT_DRY_W', 'PANTS_DRY_W', 'PETTICOAT_DRY_W',
  'PLAZO_DRY_W', 'PULLOVER_DRY_W', 'SALWAR_DRY_W', 'SAREE_DRY_W',
  'SCARF_DRY_W', 'SHAWL_DRY_W', 'SHIRT_DRY_W', 'SKIRT_LONG_DRY_W',
  'SKIRT_SHORT_DRY_W', 'SLACKS_DRY_W', 'STOCKINGS_DRY_W', 'STOLE_DRY_W',
  'SWEATER_DRY_W', 'SWEATSHIRT_DRY_W', 'TOP_DRY_W', 'TRACKPANT_DRY_W',
  'TSHIRT_DRY_W'
]);

const SERVICE_CODE_IMAGE = {
  SHIRT_DRY: 'WHITESHIRT_DRY_M.png',
  SHIRT_DRY_M: 'SHIRT_DRY_M.png',
  SHIRT_DRY_W: 'shirt.svg',
  SHIRT_DRY_K: 'shirt.svg',
  BLAZER_DRY: 'BLAZER_DRY_M.png',
  BLAZER_DRY_M: 'blazer.svg',
  BLAZER_DRY_W: 'blazer.svg',
  TSHIRT_DRY_M: 'TSHIRT_DRY_M.png',
  TSHIRT_DRY_W: 'tshirt.svg',
  TSHIRT_DRY_K: 'tshirt.svg',
  VEST_DRY_M: 'VEST_DRY_M.png',
  UNDERWEAR_DRY_M: 'UNDERWEAR_DRY_M.png',
  PULLOVER_DRY_M: 'LONGPULLOVER_DRY_M.png',
  PULLOVER_DRY_W: 'pullover.svg',
  PULLOVER_DRY_K: 'pullover.svg',
  SWEATPANT_DRY_M: 'SWEATPANTS_DRY_M.png',
  CAPRI_DRY_M: 'CAPRI_DRY_M.png',
  CAPRI_DRY_W: 'capri.svg',
  CAPRI_DRY_K: 'capri.svg',
  PYJAMA_DRY_M: 'PYJAMA_DRY_M.png',
  PYJAMA_IRON: 'pyjama.svg',
  TRACKPANT_DRY_M: 'TRACKPANT_DRY_M.png',
  TRACKPANT_DRY_W: 'track-pant.svg',
  TRACKPANT_DRY_K: 'track-pant.svg',
  SHORTS_DRY_M: 'SHORTS_DRY_M.png',
  SHORTS_DRY_K: 'shorts.svg',
  LONGCOAT_DRY_M: 'LONGCOAT_DRY_M.png',
  LONGCOAT_DRY_W: 'long-coat.svg',
  JEANS_DRY_M: 'JEANS_DRY_M.png',
  JEANS_DRY_W: 'jeans.svg',
  JEANS_DRY_K: 'jeans.svg',
  JACKET_DRY_M: 'JACKET_DRY_M.png',
  JACKET_DRY_W: 'jacket.svg',
  JACKET_DRY_K: 'jacket.svg',
  ACHKAN_DRY_M: 'ACHKAN_DRY_M.png',
  SAFARI_DRY_M: 'SAFARI_DRY_M.png',
  DHOTI_DRY_M: 'DHOTI_DRY_M.png',
  KURTA_DRY_M: 'KURTA_DRY_M.png',
  KURTA_DRY_K: 'kurta.svg',
  KURTA_IRON: 'kurta.svg',
  SUIT_DRY_M: 'SUIT_DRY_M.png',
  SWEATER_DRY_M: 'SWEATER_DRY_M.png',
  SWEATER_DRY_W: 'sweater.svg',
  SWEATER_DRY_K: 'sweater.svg',
  SWEATSHIRT_DRY_M: 'SWEATSHIRT_DRY_M.png',
  SWEATSHIRT_DRY_W: 'sweatshirt.svg',
  SWEATSHIRT_DRY_K: 'sweatshirt.svg',
  TIE_DRY_M: 'TIE_DRY_M.png',
  TIE_DRY_A: 'tie.svg',
  SHERWANI_DRY_M: 'SHERWANI_DRY_M.png',
  SHERWANI_DRY_K: 'sherwani.svg',
  PETTICOAT_DRY_W: 'petticoat.svg',
  BRASSIERES_DRY_W: 'bra.svg',
  STOCKINGS_DRY_W: 'stockings.svg',
  SCARF_DRY_W: 'scarf.svg',
  SLACKS_DRY_W: 'trousers.svg',
  LEGGINGS_DRY_W: 'leggings.svg',
  LEGGINGS_DRY_K: 'leggings.svg',
  JUMPER_DRY_W: 'sweater.svg',
  JUMPER_DRY_K: 'sweater.svg',
  DANGREE_DRY_W: 'dress.svg',
  DANGREE_DRY_K: 'dress.svg',
  PANTS_DRY_W: 'trousers.svg',
  PANTS_DRY_K: 'trousers.svg',
  TOP_DRY_W: 'blouse.svg',
  TOP_DRY_K: 'blouse.svg',
  SKIRT_LONG_DRY_W: 'skirt-long.svg',
  SKIRT_SHORT_DRY_W: 'skirt-short.svg',
  BLOUSE_DRY_W: 'blouse.svg',
  BLOUSE_DRY_K: 'blouse.svg',
  BLOUSE_IRON: 'blouse.svg',
  DUPATTA_DRY_W: 'dupatta.svg',
  DUPATTA_DRY_K: 'dupatta.svg',
  DUPATTA_IRON: 'dupatta.svg',
  LEHENGA_DRY_W: 'lehenga.svg',
  LEHENGA_DRY_K: 'lehenga.svg',
  DRESS_DRY_W: 'dress.svg',
  DRESS_DRY_K: 'dress.svg',
  SAREE_DRY_W: 'saree.svg',
  SAREE_IRON: 'saree.svg',
  SALWAR_DRY_W: 'salwar.svg',
  SALWAR_DRY_K: 'salwar.svg',
  KURTI_KAMEEZ_DRY_W: 'kurti.svg',
  KURTI_IRON: 'kurti.svg',
  SHAWL_DRY_W: 'shawl.svg',
  STOLE_DRY_W: 'stole.svg',
  FROCK_DRY_K: 'frock.svg',
  KIDS_FROCK_IRON: 'frock.svg',
  BABYBLANKET_DRY_K: 'blanket.svg',
  TABLERUNNER_DRY_H: 'bedsheet.svg',
  FOOTMAT_DRY_H: 'bedsheet.svg',
  TABLEMAT_DRY_H: 'bedsheet.svg',
  BATHROBE_DRY_H: 'blanket.svg',
  SOCKS_DRY_H: 'stockings.svg',
  HANDBAG_DRY_H: 'bag.svg',
  HANDKERCHIEF_DRY_A: 'default-laundry.svg',
  HAT_DRY_A: 'hat.svg',
  CAP_DRY_A: 'cap.svg',
  MUFFLER_DRY_A: 'shawl.svg',
  RAINCOAT_DRY_A: 'long-coat.svg',
  WASH_IRON_KG: 'washing.svg',
  WASH_FOLD_KG: 'washing.svg',
  WASH_FOLD_EXPRESS_KG: 'washing.svg',
  WASH_IRON_EXPRESS_KG: 'washing.svg',
  SPORT_SHOE: 'shoe.svg',
  CANVAS_SHOE: 'shoe.svg',
  LEATHER_SHOE: 'shoe.svg',
  SUEDE_SHOE: 'shoe.svg',
  CROCS_SANDALS: 'shoe.svg',
  SLIPPERS: 'shoe.svg',
  SOFA_SEAT: 'sofa.svg',
  SOFA_CLEANING: 'sofa.svg',
  BLANKET_DRY_H: 'blanket.svg',
  BEDSPREAD_DRY_H: 'bedsheet.svg',
  BEDSHEET_DRY_H: 'bedsheet.svg',
  DUVET_DRY_H: 'bedsheet.svg',
  QUILT_DRY_H: 'blanket.svg',
  CURTAIN_DRY_H: 'curtain.svg',
  BLIND_DRY_H: 'curtain.svg',
  TOWELS_DRY_H: 'blanket.svg',
  COVERS_DRY_H: 'bedsheet.svg',
  CUSHIONCOVERS_DRY_H: 'bedsheet.svg',
  SOFACOVER_DRY_H: 'sofa.svg',
  TABLE_NAPKIN_DRY_H: 'bedsheet.svg',
  TABLENAPKIN_DRY_H: 'bedsheet.svg',
  SHIRT_IRON: 'shirt.svg',
  TSHIRT_IRON: 'tshirt.svg',
  PANT_IRON: 'trousers.svg',
  LONG_DRESS_IRON: 'dress.svg',
  PILLOW_COVER_IRON: 'bedsheet.svg',
  COAT_BLAZER_IRON: 'blazer.svg',
  OVERCOAT_IRON: 'long-coat.svg',
  SAREE_IRON: 'saree.svg',
  KURTI_IRON: 'kurti.svg',
  DUPATTA_IRON: 'dupatta.svg',
  KIDS_IRON: 'dress.svg',
  KIDS_FROCK_IRON: 'frock.svg'
};

const SERVICE_NAME_IMAGE = {
  'white shirt': 'shirt.svg',
  shirt: 'shirt.svg',
  't shirt': 'tshirt.svg',
  'tshirt': 'tshirt.svg',
  vest: 'vest.svg',
  'under wear': 'underwear.svg',
  underwear: 'underwear.svg',
  'long pullover': 'pullover.svg',
  'sweat pants': 'sweat-pants.svg',
  capri: 'capri.svg',
  pyjama: 'pyjama.svg',
  'track pant': 'track-pant.svg',
  shorts: 'shorts.svg',
  'long coat': 'long-coat.svg',
  jeans: 'jeans.svg',
  tie: 'tie.svg',
  'sherwani set': 'sherwani.svg',
  sherwani: 'sherwani.svg',
  achkan: 'blazer.svg',
  'baby blanket': 'blanket.svg',
  'table runner': 'bedsheet.svg',
  'foot mat': 'bedsheet.svg',
  'table mat': 'bedsheet.svg',
  'bath robe': 'blanket.svg',
  handbag: 'bag.svg',
  socks: 'stockings.svg',
  'rain coat': 'long-coat.svg',
  shoe: 'shoe.svg',
  'sports shoe': 'shoe.svg',
  'canvas shoe': 'shoe.svg',
  'leather shoe': 'shoe.svg',
  'suede shoe': 'shoe.svg',
  slippers: 'shoe.svg',
  sofa: 'sofa.svg',
  'sofa cover': 'sofa.svg',
  curtain: 'curtain.svg',
  bedsheet: 'bedsheet.svg',
  'bed sheet': 'bedsheet.svg',
  duvet: 'bedsheet.svg',
  quilt: 'blanket.svg',
  dress: 'dress.svg',
  saree: 'saree.svg',
  pant: 'trousers.svg',
  trouser: 'trousers.svg',
  trousers: 'trousers.svg',
  blazer: 'blazer.svg',
  'blazer coat': 'blazer.svg',
  coat: 'blazer.svg',
  jacket: 'jacket.svg',
  dhoti: 'dhoti.svg',
  suit: 'suit.svg',
  sweater: 'sweater.svg',
  'sweat shirt': 'sweatshirt.svg',
  safari: 'safari.svg',
  blanket: 'blanket.svg',
  blouse: 'blouse.svg',
  dupatta: 'dupatta.svg',
  lehenga: 'lehenga.svg',
  kurti: 'kurti.svg',
  'kurti kameez': 'kurti.svg',
  salwar: 'salwar.svg',
  shawl: 'shawl.svg',
  stole: 'stole.svg',
  leggings: 'leggings.svg',
  petticoat: 'petticoat.svg',
  stockings: 'stockings.svg',
  'skirt long': 'skirt-long.svg',
  'skirt short': 'skirt-short.svg',
  'frock': 'frock.svg',
  'scarf': 'scarf.svg',
  'hat': 'hat.svg',
  'cap': 'cap.svg',
  'long coat': 'long-coat.svg',
  'kurta': 'kurta.svg',
  'jumper': 'sweater.svg',
  'suit': 'suit.svg',
  'safari': 'safari.svg',
  'dupatta': 'dupatta.svg',
  'top': 'blouse.svg',
  'blouse': 'blouse.svg',
  'bra': 'bra.svg'
};

const CATEGORY_IMAGE = {
  'dry clean': 'shirt.svg',
  'laundry by kg': 'washing.svg',
  ironing: 'iron.svg',
  'shoe cleaning': 'shoe.svg',
  'sofa cleaning': 'sofa.svg'
};

function resolveServiceImage(code, category, name) {
  const normalizedCode = normalizeCode(code);
  const normalizedCategory = normalize(category);
  const normalizedName = normalize(name);

  if (WOMEN_SERVICE_CODES.has(normalizedCode)) {
    return `/assets/service-images/${normalizedCode}.png`;
  }

  const codeImage = SERVICE_CODE_IMAGE[normalizedCode];
  if (!normalizedCode.endsWith('_W') && codeImage) return `/assets/service-images/${codeImage}`;

  if (!normalizedCode.endsWith('_W') && normalizedName && SERVICE_NAME_IMAGE[normalizedName]) {
    return `/assets/service-images/${SERVICE_NAME_IMAGE[normalizedName]}`;
  }

  if (normalizedCategory && CATEGORY_IMAGE[normalizedCategory]) {
    return `/assets/service-images/${CATEGORY_IMAGE[normalizedCategory]}`;
  }

  return DEFAULT_IMAGE;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { resolveServiceImage, DEFAULT_IMAGE };
}

if (typeof window !== 'undefined') {
  window.resolveServiceImage = resolveServiceImage;
}
