const baseURL = import.meta.env.VITE_API_URL ?? '/api';

export const getSearchStreamUrl = (keyword) =>
  `${baseURL}/search/stream?keyword=${encodeURIComponent(keyword)}`;
