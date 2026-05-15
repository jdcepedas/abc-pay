'use strict';

const crypto = require('crypto');

/**
 * Builds a fresh append body for sustained append-only load tests.
 */
function prepareAppend(requestParams, _ctx, _ee, next) {
  requestParams.headers = requestParams.headers || {};
  requestParams.headers['Content-Type'] = 'application/json';
  requestParams.json = {
    eventId: crypto.randomUUID(),
    eventType: 'load.append',
    payload: { t: Date.now(), note: 'artillery-append-load' },
  };
  return next();
}

module.exports = { prepareAppend };
