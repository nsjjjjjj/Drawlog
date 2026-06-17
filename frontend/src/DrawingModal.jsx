import { useEffect, useRef, useState } from 'react';
import { Brush, Eraser, Highlighter, Redo2, Save, Trash2, Undo2, X } from 'lucide-react';
import { request } from './apiClient.js';
import { CANVAS_HISTORY_LIMIT, CANVAS_INPUT_STYLE, drawingImageUrl } from './utils.js';

export default function DrawingModal({ auth, onAuth, groupId, existingDrawing, onClose, onSaved }) {
  const modalRef = useRef(null);
  const canvasRef = useRef(null);
  const canvasSizeRef = useRef({ width: 0, height: 0 });
  const rectRef = useRef(null);
  const drawingRef = useRef(false);
  const undoRef = useRef([]);
  const redoRef = useRef([]);
  const pointerRef = useRef(null);
  const canvasDebugLoggedRef = useRef(false);
  const [tool, setTool] = useState('pen');
  const [color, setColor] = useState('#202124');
  const [size, setSize] = useState(8);
  const [saving, setSaving] = useState(false);
  const [historyTick, setHistoryTick] = useState(0);

  function context() {
    const canvas = canvasRef.current;
    return canvas?.getContext('2d') || null;
  }

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const frame = window.requestAnimationFrame(() => {
      if (cancelled || !canvasRef.current) return;
      setupCanvas();
    });
    return () => {
      cancelled = true;
      window.cancelAnimationFrame(frame);
    };
  }, [existingDrawing?.id]);

  function preventCanvasEvent(event) {
    const targetEvent = event?.nativeEvent || event;
    if (targetEvent?.cancelable !== false) event.preventDefault?.();
  }

  function setupCanvas() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const fallbackSize = Math.min(Math.max(window.innerWidth - 40, 320), 680);
    const displaySize = Math.min(Math.max(Math.round(rect.width || fallbackSize), 320), 680);
    const ratio = window.devicePixelRatio || 1;
    canvas.style.width = `${displaySize}px`;
    canvas.style.height = `${displaySize}px`;
    canvas.width = Math.floor(displaySize * ratio);
    canvas.height = Math.floor(displaySize * ratio);
    canvasSizeRef.current = { width: displaySize, height: displaySize };
    const ctx = context();
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    fillWhite();
    undoRef.current = [];
    redoRef.current = [];
    setHistoryTick((value) => value + 1);

    const imageUrl = drawingImageUrl(existingDrawing);
    if (!imageUrl) return;
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => {
      if (!canvasRef.current) return;
      fillWhite();
      context().drawImage(image, 0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    };
    image.onerror = () => fillWhite();
    image.src = imageUrl;
  }

  function debugCanvasMetrics(event) {
    if (!import.meta.env.DEV) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = rectRef.current || canvas.getBoundingClientRect();
    console.debug('canvas metrics', {
      rectWidth: rect.width,
      rectHeight: rect.height,
      canvasWidth: canvas.width,
      canvasHeight: canvas.height,
      scaleX: canvas.width / (rect.width || canvas.width || 1),
      scaleY: canvas.height / (rect.height || canvas.height || 1),
      visualViewportScale: window.visualViewport?.scale,
      visualViewportOffsetTop: window.visualViewport?.offsetTop,
      rectTop: rect.top,
      pointerClientY: event?.clientY,
      modalScrollTop: modalRef.current?.scrollTop || 0,
    });
  }

  function fillWhite() {
    const ctx = context();
    if (!ctx) return;
    ctx.save();
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    ctx.restore();
  }

  function snapshot() {
    return canvasRef.current?.toDataURL('image/png') || null;
  }

  function pushUndoSnapshot() {
    const current = snapshot();
    if (!current) return;
    undoRef.current.push(current);
    if (undoRef.current.length > CANVAS_HISTORY_LIMIT) undoRef.current.shift();
    redoRef.current = [];
    setHistoryTick((value) => value + 1);
  }

  function restore(dataUrl) {
    if (!dataUrl) return;
    const image = new Image();
    image.onload = () => {
      if (!canvasRef.current) return;
      fillWhite();
      context().drawImage(image, 0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    };
    image.src = dataUrl;
  }

  function point(event) {
    const canvas = canvasRef.current;
    if (!canvas || event.clientX == null || event.clientY == null) return null;
    const rect = canvas.getBoundingClientRect();
    const width = canvasSizeRef.current.width || rect.width || 1;
    const height = canvasSizeRef.current.height || rect.height || 1;
    const scaleX = width / (rect.width || width);
    const scaleY = height / (rect.height || height);
    return {
      x: Math.max(0, Math.min(width, (event.clientX - rect.left) * scaleX)),
      y: Math.max(0, Math.min(height, (event.clientY - rect.top) * scaleY)),
    };
  }

  function applyBrush(ctx) {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalCompositeOperation = tool === 'eraser' ? 'destination-out' : 'source-over';
    ctx.globalAlpha = tool === 'highlighter' ? 0.32 : 1;
    ctx.strokeStyle = tool === 'eraser' ? '#000000' : color;
    ctx.fillStyle = ctx.strokeStyle;
    ctx.lineWidth = tool === 'highlighter' ? Math.max(size * 1.8, 2) : size;
  }

  function drawDot(at) {
    const ctx = context();
    if (!ctx || !at) return;
    ctx.save();
    configureBrush(ctx);
    ctx.beginPath();
    ctx.arc(at.x, at.y, Math.max((tool === 'highlighter' ? size * 1.8 : size) / 2, 1), 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function strokeTo(next) {
    if (!drawingRef.current || !next) return;
    const ctx = context();
    if (!ctx) return;
    ctx.lineTo(next.x, next.y);
    ctx.stroke();
  }

  function startDrawing(event) {
    preventCanvasEvent(event);
    if (event.button !== undefined && event.button !== 0) return;
    const canvas = canvasRef.current;
    const ctx = context();
    if (!canvas || !ctx) return;
    rectRef.current = canvasRef.current?.getBoundingClientRect() || null;
    if (import.meta.env.DEV && !canvasDebugLoggedRef.current) {
      canvasDebugLoggedRef.current = true;
      debugCanvasMetrics(event);
    }
    const startPoint = point(event);
    if (!startPoint) {
      rectRef.current = null;
      return;
    }
    pointerRef.current = event.pointerId ?? null;
    try {
      if (event.pointerId != null) event.currentTarget.setPointerCapture(event.pointerId);
    } catch {}
    pushUndoSnapshot();
    configureBrush(ctx);
    ctx.beginPath();
    ctx.moveTo(startPoint.x, startPoint.y);
    drawingRef.current = true;
    drawDot(startPoint);
    configureBrush(ctx);
    ctx.beginPath();
    ctx.moveTo(startPoint.x, startPoint.y);
  }

  function moveDrawing(event) {
    preventCanvasEvent(event);
    if (!drawingRef.current) return;
    if (pointerRef.current != null && event.pointerId !== pointerRef.current) return;
    strokeTo(point(event));
  }

  function stopDrawing(event) {
    preventCanvasEvent(event);
    if (!drawingRef.current) return;
    if (pointerRef.current != null && event.pointerId !== pointerRef.current) return;
    strokeTo(point(event));
    drawingRef.current = false;
    rectRef.current = null;
    const ctx = context();
    if (ctx) {
      ctx.closePath();
      ctx.globalCompositeOperation = 'source-over';
      ctx.globalAlpha = 1;
    }
    try {
      if (event.pointerId != null) event.currentTarget.releasePointerCapture(event.pointerId);
    } catch {}
    pointerRef.current = null;
  }

  function undo() {
    const previous = undoRef.current.pop();
    if (!previous) return;
    const current = snapshot();
    if (current) redoRef.current.push(current);
    restore(previous);
    setHistoryTick((value) => value + 1);
  }

  function redo() {
    const next = redoRef.current.pop();
    if (!next) return;
    const current = snapshot();
    if (current) undoRef.current.push(current);
    restore(next);
    setHistoryTick((value) => value + 1);
  }

  function clear() {
    pushUndoSnapshot();
    fillWhite();
  }

  async function canvasBlob() {
    const source = canvasRef.current;
    if (!source) return null;
    const output = document.createElement('canvas');
    output.width = source.width;
    output.height = source.height;
    const ctx = output.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, output.width, output.height);
    ctx.drawImage(source, 0, 0);
    const toBlob = (type, quality) => new Promise((resolve) => output.toBlob(resolve, type, quality));
    return (await toBlob('image/webp', 0.92)) || (await toBlob('image/png'));
  }

  async function save() {
    setSaving(true);
    try {
      const blob = await canvasBlob();
      if (!blob) throw new Error('이미지를 만들지 못했습니다.');
      const formData = new FormData();
      formData.append('image', blob, blob.type === 'image/png' ? 'drawing.png' : 'drawing.webp');
      await request(`/groups/${groupId}/drawings/today`, {
        method: existingDrawing ? 'PUT' : 'POST',
        body: formData,
      }, auth, onAuth);
      onSaved();
      onClose();
    } catch (err) {
      alert(err.message || '저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  const canUndo = undoRef.current.length > 0;
  const canRedo = redoRef.current.length > 0;
  void historyTick;

  return (
    <div className="modal-backdrop">
      <section className="draw-modal" ref={modalRef}>
        <div className="modal-header">
          <h2>{existingDrawing ? '내 그림 수정' : '내 그림 그리기'}</h2>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="toolbar">
          <button title="펜" className={tool === 'pen' ? 'active' : ''} onClick={() => setTool('pen')}><Brush size={18} /></button>
          <button title="형광펜" className={tool === 'highlighter' ? 'active' : ''} onClick={() => setTool('highlighter')}><Highlighter size={18} /></button>
          <button title="지우개" className={tool === 'eraser' ? 'active' : ''} onClick={() => setTool('eraser')}><Eraser size={18} /></button>
          <button title="뒤로" disabled={!canUndo} onClick={undo}><Undo2 size={18} /></button>
          <button title="앞으로" disabled={!canRedo} onClick={redo}><Redo2 size={18} /></button>
          <input title="색상" type="color" value={color} onChange={(event) => setColor(event.target.value)} />
          <input title="굵기" type="range" min="2" max="36" value={size} onChange={(event) => setSize(Number(event.target.value))} />
          <span className="size-chip">{size}px</span>
          <button title="전체 지우기" onClick={clear}><Trash2 size={18} /></button>
          <button className="submit" title="저장" onClick={save} disabled={saving}><Save size={18} /> {saving ? '저장 중' : '저장'}</button>
        </div>
        <div className="canvas-viewport">
          <canvas
            ref={canvasRef}
            className="drawing-canvas"
            style={CANVAS_INPUT_STYLE}
            onPointerDown={startDrawing}
            onPointerMove={moveDrawing}
            onPointerUp={stopDrawing}
            onPointerCancel={stopDrawing}
            onContextMenu={preventCanvasEvent}
          />
        </div>
      </section>
    </div>
  );
}
