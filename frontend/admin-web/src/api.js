// admin-api(8091) 호출. 개발 중에는 vite proxy 로 /api/admin → 8091
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
  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

const qs = (params) =>
  new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')).toString()

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
  chat: {
    rooms: () => request('GET', '/api/admin/chat/rooms'),
    freeze: (channelId, data) => request('PUT', `/api/admin/chat/rooms/${channelId}/freeze`, data),
    recent: (channelCode, limit = 150) => request('GET', `/api/admin/chat/messages/recent?${qs({ channelCode, limit })}`),
    search: (params) => request('GET', `/api/admin/chat/messages?${qs(params)}`),
    send: (channelCode, content) => request('POST', '/api/admin/chat/messages', { channelCode, content }),
    hide: (id) => request('POST', `/api/admin/chat/messages/${id}/hide`),
    unhide: (id) => request('POST', `/api/admin/chat/messages/${id}/unhide`),
    remove: (id) => request('DELETE', `/api/admin/chat/messages/${id}`),
  },
  stickers: {
    list: () => request('GET', '/api/admin/stickers'),
    create: (data) => request('POST', '/api/admin/stickers', data),
    update: (id, data) => request('PUT', `/api/admin/stickers/${id}`, data),
    remove: (id) => request('DELETE', `/api/admin/stickers/${id}`),
  },
  quizzes: {
    list: (params) => request('GET', `/api/admin/quizzes?${qs(params)}`),
    create: (data) => request('POST', '/api/admin/quizzes', data),
    update: (id, data) => request('PUT', `/api/admin/quizzes/${id}`, data),
    remove: (id) => request('DELETE', `/api/admin/quizzes/${id}`),
  },
  quizSchedules: {
    list: (from, to) => request('GET', `/api/admin/quiz-schedules?from=${from}&to=${to}`),
    detail: (scheduleId) => request('GET', `/api/admin/quiz-schedules/${scheduleId}`),
    add: (scheduleId, quizIds) => request('POST', `/api/admin/quiz-schedules/${scheduleId}/quizzes`, { quizIds }),
    remove: (scheduleId, mappingId) => request('DELETE', `/api/admin/quiz-schedules/${scheduleId}/quizzes/${mappingId}`),
    reorder: (scheduleId, mappingIds) => request('PUT', `/api/admin/quiz-schedules/${scheduleId}/quizzes/order`, { mappingIds }),
    push: (scheduleId, quizId) => request('POST', `/api/admin/quiz-schedules/${scheduleId}/pushes`, { quizId }),
    close: (pushId) => request('POST', `/api/admin/quiz-schedules/pushes/${pushId}/close`),
  },
  broadcasts: {
    list: (params) => request('GET', `/api/admin/broadcasts?${qs(params)}`),
    remove: (id) => request('DELETE', `/api/admin/broadcasts/${id}`),
  },
}
