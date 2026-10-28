import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const ordersCreated = new Counter('orders_created');
export const ordersSoldOut = new Counter('orders_sold_out');
export const unexpectedErrors = new Counter('unexpected_errors');

export const options = {
  scenarios: {
    saga_flash_sale: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 100,
      stages: [
        { target: 60, duration: '5s' },   // Initial burst: 100 tickets sell out quickly
        { target: 40, duration: '12s' },  // Sustained arrival: grabs compensated tickets from DLQ
        { target: 10, duration: '3s' },   // Cooldown
      ],
    },
  },
  thresholds: {
    unexpected_errors: ['count == 0'],
  },
};

export default function () {
  const url = __ENV.TARGET_URL || 'http://host.docker.internal:80/api/v1/orders';
  const vuId = __VU;
  const iterId = __ITER;

  // Exactly 20% of requests simulate payment failure (triggers retry -> DLQ -> SAGA compensation)
  const isFail = (vuId + iterId) % 5 === 0;
  const userId = isFail ? `saga-user-fail-${vuId}-${iterId}` : `saga-user-success-${vuId}-${iterId}`;
  const idempotencyKey = `idem-saga-${vuId}-${iterId}`;

  const payload = JSON.stringify({
    eventId: 'evt-1',
    userId: userId,
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
  } else {
    unexpectedErrors.add(1);
    console.error(`Unexpected status ${res.status}: ${res.body}`);
  }

  check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}
