(() => {
  const installButton = document.querySelector('#install-btn');
  const appNote = document.querySelector('#app-note');
  const message = document.querySelector('#message');
  let deferredPrompt = null;

  const isStandalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
  const isIOS = /iphone|ipad|ipod/i.test(window.navigator.userAgent);

  function notify(text, tone = 'info') {
    if (!message) return;
    message.textContent = text;
    message.dataset.tone = tone;
    setTimeout(() => {
      if (message.textContent === text) message.textContent = '';
    }, 4500);
  }

  async function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    try {
      await navigator.serviceWorker.register('./sw.js');
    } catch (error) {
      console.warn('LINKPAS service worker gagal didaftarkan:', error);
    }
  }

  function updateInstallUI() {
    if (!installButton) return;
    if (isStandalone) {
      installButton.hidden = true;
      if (appNote) appNote.textContent = 'LINKPAS terpasang sebagai aplikasi di perangkat ini.';
      return;
    }
    installButton.hidden = !(deferredPrompt || isIOS);
  }

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    deferredPrompt = event;
    updateInstallUI();
  });

  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    if (installButton) installButton.hidden = true;
    if (appNote) appNote.textContent = 'LINKPAS berhasil dipasang sebagai aplikasi.';
    notify('LINKPAS berhasil di-install.', 'success');
  });

  installButton?.addEventListener('click', async () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice.outcome === 'accepted') {
        deferredPrompt = null;
        installButton.hidden = true;
      }
      return;
    }
    if (isIOS) {
      notify('Di Safari: tekan Share lalu pilih “Add to Home Screen”.');
      return;
    }
    notify('Gunakan menu browser lalu pilih Install app / Add to Home Screen.');
  });

  registerServiceWorker();
  updateInstallUI();
})();
