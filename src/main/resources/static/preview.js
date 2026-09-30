const byId = id => document.getElementById(id);
// Render the URL supplied by the catalog API; no hardcoded icon map or Base64 data.
function setServiceIcon(container, service) {
  container.replaceChildren();
  if (!service?.iconUrl) return;
  const image = document.createElement('img');
  image.src = new URL(service.iconUrl, window.location.origin).href;
  image.alt = '';
  image.width = 27;
  image.height = 27;
  image.addEventListener('error', () => { image.hidden = true; });
  container.append(image);
}
let dictionary = {};
let selected;
let offline = false;
let savedAt;
let activeQuote;
let quoteTimer;
let paymentKey;
let paymentPending = false;
let paymentUncertain = false;
let receipt;

// Display formatting only: fee, VAT and total always come from the backend.
function formatPiastres(value) {
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`;
}

function updateQuoteCountdown() {
  if (!activeQuote) return;
  const remaining = Math.max(0, Math.ceil((Date.parse(activeQuote.expiresAt) - Date.now()) / 1000));
  byId('quote-countdown').textContent = `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`;
  byId('quote-label').textContent = remaining ? 'Quote expires in' : 'Quote expired';
  byId('quote-expired').hidden = remaining > 0;
  byId('quote-retry').hidden = remaining > 0;
  byId('quote-retry').disabled = paymentPending;
  byId('pay-button').disabled = paymentPending || (!remaining && !paymentUncertain);
  if (!remaining) clearInterval(quoteTimer);
}

function showBillDetails(quote) {
  activeQuote = quote;
  paymentKey = crypto.randomUUID();
  paymentUncertain = false;
  byId('payment-error').textContent = '';
  byId('pay-button').textContent = 'Pay now · Demo';
  clearInterval(quoteTimer);
  byId('bill-service-name').textContent = quote.serviceName;
  byId('bill-subscriber').textContent = quote.subscriberNumber;
  byId('bill-customer').textContent = quote.customerName;
  const [year, month] = quote.billMonth.split('-').map(Number);
  byId('bill-month').textContent = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(Date.UTC(year, month - 1, 1)));
  byId('bill-amount').textContent = formatPiastres(quote.amountDuePiastres);
  byId('bill-fee').textContent = formatPiastres(quote.serviceFeePiastres);
  byId('bill-vat').textContent = formatPiastres(quote.vatPiastres);
  byId('bill-total').textContent = formatPiastres(quote.totalPiastres);
  byId('bill-currency').textContent = quote.currency;
  const category = Object.keys(dictionary).find(key => dictionary[key].some(service => service.id === quote.serviceId));
  const service = Object.values(dictionary).flat().find(item => item.id === quote.serviceId);
  setServiceIcon(byId('bill-icon'), service);
  byId('bill-icon').parentElement.className = `bill-service ${category || ''}`;
  byId('inquiry-screen').hidden = true;
  byId('bill-screen').hidden = false;
  document.title = 'MMKN — Bill details';
  updateQuoteCountdown();
  if (Date.parse(quote.expiresAt) > Date.now()) quoteTimer = setInterval(updateQuoteCountdown, 1000);
  byId('bill-back').focus();
  window.scrollTo(0, 0);
}

function returnToInquiry() {
  clearInterval(quoteTimer);
  activeQuote = undefined;
  byId('bill-screen').hidden = true;
  byId('inquiry-screen').hidden = false;
  document.title = 'MMKN — Fees inquiry';
  byId('subscriber').focus();
  window.scrollTo(0, 0);
}

byId('bill-back').addEventListener('click', returnToInquiry);
byId('quote-retry').addEventListener('click', returnToInquiry);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) updateQuoteCountdown();
});

function renderServices() {
  const query = byId('search').value.trim().toLowerCase();
  const container = byId('services');
  container.replaceChildren();
  let count = 0;
  const total = Object.values(dictionary).flat().length;
  for (const [category, services] of Object.entries(dictionary)) {
    const matches = services.filter(s => category.includes(query) || s.name.toLowerCase().includes(query));
    if (!matches.length) continue;
    const heading = document.createElement('h2');
    heading.textContent = category;
    container.append(heading);
    for (const service of matches) {
      count++;
      const button = document.createElement('button');
      button.className = `service ${category}`;
      button.disabled = !service.available;
      const icon = document.createElement('span');
      icon.className = 'icon'; setServiceIcon(icon, service); icon.setAttribute('aria-hidden', 'true');
      const name = document.createElement('strong'); name.textContent = service.name;
      const state = document.createElement('span'); state.className = service.available ? 'arrow' : 'badge';
      state.textContent = service.available ? '›' : 'UNAVAILABLE';
      button.append(icon, name, state);
      button.addEventListener('click', () => openInquiry(service, category));
      container.append(button);
    }
  }
  byId('catalog-status').textContent = offline
    ? `Offline — showing saved list · ${new Date(savedAt).toLocaleString('en-GB')}`
    : `${count} of ${total} services${query ? ' · Searched on device' : ''}`;
  if (!count) container.textContent = 'No services found.';
}

async function loadServices() {
  byId('catalog-status').textContent = 'Loading services…';
  byId('retry').hidden = true;
  try {
    const response = await fetch('/api/v1/services');
    if (!response.ok) throw new Error('Could not load services.');
    dictionary = await response.json(); offline = false; savedAt = new Date().toISOString();
    try { localStorage.setItem('mmkn-catalog-v3', JSON.stringify({ dictionary, savedAt })); } catch { /* Storage can be unavailable. */ }
    renderServices();
  } catch {
    try {
      const saved = JSON.parse(localStorage.getItem('mmkn-catalog-v3'));
      if (!saved?.dictionary || !saved.savedAt) throw new Error();
      dictionary = saved.dictionary; savedAt = saved.savedAt; offline = true; renderServices();
    } catch { byId('catalog-status').textContent = 'Unable to load services. Check your connection and retry.'; }
    byId('retry').hidden = false;
  }
}

function openInquiry(service, category) {
  selected = service;
  byId('catalog-screen').hidden = true; byId('inquiry-screen').hidden = false;
  byId('selected-name').textContent = service.name;
  setServiceIcon(byId('selected-icon'), service);
  byId('selected-icon').parentElement.className = `service selected ${category}`;
  byId('subscriber').value = '';
  byId('subscriber').removeAttribute('aria-invalid');
  byId('inquiry-error').textContent = '';
  document.title = 'MMKN — Fees inquiry';
  byId('subscriber').focus();
}

byId('search').addEventListener('input', renderServices);
byId('retry').addEventListener('click', loadServices);
byId('back').addEventListener('click', () => {
  byId('inquiry-screen').hidden = true; byId('catalog-screen').hidden = false;
  document.title = 'MMKN — Services';
  byId('search').focus();
});
byId('subscriber').addEventListener('input', () => {
  byId('inquiry-error').textContent = '';
  byId('subscriber').removeAttribute('aria-invalid');
});
byId('inquiry-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (byId('check-bill').disabled) return;
  const subscriberNumber = byId('subscriber').value.replace(/\s/g, '');
  if (!/^[0-9]{10}$/.test(subscriberNumber)) {
    byId('inquiry-error').textContent = 'Enter a valid 10-digit subscriber number.';
    byId('subscriber').setAttribute('aria-invalid', 'true'); byId('subscriber').focus(); return;
  }
  byId('check-bill').disabled = true; byId('back').disabled = true; byId('subscriber').readOnly = true;
  byId('check-bill').textContent = 'Checking…'; byId('inquiry-error').textContent = '';
  try {
    const response = await fetch('/api/v1/inquiries', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serviceId: selected.id, subscriberNumber })
    });
    const result = await response.json();
    if (!response.ok) {
      byId('inquiry-error').textContent = result.message || 'Unable to check this bill.'; return;
    }
    // A successful API response supplies the next screen's data.
    // Navigation is a frontend responsibility; the API never redirects the browser.
    showBillDetails(result);
  } catch { byId('inquiry-error').textContent = 'Unable to connect. Please try again.'; }
  finally {
    byId('check-bill').disabled = false; byId('back').disabled = false; byId('subscriber').readOnly = false;
    byId('check-bill').textContent = 'Check my bill';
  }
});
byId('pay-button').addEventListener('click', async () => {
  if (paymentPending || !activeQuote) return;
  paymentPending = true;
  byId('pay-button').disabled = true;
  byId('pay-button').textContent = 'Processing…';
  byId('bill-back').disabled = true;
  byId('quote-retry').disabled = true;
  byId('payment-error').textContent = '';
  try {
    const response = await fetch('/api/v1/payments', {
      method: 'POST', headers: {'Content-Type':'application/json', 'Idempotency-Key':paymentKey},
      body: JSON.stringify({quoteId:activeQuote.quoteId})
    });
    const result = await response.json();
    if (!response.ok) {
      paymentUncertain = response.status >= 500;
      byId('payment-error').textContent = result.message || 'Unable to complete payment.';
      return;
    }
    receipt = result;
    clearInterval(quoteTimer);
    activeQuote = undefined;
    byId('receipt-total').textContent = formatPiastres(result.totalPiastres);
    byId('receipt-currency').textContent = result.currency;
    byId('receipt-service').textContent = result.serviceName;
    byId('receipt-subscriber').textContent = result.subscriberNumber;
    byId('receipt-date').textContent = new Intl.DateTimeFormat('en-GB', {dateStyle:'medium',timeStyle:'short',timeZone:'Africa/Cairo'}).format(new Date(result.paidAt)) + ' (Cairo)';
    byId('receipt-reference').textContent = result.reference;
    byId('bill-screen').hidden = true;
    byId('receipt-screen').hidden = false;
    byId('copy-status').textContent = '';
    document.title = 'MMKN — Payment successful';
    byId('copy-reference').focus();
    window.scrollTo(0,0);
  } catch {
    // Reuse the SAME key after a lost response; the server may have recorded payment.
    paymentUncertain = true;
    byId('payment-error').textContent = 'Could not confirm payment. Retry safely using the same payment request.';
  } finally {
    paymentPending = false;
    byId('bill-back').disabled = false;
    byId('pay-button').textContent = paymentUncertain ? 'Retry payment safely' : 'Pay now · Demo';
    updateQuoteCountdown();
  }
});
byId('copy-reference').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(receipt.reference); byId('copy-status').textContent = 'Reference copied.'; }
  catch { byId('copy-status').textContent = 'Copy unavailable. Select and copy the reference above.'; }
});
byId('receipt-home').addEventListener('click', () => {
  byId('receipt-screen').hidden = true;
  byId('catalog-screen').hidden = false;
  document.title = 'MMKN — Services';
  byId('search').focus();
});
loadServices();
