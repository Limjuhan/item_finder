const baseURL = import.meta.env.VITE_API_URL ?? '/api';

export const getSearchStreamUrl = (keyword) =>
  `${baseURL}/search/stream?keyword=${encodeURIComponent(keyword)}`;

export const fetchTop10Keywords = () =>
  fetch(`${baseURL}/search/top10`).then((res) => res.json());
