import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveServiceImage } from '../../../src/main/resources/static/assets/service-image-registry.js';

test('service image resolution prefers service code before name and category', () => {
  assert.equal(resolveServiceImage('SHIRT_DRY', 'Dry Clean', 'White Shirt'), '/assets/service-images/shirt.svg');
  assert.equal(resolveServiceImage('LEHENGA_DRY_W', 'Dry Clean', 'Lehenga'), '/assets/service-images/lehenga.svg');
  assert.equal(resolveServiceImage('CAP_DRY_A', 'Accessories', 'Cap'), '/assets/service-images/cap.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'White Shirt'), '/assets/service-images/shirt.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Laundry by KG', 'Unknown wash item'), '/assets/service-images/washing.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Sofa Cleaning', 'Mystery Fabric'), '/assets/service-images/sofa.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Unknown Category', 'Mystery Item'), '/assets/service-images/default-laundry.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'Blouse'), '/assets/service-images/blouse.svg');
  assert.equal(resolveServiceImage('UNKNOWN_CODE', 'Dry Clean', 'Dupatta'), '/assets/service-images/dupatta.svg');
});
