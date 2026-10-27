import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

export const ordersCreated = new Counter('orders_created');
export const ordersSoldOut = new Counter('orders_sold_out');
export const duplicateOrders = new Counter('duplicate_orders');
export const unexpectedErrors = new Counter('unexpected_errors');

export const options = {
  scenarios: {
    flash_sale: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 5000,
      maxDuration: '60s',
    },
  },
  thresholds: {
    orders_created: ['count == 100'],
    orders_sold_out: ['count == 4900'],
    unexpected_errors: ['count == 0'],
  },
};

export default function () {
  const url = __ENV.TARGET_URL || 'http://host.docker.internal:8080/api/v1/orders';
  const vuId = __VU;
  const iterId = __ITER;
  const idempotencyKey = `user-${vuId}-iter-${iterId}`;

  const payload = JSON.stringify({
    eventId: 'evt-1',
    userId: `user-${vuId}-${iterId}`,
    ticketCount: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(url, payload, params);

  if (res.status === 201) {
    ordersCreated.add(1);
  } else if (res.status === 409) {
    ordersSoldOut.add(1);
  } else if (res.status === 200) {
    duplicateOrders.add(1);
  } else {
    unexpectedErrors.add(1);
    console.error(`Unexpected status ${res.status}: ${res.body}`);
  }

  check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}
