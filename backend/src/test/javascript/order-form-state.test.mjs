import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldShowEmptyTicket, syncPieceQuantityFields } from '../../../src/main/resources/static/assets/order-form-state.mjs';

function makeRow({ serviceValue = '', quantity = '1', pieces = '1' } = {}) {
  const values = new Map();
  return {
    querySelector(selector) {
      if (selector === '[data-service]') {
        return { value: serviceValue, selectedOptions: [{ dataset: { unit: serviceValue ? 'PIECE' : '' } }] };
      }
      if (selector === '[data-quantity]') {
        return { value: quantity, dataset: {} };
      }
      if (selector === '[data-pieces]') {
        return { value: pieces, dataset: {} };
      }
      return null;
    }
  };
}

test('empty ticket is shown when there are no active order lines', () => {
  assert.equal(shouldShowEmptyTicket([]), true);
  assert.equal(shouldShowEmptyTicket([makeRow({ serviceValue: '' })]), true);
  assert.equal(shouldShowEmptyTicket([makeRow({ serviceValue: 'svc-1' })]), false);
  assert.equal(shouldShowEmptyTicket([makeRow({ serviceValue: '' }), makeRow({ serviceValue: 'svc-2' })]), false);
});

test('piece-based services keep quantity and pieces aligned', () => {
  const row = makeRow({ serviceValue: 'svc-1', quantity: '4', pieces: '2' });
  assert.equal(syncPieceQuantityFields(row), true);
  assert.equal(row.querySelector('[data-quantity]').value, '2');
  assert.equal(row.querySelector('[data-pieces]').value, '2');

  const rowFromPieces = makeRow({ serviceValue: 'svc-2', quantity: '1', pieces: '5' });
  assert.equal(syncPieceQuantityFields(rowFromPieces), true);
  assert.equal(rowFromPieces.querySelector('[data-quantity]').value, '5');
  assert.equal(rowFromPieces.querySelector('[data-pieces]').value, '5');

  const kgRow = makeRow({ serviceValue: 'kg-1', quantity: '2.5', pieces: '3' });
  const selectedKg = kgRow.querySelector('[data-service]');
  selectedKg.selectedOptions[0].dataset.unit = 'KG';
  assert.equal(syncPieceQuantityFields(kgRow), false);
  assert.equal(kgRow.querySelector('[data-quantity]').value, '2.5');
  assert.equal(kgRow.querySelector('[data-pieces]').value, '3');
});
