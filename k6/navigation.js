// Navigation scenarios: docker compose run --rm k6
// Each journey produces traces and logs you can then follow in Grafana / Jaeger.
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const CART = __ENV.CART_URL || 'http://ms-cart:8080';
const CATALOG = __ENV.CATALOG_URL || 'http://ms-catalog:8080';
const USER = __ENV.USER_URL || 'http://ms-user:8080';

// JWTs signed with the POC key (jwt/mint.sh), one per customer C-1001..C-1010
const TOKENS = JSON.parse(open('./tokens.json'));
const CUSTOMERS = Object.keys(TOKENS);

const SKUS = ['SKU-1', 'SKU-2', 'SKU-3', 'SKU-4', 'SKU-5'];

export const options = {
  scenarios: {
    // Anonymous visitor browsing the catalog: no token, so no customer.id
    anonymous_browsing: {
      executor: 'constant-vus',
      exec: 'anonymousBrowsing',
      vus: 2,
      duration: '1m',
    },
    // Logged-in customer: product pages, profile, then the cart (happy path)
    logged_in_shopping: {
      executor: 'ramping-vus',
      exec: 'loggedInShopping',
      stages: [
        { duration: '15s', target: 4 },
        { duration: '40s', target: 4 },
        { duration: '5s', target: 0 },
      ],
    },
    // A few customers whose cart contains a product the catalog cannot price -> 502
    broken_cart: {
      executor: 'constant-arrival-rate',
      exec: 'brokenCart',
      rate: 6,
      timeUnit: '1m',
      duration: '1m',
      preAllocatedVUs: 2,
    },
    // A few customers whose cart contains a product the catalog prices too slowly -> 504
    slow_cart: {
      executor: 'constant-arrival-rate',
      exec: 'slowCart',
      rate: 4,
      timeUnit: '1m',
      duration: '1m',
      preAllocatedVUs: 2,
    },
    // A cart without a token, then with a forged customer header: both refused -> 401
    unauthenticated_cart: {
      executor: 'constant-arrival-rate',
      exec: 'unauthenticatedCart',
      rate: 2,
      timeUnit: '1m',
      duration: '1m',
      preAllocatedVUs: 1,
    },
  },
  thresholds: {
    'checks{scenario:logged_in_shopping}': ['rate>0.99'],
    'http_req_duration{scenario:logged_in_shopping}': ['p(95)<500'],
  },
};

function customer() {
  const id = CUSTOMERS[(__VU - 1) % CUSTOMERS.length];
  return { id, headers: { Authorization: `Bearer ${TOKENS[id]}` } };
}

function think() {
  sleep(1 + Math.random() * 2);
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function anonymousBrowsing() {
  group('browse catalog', () => {
    for (let i = 0; i < 3; i++) {
      const res = http.get(`${CATALOG}/products/${pick(SKUS)}`, { tags: { name: 'product page' } });
      check(res, { 'product page 200': (r) => r.status === 200 });
      think();
    }
  });
}

export function loggedInShopping() {
  const { id, headers } = customer();

  group('product pages', () => {
    for (let i = 0; i < 2; i++) {
      const res = http.get(`${CATALOG}/products/${pick(SKUS)}`, { headers, tags: { name: 'product page' } });
      check(res, { 'product page 200': (r) => r.status === 200 });
      think();
    }
  });

  group('my account', () => {
    const res = http.get(`${USER}/users/${id}`, { headers, tags: { name: 'profile' } });
    check(res, { 'profile 200': (r) => r.status === 200 });
    think();
  });

  group('cart', () => {
    const res = http.get(`${CART}/carts/cart-42`, { headers, tags: { name: 'cart' } });
    check(res, {
      'cart 200': (r) => r.status === 200,
      'cart has items': (r) => r.status === 200 && r.json('items').length > 0,
    });
    think();
  });
}

export function brokenCart() {
  const res = http.get(`${CART}/carts/cart-broken`, {
    headers: customer().headers,
    tags: { name: 'cart' },
    responseCallback: http.expectedStatuses(502),
  });
  check(res, { 'broken cart answers 502': (r) => r.status === 502 });
}

export function slowCart() {
  const res = http.get(`${CART}/carts/cart-slow`, {
    headers: customer().headers,
    tags: { name: 'cart' },
    responseCallback: http.expectedStatuses(504),
  });
  check(res, { 'slow cart answers 504': (r) => r.status === 504 });
}

export function unauthenticatedCart() {
  const params = { tags: { name: 'cart' }, responseCallback: http.expectedStatuses(401) };
  const anonymous = http.get(`${CART}/carts/cart-42`, params);
  check(anonymous, { 'cart without token answers 401': (r) => r.status === 401 });

  const forged = http.get(`${CART}/carts/cart-42`, { ...params, headers: { 'x-customer-id': 'C-1001' } });
  check(forged, { 'cart with a forged header answers 401': (r) => r.status === 401 });
}
