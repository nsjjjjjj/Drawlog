export const CANVAS_HISTORY_LIMIT = 15;
export const CANVAS_INPUT_STYLE = {
  touchAction: 'none',
  userSelect: 'none',
  WebkitUserSelect: 'none',
  WebkitTouchCallout: 'none',
  pointerEvents: 'auto',
};
export const INSTALL_GUIDE_DISMISSED_KEY = 'drawlog-install-guide-dismissed';

export function isStandaloneMode() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator?.standalone === true;
}

export function detectInstallGuideMode(hasPrompt) {
  const ua = window.navigator?.userAgent || '';
  const platform = window.navigator?.platform || '';
  const isIOS = /iPad|iPhone|iPod/i.test(ua) || (platform === 'MacIntel' && window.navigator?.maxTouchPoints > 1);
  const isAndroid = /Android/i.test(ua);
  const isKakao = /KAKAOTALK/i.test(ua);
  const isNaver = /NAVER/i.test(ua);
  const isSamsung = /SamsungBrowser/i.test(ua);
  const isChrome = /Chrome|CriOS/i.test(ua) && !isSamsung && !isKakao && !isNaver;
  const isSafari = isIOS && /Safari/i.test(ua) && !/CriOS|FxiOS|OPiOS/i.test(ua) && !isKakao && !isNaver;

  if (isStandaloneMode()) return 'standalone';
  if (isKakao || isNaver) return 'inApp';
  if (isSamsung) return 'samsung';
  if (isSafari) return 'iosSafari';
  if (isAndroid && isChrome && hasPrompt) return 'androidChromePrompt';
  if (isAndroid && isChrome) return 'androidChromeManual';
  if (hasPrompt) return 'browserPrompt';
  return 'default';
}

export function todayString() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export function shiftDate(date, days) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + days);
  const offset = next.getTimezoneOffset() * 60000;
  return new Date(next.getTime() - offset).toISOString().slice(0, 10);
}

export function tomorrowString() {
  return shiftDate(todayString(), 1);
}

export function formatDateLabel(date) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date(`${date}T00:00:00`));
}

export function monthKey(date) {
  return date.slice(0, 7);
}

export function shiftMonth(month, delta) {
  const [year, monthIndex] = month.split('-').map(Number);
  const next = new Date(year, monthIndex - 1 + delta, 1);
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`;
}

export function monthDates(month) {
  const [year, monthIndex] = month.split('-').map(Number);
  const lastDay = new Date(year, monthIndex, 0).getDate();
  const firstWeekday = new Date(year, monthIndex - 1, 1).getDay();
  const dates = Array.from({ length: firstWeekday }, () => null);
  for (let day = 1; day <= lastDay; day += 1) {
    dates.push(`${year}-${String(monthIndex).padStart(2, '0')}-${String(day).padStart(2, '0')}`);
  }
  return dates;
}

export function inviteFromUrl() {
  const pathMatch = window.location.pathname.match(/^\/invite\/([^/]+)/);
  return pathMatch?.[1] || new URLSearchParams(window.location.search).get('invite') || '';
}

export function drawingImageUrl(drawing) {
  return drawing?.imageUrl || drawing?.imagePath || drawing?.thumbnailUrl || '';
}

export function quoteImageUrl(quote) {
  return quote?.imageUrl || quote?.imagePath || quote?.thumbnailUrl || '';
}

export function messagePreview(content, limit = 72) {
  const text = (content || '').trim().replace(/\s+/g, ' ');
  if (text.length <= limit) return text;
  return `${text.slice(0, limit - 1)}…`;
}

function imageFromFile(file) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const url = URL.createObjectURL(file);
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('이미지를 읽지 못했습니다.'));
    };
    image.src = url;
  });
}

export async function resizeProfileImage(file) {
  if (!file?.type?.startsWith('image/')) return file;
  const image = await imageFromFile(file);
  const sourceWidth = image.naturalWidth || image.width;
  const sourceHeight = image.naturalHeight || image.height;
  if (!sourceWidth || !sourceHeight) return file;

  const outputSize = 768;
  const sourceSize = Math.min(sourceWidth, sourceHeight);
  const sourceX = Math.max(0, (sourceWidth - sourceSize) / 2);
  const sourceY = Math.max(0, (sourceHeight - sourceSize) / 2);
  const canvas = document.createElement('canvas');
  canvas.width = outputSize;
  canvas.height = outputSize;
  const context = canvas.getContext('2d');
  if (!context) return file;
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, outputSize, outputSize);
  context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, outputSize, outputSize);

  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/webp', 0.9));
  if (!blob) return file;
  return new File([blob], 'profile.webp', { type: 'image/webp' });
}
