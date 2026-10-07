// 좌측 메뉴 정의. ready: false 인 메뉴는 아직 준비 중 화면으로 연결
export const MENU = [
  {
    group: 'LIVE',
    items: [
      { path: '/live/channels', label: '채널 관리', ready: true },
      { path: '/live/schedules', label: '스케줄 관리', ready: true },
      { path: '/live/programming', label: '편성 관리' },
      { path: '/live/monitoring', label: '모니터링' },
    ],
  },
  {
    group: '퀴즈 관리',
    items: [
      { path: '/quiz/questions', label: '퀴즈 관리', ready: true },
      { path: '/quiz/programming', label: '편성 퀴즈 관리', ready: true },
    ],
  },
  {
    group: 'VOD',
    items: [
      { path: '/vod/recordings', label: '방송 녹화 관리', ready: true },
      { path: '/vod/contents', label: '콘텐츠 관리' },
      { path: '/vod/profiles', label: '프로파일 관리' },
      { path: '/vod/packaging', label: '패키징 채널 관리' },
      { path: '/vod/categories', label: '카테고리 관리' },
    ],
  },
  {
    group: '채팅 관리',
    items: [
      { path: '/chat/messages', label: '채팅 메시지 관리', ready: true },
      { path: '/chat/stickers', label: '스티커 관리', ready: true },
      { path: '/chat/users', label: '채팅 사용자 관리' },
    ],
  },
  {
    group: '관리자',
    items: [{ path: '/admin/accounts', label: '계정 관리' }],
  },
]
