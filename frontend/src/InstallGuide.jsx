import { X } from 'lucide-react';
import { detectInstallGuideMode } from './utils.js';

export default function InstallGuideModal({ hasPrompt, onInstall, onClose, onDismiss }) {
  const mode = detectInstallGuideMode(hasPrompt);
  const canDismiss = mode !== 'inApp' && mode !== 'standalone';
  const canInstall = mode === 'androidChromePrompt' || mode === 'browserPrompt';
  const content = {
    standalone: {
      title: '이미 앱처럼 실행 중이에요.',
      body: ['홈 화면에서 실행 중이라 추가 설치 안내가 필요 없어요.'],
    },
    inApp: {
      title: '브라우저에서 열어주세요',
      body: [
        '현재 앱 내부 브라우저에서 열려 있습니다.',
        'Drawlog를 앱처럼 설치하려면 Safari, Chrome, Samsung Internet에서 열어주세요.',
      ],
    },
    iosSafari: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      steps: [
        '하단 공유 버튼(□↑)을 눌러주세요.',
        '“홈 화면에 추가”를 선택해주세요.',
        '추가를 누르면 앱처럼 사용할 수 있어요.',
      ],
    },
    androidChromePrompt: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      body: ['앱 설치 버튼을 누르면 Drawlog를 홈 화면에 추가할 수 있어요.'],
    },
    androidChromeManual: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      body: ['Chrome 메뉴(⋮)에서 앱 설치 또는 홈 화면에 추가를 선택해주세요.'],
    },
    samsung: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      body: ['메뉴에서 현재 페이지 추가를 누른 뒤 홈 화면을 선택해주세요.'],
    },
    browserPrompt: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      body: ['앱 설치 버튼을 누르면 Drawlog를 홈 화면에 추가할 수 있어요.'],
    },
    default: {
      title: '📱 Drawlog를 앱처럼 설치해보세요',
      body: ['브라우저 메뉴에서 앱 설치 또는 홈 화면에 추가를 찾아주세요.'],
    },
  }[mode];

  return (
    <div className="modal-backdrop install-guide-backdrop">
      <section className="install-guide-modal">
        <div className="modal-header">
          <h2>{content.title}</h2>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        {content.steps ? (
          <ol className="install-guide-steps">
            {content.steps.map((step) => <li key={step}>{step}</li>)}
          </ol>
        ) : (
          <div className="install-guide-copy">
            {content.body.map((line) => <p key={line}>{line}</p>)}
          </div>
        )}
        {mode === 'inApp' && (
          <ul className="install-browser-list">
            <li>Safari</li>
            <li>Chrome</li>
            <li>Samsung Internet</li>
          </ul>
        )}
        <div className="install-guide-actions">
          {canInstall && <button className="primary" onClick={onInstall}>앱 설치</button>}
          {canDismiss && <button onClick={onDismiss}>다시 보지 않기</button>}
          <button onClick={onClose}>닫기</button>
        </div>
      </section>
    </div>
  );
}
