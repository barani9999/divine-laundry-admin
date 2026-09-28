export function shouldShowEmptyTicket(rows = []) {
  if (!Array.isArray(rows) || rows.length === 0) {
    return true;
  }

  return !rows.some(row => {
    if (!row || typeof row.querySelector !== 'function') return false;
    const service = row.querySelector('[data-service]');
    return Boolean(service && service.value && String(service.value).trim().length > 0);
  });
}

export function syncPieceQuantityFields(row) {
  if (!row || typeof row.querySelector !== 'function') return false;

  const select = row.querySelector('[data-service]');
  const quantity = row.querySelector('[data-quantity]');
  const pieces = row.querySelector('[data-pieces]');

  if (!select || !quantity || !pieces) return false;
  if (select.selectedOptions[0]?.dataset?.unit !== 'PIECE') return false;

  const piecesValue = Number(pieces.value) || 0;
  const quantityValue = Number(quantity.value) || 0;
  const baseValue = piecesValue > 0 ? piecesValue : quantityValue;
  const safeValue = Math.max(0, Math.round(baseValue));

  if (Number(quantity.value) !== safeValue || Number(pieces.value) !== safeValue) {
    quantity.value = String(safeValue);
    pieces.value = String(safeValue);
    return true;
  }

  return false;
}