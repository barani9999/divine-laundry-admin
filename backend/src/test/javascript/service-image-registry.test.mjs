import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveServiceImage } from '../../../src/main/resources/static/assets/service-image-registry.js';

test('service image resolution prefers service code before name and category', () => {
  assert.equal(resolveServiceImage('SHIRT_DRY', 'Dry Clean', 'White Shirt'), '/assets/service-images/WHITESHIRT_DRY_M.png');
  assert.equal(resolveServiceImage('LEHENGA_DRY_W', 'Dry Clean', 'Lehenga'), '/assets/service-images/LEHENGA_DRY_W.png');
  assert.equal(resolveServiceImage('CAP_DRY_A', 'Accessories', 'Cap'), '/assets/service-images/cap.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'White Shirt'), '/assets/service-images/shirt.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Laundry by KG', 'Unknown wash item'), '/assets/service-images/washing.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Sofa Cleaning', 'Mystery Fabric'), '/assets/service-images/sofa.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Unknown Category', 'Mystery Item'), '/assets/service-images/default-laundry.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'Blouse'), '/assets/service-images/blouse.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'Dupatta'), '/assets/service-images/dupatta.svg');
});

test('exact Men service codes resolve to their local PNG assets', () => {
  const serviceCodes = [
    'ACHKAN_DRY_M', 'CAPRI_DRY_M', 'DHOTI_DRY_M', 'JACKET_DRY_M',
    'JEANS_DRY_M', 'KURTA_DRY_M', 'LONGCOAT_DRY_M', 'PYJAMA_DRY_M',
    'SAFARI_DRY_M', 'SHERWANI_DRY_M', 'SHIRT_DRY_M', 'SHORTS_DRY_M',
    'SUIT_DRY_M', 'SWEATER_DRY_M', 'SWEATSHIRT_DRY_M', 'TIE_DRY_M',
    'TRACKPANT_DRY_M', 'TSHIRT_DRY_M', 'UNDERWEAR_DRY_M', 'VEST_DRY_M'
  ];

  for (const code of serviceCodes) {
    assert.equal(resolveServiceImage(code, 'Dry Clean', 'White Shirt'), `/assets/service-images/${code}.png`);
  }
});

test('the four generated Men images resolve by their actual catalog service codes', () => {
  assert.equal(resolveServiceImage('BLAZER_DRY', 'Dry Clean', 'Blazer / Coat'), '/assets/service-images/BLAZER_DRY_M.png');
  assert.equal(resolveServiceImage('SHIRT_DRY', 'Dry Clean', 'White Shirt'), '/assets/service-images/WHITESHIRT_DRY_M.png');
  assert.equal(resolveServiceImage('PULLOVER_DRY_M', 'Dry Clean', 'Long Pullover'), '/assets/service-images/LONGPULLOVER_DRY_M.png');
  assert.equal(resolveServiceImage('SWEATPANT_DRY_M', 'Dry Clean', 'Sweat Pants'), '/assets/service-images/SWEATPANTS_DRY_M.png');
});

test('Women service codes resolve to same-named PNG assets before name-based matches', () => {
  const serviceCodes = [
    'BLAZER_DRY_W', 'BLOUSE_DRY_W', 'BRASSIERES_DRY_W', 'CAPRI_DRY_W',
    'DANGREE_DRY_W', 'DRESS_DRY_W', 'DUPATTA_DRY_W', 'JACKET_DRY_W',
    'JEANS_DRY_W', 'JUMPER_DRY_W', 'KURTI_KAMEEZ_DRY_W', 'LEGGINGS_DRY_W',
    'LEHENGA_DRY_W', 'LONGCOAT_DRY_W', 'PANTS_DRY_W', 'PETTICOAT_DRY_W',
    'PLAZO_DRY_W', 'PULLOVER_DRY_W', 'SALWAR_DRY_W', 'SAREE_DRY_W',
    'SCARF_DRY_W', 'SHAWL_DRY_W', 'SHIRT_DRY_W', 'SKIRT_LONG_DRY_W',
    'SKIRT_SHORT_DRY_W', 'SLACKS_DRY_W', 'STOCKINGS_DRY_W', 'STOLE_DRY_W',
    'SWEATER_DRY_W', 'SWEATSHIRT_DRY_W', 'TOP_DRY_W', 'TRACKPANT_DRY_W',
    'TSHIRT_DRY_W'
  ];

  for (const code of serviceCodes) {
    assert.equal(resolveServiceImage(code, 'Dry Clean', 'Shirt'), `/assets/service-images/${code}.png`);
  }
});

test('unmapped Women codes use category art instead of name-based collisions', () => {
  assert.equal(resolveServiceImage('UNKNOWN_DRY_W', 'Dry Clean', 'Dress'), '/assets/service-images/shirt.svg');
  assert.equal(resolveServiceImage('UNKNOWN_DRY_W', 'Dry Clean', 'Track Pant'), '/assets/service-images/shirt.svg');
});
