// reorder.js — drag-to-reorder for the app's local pages (settings.html: the account list). From LLM Central.
//
// How to use: give each row a data-id and a handle button (CSS touch-action: none, so the rest of the list still
// scrolls), all rows being children of one list element; then
//   handle.addEventListener('pointerdown', e => Reorder.start(e, row, onDrop));
//   handle.addEventListener('keydown', e => Reorder.key(e, row, onDrop));   // ↑ / ↓ move one place
// onDrop(ids, movedId) gets the whole new order (the rows' data-ids) once, when the finger lifts (or a key moves the
// row); the page saves it and redraws, so the screen always shows what was really saved. Rows may have different
// heights: the others slide by the dragged row's height. Back cancels a drag
// (Reorder.active / Reorder.cancel). While dragging, the list gets class "sorting" and the row "dragging".
(function() {
  var drag = null;

  function start(e, row, onDrop) {
    if (drag || (e.pointerType === 'mouse' && e.button !== 0)) return;
    e.preventDefault();
    var list = row.parentElement;
    var rows = Array.prototype.slice.call(list.children);
    var from = rows.indexOf(row);
    if (from < 0 || rows.length < 2) return;
    var handle = e.currentTarget;
    drag = { list: list, row: row, rows: rows, from: from, to: from, handle: handle, pointerId: e.pointerId,
             onDrop: onDrop, rects: rows.map(function(r) { return r.getBoundingClientRect(); }),
             startY: e.clientY, lastY: e.clientY, startScroll: window.scrollY, frame: 0 };
    try { handle.setPointerCapture(e.pointerId); } catch (err) { /* pointer already gone */ }
    handle.addEventListener('pointermove', onMove);
    handle.addEventListener('pointerup', onUp);
    handle.addEventListener('pointercancel', onCancel);
    list.classList.add('sorting');
    row.classList.add('dragging');
    drag.frame = requestAnimationFrame(autoScroll);
  }
  function onMove(e) {
    if (!drag || e.pointerId !== drag.pointerId) return;
    drag.lastY = e.clientY;
    layout();
  }
  function onUp(e) { if (drag && e.pointerId === drag.pointerId) end(true); }
  function onCancel(e) { if (drag && e.pointerId === drag.pointerId) end(false); }

  // Moves the dragged row with the finger (kept inside the list) and slides the others into the gap.
  function layout() {
    var d = drag, r = d.rects, last = r.length - 1;
    var moved = d.lastY - d.startY + (window.scrollY - d.startScroll);
    var y = Math.max(r[0].top - r[d.from].top, Math.min(r[last].bottom - r[d.from].bottom, moved));
    d.row.style.transform = 'translateY(' + y + 'px)';
    var centre = r[d.from].top + r[d.from].height / 2 + y;
    // The new place: the furthest row whose middle the dragged row has reached (ties count, so a row dragged to the
    // very end, where it is held at the last row's middle, takes the last place).
    function mid(i) { return r[i].top + r[i].height / 2; }
    var to = d.from, i;
    for (i = d.from + 1; i < r.length && mid(i) <= centre; i++) to = i;
    for (i = d.from - 1; i >= 0 && mid(i) >= centre; i--) to = i;
    d.to = to;
    var gap = Math.max(0, r[1].top - r[0].bottom);   // the list's spacing between rows (≥ 2 rows while dragging)
    var shift = r[d.from].height + gap;
    d.rows.forEach(function(other, k) {
      if (other === d.row) return;
      var offset = (d.from < k && k <= to) ? -shift : (to <= k && k < d.from) ? shift : 0;
      other.style.transform = offset ? 'translateY(' + offset + 'px)' : '';
    });
  }
  // Near the top or bottom edge, the page scrolls so a row can be dragged past the screen.
  function autoScroll() {
    if (!drag) return;
    var edge = 72, h = window.innerHeight;
    var speed = drag.lastY < edge ? -Math.ceil((edge - drag.lastY) / 6)
        : drag.lastY > h - edge ? Math.ceil((drag.lastY - (h - edge)) / 6) : 0;
    if (speed) { window.scrollBy(0, speed); layout(); }
    drag.frame = requestAnimationFrame(autoScroll);
  }
  function end(commit) {
    var d = drag;
    if (!d) return;
    drag = null;
    cancelAnimationFrame(d.frame);
    d.handle.removeEventListener('pointermove', onMove);
    d.handle.removeEventListener('pointerup', onUp);
    d.handle.removeEventListener('pointercancel', onCancel);
    try { d.handle.releasePointerCapture(d.pointerId); } catch (err) { /* already released */ }
    d.list.classList.remove('sorting');
    d.row.classList.remove('dragging');
    d.rows.forEach(function(r) { r.style.transform = ''; });
    if (!commit || d.to === d.from) return;
    var ids = d.rows.map(function(r) { return r.dataset.id; });
    ids.splice(d.to, 0, ids.splice(d.from, 1)[0]);
    d.onDrop(ids, d.row.dataset.id);
  }
  // Keyboard (and TalkBack's volume-key actions on a focused button): ↑ / ↓ move the row one place.
  function key(e, row, onDrop) {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
    e.preventDefault();
    var ids = Array.prototype.map.call(row.parentElement.children, function(r) { return r.dataset.id; });
    var from = ids.indexOf(row.dataset.id), to = from + (e.key === 'ArrowUp' ? -1 : 1);
    if (from < 0 || to < 0 || to >= ids.length) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    onDrop(ids, row.dataset.id);
  }

  window.Reorder = {
    start: start,
    key: key,
    active: function() { return drag !== null; },
    cancel: function() { end(false); }
  };
})();
