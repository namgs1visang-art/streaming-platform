// admin-api(8081) 호출. 개발 중에는 vite proxy 로 /api/admin → 8081
async function request(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `요청 실패 (${res.status})`
    try {
      message = (await res.json()).message || message
    } catch { /* 본문 없음 */ }
    throw new Error(message)
  }
  return res.status === 204 ? null : res.json()
}

export const api = {
  channels: {
    list: () => request('GET', '/api/admin/channels'),
    create: (data) => request('POST', '/api/admin/channels', data),
    update: (id, data) => request('PUT', `/api/admin/channels/${id}`, data),
    regenerateKey: (id) => request('POST', `/api/admin/channels/${id}/stream-key`),
    remove: (id) => request('DELETE', `/api/admin/channels/${id}`),
  },
  schedules: {
    list: (from, to) => request('GET', `/api/admin/schedules?from=${from}&to=${to}`),
    create: (data) => request('POST', '/api/admin/schedules', data),
    remove: (id) => request('DELETE', `/api/admin/schedules/${id}`),
  },
}
